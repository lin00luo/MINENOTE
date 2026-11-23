package com.example.minenote;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** Captures one frame after the capture activity has left the foreground. */
public class ScreenCaptureService extends Service {
    public static final String EXTRA_IMAGE_PATH = "image_path";
    static final String CHANNEL_ID = "minenote_screen_capture";
    private static final int NOTIFICATION_ID = 701;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private static final String ACTION_CANCEL = "com.example.minenote.CANCEL_CAPTURE";
    private static final long FRAME_TIMEOUT_MS = 5000;
    private boolean capturing;
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { stopCapture(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            stopCapture();
            return START_NOT_STICKY;
        }
        if (capturing) return START_NOT_STICKY;
        capturing = true;
        final String shape = intent == null ? null : intent.getStringExtra("shape");
        final int noteId = intent == null ? -1 : intent.getIntExtra("note_id", -1);

        try {
            PendingIntent cancel = PendingIntent.getService(this, NOTIFICATION_ID,
                    new Intent(this, ScreenCaptureService.class).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_IMMUTABLE);
            Notification ongoing = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Minenote 正在截图")
                    .setContentText("正在获取屏幕画面")
                    .setOngoing(true)
                    .addAction(0, "取消截图", cancel)
                    .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, ongoing,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else startForeground(NOTIFICATION_ID, ongoing);

            Intent permissionData = intent == null ? null : getProjectionData(intent);
            int resultCode = intent == null ? Activity.RESULT_CANCELED
                    : intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            if (permissionData == null || resultCode != Activity.RESULT_OK) {
                stopCapture();
                return START_NOT_STICKY;
            }
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, permissionData);
            if (projection == null) throw new IllegalStateException("无法启动屏幕捕获");

            projection.registerCallback(projectionCallback, handler);
            handler.postDelayed(() -> fail(shape, noteId, "等待屏幕画面超时"), FRAME_TIMEOUT_MS);
            // Let the permission activity leave before producing the first frame.
            handler.postDelayed(() -> startDisplay(shape, noteId), 650);
        } catch (RuntimeException e) {
            fail(shape, noteId, "截图启动失败：" + e.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void startDisplay(String shape, int noteId) {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            final int width;
            final int height;
            final int densityDpi = getResources().getDisplayMetrics().densityDpi;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
                width = bounds.width();
                height = bounds.height();
            } else {
                DisplayMetrics metrics = new DisplayMetrics();
                //noinspection deprecation
                wm.getDefaultDisplay().getRealMetrics(metrics);
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            }
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(reader -> captureFrame(reader, shape, noteId), handler);
            virtualDisplay = projection.createVirtualDisplay(
                    "MinenoteCapture", width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);

            if (virtualDisplay == null) throw new IllegalStateException("无法创建截图显示");
        } catch (RuntimeException e) {
            fail(shape, noteId, "截图启动失败：" + e.getMessage());
        }
    }

    private void captureFrame(ImageReader reader, String shape, int noteId) {
        if (reader != imageReader) return;
        Bitmap frame;
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) return;
            frame = copyFrame(image);
        } catch (RuntimeException | OutOfMemoryError e) {
            fail(shape, noteId, "读取屏幕画面失败：" + e.getMessage());
            return;
        }

        // Close the projection and its foreground notification before saving or showing the result.
        stopCapture();
        File output = null;
        try {
            output = File.createTempFile("minenote_capture_", ".png", getCacheDir());
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!frame.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IllegalStateException("截图文件写入失败");
            }
            showResultNotification(output.getAbsolutePath(), shape, noteId, null);
        } catch (Exception | OutOfMemoryError e) {
            if (output != null) output.delete();
            showResultNotification(null, shape, noteId, "截图保存失败：" + e.getMessage());
        } finally {
            frame.recycle();
        }
    }

    private Bitmap copyFrame(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowBytes = width * 4;
        if (plane.getPixelStride() != 4 || plane.getRowStride() < rowBytes)
            throw new IllegalStateException("不支持的截图像素格式");

        // The final row may omit its trailing padding.
        ByteBuffer pixels = ByteBuffer.allocate(rowBytes * height);
        int start = buffer.position();
        int limit = buffer.limit();
        for (int row = 0; row < height; row++) {
            int offset = start + row * plane.getRowStride();
            buffer.limit(limit);
            buffer.position(offset);
            buffer.limit(offset + rowBytes);
            pixels.put(buffer);
        }
        pixels.flip();
        Bitmap frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            frame.copyPixelsFromBuffer(pixels);
            return frame;
        } catch (RuntimeException | OutOfMemoryError e) {
            frame.recycle();
            throw e;
        }
    }

    private void fail(String shape, int noteId, String message) {
        stopCapture();
        showResultNotification(null, shape, noteId, message);
    }

    private void showResultNotification(String path, String shape, int noteId, String error) {
        Intent open = new Intent(this, CaptureActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (path != null) open.putExtra(EXTRA_IMAGE_PATH, path);
        if (error != null) open.putExtra("capture_error", error);
        open.putExtra("shape", shape);
        open.putExtra("note_id", noteId);
        PendingIntent pending = PendingIntent.getActivity(this, 702, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification result = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(error == null ? "截图已准备好" : "Minenote 截图失败")
                .setContentText(error == null ? "点击选择截图区域并插入笔记" : error)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        try {
            manager.notify(NOTIFICATION_ID + 1, result);
        } catch (RuntimeException e) {
            if (path != null) new File(path).delete();
            Log.w("ScreenCaptureService", "Could not publish capture result", e);
        }
    }

    private Intent getProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra("resultData", Intent.class);
        //noinspection deprecation
        return intent.getParcelableExtra("resultData");
    }

    private void releaseCapture() {
        handler.removeCallbacksAndMessages(null);
        VirtualDisplay display = virtualDisplay;
        ImageReader reader = imageReader;
        MediaProjection activeProjection = projection;
        virtualDisplay = null;
        imageReader = null;
        projection = null;
        if (display != null) cleanup(display::release);
        if (reader != null) {
            cleanup(() -> reader.setOnImageAvailableListener(null, null));
            cleanup(reader::close);
        }
        if (activeProjection != null) {
            cleanup(() -> activeProjection.unregisterCallback(projectionCallback));
            cleanup(activeProjection::stop);
        }
    }

    private void cleanup(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Log.w("ScreenCaptureService", "Capture cleanup failed", e);
        }
    }

    private void stopCapture() {
        releaseCapture();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        releaseCapture();
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "屏幕截图", NotificationManager.IMPORTANCE_DEFAULT);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
