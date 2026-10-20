package com.example.minenote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private LinearLayout navigationDrawer;

    private FolderAdapter folderAdapter;
    private RecyclerView folderRecyclerView;
    private TextView aiBalanceProvider;
    private TextView aiBalanceAmount;
    private TextView aiLastUsage;
    private AlertDialog aiUsageDialog;
    private int balanceRequestSequence = 0;

    private NoteAdapter noteAdapter;
    private AppDatabase db;

    private int selectedMenuId = -1;

    private final int SELECTED_BACKGROUND =
            Color.rgb(255, 248, 232);

    private final int NORMAL_TEXT =
            Color.rgb(68, 68, 68);

    private final int SELECTED_TEXT =
            Color.rgb(32, 33, 36);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationDrawer = findViewById(R.id.navigationDrawer);

        findViewById(R.id.tokenRingView).setOnClickListener(v -> showAiUsageDialog());

        navigationDrawer.post(() -> {
            int screenWidth =
                    getResources()
                            .getDisplayMetrics()
                            .widthPixels;

            navigationDrawer.getLayoutParams().width =
                    screenWidth * 3 / 4;

            navigationDrawer.requestLayout();
        });

        db = Room.databaseBuilder(
                getApplicationContext(),
                AppDatabase.class,
                "minenote_database"
        ).addMigrations(
                AppDatabaseMigration.MIGRATION_1_2,
                AppDatabaseMigrationV3.MIGRATION_2_3,
                AppDatabaseMigrationV4.MIGRATION_3_4,
                AppDatabaseMigrationV5.MIGRATION_4_5
        ).build();

        setupNotes();
        setupFolders();
        setupClicks();

        loadFolders();
        loadAllNotes();
    }

    private void setupNotes() {

        RecyclerView recyclerNotes =
                findViewById(R.id.recyclerNotes);

        recyclerNotes.setLayoutManager(
                new LinearLayoutManager(this)
        );

        noteAdapter = new NoteAdapter(
                new ArrayList<>(),
                note -> {
                    Intent intent = new Intent(
                            MainActivity.this,
                            NoteDetailActivity.class
                    );

                    intent.putExtra(
                            "note_id",
                            note.getId()
                    );

                    startActivity(intent);
                }
        );

        recyclerNotes.setAdapter(noteAdapter);
    }

    private void setupFolders() {

        folderRecyclerView =
                findViewById(R.id.folderRecyclerView);

        folderRecyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        folderAdapter = new FolderAdapter(
                new ArrayList<>(),
                new FolderAdapter.Listener() {

                    @Override
                    public void onFolderClick(
                            Folder folder
                    ) {
                        selectedMenuId =
                                folder.getId();

                        loadFolderNotes(
                                folder.getId()
                        );

                        updateMenuSelection();

                        drawerLayout.closeDrawer(
                                Gravity.START
                        );
                    }

                    @Override
                    public void onFolderMore(
                            Folder folder
                    ) {
                        showFolderMenu(folder);
                    }

                    @Override
                    public void onStartDrag(
                            FolderAdapter.FolderViewHolder holder
                    ) {
                        itemTouchHelper.startDrag(holder);
                    }
                }
        );

        folderRecyclerView.setAdapter(folderAdapter);

        ItemTouchHelper.Callback callback =
                new ItemTouchHelper.SimpleCallback(
                        ItemTouchHelper.UP
                                | ItemTouchHelper.DOWN,
                        0
                ) {

                    @Override
                    public boolean onMove(
                            RecyclerView recyclerView,
                            RecyclerView.ViewHolder viewHolder,
                            RecyclerView.ViewHolder target
                    ) {

                        int from =
                                viewHolder.getBindingAdapterPosition();

                        int to =
                                target.getBindingAdapterPosition();

                        if (from == RecyclerView.NO_POSITION
                                || to == RecyclerView.NO_POSITION) {
                            return false;
                        }

                        folderAdapter.moveItem(
                                from,
                                to
                        );

                        return true;
                    }

                    @Override
                    public void onSwiped(
                            RecyclerView.ViewHolder viewHolder,
                            int direction
                    ) {
                        // 不支持侧滑删除
                    }

                    @Override
                    public void clearView(
                            RecyclerView recyclerView,
                            RecyclerView.ViewHolder viewHolder
                    ) {
                        super.clearView(
                                recyclerView,
                                viewHolder
                        );

                        saveFolderOrder();
                    }

                    @Override
                    public void onSelectedChanged(
                            RecyclerView.ViewHolder viewHolder,
                            int actionState
                    ) {
                        super.onSelectedChanged(
                                viewHolder,
                                actionState
                        );

                        if (actionState ==
                                ItemTouchHelper.ACTION_STATE_DRAG) {

                            viewHolder.itemView.animate()
                                    .scaleX(1.03f)
                                    .scaleY(1.03f)
                                    .alpha(0.88f)
                                    .setDuration(100)
                                    .start();
                        }
                    }
                };

        itemTouchHelper =
                new ItemTouchHelper(callback);

        itemTouchHelper.attachToRecyclerView(
                folderRecyclerView
        );
    }

    private ItemTouchHelper itemTouchHelper;

    private void setupClicks() {

        findViewById(R.id.btnMenu)
                .setOnClickListener(v ->
                        drawerLayout.openDrawer(
                                Gravity.START
                        )
                );

        findViewById(R.id.btnSearch)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            MainActivity.this,
                            SearchActivity.class
                    );

                    startActivity(intent);
                });

        findViewById(R.id.btnNewNote)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            MainActivity.this,
                            NewNoteActivity.class
                    );

                    startActivity(intent);
                });

        findViewById(R.id.menuAllNotes)
                .setOnClickListener(v -> {

                    selectedMenuId = -1;

                    loadAllNotes();
                    updateMenuSelection();

                    drawerLayout.closeDrawer(
                            Gravity.START
                    );
                });

        findViewById(R.id.menuUncategorized)
                .setOnClickListener(v -> {

                    selectedMenuId = -2;

                    loadUncategorizedNotes();
                    updateMenuSelection();

                    drawerLayout.closeDrawer(
                            Gravity.START
                    );
                });

        findViewById(R.id.btnNewFolder)
                .setOnClickListener(v ->
                        showCreateFolderDialog()
                );

        findViewById(R.id.menuSettings)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            MainActivity.this,
                            SettingsActivity.class
                    );

                    startActivity(intent);
                });


    }

    @Override
    protected void onResume() {
        super.onResume();

        if (db == null
                || noteAdapter == null
                || folderAdapter == null) {
            return;
        }

        loadFolders();

        if (selectedMenuId == -1) {
            loadAllNotes();
        } else if (selectedMenuId == -2) {
            loadUncategorizedNotes();
        } else {
            loadFolderNotes(selectedMenuId);
        }
    }

    private void loadAllNotes() {

        new Thread(() -> {

            List<Note> notes =
                    db.noteDao().getAllNotes();

            runOnUiThread(() -> {

                noteAdapter.setNotes(notes);
                updateMenuSelection();
            });

        }).start();
    }

    private void loadUncategorizedNotes() {

        new Thread(() -> {

            List<Note> notes =
                    db.noteDao().getUncategorizedNotes();

            runOnUiThread(() -> {

                noteAdapter.setNotes(notes);
                updateMenuSelection();
            });

        }).start();
    }

    private void loadFolderNotes(
            int folderId
    ) {

        new Thread(() -> {

            List<Note> notes =
                    db.noteDao().getNotesByFolder(
                            folderId
                    );

            runOnUiThread(() -> {

                noteAdapter.setNotes(notes);
                updateMenuSelection();
            });

        }).start();
    }

    private void loadFolders() {

        new Thread(() -> {

            List<Folder> folders =
                    db.folderDao().getAllFolders();

            runOnUiThread(() -> {

                folderAdapter.setFolders(
                        folders
                );

                folderAdapter.setSelectedFolderId(
                        selectedMenuId
                );
            });

        }).start();
    }

    private void saveFolderOrder() {

        List<Folder> folders =
                folderAdapter.getFolders();

        new Thread(() -> {

            for (int i = 0;
                 i < folders.size();
                 i++) {

                Folder folder =
                        folders.get(i);

                db.folderDao().updateFolderOrder(
                        folder.getId(),
                        i
                );

                folder.setSortOrder(i);
            }

        }).start();
    }

    private void updateMenuSelection() {

        View allNotes =
                findViewById(R.id.menuAllNotes);

        View uncategorized =
                findViewById(R.id.menuUncategorized);

        setMenuItemSelected(
                allNotes,
                selectedMenuId == -1
        );

        setMenuItemSelected(
                uncategorized,
                selectedMenuId == -2
        );

        if (folderAdapter != null) {

            folderAdapter.setSelectedFolderId(
                    selectedMenuId
            );
        }
    }

    private void setMenuItemSelected(
            View view,
            boolean selected
    ) {

        if (view == null) {
            return;
        }

        if (selected) {

            view.setBackground(
                    createSelectedBackground()
            );

            if (view instanceof TextView) {

                ((TextView) view)
                        .setTextColor(
                                SELECTED_TEXT
                        );
            }

        } else {

            view.setBackgroundColor(
                    Color.TRANSPARENT
            );

            if (view instanceof TextView) {

                ((TextView) view)
                        .setTextColor(
                                NORMAL_TEXT
                        );
            }
        }
    }

    private GradientDrawable createSelectedBackground() {

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                SELECTED_BACKGROUND
        );

        background.setCornerRadius(
                dp(10)
        );

        return background;
    }

    private void showCreateFolderDialog() {

        EditText input =
                new EditText(this);

        input.setSingleLine(true);
        input.setHint("文件夹名称");
        input.setTextSize(16);
        input.setTextColor(
                Color.rgb(32, 33, 36)
        );
        input.setHintTextColor(
                Color.rgb(150, 150, 150)
        );

        input.setPadding(
                dp(16),
                0,
                dp(16),
                0
        );

        GradientDrawable inputBackground =
                new GradientDrawable();

        inputBackground.setColor(
                Color.rgb(250, 250, 250)
        );

        inputBackground.setCornerRadius(
                dp(12)
        );

        inputBackground.setStroke(
                dp(1),
                Color.rgb(230, 230, 230)
        );

        input.setBackground(
                inputBackground
        );

        LinearLayout inputContainer =
                new LinearLayout(this);

        inputContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        inputContainer.setPadding(
                dp(24),
                dp(4),
                dp(24),
                dp(4)
        );

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                );

        inputContainer.addView(
                input,
                inputParams
        );

        TextView title =
                new TextView(this);

        title.setText("新建文件夹");
        title.setTextSize(20);
        title.setTextColor(
                Color.rgb(32, 33, 36)
        );

        title.setGravity(
                Gravity.CENTER_VERTICAL
        );

        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        title.setPadding(
                dp(24),
                dp(22),
                dp(24),
                dp(12)
        );

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setBackgroundColor(
                Color.rgb(250, 250, 250)
        );

        container.addView(title);
        container.addView(inputContainer);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(container)
                        .setNegativeButton(
                                "取消",
                                null
                        )
                        .setPositiveButton(
                                "创建",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {

            TextView positiveButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            positiveButton.setTextColor(
                    Color.rgb(107, 91, 0)
            );

            positiveButton.setTextSize(15);

            TextView negativeButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_NEGATIVE
                    );

            negativeButton.setTextColor(
                    Color.rgb(120, 120, 120)
            );

            negativeButton.setTextSize(15);

            positiveButton.setOnClickListener(v -> {

                String name =
                        input.getText()
                                .toString()
                                .trim();

                if (name.isEmpty()) {

                    input.setError(
                            "请输入文件夹名称"
                    );

                    input.requestFocus();

                    return;
                }

                new Thread(() -> {

                    long newId =
                            db.folderDao()
                                    .insert(
                                            new Folder(name)
                                    );

                    List<Folder> folders =
                            db.folderDao()
                                    .getAllFolders();

                    if (!folders.isEmpty()) {

                        Folder newFolder = null;

                        for (Folder folder :
                                folders) {

                            if (folder.getId() ==
                                    (int) newId) {

                                newFolder = folder;
                                break;
                            }
                        }

                        if (newFolder != null) {

                            int newOrder =
                                    folders.size() - 1;

                            db.folderDao()
                                    .updateFolderOrder(
                                            newFolder.getId(),
                                            newOrder
                                    );
                        }
                    }

                    runOnUiThread(() -> {

                        dialog.dismiss();

                        loadFolders();

                        Toast.makeText(
                                this,
                                "文件夹已创建",
                                Toast.LENGTH_SHORT
                        ).show();
                    });

                }).start();
            });

            input.requestFocus();

            input.postDelayed(() -> {

                InputMethodManager imm =
                        (InputMethodManager)
                                getSystemService(
                                        Context.INPUT_METHOD_SERVICE
                                );

                if (imm != null) {

                    imm.showSoftInput(
                            input,
                            InputMethodManager
                                    .SHOW_IMPLICIT
                    );
                }

            }, 200);
        });

        dialog.show();

        Window window =
                dialog.getWindow();

        if (window != null) {

            window.setBackgroundDrawable(
                    createDialogBackground()
            );

            window.setLayout(
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels
                                    * 0.88f
                    ),
                    WindowManager.LayoutParams
                            .WRAP_CONTENT
            );
        }
    }

    private GradientDrawable createDialogBackground() {

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(250, 250, 250)
        );

        background.setCornerRadius(
                dp(22)
        );

        return background;
    }

    private void showFolderMenu(
            Folder folder
    ) {

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(8), dp(8), dp(8), dp(8)
        );

        container.setBackgroundColor(
                Color.rgb(250, 250, 250)
        );

        TextView title = new TextView(this);
        title.setText(folder.getName());
        title.setTextSize(18);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(
                dp(16), dp(12), dp(16), dp(12)
        );

        container.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        TextView rename = createFolderActionRow(
                "重命名",
                Color.rgb(32, 33, 36)
        );

        TextView delete = createFolderActionRow(
                "删除文件夹",
                Color.rgb(210, 75, 75)
        );

        container.addView(
                rename,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        container.addView(
                delete,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(container)
                        .create();

        rename.setOnClickListener(v -> {
            dialog.dismiss();
            showRenameFolderDialog(folder);
        });

        delete.setOnClickListener(v -> {
            dialog.dismiss();
            showDeleteFolderDialog(folder);
        });

        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    createDialogBackground()
            );

            window.setLayout(
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels
                                    * 0.82f
                    ),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private TextView createFolderActionRow(
            String text,
            int textColor
    ) {

        TextView row = new TextView(this);

        row.setText(text);
        row.setTextSize(16);
        row.setTextColor(textColor);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(16), 0, dp(16), 0
        );
        row.setBackground(
                createActionRowBackground()
        );
        row.setClickable(true);
        row.setFocusable(true);

        return row;
    }

    private GradientDrawable createActionRowBackground() {

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(245, 245, 245)
        );

        background.setCornerRadius(
                dp(12)
        );

        return background;
    }

    private void showRenameFolderDialog(
            Folder folder
    ) {

        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setHint("文件夹名称");
        input.setText(folder.getName());
        input.setSelection(input.length());
        input.setTextSize(16);
        input.setTextColor(Color.rgb(32, 33, 36));
        input.setHintTextColor(Color.rgb(150, 150, 150));
        input.setPadding(
                dp(16), 0, dp(16), 0
        );

        GradientDrawable inputBackground =
                new GradientDrawable();

        inputBackground.setColor(
                Color.rgb(245, 245, 245)
        );
        inputBackground.setCornerRadius(dp(12));
        inputBackground.setStroke(
                dp(1),
                Color.rgb(225, 225, 225)
        );

        input.setBackground(inputBackground);

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );
        container.setPadding(
                dp(24), dp(6), dp(24), dp(8)
        );
        container.setBackgroundColor(
                Color.rgb(250, 250, 250)
        );

        TextView title = new TextView(this);
        title.setText("重命名文件夹");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(
                0, dp(12), 0, dp(12)
        );

        container.addView(
                title,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(54)
                )
        );

        container.addView(
                input,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(52)
                )
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(container)
                        .setNegativeButton(
                                "取消",
                                null
                        )
                        .setPositiveButton(
                                "保存",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {

            TextView positiveButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            positiveButton.setTextColor(
                    Color.rgb(107, 91, 0)
            );
            positiveButton.setTextSize(15);

            TextView negativeButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_NEGATIVE
                    );

            negativeButton.setTextColor(
                    Color.rgb(120, 120, 120)
            );
            negativeButton.setTextSize(15);

            positiveButton.setOnClickListener(v -> {

                String newName =
                        input.getText()
                                .toString()
                                .trim();

                if (newName.isEmpty()) {
                    input.setError(
                            "请输入文件夹名称"
                    );
                    input.requestFocus();
                    return;
                }

                new Thread(() -> {

                    db.folderDao()
                            .renameFolder(
                                    folder.getId(),
                                    newName
                            );

                    runOnUiThread(() -> {

                        dialog.dismiss();
                        loadFolders();

                        Toast.makeText(
                                this,
                                "文件夹已重命名",
                                Toast.LENGTH_SHORT
                        ).show();
                    });

                }).start();
            });

            input.requestFocus();

            input.postDelayed(() -> {

                InputMethodManager imm =
                        (InputMethodManager)
                                getSystemService(
                                        Context.INPUT_METHOD_SERVICE
                                );

                if (imm != null) {
                    imm.showSoftInput(
                            input,
                            InputMethodManager
                                    .SHOW_IMPLICIT
                    );
                }

            }, 200);
        });

        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    createDialogBackground()
            );

            window.setLayout(
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels
                                    * 0.88f
                    ),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void showDeleteFolderDialog(
            Folder folder
    ) {

        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );
        container.setPadding(
                dp(24), dp(8), dp(24), dp(8)
        );
        container.setBackgroundColor(
                Color.rgb(250, 250, 250)
        );

        TextView title = new TextView(this);
        title.setText("删除文件夹");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
        title.setPadding(
                0, dp(12), 0, dp(10)
        );

        TextView folderName = new TextView(this);
        folderName.setText(
                "「" + folder.getName() + "」"
        );
        folderName.setTextSize(17);
        folderName.setTextColor(
                Color.rgb(32, 33, 36)
        );
        folderName.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
        folderName.setPadding(
                dp(14), dp(10), dp(14), dp(10)
        );
        folderName.setBackground(
                createFolderNameBackground()
        );

        TextView message = new TextView(this);
        message.setText(
                "删除后，文件夹中的笔记不会被删除，"
                        + "会自动变成未分类。"
        );
        message.setTextSize(14);
        message.setTextColor(
                Color.rgb(110, 110, 110)
        );
        message.setLineSpacing(0, 1.15f);
        message.setPadding(
                0, dp(14), 0, dp(8)
        );

        container.addView(title);

        container.addView(
                folderName,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(48)
                )
        );

        container.addView(
                message,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(58)
                )
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setView(container)
                        .setNegativeButton(
                                "取消",
                                null
                        )
                        .setPositiveButton(
                                "删除",
                                null
                        )
                        .create();

        dialog.setOnShowListener(d -> {

            TextView deleteButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            deleteButton.setTextColor(
                    Color.rgb(210, 75, 75)
            );
            deleteButton.setTextSize(15);

            TextView cancelButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_NEGATIVE
                    );

            cancelButton.setTextColor(
                    Color.rgb(120, 120, 120)
            );
            cancelButton.setTextSize(15);

            deleteButton.setOnClickListener(v -> {

                new Thread(() -> {

                    db.folderDao()
                            .clearFolderFromNotes(
                                    folder.getId()
                            );

                    db.folderDao()
                            .delete(folder);

                    runOnUiThread(() -> {

                        dialog.dismiss();

                        if (selectedMenuId ==
                                folder.getId()) {

                            selectedMenuId = -1;
                            loadAllNotes();
                        }

                        loadFolders();

                        Toast.makeText(
                                this,
                                "文件夹已删除",
                                Toast.LENGTH_SHORT
                        ).show();

                    });

                }).start();
            });
        });

        dialog.show();

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    createDialogBackground()
            );

            window.setLayout(
                    (int) (
                            getResources()
                                    .getDisplayMetrics()
                                    .widthPixels
                                    * 0.88f
                    ),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private GradientDrawable createFolderNameBackground() {

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                Color.rgb(245, 245, 245)
        );
        background.setCornerRadius(
                dp(12)
        );

        return background;
    }


    /**
     * 刷新 Token 环。
     *
     * TokenRingView 是自定义 View。为了避免 MainActivity
     * 依赖它某个不存在的 setter，这里只要求它重新绘制。
     * 如果 TokenRingView 自己从 SharedPreferences 读取统计数据，
     * invalidate() 就会让它立即刷新。
     */
    private void updateTokenRing() {
        // The circle is an entry point, not a fabricated 100% token quota.
    }

    /**
     * 显示 Token 使用详情。
     *
     * AIClient 使用 token_stats 保存统计数据。
     * 这里同时兼容 int / long 两种保存方式。
     */
    private void showAiUsageDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(10));

        TextView title = new TextView(this);
        title.setText("AI 余额与 Token 消耗");
        title.setTextSize(21);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        aiBalanceProvider = createTokenInfoText("AI 账户余额");
        aiBalanceProvider.setPadding(0, dp(18), 0, dp(6));
        root.addView(aiBalanceProvider);

        aiBalanceAmount = new TextView(this);
        aiBalanceAmount.setText("正在查询余额…");
        aiBalanceAmount.setTextSize(19);
        aiBalanceAmount.setTextColor(Color.rgb(32, 33, 36));
        aiBalanceAmount.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(aiBalanceAmount);

        TextView refresh = createTokenInfoText("⟳  刷新余额");
        refresh.setTextColor(Color.rgb(130, 105, 28));
        refresh.setPadding(0, dp(12), 0, dp(12));
        refresh.setClickable(true);
        refresh.setOnClickListener(v -> refreshAiBalance());
        root.addView(refresh);
        root.addView(createTokenDivider());

        TextView lastLabel = createTokenInfoText("最近一次 AI 请求");
        lastLabel.setPadding(0, dp(14), 0, dp(6));
        root.addView(lastLabel);
        aiLastUsage = createTokenInfoText("暂无记录");
        root.addView(aiLastUsage);

        TextView tip = createTokenInfoText("余额来自服务商接口；Token 用量仅统计 MINENOTE 内的请求。余额不等于固定 Token 配额。");
        tip.setPadding(0, dp(18), 0, dp(4));
        root.addView(tip);

        aiUsageDialog = new AlertDialog.Builder(this)
                .setView(root)
                .setPositiveButton("关闭", null)
                .create();
        aiUsageDialog.setOnDismissListener(d -> {
            balanceRequestSequence++;
            aiBalanceProvider = null;
            aiBalanceAmount = null;
            aiLastUsage = null;
            aiUsageDialog = null;
        });
        aiUsageDialog.show();
        refreshAiBalance();
    }

    /** Refresh the provider's actual balance, never interpret it as a token quota. */
    private void refreshAiBalance() {
        SharedPreferences settings = getSharedPreferences("ai_settings", MODE_PRIVATE);
        String provider = settings.getString("provider", "OpenAI");
        String key = settings.getString("api_key", "").trim();
        int requestId = ++balanceRequestSequence;
        aiBalanceProvider.setText(provider + " · API 余额");
        updateLastUsage();
        if (!"DeepSeek".equalsIgnoreCase(provider)) {
            aiBalanceAmount.setText("当前服务商暂不支持余额查询");
            aiBalanceAmount.setTextSize(15);
            return;
        }
        if (key.isEmpty()) {
            aiBalanceAmount.setText("请先在设置中填写 API Key");
            aiBalanceAmount.setTextSize(15);
            return;
        }
        aiBalanceAmount.setText("正在查询余额…");
        aiBalanceAmount.setTextSize(16);
        AiBalanceClient.queryDeepSeek(key, result -> runOnUiThread(() -> {
            if (isFinishing() || aiBalanceAmount == null || requestId != balanceRequestSequence) return;
            SharedPreferences now = getSharedPreferences("ai_settings", MODE_PRIVATE);
            if (!"DeepSeek".equalsIgnoreCase(now.getString("provider", "")) ||
                    !key.equals(now.getString("api_key", "").trim())) return;
            aiBalanceAmount.setText(result);
            aiBalanceAmount.setTextSize(18);
        }));
    }

    private void updateLastUsage() {
        if (aiLastUsage == null) return;
        SharedPreferences settings = getSharedPreferences("ai_settings", MODE_PRIVATE);
        SharedPreferences stats = getSharedPreferences("token_stats", MODE_PRIVATE);
        String provider = settings.getString("provider", "OpenAI");
        String key = settings.getString("api_key", "");
        String stamp = provider + ":" + key.hashCode();
        if (!stamp.equals(stats.getString("last_account", ""))) {
            aiLastUsage.setText("最近一次 AI 请求：暂无记录");
            return;
        }
        long input = stats.getLong("last_prompt_tokens", 0);
        long output = stats.getLong("last_completion_tokens", 0);
        aiLastUsage.setText("最近一次请求：输入 " + input + " · 输出 " + output +
                " · 合计 " + (input + output) + " tokens");
    }

    private void showTokenUsageDialog() {

        SharedPreferences preferences =
                getSharedPreferences(
                        "token_stats",
                        MODE_PRIVATE
                );

        long promptTokens = getTokenValue(preferences, "prompt_tokens");
        long completionTokens = getTokenValue(preferences, "completion_tokens");
        long totalTokens = getTokenValue(preferences, "total_tokens");

        if (totalTokens == 0 &&
                (promptTokens > 0 || completionTokens > 0)) {
            totalTokens = promptTokens + completionTokens;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), dp(6));
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        // 标题
        TextView title = new TextView(this);
        title.setText("Token 消耗");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(32, 33, 36));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(4), 0, dp(3));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("AI 请求的 Token 使用情况");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(125, 125, 125));
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        // 总 Token 卡片
        LinearLayout totalCard = new LinearLayout(this);
        totalCard.setOrientation(LinearLayout.VERTICAL);
        totalCard.setGravity(Gravity.CENTER_VERTICAL);
        totalCard.setPadding(dp(20), dp(17), dp(20), dp(17));
        totalCard.setBackground(createTokenCardBackground(
                Color.rgb(255, 248, 225), dp(18)
        ));

        TextView totalLabel = new TextView(this);
        totalLabel.setText("总消耗");
        totalLabel.setTextSize(13);
        totalLabel.setTextColor(Color.rgb(110, 100, 65));

        TextView totalValue = new TextView(this);
        totalValue.setText(formatTokenNumber(totalTokens));
        totalValue.setTextSize(30);
        totalValue.setTextColor(Color.rgb(60, 55, 35));
        totalValue.setTypeface(null, android.graphics.Typeface.BOLD);
        totalValue.setPadding(0, dp(3), 0, 0);

        TextView totalUnit = new TextView(this);
        totalUnit.setText("tokens");
        totalUnit.setTextSize(12);
        totalUnit.setTextColor(Color.rgb(125, 115, 80));
        totalUnit.setPadding(0, dp(1), 0, 0);

        totalCard.addView(totalLabel);
        totalCard.addView(totalValue);
        totalCard.addView(totalUnit);

        root.addView(totalCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(118)
        ));

        // 输入 / 输出两项
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0, dp(12), 0, dp(6));

        LinearLayout inputCard = createTokenMetricCard(
                "输入 Token",
                formatTokenNumber(promptTokens)
        );

        LinearLayout outputCard = createTokenMetricCard(
                "输出 Token",
                formatTokenNumber(completionTokens)
        );

        LinearLayout.LayoutParams metricParams =
                new LinearLayout.LayoutParams(0, dp(82), 1f);
        metricParams.rightMargin = dp(5);
        metrics.addView(inputCard, metricParams);

        LinearLayout.LayoutParams outputParams =
                new LinearLayout.LayoutParams(0, dp(82), 1f);
        outputParams.leftMargin = dp(5);
        metrics.addView(outputCard, outputParams);

        root.addView(metrics);

        TextView hint = new TextView(this);
        hint.setText("输入：发送给 AI 的内容    ·    输出：AI 返回的内容");
        hint.setTextSize(11);
        hint.setTextColor(Color.rgb(145, 145, 145));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(8), 0, dp(6));
        root.addView(hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .setPositiveButton("关闭", null)
                .create();

        dialog.setOnShowListener(d -> {
            TextView closeButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );
            closeButton.setTextColor(Color.rgb(107, 91, 0));
            closeButton.setTextSize(14);
            closeButton.setTypeface(null, android.graphics.Typeface.BOLD);
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(createDialogBackground());
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private LinearLayout createTokenMetricCard(
            String label,
            String value
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(11), dp(15), dp(11));
        card.setBackground(createTokenCardBackground(
                Color.rgb(245, 245, 245), dp(15)
        ));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(Color.rgb(125, 125, 125));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(19);
        valueView.setTextColor(Color.rgb(45, 45, 45));
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setPadding(0, dp(4), 0, 0);

        card.addView(labelView);
        card.addView(valueView);

        return card;
    }

    private GradientDrawable createTokenCardBackground(
            int color,
            int radius
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private String formatTokenNumber(long value) {
        return String.format(
                java.util.Locale.getDefault(),
                "%,d",
                value
        );
    }

    private long getTokenValue(
            SharedPreferences preferences,
            String key
    ) {
        try {
            return preferences.getLong(key, 0L);
        } catch (ClassCastException e) {
            return preferences.getInt(key, 0);
        }
    }

    private TextView createTokenInfoText(
            String text
    ) {
        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(
                Color.rgb(120, 120, 120)
        );
        view.setPadding(
                0,
                dp(8),
                0,
                dp(2)
        );

        return view;
    }

    private TextView createTokenValueText(
            String text
    ) {
        TextView view =
                new TextView(this);

        view.setText(text);
        view.setTextSize(22);
        view.setTextColor(
                Color.rgb(32, 33, 36)
        );
        view.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );
        view.setPadding(
                0,
                0,
                0,
                dp(8)
        );

        return view;
    }

    private View createTokenDivider() {
        View divider =
                new View(this);

        divider.setBackgroundColor(
                Color.rgb(232, 232, 232)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );

        params.topMargin = dp(6);
        params.bottomMargin = dp(6);

        divider.setLayoutParams(params);

        return divider;
    }

    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }
}
