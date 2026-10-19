package com.example.minenote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter
        extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final List<Note> notes;

    private final OnNoteClickListener listener;


    // =========================================================
    // 点击监听
    // =========================================================

    public interface OnNoteClickListener {

        void onNoteClick(Note note);
    }


    // =========================================================
    // 构造方法
    // =========================================================

    public NoteAdapter(
            List<Note> notes,
            OnNoteClickListener listener
    ) {

        this.notes = notes;

        this.listener = listener;
    }


    // =========================================================
    // 更新笔记列表
    // =========================================================

    public void setNotes(List<Note> newNotes) {

        notes.clear();

        if (newNotes != null) {

            notes.addAll(newNotes);
        }

        notifyDataSetChanged();
    }


    // =========================================================
    // 创建 ViewHolder
    // =========================================================

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view =
                LayoutInflater.from(
                        parent.getContext()
                ).inflate(
                        R.layout.item_note,
                        parent,
                        false
                );

        return new NoteViewHolder(view);
    }


    // =========================================================
    // 绑定数据
    // =========================================================

    @Override
    public void onBindViewHolder(
            @NonNull NoteViewHolder holder,
            int position
    ) {

        Note note =
                notes.get(position);


        // =====================================================
        // 标题
        // =====================================================

        holder.titleText.setText(
                note.getTitle()
        );


        // =====================================================
        // 内容
        // =====================================================

        holder.contentText.setText(
                note.getContent()
        );


        // =====================================================
        // 日期
        // =====================================================

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "yyyy/M/d",
                        Locale.getDefault()
                );


        String date =
                dateFormat.format(
                        new Date(
                                note.getCreatedAt()
                        )
                );


        holder.dateText.setText(
                date
        );


        // =====================================================
        // 文件夹
        // =====================================================

        Integer folderId =
                note.getFolderId();


        if (folderId == null) {

            holder.folderText.setText(
                    "未分类"
            );

        } else {

            holder.folderText.setText(
                    "文件夹"
            );


            // Room 查询放到后台线程
            new Thread(() -> {

                AppDatabase database =
                        androidx.room.Room.databaseBuilder(
                                        holder.itemView
                                                .getContext()
                                                .getApplicationContext(),
                                        AppDatabase.class,
                                        "minenote_database"
                                )
                                .addMigrations(
                                        AppDatabaseMigration.MIGRATION_1_2,
                                        AppDatabaseMigrationV3.MIGRATION_2_3,
                                        AppDatabaseMigrationV4.MIGRATION_3_4,
                                        AppDatabaseMigrationV5.MIGRATION_4_5
                                )
                                .build();


                FolderDao folderDao =
                        database.folderDao();


                List<Folder> folders =
                        folderDao.getAllFolders();


                String folderName = null;


                for (Folder folder : folders) {

                    if (
                            folder.getId()
                                    == folderId
                    ) {

                        folderName =
                                folder.getName();

                        break;
                    }
                }


                String finalFolderName =
                        folderName;


                database.close();


                holder.itemView.post(() -> {

                    /*
                     * RecyclerView 会复用 ViewHolder，
                     * 所以这里再次检查当前笔记的文件夹。
                     */

                    if (
                            note.getFolderId()
                                    == null
                    ) {

                        holder.folderText.setText(
                                "未分类"
                        );

                    } else if (
                            finalFolderName != null
                    ) {

                        holder.folderText.setText(

                                        finalFolderName
                        );

                    } else {

                        holder.folderText.setText(
                                "未分类"
                        );
                    }
                });


            }).start();
        }


        // =====================================================
        // 点击笔记
        // =====================================================

        holder.itemView.setOnClickListener(v -> {

            listener.onNoteClick(note);

        });
    }


    // =========================================================
    // 获取项目数量
    // =========================================================

    @Override
    public int getItemCount() {

        return notes.size();
    }


    // =========================================================
    // ViewHolder
    // =========================================================

    static class NoteViewHolder
            extends RecyclerView.ViewHolder {

        TextView titleText;

        TextView contentText;

        TextView dateText;

        TextView folderText;


        public NoteViewHolder(
                @NonNull View itemView
        ) {

            super(itemView);


            titleText =
                    itemView.findViewById(
                            R.id.tvNoteTitle
                    );


            contentText =
                    itemView.findViewById(
                            R.id.tvNoteContent
                    );


            dateText =
                    itemView.findViewById(
                            R.id.tvNoteDate
                    );


            folderText =
                    itemView.findViewById(
                            R.id.tvNoteFolder
                    );
        }
    }
}