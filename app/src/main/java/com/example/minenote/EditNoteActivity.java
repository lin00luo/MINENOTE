package com.example.minenote;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.room.Room;

public class EditNoteActivity extends AppCompatActivity {

    private AppDatabase database;

    private int noteId;

    private EditText titleInput;

    private EditText contentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_edit_note);

        // 创建数据库
        database = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class,
                "minenote_database"
        ).build();

        // 找到输入框
        titleInput = findViewById(R.id.etEditTitle);

        contentInput = findViewById(R.id.etEditContent);

        Button updateButton = findViewById(R.id.btnUpdate);

        // 获取上一个页面传过来的笔记 ID
        noteId = getIntent().getIntExtra("note_id", -1);

        // 根据 ID 查询笔记
        loadNote();

        // 点击保存修改
        updateButton.setOnClickListener(v -> {

            String title = titleInput.getText().toString();

            String content = contentInput.getText().toString();

            Note note = new Note(title, content);

            // 设置原来的 ID
            note.setId(noteId);

            // 更新数据库
            new Thread(() -> {

                database.noteDao().update(note);

                runOnUiThread(() -> {

                    Toast.makeText(
                            EditNoteActivity.this,
                            "修改成功！",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });

            }).start();
        });

        // 处理屏幕边缘区域
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {

                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }

    private void loadNote() {

        new Thread(() -> {

            Note note = database.noteDao().getNoteById(noteId);

            runOnUiThread(() -> {

                if (note != null) {

                    titleInput.setText(note.getTitle());

                    contentInput.setText(note.getContent());
                }
            });

        }).start();
    }
}