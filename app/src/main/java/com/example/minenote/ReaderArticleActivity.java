package com.example.minenote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native text article reader with selection actions; intentionally no WebView. */
public class ReaderArticleActivity extends AppCompatActivity {
    private AppDatabase db;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private ReaderArticle article;
    private TextView bodyView;
    private String selectedText="";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db=Room.databaseBuilder(getApplicationContext(),AppDatabase.class,"minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,AppDatabaseMigrationV5.MIGRATION_4_5,
                        ReaderDatabaseMigrationV6.MIGRATION_5_6).build();
        buildLoading();
        int id=getIntent().getIntExtra("article_id",-1);
        io.execute(()->{article=db.readerDao().getArticle(id);if(article==null){runOnUiThread(this::finish);return;}
            String fallback=article.content==null?article.summary:article.content;
            String text=ReaderFetcher.extractArticle(article.url,fallback);
            article.content=text; article.isRead=true; db.readerDao().updateArticle(article);
            String finalText=text;runOnUiThread(()->render(finalText));});
    }

    private void buildLoading(){
        TextView loading=new TextView(this);loading.setText("正在整理文章…");loading.setTextSize(16);loading.setPadding(24,40,24,24);setContentView(loading);
    }

    private void render(String articleText) {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(250,248,242));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.setPadding(dp(14),dp(10),dp(14),dp(10));
        TextView back=label("‹",30,Color.DKGRAY,true);bar.addView(back);back.setOnClickListener(v->finish());
        TextView source=label("  Reader",14,Color.GRAY,false);bar.addView(source,new LinearLayout.LayoutParams(0,-2,1));
        TextView save=label(article.isSaved?"★":"☆",25,Color.rgb(176,132,39),true);bar.addView(save);
        save.setOnClickListener(v->{article.isSaved=!article.isSaved;save.setText(article.isSaved?"★":"☆");io.execute(()->db.readerDao().updateArticle(article));});
        root.addView(bar);
        ScrollView scroll=new ScrollView(this);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(14),dp(22),dp(36));
        TextView title=label(article.title,26,Color.rgb(35,35,35),true);title.setLineSpacing(dp(3),1.08f);page.addView(title);
        String meta=(article.author==null?"":article.author+"  ·  ")+(article.url==null?"":article.url);
        TextView metadata=label(meta,11,Color.GRAY,false);metadata.setPadding(0,dp(10),0,dp(22));page.addView(metadata);
        bodyView=label(articleText==null||articleText.isEmpty()?"暂时无法提取正文。你可以查看原网页，或使用 RSS 摘要。":articleText,17,Color.rgb(54,52,48),false);
        bodyView.setTextIsSelectable(true);bodyView.setLineSpacing(dp(7),1.25f);bodyView.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
            @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,1,1,"加入笔记");menu.add(0,2,2,"询问 AI");return true;}
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){int a=bodyView.getSelectionStart(),b=bodyView.getSelectionEnd();if(a<0||b<=a)return false;selectedText=bodyView.getText().subSequence(a,b).toString();mode.finish();if(item.getItemId()==1)showNotePicker();else askAi();return true;}
            @Override public void onDestroyActionMode(ActionMode mode){}
        });
        page.addView(bodyView);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView original=label("打开原网页 ↗",13,Color.rgb(145,105,35),true);original.setPadding(dp(20),dp(13),dp(20),dp(13));original.setOnClickListener(v->{try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(article.url)));}catch(Exception ignored){}});root.addView(original);
        setContentView(root);
    }

    private void showNotePicker(){
        io.execute(()->{List<Note> notes=db.noteDao().getAllNotes();runOnUiThread(()->{
            if(notes.isEmpty()){Toast.makeText(this,"请先创建一篇笔记",Toast.LENGTH_SHORT).show();return;}
            String[] labels=new String[notes.size()];for(int i=0;i<notes.size();i++)labels[i]=(notes.get(i).getTitle()==null?"未命名笔记":notes.get(i).getTitle());
            new AlertDialog.Builder(this).setTitle("加入哪篇笔记？").setItems(labels,(d,which)->appendToNote(notes.get(which))).setNegativeButton("取消",null).show();
        });});
    }

    private void appendToNote(Note note){
        String excerpt="\n\n> "+selectedText.replace("\n","\n> ")+"\n\n来源：《"+article.title+"》\n"+article.url+"\n";
        io.execute(()->{String old=note.getContent()==null?"":note.getContent();note.setContent(old+excerpt);db.noteDao().update(note);runOnUiThread(()->Toast.makeText(this,"已加入「"+note.getTitle()+"」",Toast.LENGTH_SHORT).show());});
    }

    private void askAi(){
        new AIClient(this).analyzeNote("文章：《"+article.title+"》\n文章地址："+article.url+"\n\n选中的原文：\n"+selectedText,
                "解释以下文章摘录，先用通俗中文解释，再给出必要背景和实际例子。不要超出摘录内容臆造事实。",
                new AIClient.Callback(){@Override public void onSuccess(String result){runOnUiThread(()->new AlertDialog.Builder(ReaderArticleActivity.this).setTitle("AI 阅读助手").setMessage(result).setPositiveButton("知道了",null).show());}
                @Override public void onError(String error){runOnUiThread(()->new AlertDialog.Builder(ReaderArticleActivity.this).setTitle("AI 请求失败").setMessage(error).setPositiveButton("确定",null).show());}});
    }

    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}