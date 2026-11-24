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
    private ReaderSource sourceInfo;
    private TextView bodyView;
    private float bodyTextSize=18f;
    private String selectedText="";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db=Room.databaseBuilder(getApplicationContext(),AppDatabase.class,"minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,AppDatabaseMigrationV5.MIGRATION_4_5,
                        ReaderDatabaseMigrationV6.MIGRATION_5_6).build();
        buildLoading();
        int id=getIntent().getIntExtra("article_id",-1);
        io.execute(()->{article=db.readerDao().getArticle(id);if(article==null){runOnUiThread(this::finish);return;} sourceInfo=db.readerDao().getSource(article.sourceId);
            String fallback=article.content==null?article.summary:article.content;
            String text=ReaderFetcher.extractArticle(article.url,fallback);
            article.content=text; article.isRead=true; db.readerDao().updateArticle(article);
            String finalText=text;runOnUiThread(()->render(finalText));});
    }

    private void buildLoading(){
        TextView loading=new TextView(this);loading.setText("正在整理文章…");loading.setTextSize(16);loading.setPadding(24,40,24,24);setContentView(loading);
    }

    private void render(String articleText) {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,247,242));
        if(android.os.Build.VERSION.SDK_INT>=30){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(9),dp(16),dp(9));
        TextView back=label("‹",30,Color.rgb(55,52,46),false);back.setGravity(android.view.Gravity.CENTER);bar.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));back.setOnClickListener(v->finish());
        LinearLayout barTitle=new LinearLayout(this);barTitle.setOrientation(LinearLayout.VERTICAL);
        barTitle.addView(label("Reader",15,Color.rgb(52,50,45),true));
        barTitle.addView(label("沉浸阅读",10,Color.rgb(145,140,130),false));
        bar.addView(barTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView save=label(article.isSaved?"★":"☆",25,Color.rgb(177,133,49),true);save.setGravity(android.view.Gravity.CENTER);bar.addView(save,new LinearLayout.LayoutParams(dp(42),dp(42)));
        save.setOnClickListener(v->{article.isSaved=!article.isSaved;save.setText(article.isSaved?"★":"☆");io.execute(()->db.readerDao().updateArticle(article));});
        root.addView(bar);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(23),dp(24),dp(23),dp(34));
        TextView sourceLabel=label(sourceDisplayName().toUpperCase(java.util.Locale.ROOT),11,Color.rgb(153,119,58),true);
        sourceLabel.setPadding(0,0,0,dp(13));page.addView(sourceLabel);
        TextView title=label(article.title,29,Color.rgb(39,38,34),true);title.setLineSpacing(dp(4),1.04f);page.addView(title);
        String author=article.author==null||article.author.trim().isEmpty()?"":article.author+"  ·  ";
        TextView metadata=label(author+dateLabel(article.publishedAt),12,Color.rgb(142,137,127),false);
        metadata.setPadding(0,dp(14),0,dp(23));page.addView(metadata);
        View rule=new View(this);rule.setBackgroundColor(Color.rgb(230,225,214));page.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        bodyView=label(articleText==null||articleText.isEmpty()?"暂时无法提取正文。你可以查看原网页，或使用 RSS 摘要。":articleText,(int)bodyTextSize,Color.rgb(61,58,52),false);
        bodyView.setPadding(0,dp(24),0,dp(20));bodyView.setTextIsSelectable(true);bodyView.setLineSpacing(dp(8),1.27f);
        bodyView.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
            @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,1,1,"加入笔记");menu.add(0,2,2,"询问 AI");return true;}
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){int a=bodyView.getSelectionStart(),b=bodyView.getSelectionEnd();if(a<0||b<=a)return false;selectedText=bodyView.getText().subSequence(a,b).toString();mode.finish();if(item.getItemId()==1)showNotePicker();else askAi();return true;}
            @Override public void onDestroyActionMode(ActionMode mode){}
        });
        page.addView(bodyView);
        TextView sourceNote=label("本文由 Minenote Reader 整理 · 原文内容及版权归原作者所有",10,Color.rgb(157,151,140),false);
        sourceNote.setPadding(0,dp(20),0,dp(10));page.addView(sourceNote);
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);bottom.setPadding(dp(16),dp(9),dp(16),dp(9));bottom.setBackgroundColor(Color.WHITE);
        TextView smaller=bottomAction("A−");smaller.setOnClickListener(v->{bodyTextSize=Math.max(14,bodyTextSize-1);bodyView.setTextSize(bodyTextSize);});
        TextView bigger=bottomAction("A＋");bigger.setOnClickListener(v->{bodyTextSize=Math.min(26,bodyTextSize+1);bodyView.setTextSize(bodyTextSize);});
        bottom.addView(smaller);bottom.addView(bigger);
        TextView spacer=label("原文网页 ↗",13,Color.rgb(142,103,36),true);spacer.setGravity(android.view.Gravity.CENTER);bottom.addView(spacer,new LinearLayout.LayoutParams(0,dp(42),1));
        spacer.setOnClickListener(v->{try{startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(article.url)));}catch(Exception ignored){}});
        root.addView(bottom);setContentView(root);
    }

    private TextView bottomAction(String value){TextView t=label(value,15,Color.rgb(83,77,66),true);t.setGravity(android.view.Gravity.CENTER);t.setBackground(roundBg());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(40));p.rightMargin=dp(7);t.setLayoutParams(p);return t;}
    private android.graphics.drawable.GradientDrawable roundBg(){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(Color.rgb(247,243,234));d.setCornerRadius(dp(13));return d;}
    private String sourceDisplayName(){
        if(sourceInfo==null)return "MINENOTE READER";
        String title=sourceInfo.title==null?"":sourceInfo.title.trim();
        if(title.isEmpty()||title.matches("(?i)^https?://.*")){
            try{String host=new java.net.URI(sourceInfo.url).getHost();if(host!=null)return host.replaceFirst("(?i)^www\\.","");}
            catch(Exception ignored){}
        }
        return title;
    }
    private String dateLabel(long time){if(time<=0)return "";return new java.text.SimpleDateFormat("yyyy年M月d日",java.util.Locale.CHINA).format(new java.util.Date(time));}

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