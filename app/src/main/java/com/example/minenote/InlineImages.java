package com.example.minenote;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps image references as stable text markers, so existing Room notes need no migration. */
public final class InlineImages {
    private InlineImages() {}
    public static final Pattern MARKER = Pattern.compile("\\[\\[MINENOTE_IMAGE:([a-zA-Z0-9_-]+\\.jpg)(?::([1-9][0-9]{0,5}))?\\]\\]");

    public static String importImage(Context context, Uri uri) throws Exception {
        File folder = new File(context.getFilesDir(), "note_images");
        if (!folder.exists() && !folder.mkdirs()) throw new Exception("Cannot create image folder");
        String name = UUID.randomUUID().toString() + ".jpg";
        File target = new File(folder, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Cannot read selected image");
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1800) sample *= 2;
            try (InputStream second = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                Bitmap bmp = BitmapFactory.decodeStream(second, null, opts);
                if (bmp == null) throw new Exception("Unsupported image");
                try (FileOutputStream out = new FileOutputStream(target)) {
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)) throw new Exception("Image encoding failed");
                }
                bmp.recycle();
            }
        } catch (Exception e) {
            target.delete();
            throw e;
        }
        return "[[MINENOTE_IMAGE:" + name + "]]";
    }

    public static File file(Context context, String name) {
        return new File(new File(context.getFilesDir(), "note_images"), name);
    }

    public static void render(Context context, EditText editor) {
        SpannableStringBuilder value = new SpannableStringBuilder(editor.getText());
        ImageSpan[] previous = value.getSpans(0, value.length(), ImageSpan.class);
        for (ImageSpan span : previous) value.removeSpan(span);
        Matcher matcher = MARKER.matcher(value.toString());
        int maxWidth = editorWidth(editor);
        while (matcher.find()) {
            File f = file(context, matcher.group(1));
            if (!f.isFile()) continue;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int sample = 1;
            while (Math.max(opts.outWidth / sample, opts.outHeight / sample) > 1200) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bmp == null) continue;
            String savedWidth = matcher.group(2);
            int width = savedWidth == null ? bmp.getWidth() : dp(context, Integer.parseInt(savedWidth));
            ResizeDrawable drawable = new ResizeDrawable(context, bmp);
            drawable.setWidth(Math.min(maxWidth, width));
            value.setSpan(new ImageSpan(drawable, f.getAbsolutePath(), ImageSpan.ALIGN_BOTTOM),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int cursor = Math.max(0, editor.getSelectionStart());
        editor.setText(value);
        editor.setSelection(Math.min(cursor, editor.length()));
        enableImageGestures(editor);
    }

    private static void enableImageGestures(EditText editor) {
        GestureDetector detector = new GestureDetector(editor.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    private ImageSpan firstImage;

                    @Override public boolean onDoubleTap(MotionEvent firstDown) {
                        firstImage = imageAt(editor, firstDown);
                        return false;
                    }

                    @Override public boolean onDoubleTapEvent(MotionEvent event) {
                        if (firstImage == null) return false;
                        ImageSpan tapped = imageAt(editor, event);
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && tapped != firstImage) {
                            firstImage = null;
                            return false;
                        }
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            if (tapped == firstImage) showPreview(editor.getContext(), tapped.getSource());
                            firstImage = null;
                        } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                            firstImage = null;
                        }
                        return true;
                    }
                });
        detector.setIsLongpressEnabled(false);
        editor.setOnTouchListener(new ResizeGesture(editor, detector));
    }

    private static ImageSpan imageAt(EditText editor, MotionEvent event) {
        Layout layout = editor.getLayout();
        if (layout == null) return null;
        float x = event.getX() - editor.getTotalPaddingLeft() + editor.getScrollX();
        float y = event.getY() - editor.getTotalPaddingTop() + editor.getScrollY();
        if (y < 0 || y >= layout.getHeight()) return null;
        int line = layout.getLineForVertical((int) y);
        Spannable text = editor.getText();
        for (ImageSpan span : text.getSpans(layout.getLineStart(line), layout.getLineEnd(line),
                ImageSpan.class)) {
            int start = text.getSpanStart(span);
            if (span.getSource() == null || layout.getLineForOffset(start) != line) continue;
            if (imageBounds(layout, line, start, span).contains(x, y)) {
                return span;
            }
        }
        return null;
    }

    private static RectF imageBounds(Layout layout, int line, int start, ImageSpan span) {
        float left = layout.getPrimaryHorizontal(start);
        int bottom = layout.getLineBottom(line);
        Rect size = span.getDrawable().getBounds();
        return new RectF(left, bottom - size.height(), left + size.width(), bottom);
    }

    private static int editorWidth(EditText editor) {
        int width = editor.getWidth() - editor.getTotalPaddingLeft() - editor.getTotalPaddingRight();
        if (width > 0) return width;
        Context context = editor.getContext();
        return Math.max(1, context.getResources().getDisplayMetrics().widthPixels - dp(context, 64));
    }

    private static final class ResizeDrawable extends BitmapDrawable {
        private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int handleSize;

        ResizeDrawable(Context context, Bitmap bitmap) {
            super(context.getResources(), bitmap);
            handleSize = dp(context, 22);
        }

        void setWidth(int width) {
            int height = Math.max(1, Math.round(width * aspectRatio()));
            setBounds(0, 0, width, height);
        }

        float aspectRatio() {
            return (float) getBitmap().getHeight() / getBitmap().getWidth();
        }

        @Override public void draw(Canvas canvas) {
            super.draw(canvas);
            Rect bounds = getBounds();
            int size = Math.min(handleSize, Math.min(bounds.width(), bounds.height()));
            float x = bounds.right - size / 2f;
            float y = bounds.bottom - size / 2f;
            handle.setColor(0xff245da8);
            canvas.drawCircle(x, y, size * .46f, handle);
            handle.setColor(Color.WHITE);
            for (int i = 1; i <= 3; i++) {
                float offset = size * i / 8f;
                canvas.drawCircle(x + offset - size / 4f, y - offset + size / 4f,
                        size * .065f, handle);
            }
        }
    }

    private static final class ResizeGesture implements android.view.View.OnTouchListener {
        private final EditText editor;
        private final GestureDetector preview;
        private ImageSpan active;
        private int originalWidth;
        private float startX;
        private float startY;

        ResizeGesture(EditText editor, GestureDetector preview) {
            this.editor = editor;
            this.preview = preview;
        }

        @Override public boolean onTouch(android.view.View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN && beginResize(event)) return true;
            if (active == null) return preview.onTouchEvent(event);

            if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                ResizeDrawable drawable = (ResizeDrawable) active.getDrawable();
                float ratio = drawable.aspectRatio();
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                int width = originalWidth + Math.round((dx + dy * ratio) / (1 + ratio * ratio));
                int max = editorWidth(editor);
                int min = Math.min(max, Math.min(originalWidth, dp(editor.getContext(), 48)));
                updateWidth(Math.max(min, Math.min(max, width)));
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (action == MotionEvent.ACTION_UP) saveWidth();
                else updateWidth(originalWidth);
                active = null;
                editor.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }

        private boolean beginResize(MotionEvent event) {
            if (!editor.isEnabled() || editor.getKeyListener() == null) return false;
            ImageSpan span = imageAt(editor, event);
            if (span == null || !(span.getDrawable() instanceof ResizeDrawable)) return false;
            Layout layout = editor.getLayout();
            int start = editor.getText().getSpanStart(span);
            RectF bounds = imageBounds(layout, layout.getLineForOffset(start), start, span);
            float x = event.getX() - editor.getTotalPaddingLeft() + editor.getScrollX();
            float y = event.getY() - editor.getTotalPaddingTop() + editor.getScrollY();
            int hitSize = dp(editor.getContext(), 30);
            if (x < bounds.right - Math.min(hitSize, bounds.width() / 2)
                    || y < bounds.bottom - Math.min(hitSize, bounds.height() / 2)) return false;
            active = span;
            originalWidth = span.getDrawable().getBounds().width();
            startX = event.getRawX();
            startY = event.getRawY();
            editor.getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        private void updateWidth(int width) {
            ResizeDrawable drawable = (ResizeDrawable) active.getDrawable();
            if (drawable.getBounds().width() == width) return;
            Editable text = editor.getText();
            int start = text.getSpanStart(active);
            int end = text.getSpanEnd(active);
            if (start < 0) return;
            text.removeSpan(active);
            drawable.setWidth(width);
            text.setSpan(active, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        private void saveWidth() {
            int width = active.getDrawable().getBounds().width();
            if (width == originalWidth) return;
            Editable text = editor.getText();
            int start = text.getSpanStart(active);
            int end = text.getSpanEnd(active);
            if (start < 0) return;
            Matcher marker = MARKER.matcher(text).region(start, end);
            if (!marker.matches()) return;

            float density = editor.getResources().getDisplayMetrics().density;
            int widthDp = Math.max(1, Math.round(width / density));
            String replacement = "[[MINENOTE_IMAGE:" + marker.group(1) + ":" + widthDp + "]]";
            text.removeSpan(active);
            SpannableStringBuilder sizedImage = new SpannableStringBuilder(replacement);
            sizedImage.setSpan(active, 0, replacement.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.replace(start, end, sizedImage);
        }
    }

    private static void showPreview(Context context, String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2560) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) {
            Toast.makeText(context, "无法打开图片", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        Button close = new Button(context);
        close.setText("关闭预览");
        close.setOnClickListener(view -> dialog.dismiss());
        root.addView(close, new LinearLayout.LayoutParams(-1, -2));
        ImageView image = new ImageView(context);
        image.setContentDescription("笔记图片预览");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> image.setImageDrawable(null));
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static void insert(Context context, EditText editor, Uri uri) {
        new Thread(() -> {
            try {
                String marker = importImage(context, uri);
                editor.post(() -> {
                    int cursor = Math.max(0, editor.getSelectionStart());
                    editor.getText().insert(cursor, "\n" + marker + "\n");
                    render(context, editor);
                });
            } catch (Exception e) {
                editor.post(() -> android.widget.Toast.makeText(context, "图片插入失败：" + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    private static int dp(Context c, int d) { return (int)(d * c.getResources().getDisplayMetrics().density); }
}
