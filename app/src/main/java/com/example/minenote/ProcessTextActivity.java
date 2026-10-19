package com.example.minenote;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.List;

public class ProcessTextActivity extends AppCompatActivity {

    private AppDatabase database;

    // 从其他 App 传过来的文字
    private String selectedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (intent != null
                && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {

            selectedText = intent.getStringExtra(
                    Intent.EXTRA_PROCESS_TEXT
            );
        }

        if (selectedText == null) {
            selectedText = "";
        }

        selectedText = selectedText.trim();

        database = Room.databaseBuilder(
                        getApplicationContext(),
                        AppDatabase.class,
                        "minenote_database"
                )
                .addMigrations(
                        AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4
                )
                .build();

        createPage();
    }

    /**
     * 创建页面
     */
    private void createPage() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        root.setPadding(
                dp(24),
                dp(48),
                dp(24),
                dp(32)
        );

        root.setBackgroundColor(
                0xFFFAFAFA
        );

        // 标题
        TextView title =
                new TextView(this);

        title.setText("MINENOTE");

        title.setTextSize(24);

        title.setTextColor(
                0xFF202124
        );

        title.setGravity(
                Gravity.CENTER
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        root.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60)
                )
        );

        // 提示
        TextView hint =
                new TextView(this);

        hint.setText("已获取选中的文字");

        hint.setTextSize(16);

        hint.setTextColor(
                0xFF777777
        );

        hint.setGravity(
                Gravity.CENTER
        );

        root.addView(
                hint,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(40)
                )
        );

        // 选中的文字
        TextView content =
                new TextView(this);

        content.setText(
                selectedText
        );

        content.setTextSize(17);

        content.setTextColor(
                0xFF333333
        );

        content.setGravity(
                Gravity.TOP
        );

        content.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        content.setBackgroundColor(
                0xFFFFF8E1
        );

        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                );

        contentParams.topMargin =
                dp(16);

        contentParams.bottomMargin =
                dp(24);

        root.addView(
                content,
                contentParams
        );

        // 底部按钮
        LinearLayout buttonLayout =
                new LinearLayout(this);

        buttonLayout.setOrientation(
                LinearLayout.HORIZONTAL
        );

        buttonLayout.setGravity(
                Gravity.CENTER
        );

        // 取消
        TextView cancelButton =
                new TextView(this);

        cancelButton.setText("取消");

        cancelButton.setTextSize(16);

        cancelButton.setTextColor(
                0xFF555555
        );

        cancelButton.setGravity(
                Gravity.CENTER
        );

        cancelButton.setClickable(true);

        cancelButton.setFocusable(true);

        LinearLayout.LayoutParams cancelParams =
                new LinearLayout.LayoutParams(
                        dp(120),
                        dp(52)
                );

        cancelParams.rightMargin =
                dp(12);

        buttonLayout.addView(
                cancelButton,
                cancelParams
        );

        // 添加到笔记
        TextView addButton =
                new TextView(this);

        addButton.setText("添加到笔记");

        addButton.setTextSize(16);

        addButton.setTextColor(
                0xFF6B5B00
        );

        addButton.setGravity(
                Gravity.CENTER
        );

        addButton.setClickable(true);

        addButton.setFocusable(true);

        addButton.setBackgroundColor(
                0xFFFFF1B8
        );

        buttonLayout.addView(
                addButton,
                new LinearLayout.LayoutParams(
                        dp(150),
                        dp(52)
                )
        );

        root.addView(
                buttonLayout,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(60)
                )
        );

        // 取消
        cancelButton.setOnClickListener(v ->
                finish()
        );

        // 添加到笔记
        addButton.setOnClickListener(v -> {

            if (TextUtils.isEmpty(selectedText)) {

                Toast.makeText(
                        ProcessTextActivity.this,
                        "没有获取到选中的文字",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            showActionDialog();
        });

        setContentView(root);
    }

    /**
     * 选择操作
     */
    private void showActionDialog() {

        String[] options = {
                "📝 添加到已有笔记",
                "＋ 创建新笔记"
        };

        new AlertDialog.Builder(this)
                .setTitle("选择操作")
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {

                                // 添加到已有笔记
                                showFolderDialog();

                            } else {

                                // 创建新笔记
                                showCreateNoteDialog();
                            }
                        }
                )
                .setNegativeButton(
                        "取消",
                        null
                )
                .show();
    }

    /**
     * 选择文件夹
     */
    private void showFolderDialog() {

        new Thread(() -> {

            List<Folder> folders =
                    database.folderDao()
                            .getAllFolders();

            runOnUiThread(() -> {

                List<String> names =
                        new ArrayList<>();

                List<Folder> folderList =
                        new ArrayList<>();

                // 未分类
                names.add("❓ 未分类");

                folderList.add(null);

                // 文件夹
                for (Folder folder : folders) {

                    names.add(
                            "📁 " +
                                    folder.getName()
                    );

                    folderList.add(folder);
                }

                String[] items =
                        names.toArray(
                                new String[0]
                        );

                new AlertDialog.Builder(
                        ProcessTextActivity.this
                )
                        .setTitle("选择文件夹")
                        .setItems(
                                items,
                                (dialog, which) -> {

                                    Folder folder =
                                            folderList.get(
                                                    which
                                            );

                                    if (folder == null) {

                                        showNoteDialog(
                                                null,
                                                "未分类"
                                        );

                                    } else {

                                        showNoteDialog(
                                                folder.getId(),
                                                folder.getName()
                                        );
                                    }
                                }
                        )
                        .setNegativeButton(
                                "取消",
                                null
                        )
                        .show();
            });

        }).start();
    }

    /**
     * 选择已有笔记
     */
    private void showNoteDialog(
            Integer folderId,
            String folderName
    ) {

        new Thread(() -> {

            List<Note> notes;

            if (folderId == null) {

                notes =
                        database.noteDao()
                                .getUncategorizedNotes();

            } else {

                notes =
                        database.noteDao()
                                .getNotesByFolder(
                                        folderId
                                );
            }

            runOnUiThread(() -> {

                if (notes.isEmpty()) {

                    Toast.makeText(
                            ProcessTextActivity.this,
                            "「" +
                                    folderName +
                                    "」里还没有笔记",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                List<String> names =
                        new ArrayList<>();

                for (Note note : notes) {

                    names.add(
                            getNoteDisplayName(note)
                    );
                }

                String[] items =
                        names.toArray(
                                new String[0]
                        );

                new AlertDialog.Builder(
                        ProcessTextActivity.this
                )
                        .setTitle("选择笔记")
                        .setItems(
                                items,
                                (dialog, which) -> {

                                    Note note =
                                            notes.get(
                                                    which
                                            );

                                    appendTextToNote(
                                            note
                                    );
                                }
                        )
                        .setNegativeButton(
                                "返回",
                                null
                        )
                        .show();
            });

        }).start();
    }

    /**
     * 将文字追加到已有笔记
     */
    private void appendTextToNote(
            Note note
    ) {

        new Thread(() -> {

            String oldContent =
                    note.getContent();

            if (oldContent == null) {
                oldContent = "";
            }

            String newContent;

            if (oldContent.trim().isEmpty()) {

                newContent =
                        selectedText;

            } else {

                newContent =
                        oldContent
                                + "\n\n"
                                + selectedText;
            }

            note.setContent(
                    newContent
            );

            database.noteDao()
                    .update(note);

            runOnUiThread(() -> {

                Toast.makeText(
                        ProcessTextActivity.this,
                        "已添加到「" +
                                getNoteDisplayName(note) +
                                "」",
                        Toast.LENGTH_SHORT
                ).show();

                finishDelayed();
            });

        }).start();
    }

    /**
     * 创建新笔记
     */
    private void showCreateNoteDialog() {

        // 默认标题
        String defaultTitle =
                createDefaultTitle(
                        selectedText
                );

        EditText titleInput =
                new EditText(this);

        titleInput.setSingleLine(true);

        titleInput.setText(
                defaultTitle
        );

        titleInput.setSelectAllOnFocus(false);

        titleInput.setTextSize(16);

        titleInput.setHint("请输入笔记标题");

        titleInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        titleInput.setPadding(
                dp(20),
                0,
                dp(20),
                0
        );

        new AlertDialog.Builder(this)
                .setTitle("创建新笔记")
                .setMessage("选中的文字将作为笔记正文")
                .setView(titleInput)
                .setNegativeButton(
                        "取消",
                        null
                )
                .setPositiveButton(
                        "下一步",
                        (dialog, which) -> {

                            String title =
                                    titleInput
                                            .getText()
                                            .toString()
                                            .trim();

                            if (title.isEmpty()) {

                                title =
                                        "新笔记";
                            }

                            showCreateNoteFolderDialog(
                                    title
                            );
                        }
                )
                .show();
    }

    /**
     * 创建笔记时选择文件夹
     */
    private void showCreateNoteFolderDialog(
            String title
    ) {

        new Thread(() -> {

            List<Folder> folders =
                    database.folderDao()
                            .getAllFolders();

            runOnUiThread(() -> {

                List<String> names =
                        new ArrayList<>();

                List<Integer> folderIds =
                        new ArrayList<>();

                // 未分类
                names.add("❓ 未分类");

                folderIds.add(null);

                // 文件夹
                for (Folder folder : folders) {

                    names.add(
                            "📁 " +
                                    folder.getName()
                    );

                    folderIds.add(
                            folder.getId()
                    );
                }

                String[] items =
                        names.toArray(
                                new String[0]
                        );

                new AlertDialog.Builder(
                        ProcessTextActivity.this
                )
                        .setTitle("选择文件夹")
                        .setItems(
                                items,
                                (dialog, which) -> {

                                    Integer folderId =
                                            folderIds.get(
                                                    which
                                            );

                                    createNewNote(
                                            title,
                                            folderId
                                    );
                                }
                        )
                        .setNegativeButton(
                                "返回",
                                null
                        )
                        .show();
            });

        }).start();
    }

    /**
     * 创建新笔记并保存
     */
    private void createNewNote(
            String title,
            Integer folderId
    ) {

        new Thread(() -> {

            Note note =
                    new Note(
                            title,
                            selectedText,
                            System.currentTimeMillis(),
                            folderId
                    );

            database.noteDao()
                    .insert(note);

            runOnUiThread(() -> {

                Toast.makeText(
                        ProcessTextActivity.this,
                        "笔记已创建",
                        Toast.LENGTH_SHORT
                ).show();

                finishDelayed();
            });

        }).start();
    }

    /**
     * 根据选中文字生成默认标题
     */
    private String createDefaultTitle(
            String text
    ) {

        if (text == null
                || text.trim().isEmpty()) {

            return "新笔记";
        }

        String title =
                text.trim();

        // 换行替换成空格
        title =
                title.replace(
                        "\n",
                        " "
                );

        title =
                title.replace(
                        "\r",
                        " "
                );

        // 最多 20 个字符
        if (title.length() > 20) {

            title =
                    title.substring(
                            0,
                            20
                    ) + "...";
        }

        return title;
    }

    /**
     * 获取笔记名称
     */
    private String getNoteDisplayName(
            Note note
    ) {

        String title =
                note.getTitle();

        if (title == null
                || title.trim().isEmpty()) {

            return "无标题笔记";
        }

        return title;
    }

    /**
     * 延迟关闭页面
     */
    private void finishDelayed() {

        new Handler(
                Looper.getMainLooper()
        ).postDelayed(
                this::finish,
                500
        );
    }

    /**
     * dp 转 px
     */
    private int dp(float value) {

        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f
        );
    }

    @Override
    protected void onDestroy() {

        if (database != null) {
            database.close();
        }

        super.onDestroy();
    }
}
