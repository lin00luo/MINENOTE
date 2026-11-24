package com.example.minenote;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.room.Room;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/** Requests screen capture consent and displays the saved frame for region selection. */
public class CaptureActivity extends AppCompatActivity {
    private static final int REQUEST_CAPTURE = 702;
    private static final int REQUEST_NOTIFICATIONS = 703;
    private String shape;
    private int noteId = -1;
    private Bitmap screenshot;
    private SelectionView selectionView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        noteId = getIntent().getIntExtra("note_id",
                getSharedPreferences("capture_state", MODE_PRIVATE).getInt("current_note_id", -1));
        String error = getIntent().getStringExtra("capture_error");
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String imagePath = getIntent().getStringExtra(ScreenCaptureService.EXTRA_IMAGE_PATH);
        if (imagePath != null) {
            shape = getIntent().getStringExtra("shape");
            if (shape == null) shape = "rect";
            screenshot = BitmapFactory.decodeFile(imagePath);
            new File(imagePath).delete();
            if (screenshot == null) {
                Toast.makeText(this, "截图文件无法读取", Toast.LENGTH_LONG).show();
                finish();
            } else showSelection();
            return;
        }
        if (noteId < 0) {
            Toast.makeText(this, "请先在 Minenote 中打开一篇笔记，再使用智能截图", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (state != null) shape = state.getString("shape");
        if (shape == null) chooseShape();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("shape", shape);
        super.onSaveInstanceState(state);
    }

    private void chooseShape() {
        new AlertDialog.Builder(this).setTitle("先选择截图形状")
                .setItems(new String[]{"矩形截图", "自由形状截图"}, (d, which) -> {
                    shape = which == 0 ? "rect" : "free";
                    requestProjection();
                }).setOnCancelListener(d -> finish()).show();
    }

    private void requestProjection() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        launchConsent();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) launchConsent();
    }

    private void launchConsent() {
        NotificationManagerCompat notifications = NotificationManagerCompat.from(this);
        android.app.NotificationChannel channel = Build.VERSION.SDK_INT >= 26
                ? notifications.getNotificationChannel(ScreenCaptureService.CHANNEL_ID) : null;
        if (!notifications.areNotificationsEnabled() || (channel != null
                && channel.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE)) {
            Toast.makeText(this, "请开启 Minenote 通知，以便打开截图选区", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法打开屏幕截图授权：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "已取消屏幕截图授权", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent service = new Intent(this, ScreenCaptureService.class);
        service.putExtra("resultCode", resultCode);
        service.putExtra("resultData", data);
        service.putExtra("shape", shape);
        service.putExtra("note_id", noteId);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (RuntimeException e) {
            Toast.makeText(this, "无法启动屏幕截图：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            // Reveal the app or window selected for sharing.
            finish();
        }
    }

    private void showSelection() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        TextView hint = new TextView(this);
        hint.setText("free".equals(shape) ? "手指圈出需要的区域" : "拖动手指框选矩形区域");
        hint.setTextColor(Color.WHITE);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(48)));
        selectionView = new SelectionView();
        root.addView(selectionView, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(this);
        Button cancel = new Button(this);
        cancel.setText("取消");
        Button done = new Button(this);
        done.setText("完成选区");
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(done, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(actions);
        setContentView(root);
        cancel.setOnClickListener(v -> finish());
        done.setOnClickListener(v -> {
            Bitmap crop = selectionView.crop();
            if (crop == null) {
                Toast.makeText(this, "请先选择有效区域", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("截图处理方式")
                    .setItems(new String[]{"识别文字并插入", "作为图片插入"}, (d, which) -> {
                        if (which == 0) recognizeAndInsert(crop);
                        else saveImageAndInsert(crop);
                    }).setOnCancelListener(d -> crop.recycle()).show();
        });
    }

    private void recognizeAndInsert(Bitmap crop) {
        Toast.makeText(this, "正在识别文字……", Toast.LENGTH_SHORT).show();
        com.google.mlkit.vision.text.TextRecognizer recognizer =
                TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener(result -> {
                    String value = result.getText();
                    if (value == null || value.trim().isEmpty()) {
                        Toast.makeText(this, "没有识别到文字", Toast.LENGTH_SHORT).show();
                    } else updateNote("\n" + value.trim() + "\n", false);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "OCR 失败：" + e.getMessage(), Toast.LENGTH_LONG).show())
                .addOnCompleteListener(task -> {
                    recognizer.close();
                    crop.recycle();
                });
    }

    private void saveImageAndInsert(Bitmap crop) {
        new Thread(() -> {
            try {
                File folder = new File(getFilesDir(), "note_images");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("无法创建图片目录");
                String name = UUID.randomUUID().toString() + ".jpg";
                File image = new File(folder, name);
                try (FileOutputStream out = new FileOutputStream(image)) {
                    if (!crop.compress(Bitmap.CompressFormat.JPEG, 90, out))
                        throw new IllegalStateException("图片编码失败");
                }
                runOnUiThread(() -> updateNote("\n[[MINENOTE_IMAGE:" + name + "]]\n", true));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "图片保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                crop.recycle();
            }
        }).start();
    }

    private void updateNote(String addition, boolean image) {
        if (noteId < 0) {
            Toast.makeText(this, "没有找到原笔记，请先打开笔记后再截图", Toast.LENGTH_LONG).show();
            return;
        }
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "minenote_database")
                .addMigrations(AppDatabaseMigration.MIGRATION_1_2,
                        AppDatabaseMigrationV3.MIGRATION_2_3,
                        AppDatabaseMigrationV4.MIGRATION_3_4,
                        AppDatabaseMigrationV5.MIGRATION_4_5,
                        ReaderDatabaseMigrationV6.MIGRATION_5_6).build();
        new Thread(() -> {
            try {
                Note note = db.noteDao().getNoteById(noteId);
                if (note == null) throw new IllegalStateException("原笔记不存在");
                note.setContent((note.getContent() == null ? "" : note.getContent()) + addition);
                db.noteDao().update(note);
                runOnUiThread(() -> {
                    Toast.makeText(this, image ? "截图图片已插入笔记" : "识别文字已插入笔记", Toast.LENGTH_SHORT).show();
                    Intent back = new Intent(this, NoteDetailActivity.class);
                    back.putExtra("note_id", noteId);
                    back.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(back);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "笔记更新失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { db.close(); }
        }).start();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    private class SelectionView extends View {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint dimPaint = new Paint();
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF imageRect = new RectF();
        private float startX, startY, endX, endY;
        private boolean selected;

        SelectionView() {
            super(CaptureActivity.this);
            dimPaint.setColor(0x99000000);
            outline.setColor(0xFFFFD66B);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(3));
            outline.setStrokeJoin(Paint.Join.ROUND);
            outline.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (screenshot == null) return;
            float scale = Math.min((float) getWidth() / screenshot.getWidth(),
                    (float) getHeight() / screenshot.getHeight());
            float w = screenshot.getWidth() * scale, h = screenshot.getHeight() * scale;
            imageRect.set((getWidth()-w)/2f, (getHeight()-h)/2f,
                    (getWidth()+w)/2f, (getHeight()+h)/2f);
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(screenshot, null, imageRect, bitmapPaint);
            canvas.drawRect(imageRect, dimPaint);
            if (!selected) return;
            canvas.save();
            if ("rect".equals(shape)) {
                RectF rect = rect();
                canvas.clipRect(rect);
            } else canvas.clipPath(path);
            canvas.drawBitmap(screenshot, null, imageRect, bitmapPaint);
            canvas.restore();
            if ("rect".equals(shape)) canvas.drawRect(rect(), outline);
            else canvas.drawPath(path, outline);
        }

        private RectF rect() {
            RectF r = new RectF(startX, startY, endX, endY);
            r.sort();
            return r;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = Math.max(imageRect.left, Math.min(imageRect.right, event.getX()));
            float y = Math.max(imageRect.top, Math.min(imageRect.bottom, event.getY()));
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = endX = x; startY = endY = y;
                    path.reset(); path.moveTo(x, y);
                    selected = false; invalidate(); return true;
                case MotionEvent.ACTION_MOVE:
                    endX = x; endY = y;
                    if ("free".equals(shape)) path.lineTo(x, y);
                    invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    endX = x; endY = y;
                    if ("free".equals(shape)) { path.lineTo(x, y); path.close(); }
                    selected = true; invalidate(); return true;
            }
            return true;
        }

        Bitmap crop() {
            if (!selected || imageRect.isEmpty()) return null;
            RectF bounds;
            if ("rect".equals(shape)) bounds = rect();
            else { bounds = new RectF(); path.computeBounds(bounds, true); }
            if (bounds.width() < dp(12) || bounds.height() < dp(12)) return null;

            float sx = screenshot.getWidth() / imageRect.width();
            float sy = screenshot.getHeight() / imageRect.height();
            int left = Math.max(0, Math.round((bounds.left-imageRect.left)*sx));
            int top = Math.max(0, Math.round((bounds.top-imageRect.top)*sy));
            int right = Math.min(screenshot.getWidth(), Math.round((bounds.right-imageRect.left)*sx));
            int bottom = Math.min(screenshot.getHeight(), Math.round((bounds.bottom-imageRect.top)*sy));
            if (right <= left || bottom <= top) return null;
            Bitmap rectangular = Bitmap.createBitmap(screenshot, left, top, right-left, bottom-top);
            if ("rect".equals(shape)) return rectangular;

            Bitmap masked = Bitmap.createBitmap(rectangular.getWidth(), rectangular.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(masked);
            MatrixHolder holder = new MatrixHolder(sx, sy, imageRect.left, imageRect.top, left, top);
            Path bitmapPath = new Path();
            path.transform(holder.matrix, bitmapPath);
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawPath(bitmapPath, maskPaint);
            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            imagePaint.setXfermode(new android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(rectangular, 0, 0, imagePaint);
            imagePaint.setXfermode(null);
            rectangular.recycle();
            return masked;
        }
    }

    private static class MatrixHolder {
        final android.graphics.Matrix matrix = new android.graphics.Matrix();
        MatrixHolder(float sx, float sy, float imageLeft, float imageTop, int cropLeft, int cropTop) {
            matrix.setValues(new float[]{sx, 0, -imageLeft*sx-cropLeft,
                    0, sy, -imageTop*sy-cropTop, 0, 0, 1});
        }
    }
}
