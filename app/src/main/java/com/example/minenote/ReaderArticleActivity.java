package com.example.minenote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native article reader. HTML is converted to Android text spans; no WebView is used. */
public class ReaderArticleActivity extends AppCompatActivity {
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService imageIo = Executors.newFixedThreadPool(3);
    private final ConcurrentHashMap<String, ArticleImageDrawable> imageCache = new ConcurrentHashMap<>();
    private ReaderArticle article;
    private ReaderSource sourceInfo;
    private TextView bodyView;
    private float bodyTextSize = 19f;
    private String selectedText = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,
                        AppDatabaseMigrationV5.MIGRATION_4_5,
                        ReaderDatabaseMigrationV6.MIGRATION_5_6).build();
        buildLoading();
        int id = getIntent().getIntExtra("article_id", -1);
        io.execute(() -> {
            article = db.readerDao().getArticle(id);
            if (article == null) { runOnUiThread(this::finish); return; }
            sourceInfo = db.readerDao().getSource(article.sourceId);
            String fallback = article.content == null ? article.summary : article.content;
            String html = ReaderFetcher.extractArticle(article.url, fallback);
            article.content = html;
            article.isRead = true;
            db.readerDao().updateArticle(article);
            runOnUiThread(() -> render(html));
        });
    }

    private void buildLoading() {
        TextView loading = new TextView(this);
        loading.setText("正在整理文章…");
        loading.setTextSize(16);
        loading.setTextColor(Color.rgb(80, 76, 68));
        loading.setPadding(dp(24), dp(40), dp(24), dp(24));
        loading.setBackgroundColor(Color.rgb(248, 247, 242));
        setContentView(loading);
    }

    private void render(String articleHtml) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 247, 242));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(7), dp(16), dp(7));
        TextView back = label("‹", 30, Color.rgb(55, 52, 46), false);
        back.setGravity(android.view.Gravity.CENTER);
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(42)));
        back.setOnClickListener(v -> finish());

        LinearLayout barTitle = new LinearLayout(this);
        barTitle.setOrientation(LinearLayout.VERTICAL);
        barTitle.addView(label("Reader", 16, Color.rgb(52, 50, 45), true));
        barTitle.addView(label("沉浸阅读", 11, Color.rgb(145, 140, 130), false));
        bar.addView(barTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView save = label(article.isSaved ? "★" : "☆", 26, Color.rgb(177, 133, 49), true);
        save.setGravity(android.view.Gravity.CENTER);
        bar.addView(save, new LinearLayout.LayoutParams(dp(42), dp(42)));
        save.setOnClickListener(v -> {
            article.isSaved = !article.isSaved;
            save.setText(article.isSaved ? "★" : "☆");
            io.execute(() -> db.readerDao().updateArticle(article));
        });
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(23), dp(22), dp(23), dp(34));

        TextView sourceLabel = label(sourceDisplayName().toUpperCase(Locale.ROOT),
                11, Color.rgb(153, 119, 58), true);
        sourceLabel.setPadding(0, 0, 0, dp(12));
        page.addView(sourceLabel);

        TextView title = label(article.title, 29, Color.rgb(39, 38, 34), true);
        title.setLineSpacing(dp(4), 1.05f);
        page.addView(title);

        String author = article.author == null || article.author.trim().isEmpty()
                ? "" : article.author.trim() + "  ·  ";
        TextView metadata = label(author + dateLabel(article.publishedAt),
                12, Color.rgb(142, 137, 127), false);
        metadata.setPadding(0, dp(13), 0, dp(20));
        page.addView(metadata);

        View rule = new View(this);
        rule.setBackgroundColor(Color.rgb(230, 225, 214));
        page.addView(rule, new LinearLayout.LayoutParams(-1, dp(1)));

        bodyView = label("", (int) bodyTextSize, Color.rgb(53, 50, 45), false);
        bodyView.setTypeface(Typeface.SERIF);
        bodyView.setPadding(0, dp(22), 0, dp(20));
        bodyView.setTextIsSelectable(true);
        bodyView.setLineSpacing(dp(7), 1.22f);
        bodyView.setLinkTextColor(Color.rgb(126, 96, 44));
        bodyView.setMovementMethod(LinkMovementMethod.getInstance());
        bodyView.setText(Html.fromHtml(toDisplayHtml(articleHtml),
                Html.FROM_HTML_MODE_LEGACY, this::loadArticleImage, null));

        bodyView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, 1, 1, "加入笔记");
                menu.add(0, 2, 2, "询问 AI");
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int start = bodyView.getSelectionStart();
                int end = bodyView.getSelectionEnd();
                if (start < 0 || end <= start) return false;
                selectedText = bodyView.getText().subSequence(start, end).toString().trim();
                mode.finish();
                if (item.getItemId() == 1) showNotePicker(); else askAi();
                return true;
            }
            @Override public void onDestroyActionMode(ActionMode mode) { }
        });
        page.addView(bodyView);

        TextView sourceNote = label("本文由 Minenote Reader 整理 · 原文内容及版权归原作者所有",
                10, Color.rgb(157, 151, 140), false);
        sourceNote.setPadding(0, dp(20), 0, dp(10));
        page.addView(sourceNote);

        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(9), dp(16), dp(9));
        bottom.setBackgroundColor(Color.WHITE);
        TextView smaller = bottomAction("A−");
        smaller.setOnClickListener(v -> changeTextSize(-1));
        TextView bigger = bottomAction("A＋");
        bigger.setOnClickListener(v -> changeTextSize(1));
        bottom.addView(smaller);
        bottom.addView(bigger);
        TextView original = label("原文网页 ↗", 13, Color.rgb(142, 103, 36), true);
        original.setGravity(android.view.Gravity.CENTER);
        bottom.addView(original, new LinearLayout.LayoutParams(0, dp(42), 1));
        original.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(article.url)));
            } catch (Exception ignored) { }
        });
        root.addView(bottom);
        setContentView(root);
    }

    private String toDisplayHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "<p>暂时无法提取正文。你可以查看原网页，或使用 RSS 摘要。</p>";
        }
        String trimmed = html.trim();
        if (!trimmed.contains("<")) {
            String escaped = TextUtils.htmlEncode(trimmed)
                    .replace("\r\n", "\n").replace("\r", "\n")
                    .replace("\n\n", "</p><p>").replace("\n", "<br>");
            return "<p>" + escaped + "</p>";
        }
        return trimmed;
    }

    private Drawable loadArticleImage(String source) {
        if (source == null || !(source.startsWith("https://") || source.startsWith("http://"))) {
            return new ArticleImageDrawable(dp(280), dp(158));
        }
        ArticleImageDrawable cached = imageCache.get(source);
        if (cached != null) return cached;

        int width = Math.max(dp(180), getResources().getDisplayMetrics().widthPixels - dp(46));
        ArticleImageDrawable drawable = new ArticleImageDrawable(width, Math.round(width * 0.58f));
        imageCache.put(source, drawable);
        imageIo.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(source).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 MinenoteReader/1.0");
                connection.connect();
                if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(connection.getInputStream(), null, options);
                    int sample = 1;
                    while (options.outWidth / sample > width * 2) sample *= 2;
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sample;
                    Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream(), null, options);
                    if (bitmap != null) runOnUiThread(() -> {
                        drawable.setBitmap(bitmap);
                        if (bodyView != null) bodyView.invalidate();
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
        return drawable;
    }

    private class ArticleImageDrawable extends Drawable {
        private final int width;
        private final int height;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;

        ArticleImageDrawable(int width, int height) {
            this.width = width;
            this.height = height;
            setBounds(0, 0, width, height);
        }

        void setBitmap(Bitmap value) { bitmap = value; invalidateSelf(); }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bitmap != null && !bitmap.isRecycled()) {
                RectF target = new RectF(bounds);
                float scale = Math.min(target.width() / bitmap.getWidth(),
                        target.height() / bitmap.getHeight());
                float drawWidth = bitmap.getWidth() * scale;
                float drawHeight = bitmap.getHeight() * scale;
                float left = target.centerX() - drawWidth / 2f;
                float top = target.centerY() - drawHeight / 2f;
                canvas.drawBitmap(bitmap, null,
                        new RectF(left, top, left + drawWidth, top + drawHeight), paint);
            } else {
                paint.setColor(Color.rgb(235, 231, 221));
                canvas.drawRoundRect(new RectF(bounds), dp(8), dp(8), paint);
            }
        }

        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return height; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void changeTextSize(int delta) {
        bodyTextSize = Math.max(14, Math.min(26, bodyTextSize + delta));
        if (bodyView != null) bodyView.setTextSize(bodyTextSize);
    }

    private TextView bottomAction(String value) {
        TextView t = label(value, 15, Color.rgb(83, 77, 66), true);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(roundBg());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(42));
        p.rightMargin = dp(8);
        t.setLayoutParams(p);
        return t;
    }

    private android.graphics.drawable.GradientDrawable roundBg() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Color.rgb(247, 243, 234));
        d.setCornerRadius(dp(15));
        return d;
    }

    private String sourceDisplayName() {
        if (sourceInfo == null) return "MINENOTE READER";
        String title = sourceInfo.title == null ? "" : sourceInfo.title.trim();
        if (title.isEmpty() || title.matches("(?i)^https?://.*")) {
            try {
                String host = new java.net.URI(sourceInfo.url).getHost();
                if (host != null) return host.replaceFirst("(?i)^www\\.", "");
            } catch (Exception ignored) { }
        }
        return title;
    }

    private String dateLabel(long time) {
        if (time <= 0) return "";
        return new java.text.SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
                .format(new java.util.Date(time));
    }

    private void showNotePicker() {
        io.execute(() -> {
            List<Note> notes = db.noteDao().getAllNotes();
            runOnUiThread(() -> {
                if (notes.isEmpty()) {
                    Toast.makeText(this, "请先创建一篇笔记", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[notes.size()];
                for (int i = 0; i < notes.size(); i++) {
                    labels[i] = notes.get(i).getTitle() == null ? "未命名笔记" : notes.get(i).getTitle();
                }
                new AlertDialog.Builder(this).setTitle("加入哪篇笔记？")
                        .setItems(labels, (d, which) -> appendToNote(notes.get(which)))
                        .setNegativeButton("取消", null).show();
            });
        });
    }

    private void appendToNote(Note note) {
        String excerpt = "\n\n> " + selectedText.replace("\n", "\n> ")
                + "\n\n来源：《" + article.title + "》\n" + article.url + "\n";
        io.execute(() -> {
            String old = note.getContent() == null ? "" : note.getContent();
            note.setContent(old + excerpt);
            db.noteDao().update(note);
            runOnUiThread(() -> Toast.makeText(this,
                    "已加入「" + note.getTitle() + "」", Toast.LENGTH_SHORT).show());
        });
    }

    private void askAi() {
        new AIClient(this).analyzeNote("文章：《" + article.title + "》\n文章地址：" + article.url
                        + "\n\n选中的原文：\n" + selectedText,
                "解释以下文章摘录，先用通俗中文解释，再给出必要背景和实际例子。不要超出摘录内容臆造事实。",
                new AIClient.Callback() {
                    @Override public void onSuccess(String result) {
                        runOnUiThread(() -> new AlertDialog.Builder(ReaderArticleActivity.this)
                                .setTitle("AI 阅读助手").setMessage(result)
                                .setPositiveButton("知道了", null).show());
                    }
                    @Override public void onError(String error) {
                        runOnUiThread(() -> new AlertDialog.Builder(ReaderArticleActivity.this)
                                .setTitle("AI 请求失败").setMessage(error)
                                .setPositiveButton("确定", null).show());
                    }
                });
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value == null ? "" : value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + .5f);
    }

    @Override protected void onDestroy() {
        io.shutdown();
        imageIo.shutdown();
        super.onDestroy();
    }
}