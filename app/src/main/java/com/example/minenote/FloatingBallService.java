package com.example.minenote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Draggable, edge-snapping pencil shortcut. Long-press to hide it. */
public class FloatingBallService extends Service {
    public static final String ACTION_STOP = "com.example.minenote.STOP_FLOATING_BALL";
    private static final String CHANNEL = "minenote_floating_ball";
    private static final int NOTIFICATION_ID = 731;
    private static final long LONG_PRESS_MS = 650L;
    private WindowManager windowManager;
    private PencilButton ball;
    private WindowManager.LayoutParams params;
    private float downX, downY, startX, startY;
    private boolean moved;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Minenote 截图按钮已开启")
                .setContentText("点击铅笔截图；拖动移动；长按隐藏")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification);
        } else startForeground(NOTIFICATION_ID, notification);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Settings.canDrawOverlays(this)) addBall();
        else stopSelf();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            hideAndStop();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void addBall() {
        if (ball != null) return;
        ball = new PencilButton();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF4D98B);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(1), 0xFFD5B45C);
        ball.setBackground(bg);
        ball.setElevation(dp(6));
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(48), dp(48), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getSharedPreferences("floating_ball", MODE_PRIVATE).getInt("x", 0);
        params.y = getSharedPreferences("floating_ball", MODE_PRIVATE).getInt("y", dp(220));
        ball.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true;
                    params.x = Math.max(0, Math.min(Math.round(startX + dx),
                            getResources().getDisplayMetrics().widthPixels - dp(48)));
                    params.y = Math.max(dp(24), Math.min(Math.round(startY + dy),
                            getResources().getDisplayMetrics().heightPixels - dp(100)));
                    try { windowManager.updateViewLayout(ball, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    long pressDuration = event.getEventTime() - event.getDownTime();
                    if (!moved && pressDuration >= LONG_PRESS_MS) {
                        hideAndStop();
                    } else if (moved) {
                        snapToEdge();
                    } else {
                        launchCapture();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return true;
        });
        try { windowManager.addView(ball, params); }
        catch (Exception e) { ball = null; stopSelf(); }
    }

    private void snapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        params.x = params.x + dp(24) < screenWidth / 2 ? 0 : screenWidth - dp(48);
        try { windowManager.updateViewLayout(ball, params); } catch (Exception ignored) {}
        getSharedPreferences("floating_ball", MODE_PRIVATE).edit()
                .putInt("x", params.x).putInt("y", params.y).apply();
    }

    private void launchCapture() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("note_id", getSharedPreferences("capture_state", MODE_PRIVATE)
                .getInt("current_note_id", -1));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开截图页面：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Hide the overlay; the user can turn it on again from Minenote Settings. */
    private void hideAndStop() {
        getSharedPreferences("floating_ball", MODE_PRIVATE).edit()
                .putBoolean("enabled", false).apply();
        stopSelf();
        Toast.makeText(this, "截图按钮已隐藏，可在 Minenote 设置中重新开启",
                Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Minenote 截图按钮", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (ball != null && windowManager != null) {
            try { windowManager.removeView(ball); } catch (Exception ignored) {}
            ball = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    /** Simple vector pencil glyph, drawn locally so no external icon asset is required. */
    private class PencilButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path pencil = new Path();
        PencilButton() { super(FloatingBallService.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            canvas.save();
            canvas.rotate(-42f, cx, cy);
            float w = dp(8), h = dp(25);
            float left = cx - w / 2f, top = cy - h / 2f;
            paint.setColor(0xFF5A4930);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left, top, left + w, top + h, dp(2), dp(2), paint);
            paint.setColor(0xFFFFF8E8);
            canvas.drawRect(left + dp(1), top + dp(4), left + w - dp(1), top + h - dp(5), paint);
            paint.setColor(0xFF5A4930);
            pencil.reset();
            pencil.moveTo(left, top + h);
            pencil.lineTo(cx, top + h + dp(6));
            pencil.lineTo(left + w, top + h);
            pencil.close();
            canvas.drawPath(pencil, paint);
            paint.setColor(0xFFD7A84D);
            canvas.drawRect(left, top, left + w, top + dp(4), paint);
            canvas.restore();
        }
    }
}
