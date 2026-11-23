package com.example.minenote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Draggable squirrel shortcut that snaps to the nearest screen edge. */
public class FloatingBallService extends Service {
    public static final String ACTION_STOP = "com.example.minenote.STOP_FLOATING_BALL";
    private static final String CHANNEL = "minenote_floating_ball";
    private static final int NOTIFICATION_ID = 731;
    private WindowManager windowManager;
    private TextView ball;
    private WindowManager.LayoutParams params;
    private float downX, downY, startX, startY;
    private boolean moved;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Minenote 截图悬浮球已开启")
                .setContentText("点击松鼠开始截图，拖动可移动位置")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(NOTIFICATION_ID, notification);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Settings.canDrawOverlays(this)) addBall();
        else stopSelf();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void addBall() {
        if (ball != null) return;
        ball = new TextView(this);
        ball.setText("🐿");
        ball.setTextSize(25);
        ball.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF6D98B);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(dp(1), 0xFFD6B65E);
        ball.setBackground(bg);
        ball.setElevation(dp(6));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(52), dp(52), type,
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
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) moved = true;
                    params.x = Math.max(0, Math.min(startX + (int) dx,
                            getResources().getDisplayMetrics().widthPixels - dp(52)));
                    params.y = Math.max(dp(24), Math.min(startY + (int) dy,
                            getResources().getDisplayMetrics().heightPixels - dp(100)));
                    try { windowManager.updateViewLayout(ball, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) snapToEdge();
                    else launchCapture();
                    return true;
            }
            return true;
        });
        try { windowManager.addView(ball, params); }
        catch (Exception e) { ball = null; stopSelf(); }
    }

    private void snapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        params.x = params.x + dp(26) < screenWidth / 2
                ? 0 : screenWidth - dp(52);
        try { windowManager.updateViewLayout(ball, params); } catch (Exception ignored) {}
        getSharedPreferences("floating_ball", MODE_PRIVATE).edit()
                .putInt("x", params.x).putInt("y", params.y).apply();
    }

    private void launchCapture() {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("note_id", getSharedPreferences("capture_state", MODE_PRIVATE)
                .getInt("current_note_id", -1));
        try { startActivity(intent); }
        catch (Exception e) {
            android.widget.Toast.makeText(this, "无法启动截图，请先打开 Minenote", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Minenote 悬浮截图", NotificationManager.IMPORTANCE_LOW);
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
}
