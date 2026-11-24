package com.example.minenote;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Minenote Reader: manages feeds and displays fetched articles. */
public class ReaderActivity extends AppCompatActivity {
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private boolean showSaved = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,
                        AppDatabaseMigrationV5.MIGRATION_4_5,
                        ReaderDatabaseMigrationV6.MIGRATION_5_6).build();
        buildUi();
        reload();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250,248,242));
        root.setPadding(dp(16),dp(12),dp(16),dp(12));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹",30,Color.DKGRAY,true);
        header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        back.setOnClickListener(v -> finish());
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Minenote Reader",22,Color.rgb(35,35,35),true));
        titles.addView(text("订阅、阅读，并把知识收进笔记",12,Color.GRAY,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView add = text("＋ 添加源",14,Color.rgb(95,70,25),true);
        add.setPadding(dp(12),dp(10),dp(12),dp(10));
        add.setBackground(round(Color.rgb(245,226,180),18));
        add.setOnClickListener(v -> showAddSourceDialog());
        header.addView(add); root.addView(header);

        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(0,dp(12),0,dp(8));
        TextView all = tab("最新文章",!showSaved); all.setOnClickListener(v->{showSaved=false;reload();});
        TextView saved = tab("已收藏",showSaved); saved.setOnClickListener(v->{showSaved=true;reload();});
        tabs.addView(all); tabs.addView(saved); root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private TextView tab(String label, boolean active) {
        TextView t=text(label,14,active?Color.rgb(112,82,27):Color.GRAY,active);
        t.setPadding(dp(14),dp(9),dp(14),dp(9));
        t.setBackground(round(active?Color.rgb(245,232,199):Color.TRANSPARENT,18));
        return t;
    }

    private void reload() {
        io.execute(() -> {
            List<ReaderSource> sources=db.readerDao().getSources();
            List<ReaderArticle> articles=showSaved?db.readerDao().getSavedArticles():db.readerDao().getArticles();
            runOnUiThread(() -> render(sources,articles));
        });
    }

    private void render(List<ReaderSource> sources,List<ReaderArticle> articles) {
        content.removeAllViews();
        TextView sourceTitle=text("我的订阅源  ·  "+sources.size(),16,Color.rgb(45,45,45),true);
        sourceTitle.setPadding(0,dp(8),0,dp(8)); content.addView(sourceTitle);
        if(sources.isEmpty()) content.addView(empty("还没有订阅源，点击右上角添加 RSS 或网站地址。"));
        for(ReaderSource source:sources) {
            LinearLayout row=card();
            LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=text(source.title,15,Color.rgb(45,45,45),true);
            line.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView refresh=text("刷新",13,Color.rgb(125,91,31),true);
            refresh.setOnClickListener(v->refreshSource(source)); line.addView(refresh);
            TextView delete=text("  ⋯",20,Color.GRAY,false);
            delete.setOnClickListener(v->new AlertDialog.Builder(this).setItems(new String[]{"删除订阅源"},(d,w)->deleteSource(source)).show());
            line.addView(delete); row.addView(line);
            TextView url=text(source.url,11,Color.GRAY,false); row.addView(url);
            content.addView(row);
        }
        TextView articleTitle=text(showSaved?"收藏文章":"最新文章",17,Color.rgb(45,45,45),true);
        articleTitle.setPadding(0,dp(18),0,dp(8)); content.addView(articleTitle);
        if(articles.isEmpty()) content.addView(empty("暂无文章。添加订阅源后，点击刷新获取内容。"));
        for(ReaderArticle article:articles) {
            LinearLayout row=card();
            row.addView(text(article.title,16,Color.rgb(40,40,40),true));
            String desc=article.summary==null?"":article.summary;
            if(desc.length()>150) desc=desc.substring(0,150)+"…";
            TextView summary=text(desc,13,Color.rgb(105,105,105),false);
            summary.setPadding(0,dp(7),0,dp(5)); row.addView(summary);
            row.addView(text((article.isRead?"已读":"未读")+"  ·  "+(article.author==null?"":article.author),11,Color.GRAY,false));
            row.setOnClickListener(v->{Intent i=new Intent(this,ReaderArticleActivity.class);i.putExtra("article_id",article.id);startActivity(i);});
            content.addView(row);
        }
    }

    private void showAddSourceDialog() {
        EditText input=new EditText(this); input.setSingleLine(true); input.setHint("粘贴 RSS 地址或网站地址");
        new AlertDialog.Builder(this).setTitle("添加订阅源").setMessage("支持 RSS / Atom 地址，也可以输入网站主页自动发现订阅源。")
                .setView(input).setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->addSource(input.getText().toString().trim())).show();
    }

    private void addSource(String raw) {
        if(raw.isEmpty()) return;
        io.execute(() -> {
            try {
                String url=ReaderFetcher.normalizeHttpUrl(raw);
                String feed;
                List<ReaderFetcher.ParsedArticle> parsed;
                try { parsed=ReaderFetcher.fetchFeed(url); feed=url; }
                catch(Exception notFeed) { feed=ReaderFetcher.discoverFeed(url); parsed=ReaderFetcher.fetchFeed(feed); }
                final String feedUrl=feed;
                String title=raw;
                try { org.jsoup.nodes.Document doc=org.jsoup.Jsoup.connect(url).timeout(8000).get(); if(!doc.title().isEmpty()) title=doc.title(); } catch(Exception ignored) {}
                ReaderSource source=new ReaderSource(title,url,feedUrl,System.currentTimeMillis());
                long id=db.readerDao().insertSource(source); source.id=(int)id;
                saveParsed(source,parsed);
                runOnUiThread(()->{Toast.makeText(this,"订阅源添加成功",Toast.LENGTH_SHORT).show();reload();});
            } catch(Exception e) { runOnUiThread(()->new AlertDialog.Builder(this).setTitle("添加失败").setMessage(e.getMessage()).setPositiveButton("确定",null).show()); }
        });
    }

    private void refreshSource(ReaderSource source) {
        Toast.makeText(this,"正在刷新："+source.title,Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try {
                List<ReaderFetcher.ParsedArticle> parsed=ReaderFetcher.fetchFeed(source.feedUrl);
                saveParsed(source,parsed);
                source.lastFetchedAt=System.currentTimeMillis(); source.lastError="";
                db.readerDao().updateSource(source);
                runOnUiThread(()->{Toast.makeText(this,"已更新 "+parsed.size()+" 篇文章",Toast.LENGTH_SHORT).show();reload();});
            } catch(Exception e) {
                source.lastError=e.getMessage(); db.readerDao().updateSource(source);
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("刷新失败").setMessage(e.getMessage()).setPositiveButton("确定",null).show());
            }
        });
    }

    private void saveParsed(ReaderSource source,List<ReaderFetcher.ParsedArticle> parsed) {
        for(ReaderFetcher.ParsedArticle p:parsed) {
            ReaderArticle old=db.readerDao().findByUrl(p.url);
            if(old==null) db.readerDao().insertArticle(new ReaderArticle(source.id,p.guid,p.title,p.url,p.author,p.summary,p.content,p.publishedAt));
        }
    }

    private void deleteSource(ReaderSource source) {
        io.execute(()->{db.readerDao().deleteArticlesForSource(source.id);db.readerDao().deleteSource(source.id);runOnUiThread(this::reload);});
    }

    private LinearLayout card() {
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14),dp(13),dp(14),dp(13)); c.setBackground(round(Color.WHITE,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);c.setLayoutParams(p);return c;
    }
    private TextView empty(String s){TextView t=text(s,13,Color.GRAY,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));return t;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onResume(){super.onResume();if(db!=null)reload();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}