package com.example.minenote;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private AppDatabase database;

    private EditText searchInput;

    private RecyclerView recyclerSearchResults;

    private NoteAdapter noteAdapter;

    private List<Note> searchResults =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_search
        );

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

        searchInput =
                findViewById(R.id.etSearch);

        recyclerSearchResults =
                findViewById(
                        R.id.recyclerSearchResults
                );

        TextView backButton =
                findViewById(R.id.btnBack);

        recyclerSearchResults.setLayoutManager(
                new LinearLayoutManager(this)
        );

        noteAdapter = new NoteAdapter(
                searchResults,
                note -> {

                    Intent intent = new Intent(
                            SearchActivity.this,
                            NoteDetailActivity.class
                    );

                    intent.putExtra(
                            "note_id",
                            note.getId()
                    );

                    startActivity(intent);
                }
        );

        recyclerSearchResults.setAdapter(
                noteAdapter
        );

        backButton.setOnClickListener(v -> {
            finish();
        });

        searchInput.addTextChangedListener(
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

                        String keyword =
                                s.toString().trim();

                        searchNotes(keyword);
                    }

                    @Override
                    public void afterTextChanged(
                            android.text.Editable s
                    ) {
                    }
                }
        );

        searchInput.requestFocus();

        searchInput.postDelayed(() -> {

            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                            );

            if (imm != null) {

                imm.showSoftInput(
                        searchInput,
                        InputMethodManager.SHOW_IMPLICIT
                );
            }

        }, 200);
    }

    private void searchNotes(String keyword) {

        if (keyword.isEmpty()) {

            searchResults.clear();

            noteAdapter.notifyDataSetChanged();

            return;
        }

        new Thread(() -> {

            List<Note> notes =
                    database.noteDao()
                            .searchNotes(keyword);

            runOnUiThread(() -> {

                searchResults.clear();

                searchResults.addAll(notes);

                noteAdapter.notifyDataSetChanged();
            });

        }).start();
    }
}