package com.gamelauncher.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Mostra um overlay (por cima de outros apps) com FPS da tela e Ping em tempo real. */
public class OverlayService extends Service {

    static final String ACTION_STOP = "com.gamelauncher.app.STOP";
    private static final String CHANNEL = "overlay";
    private static final int NOTIF_ID = 1;

    /** Conta quadros por segundo da tela usando o Choreographer (precisa da thread principal). */
    static final class FpsCounter implements Choreographer.FrameCallback {
        private int frames = 0;
        private boolean active = false;

        void start() {
            if (active) return;
            active = true;
            frames = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            active = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            frames++;
            if (active) Choreographer.getInstance().postFrameCallback(this);
        }

        /** Chame a cada 1 segundo: devolve os quadros do último segundo. */
        int sample() {
            int f = frames;
            frames = 0;
            return f;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FpsCounter fpsCounter = new FpsCounter();
    private WindowManager wm;
    private TextView view;
    private NetMonitor net;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || view == null) return;
            int fps = fpsCounter.sample();
            int ping = net.pingMs();
            view.setText("FPS (tela): " + fps + "\nPing: " + (ping >= 0 ? ping + " ms" : "--"));
            if (ping < 0 || ping >= 120) view.setTextColor(0xFFFF5A5A);
            else if (ping >= 60) view.setTextColor(0xFFFFD25A);
            else view.setTextColor(0xFF6EFF96);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startInForeground();
        if (view == null) addOverlay();
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Overlay FPS/Ping", NotificationManager.IMPORTANCE_LOW));

        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Parar overlay", pi).build();

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Game Launcher")
                .setContentText("Overlay de FPS e Ping ativo")
                .setOngoing(true)
                .addAction(stopAction)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void addOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        float d = getResources().getDisplayMetrics().density;

        view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setTextSize(14);
        view.setTextColor(0xFF6EFF96);
        view.setBackgroundColor(0xB3000000);
        view.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        view.setText("FPS (tela): --\nPing: --");

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) (12 * d);
        lp.y = (int) (48 * d);

        try {
            wm.addView(view, lp);
        } catch (Exception e) {  // sem permissão de sobrepor outros apps
            view = null;
            stopSelf();
            return;
        }

        net = NetMonitor.acquire(this);
        running = true;
        fpsCounter.start();
        handler.post(tick);
    }

    @Override
    public void onDestroy() {
        boolean hadOverlay = running;
        running = false;
        handler.removeCallbacksAndMessages(null);
        fpsCounter.stop();
        if (view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
            view = null;
        }
        if (hadOverlay) NetMonitor.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
