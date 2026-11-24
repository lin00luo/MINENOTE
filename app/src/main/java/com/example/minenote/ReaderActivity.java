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
import android.widget.HorizontalScrollView;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
    private int filterMode = 0; // 0 all, 1 unread, 2 saved
    private int selectedSourceId = -1;
    private String searchQuery = "";

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
        root.setBackgroundColor(Color.rgb(248,247,242));
        root.setPadding(dp(18),dp(10),dp(18),0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹",30,Color.rgb(52,50,45),false);
        header.addView(back,new LinearLayout.LayoutParams(dp(38),dp(48)));
        back.setOnClickListener(v -> finish());
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Reader",27,Color.rgb(37,37,34),true));
        titles.addView(text("把值得读的内容，慢慢收进来",12,Color.rgb(139,136,126),false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView search = action("⌕",24); search.setOnClickListener(v->showSearchDialog()); header.addView(search);
        TextView add = action("＋",23); add.setBackground(round(Color.rgb(242,222,174),dp(15)));
        add.setOnClickListener(v -> showAddSourceDialog()); header.addView(add);
        root.addView(header);

        LinearLayout intro = new LinearLayout(this); intro.setOrientation(LinearLayout.VERTICAL);
        intro.setPadding(0,dp(22),0,dp(12));
        intro.addView(text("今日阅读",17,Color.rgb(55,53,48),true));
        intro.addView(text("订阅你感兴趣的内容，保持好奇。",12,Color.rgb(145,141,132),false));
        root.addView(intro);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0,0,0,dp(12));
        String[] names={"全部","未读","收藏"};
        for(int i=0;i<names.length;i++){
            final int mode=i;
            TextView chip=tab(names[i],filterMode==i);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=dp(8);
            tabs.addView(chip,p);
            chip.setOnClickListener(v->{filterMode=mode;reload();});
        }
        root.addView(tabs);

        HorizontalScrollView sourceScroll=new HorizontalScrollView(this);
        sourceScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout sourceStrip=new LinearLayout(this);sourceStrip.setGravity(Gravity.CENTER_VERTICAL);
        sourceStrip.setPadding(0,0,0,dp(14));
        sourceStrip.setTag("sourceStrip");
        sourceScroll.addView(sourceStrip);root.addView(sourceScroll);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,dp(2),0,dp(24));
        scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private TextView action(String label,int size){
        TextView t=text(label,size,Color.rgb(65,62,55),true);
        t.setGravity(Gravity.CENTER);t.setBackground(round(Color.WHITE,dp(15)));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(42),dp(42)));
        return t;
    }

    private TextView tab(String label, boolean active) {
        TextView t=text(label,13,active?Color.rgb(91,68,28):Color.rgb(120,117,108),active);
        t.setPadding(dp(16),dp(9),dp(16),dp(9));
        t.setBackground(round(active?Color.rgb(242,226,190):Color.WHITE,dp(18)));
        return t;
    }

    private void reload() {
        io.execute(() -> {
            List<ReaderSource> sources=db.readerDao().getSources();
            List<ReaderArticle> articles;
            if(filterMode==2) articles=db.readerDao().getSavedArticles();
            else articles=db.readerDao().getArticles();
            List<ReaderArticle> visible=new ArrayList<>();
            for(ReaderArticle a:articles){
                if(filterMode==1 && a.isRead) continue;
                if(selectedSourceId!=-1 && a.sourceId!=selectedSourceId) continue;
                String q=searchQuery.toLowerCase(Locale.ROOT);
                if(!q.isEmpty() && !(safe(a.title).toLowerCase(Locale.ROOT).contains(q)
                        || safe(a.summary).toLowerCase(Locale.ROOT).contains(q)
                        || safe(a.author).toLowerCase(Locale.ROOT).contains(q))) continue;
                visible.add(a);
            }
            runOnUiThread(() -> render(sources,visible));
        });
    }

    private void render(List<ReaderSource> sources,List<ReaderArticle> articles) {
        content.removeAllViews();
        View stripView=findViewById(android.R.id.content);
        HorizontalScrollView sourceScroll=findSourceScroll((LinearLayout)((android.view.ViewGroup)stripView).getChildAt(0));
        if(sourceScroll!=null){
            LinearLayout strip=(LinearLayout)sourceScroll.getChildAt(0);strip.removeAllViews();
            TextView all=tab("全部来源",selectedSourceId==-1);strip.addView(all);
            all.setOnClickListener(v->{selectedSourceId=-1;reload();});
            for(ReaderSource source:sources){
                TextView chip=tab(source.title,selectedSourceId==source.id);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.leftMargin=dp(7);strip.addView(chip,cp);
                chip.setOnClickListener(v->{selectedSourceId=source.id;reload();});
                chip.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle(source.title)
                        .setItems(new String[]{"刷新订阅","删除订阅"},(d,w)->{if(w==0)refreshSource(source);else new AlertDialog.Builder(this).setMessage("删除订阅源及其文章？").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->deleteSource(source)).show();}).show();return true;});
            }
        }
        TextView heading=text(filterMode==2?"稍后阅读":filterMode==1?"未读文章":"最新文章",17,Color.rgb(52,50,45),true);
        heading.setPadding(0,dp(8),0,dp(12));content.addView(heading);
        if(articles.isEmpty()){
            LinearLayout emptyCard=card();emptyCard.setPadding(dp(20),dp(24),dp(20),dp(24));
            TextView icon=text("✦",25,Color.rgb(184,143,59),true);icon.setGravity(Gravity.CENTER);emptyCard.addView(icon);
            TextView title=text(sources.isEmpty()?"从一个喜欢的网站开始":"这里暂时很安静",16,Color.rgb(55,52,46),true);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(8),0,dp(5));emptyCard.addView(title);
            TextView desc=text(sources.isEmpty()?"添加 RSS / Atom 地址，或直接粘贴网站主页。":"试试切换分类，或刷新订阅源获取新文章。",13,Color.rgb(137,133,123),false);desc.setGravity(Gravity.CENTER);emptyCard.addView(desc);
            content.addView(emptyCard);
            if(sources.isEmpty()){TextView add=tab("＋ 添加第一个订阅源",true);add.setGravity(Gravity.CENTER);add.setOnClickListener(v->showAddSourceDialog());content.addView(add);}
        }
        for(ReaderArticle article:articles){
            LinearLayout item=card();item.setPadding(dp(17),dp(16),dp(17),dp(16));
            LinearLayout metaLine=new LinearLayout(this);metaLine.setGravity(Gravity.CENTER_VERTICAL);
            View dot=new View(this);dot.setBackground(round(article.isRead?Color.rgb(205,202,193):Color.rgb(190,143,55),dp(5)));
            metaLine.addView(dot,new LinearLayout.LayoutParams(dp(8),dp(8)));
            TextView sourceName=text("  "+sourceName(sources,article.sourceId),11,Color.rgb(135,130,119),true);
            metaLine.addView(sourceName,new LinearLayout.LayoutParams(0,-2,1));
            metaLine.addView(text(dateLabel(article.publishedAt),10,Color.rgb(157,152,142),false));
            item.addView(metaLine);
            TextView title=text(article.title,17,article.isRead?Color.rgb(103,101,95):Color.rgb(39,39,36),!article.isRead);
            title.setPadding(0,dp(10),0,dp(7));title.setLineSpacing(dp(2),1.05f);item.addView(title);
            String desc=safe(article.summary).replaceAll("\\s+"," ").trim();
            if(desc.length()>175)desc=desc.substring(0,175)+"…";
            if(!desc.isEmpty()){TextView summary=text(desc,13,Color.rgb(119,116,108),false);summary.setLineSpacing(dp(3),1.08f);item.addView(summary);}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);item.setLayoutParams(p);
            item.setOnClickListener(v->{Intent i=new Intent(this,ReaderArticleActivity.class);i.putExtra("article_id",article.id);startActivity(i);});
            content.addView(item);
        }
    }

    private HorizontalScrollView findSourceScroll(LinearLayout root){
        for(int i=0;i<root.getChildCount();i++)if(root.getChildAt(i) instanceof HorizontalScrollView)return (HorizontalScrollView)root.getChildAt(i);
        return null;
    }
    private String sourceName(List<ReaderSource> sources,int id){for(ReaderSource s:sources)if(s.id==id)return s.title;return "订阅";}
    private String safe(String s){return s==null?"":s;}
    private String dateLabel(long time){if(time<=0)return "";return new SimpleDateFormat("MM月dd日",Locale.CHINA).format(new Date(time));}
    private void showSearchDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("搜索标题、摘要或作者");input.setText(searchQuery);
        new AlertDialog.Builder(this).setTitle("搜索文章").setView(input).setNegativeButton("清除",(d,w)->{searchQuery="";reload();})
                .setNeutralButton("取消",null).setPositiveButton("搜索",(d,w)->{searchQuery=input.getText().toString().trim();reload();}).show();
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
                try {
                    parsed=ReaderFetcher.fetchFeed(url);
                    if(parsed.isEmpty()) throw new IllegalArgumentException("该地址不是可识别的 RSS / Atom 源");
                    feed=url;
                } catch(Exception notFeed) {
                    feed=ReaderFetcher.discoverFeed(url);
                    parsed=ReaderFetcher.fetchFeed(feed);
                    if(parsed.isEmpty()) throw new IllegalArgumentException("已找到订阅地址，但没有解析到文章");
                }
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
        c.setPadding(dp(14),dp(13),dp(14),dp(13)); c.setBackground(round(Color.WHITE,dp(20)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);c.setLayoutParams(p);return c;
    }
    private TextView empty(String s){TextView t=text(s,13,Color.GRAY,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));return t;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onResume(){super.onResume();if(db!=null)reload();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}