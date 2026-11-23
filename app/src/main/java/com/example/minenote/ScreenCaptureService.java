package com.example.minenote;

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
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** Captures one frame after the capture activity has left the foreground. */
public class ScreenCaptureService extends Service {
    public static final String EXTRA_IMAGE_PATH = "image_path";
    private static final String CHANNEL_ID = "minenote_screen_capture";
    private static final int NOTIFICATION_ID = 701;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean delivered;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification ongoing = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Minenote 正在截图")
                .setContentText("正在获取屏幕画面")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, ongoing,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(NOTIFICATION_ID, ongoing);

        if (intent == null) { stopCapture(); return START_NOT_STICKY; }
        Intent permissionData = getProjectionData(intent);
        if (permissionData == null) { stopCapture(); return START_NOT_STICKY; }
        final int resultCode = intent.getIntExtra("resultCode", -1);
        final String shape = intent.getStringExtra("shape");
        final int noteId = intent.getIntExtra("note_id", -1);

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, permissionData);
            if (projection == null) throw new IllegalStateException("无法启动屏幕捕获");

            // A Service has no associated Display on Android 16.
            // Use maximum window metrics from the WindowManager instead of Context.getDisplay().
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
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { releaseCapture(); }
            }, handler);

            virtualDisplay = projection.createVirtualDisplay(
                    "MinenoteCapture", width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, handler);

            // Give Android time to return to the app that was underneath the tile activity.
            handler.postDelayed(() -> captureFrame(width, height, shape, noteId), 650);
        } catch (Exception e) {
            showResultNotification(null, shape, noteId, "截图启动失败：" + e.getMessage());
            stopCapture();
        }
        return START_NOT_STICKY;
    }

    private void captureFrame(int width, int height, String shape, int noteId) {
        if (imageReader == null || delivered) return;
        imageReader.setOnImageAvailableListener(reader -> {
            if (delivered) return;
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;
                delivered = true;
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();
                int pixelStride = plane.getPixelStride();
                int rowPadding = plane.getRowStride() - pixelStride * width;
                Bitmap padded = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                buffer.rewind();
                padded.copyPixelsFromBuffer(buffer);
                Bitmap frame = Bitmap.createBitmap(padded, 0, 0, width, height);
                padded.recycle();

                File output = new File(getCacheDir(),
                        "minenote_capture_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!frame.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        throw new IllegalStateException("截图文件写入失败");
                }
                frame.recycle();
                showResultNotification(output.getAbsolutePath(), shape, noteId, null);
            } catch (Exception e) {
                showResultNotification(null, shape, noteId, "截图失败：" + e.getMessage());
            } finally {
                if (image != null) image.close();
                stopCapture();
            }
        }, handler);
    }

    private void showResultNotification(String path, String shape, int noteId, String error) {
        Intent open = new Intent(this, CaptureActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
        manager.notify(NOTIFICATION_ID + 1, result);
    }

    private Intent getProjectionData(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) return intent.getParcelableExtra("resultData", Intent.class);
        //noinspection deprecation
        return intent.getParcelableExtra("resultData");
    }

    private void releaseCapture() {
        VirtualDisplay display = virtualDisplay;
        ImageReader reader = imageReader;
        MediaProjection activeProjection = projection;
        virtualDisplay = null;
        imageReader = null;
        projection = null;
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (activeProjection != null) activeProjection.stop(); } catch (Exception ignored) {}
    }

    private void stopCapture() {
        handler.post(() -> {
            releaseCapture();
            stopForeground(true);
            stopSelf();
        });
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
