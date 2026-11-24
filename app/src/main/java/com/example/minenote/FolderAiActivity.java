package com.example.minenote;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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

/**
 * Folder-scoped AI assistant. All context is loaded only from the selected folder.
 * Existing AIClient settings and request implementation are reused.
 */
public class FolderAiActivity extends AppCompatActivity {
    private AppDatabase database;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private EditText questionInput;
    private int folderId;
    private String folderName;
    private List<Note> folderNotes = new ArrayList<>();
    private String pendingGeneratedText = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        folderId = getIntent().getIntExtra("folder_id", -1);
        folderName = getIntent().getStringExtra("folder_name");
        if (folderName == null) folderName = "当前文件夹";
        if (folderId <= 0) { finish(); return; }

        database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class,
                "minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,
                        AppDatabaseMigrationV5.MIGRATION_4_5)
                .build();
        buildUi();
        addMessage("assistant", "你好！我是 " + folderName + " 的 AI 助手。你可以让我总结资料、回答问题、分析笔记之间的联系，或生成一篇新笔记。");
        loadFolderNotes();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 250));
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(34);
        back.setTextColor(Color.rgb(50, 50, 50));
        back.setPadding(dp(4), 0, dp(16), 0);
        back.setOnClickListener(v -> finish());
        header.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Folder AI");
        title.setTextSize(21);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(null, 1);
        TextView subtitle = new TextView(this);
        subtitle.setText(folderName + " · 正在读取笔记");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.GRAY);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(14), 0, dp(14));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions, "总结", "请总结当前文件夹的全部笔记，提炼核心主题、关键知识点和笔记之间的联系。");
        addAction(actions, "关联分析", "请分析当前文件夹不同笔记之间的关联、相同点、差异和知识脉络。");
        addAction(actions, "生成笔记", "请根据当前文件夹资料生成一篇结构清晰、适合保存的新笔记。请先给出标题和正文。");
        root.addView(actions);

        questionInput = new EditText(this);
        questionInput.setSingleLine(false);
        questionInput.setMinLines(2);
        questionInput.setMaxLines(4);
        questionInput.setHint("询问这个文件夹里的任何问题……");
        root.addView(questionInput, new LinearLayout.LayoutParams(-1, -2));
        Button send = new Button(this);
        send.setText("发送");
        send.setOnClickListener(v -> {
            String q = questionInput.getText().toString().trim();
            if (!q.isEmpty()) {
                questionInput.setText("");
                ask(q);
            }
        });
        root.addView(send);
        setContentView(root);
    }

    private void addAction(LinearLayout row, String label, String prompt) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setOnClickListener(v -> ask(prompt));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void loadFolderNotes() {
        io.execute(() -> {
            folderNotes = database.noteDao().getNotesByFolder(folderId);
            runOnUiThread(() -> {
                // The note count is shown in the conversation for clarity.
                addMessage("system", "已读取 " + folderNotes.size() + " 篇本文件夹笔记。回答仅使用这些笔记作为资料来源。");
            });
        });
    }

    private String buildFolderContext() {
        StringBuilder context = new StringBuilder();
        int count = Math.min(folderNotes.size(), 30);
        for (int i = 0; i < count; i++) {
            Note n = folderNotes.get(i);
            String title = n.getTitle() == null || n.getTitle().trim().isEmpty()
                    ? "未命名笔记" : n.getTitle().trim();
            String body = n.getContent() == null ? "" : n.getContent();
            if (body.length() > 2400) body = body.substring(0, 2400) + "……[内容截断]";
            context.append("\n\n【来源笔记ID:").append(n.getId()).append("｜")
                    .append(title).append("】\n").append(body);
            if (context.length() > 42000) {
                context.append("\n[上下文达到长度限制，后续笔记未载入]");
                break;
            }
        }
        if (folderNotes.size() > count) context.append("\n[提示：文件夹共有 ")
                .append(folderNotes.size()).append(" 篇笔记，本次上下文最多载入前 ")
                .append(count).append(" 篇。]");
        return context.toString();
    }

    private void ask(String question) {
        if (folderNotes.isEmpty()) {
            addMessage("assistant", "这个文件夹暂时没有笔记，先添加一些资料再使用 Folder AI 吧。");
            return;
        }
        addMessage("user", question);
        addMessage("system", "正在结合文件夹笔记思考……");
        String context = buildFolderContext();
        String task = "你是 Minenote 的文件夹知识助手。当前文件夹名称：" + folderName
                + "。请严格依据下面提供的本文件夹笔记回答。不要把推测说成笔记事实；资料不足时明确说明。"
                + "引用来源时保留来源笔记标题和ID。若用户要求生成笔记，请输出清晰的标题和正文，但不要声称已经保存。"
                + "\n\n用户任务：" + question + "\n\n文件夹笔记资料：" + context;
        new AIClient(this).analyzeNote(task, "自定义提问", new AIClient.Callback() {
            @Override public void onSuccess(String result) {
                runOnUiThread(() -> {
                    addMessage("assistant", result);
                    if (question.contains("生成") || question.contains("新笔记")) {
                        pendingGeneratedText = result;
                        showSaveGeneratedDialog(result);
                    }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> addMessage("error", error));
            }
        });
    }

    private void showSaveGeneratedDialog(String content) {
        new AlertDialog.Builder(this)
                .setTitle("保存 AI 生成的笔记？")
                .setMessage("AI 已生成内容。确认后会在「" + folderName + "」中创建一篇新笔记，不会覆盖原笔记。")
                .setNegativeButton("暂不保存", null)
                .setPositiveButton("预览并保存", (d, w) -> previewAndSave(content))
                .show();
    }

    private void previewAndSave(String content) {
        EditText preview = new EditText(this);
        preview.setText(content);
        preview.setMinLines(8);
        new AlertDialog.Builder(this)
                .setTitle("编辑新笔记")
                .setView(preview)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存到文件夹", (d, w) -> {
                    String text = preview.getText().toString().trim();
                    if (text.isEmpty()) return;
                    String title = "AI 整理笔记";
                    String body = text;
                    int newline = text.indexOf('\n');
                    if (newline > 0 && newline < 60) {
                        title = text.substring(0, newline).replace("#", "").trim();
                        body = text.substring(newline + 1).trim();
                    }
                    final Note note = new Note(title, body, System.currentTimeMillis(), folderId);
                    io.execute(() -> {
                        database.noteDao().insert(note);
                        runOnUiThread(() -> Toast.makeText(this, "已保存到 " + folderName, Toast.LENGTH_SHORT).show());
                    });
                }).show();
    }

    private void addMessage(String role, String text) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setTextColor(role.equals("user") ? Color.WHITE : Color.rgb(45, 45, 45));
        bubble.setPadding(dp(13), dp(11), dp(13), dp(11));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(role.equals("user") ? Color.rgb(185, 145, 55)
                : role.equals("error") ? Color.rgb(255, 230, 230)
                : role.equals("system") ? Color.rgb(240, 240, 240) : Color.WHITE);
        bubble.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                role.equals("user") ? (int)(getResources().getDisplayMetrics().widthPixels * .78) : -1, -2);
        lp.topMargin = dp(8);
        if (role.equals("user")) lp.gravity = Gravity.END;
        messages.addView(bubble, lp);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
