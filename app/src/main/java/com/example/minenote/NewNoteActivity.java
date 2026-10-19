package com.example.minenote;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

public class NewNoteActivity extends AppCompatActivity {

    private AppDatabase database;

    private EditText titleInput;
    private EditText contentInput;

    private final android.os.Handler saveHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable saveRunnable = this::saveNote;

    // -1 表示这篇笔记还没有真正创建
    private int noteId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_new_note);

        database = Room.databaseBuilder(
                        getApplicationContext(),
                        AppDatabase.class,
                        "minenote_database"
                )
                .addMigrations(
                        AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3
                )
                .build();

        titleInput = findViewById(R.id.etTitle);
        contentInput = findViewById(R.id.etContent);

        View backButton = findViewById(R.id.btnBack);
        View moreButton = findViewById(R.id.btnMore);

        // 标题变化
        titleInput.addTextChangedListener(
                new android.text.TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                        scheduleAutoSave();
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s
                    ) {
                    }
                }
        );

        // 正文变化
        contentInput.addTextChangedListener(
                new android.text.TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {
                        scheduleAutoSave();
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s
                    ) {
                    }
                }
        );

        // 返回
        backButton.setOnClickListener(v -> {
            saveHandler.removeCallbacks(saveRunnable);
            saveNote();
            finish();
        });

        // 更多菜单
        moreButton.setOnClickListener(this::showMoreMenu);

        // 自动聚焦标题
        titleInput.requestFocus();
    }

    /**
     * 延迟自动保存
     */
    private void scheduleAutoSave() {

        saveHandler.removeCallbacks(saveRunnable);

        saveHandler.postDelayed(
                saveRunnable,
                500
        );
    }

    /**
     * 保存笔记
     *
     * 如果还没有创建笔记：
     * 只有真正输入了标题或正文之后，
     * 才创建数据库记录。
     */
    private void saveNote() {

        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();

        // 什么都没写，不创建笔记
        if (title.isEmpty() && content.isEmpty()) {
            return;
        }

        new Thread(() -> {

            if (noteId == -1) {

                // 第一次保存，创建新笔记
                Note note = new Note(title, content);

                database.noteDao().insert(note);

                // Room 自动生成 ID
                noteId = note.getId();

            } else {

                // 已经存在，正常更新
                Note note =
                        database.noteDao().getNoteById(noteId);

                if (note != null) {

                    note.setTitle(title);
                    note.setContent(content);

                    database.noteDao().update(note);
                }
            }

        }).start();
    }

    /**
     * 显示右上角三个点菜单
     */
    private void showMoreMenu(View anchor) {

        PopupMenu popupMenu = new PopupMenu(
                this,
                anchor
        );

        popupMenu.getMenu().add("删除笔记");

        popupMenu.getMenu().add(
                "其他功能（之后添加）"
        );

        popupMenu.setOnMenuItemClickListener(item -> {

            if (item.getTitle().toString().equals("删除笔记")) {

                deleteNote();

                return true;
            }

            if (item.getTitle().toString().equals(
                    "其他功能（之后添加）"
            )) {

                Toast.makeText(
                        this,
                        "这个功能之后添加",
                        Toast.LENGTH_SHORT
                ).show();

                return true;
            }

            return false;
        });

        popupMenu.show();
    }

    /**
     * 删除笔记
     */
    private void deleteNote() {

        // 还没有创建数据库记录
        if (noteId == -1) {
            finish();
            return;
        }

        new Thread(() -> {

            Note note =
                    database.noteDao().getNoteById(noteId);

            if (note != null) {

                database.noteDao().delete(note);

                runOnUiThread(() -> {

                    Toast.makeText(
                            this,
                            "笔记已删除",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });
            }

        }).start();
    }

    @Override
    protected void onDestroy() {

        saveHandler.removeCallbacks(saveRunnable);

        super.onDestroy();
    }
}
