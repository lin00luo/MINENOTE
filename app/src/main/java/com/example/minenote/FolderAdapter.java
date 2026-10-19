package com.example.minenote;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FolderAdapter
        extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    public interface Listener {
        void onFolderClick(Folder folder);

        void onFolderMore(Folder folder);

        void onStartDrag(FolderViewHolder holder);
    }

    private final List<Folder> folders;
    private final Listener listener;
    private int selectedFolderId = -1;

    private final int NORMAL_TEXT = Color.rgb(68, 68, 68);
    private final int SELECTED_TEXT = Color.rgb(32, 33, 36);
    private final int SELECTED_BACKGROUND = Color.rgb(255, 248, 232);

    public FolderAdapter(
            List<Folder> folders,
            Listener listener
    ) {
        this.folders = folders;
        this.listener = listener;
    }

    public void setFolders(List<Folder> newFolders) {
        folders.clear();

        if (newFolders != null) {
            folders.addAll(newFolders);
        }

        notifyDataSetChanged();
    }

    public void setSelectedFolderId(int folderId) {
        selectedFolderId = folderId;
        notifyDataSetChanged();
    }

    public List<Folder> getFolders() {
        return folders;
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0
                || toPosition < 0
                || fromPosition >= folders.size()
                || toPosition >= folders.size()) {
            return;
        }

        Folder movedFolder = folders.remove(fromPosition);
        folders.add(toPosition, movedFolder);

        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.folder_item, parent, false);

        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FolderViewHolder holder,
            int position
    ) {
        Folder folder = folders.get(position);

        holder.name.setText(folder.getName());

        boolean selected = folder.getId() == selectedFolderId;

        if (selected) {
            holder.root.setBackground(createSelectedBackground());
            holder.name.setTextColor(SELECTED_TEXT);
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT);
            holder.name.setTextColor(NORMAL_TEXT);
        }

        holder.root.setOnClickListener(v ->
                listener.onFolderClick(folder)
        );

        holder.more.setOnClickListener(v ->
                listener.onFolderMore(folder)
        );

        holder.root.setOnLongClickListener(v -> {
            listener.onStartDrag(holder);
            return true;
        });
    }

    private GradientDrawable createSelectedBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(SELECTED_BACKGROUND);
        background.setCornerRadius(
                holderDp(10)
        );
        return background;
    }

    private int holderDp(int value) {
        return (int) (
                value
                        * foldersContextDensity
                        + 0.5f
        );
    }

    private float foldersContextDensity = 1f;

    @Override
    public void onAttachedToRecyclerView(
            @NonNull RecyclerView recyclerView
    ) {
        super.onAttachedToRecyclerView(recyclerView);
        foldersContextDensity =
                recyclerView.getResources()
                        .getDisplayMetrics()
                        .density;
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    static class FolderViewHolder
            extends RecyclerView.ViewHolder {

        LinearLayout root;
        TextView name;
        TextView more;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);

            root = itemView.findViewById(
                    R.id.folderItemRoot
            );

            name = itemView.findViewById(
                    R.id.tvFolderName
            );

            more = itemView.findViewById(
                    R.id.btnFolderMore
            );
        }
    }
}
