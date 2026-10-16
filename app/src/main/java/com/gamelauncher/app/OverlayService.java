package com.gamelauncher.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Serviço em primeiro plano:
 *  - mostra uma notificação fixa com o estado da conexão (Conectado - Sincronizado);
 *  - avisa quando a conexão muda;
 *  - mostra o overlay de FPS/Ping SOMENTE enquanto o jogo escolhido está na tela;
 *  - mantém o Wi-Fi em modo de baixa latência (se ativado).
 */
public class OverlayService extends Service {

    static final String ACTION_STOP = "com.gamelauncher.app.STOP";
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_OVERLAY = "overlay";
    static final String EXTRA_STABLE = "stable";

    private static final String CH_STATUS = "status";
    private static final String CH_ALERT = "alerts";
    private static final int ID_STATUS = 1;
    private static final int ID_ALERT = 2;
    private static final long IDLE_STOP_MS = 10 * 60 * 1000L;

    /** Conta quadros por segundo da tela (Choreographer). */
    private static final class FpsCounter implements Choreographer.FrameCallback {
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

        int sample() {
            int f = frames;
            frames = 0;
            return f;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FpsCounter fpsCounter = new FpsCounter();

    private WindowManager wm;
    private UsageStatsManager usm;
    private NetMonitor net;
    private WifiManager.WifiLock wifiLock;

    private LinearLayout overlayRoot;
    private TextView fpsText, pingText;

    private String pkg;
    private boolean overlayEnabled = true;
    private boolean stable = true;
    private boolean running = false;

    private String fgPkg = null;
    private long lastQuery = 0;
    private long lastSeen = 0;
    private int outTicks = 0;
    private int tickCount = 0;
    private int lastLevel = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            updateForeground(now);

            boolean inGame = pkg != null && pkg.equals(fgPkg);
            if (inGame) {
                lastSeen = now;
                outTicks = 0;
            } else {
                outTicks++;
            }

            boolean canShow = overlayEnabled && Settings.canDrawOverlays(OverlayService.this);
            if (canShow && inGame) {
                showOverlay();
                updateOverlayText();
            } else if (outTicks >= 2 || !canShow) {
                hideOverlay();  // saiu do jogo (2 checagens seguidas, para não piscar)
            }

            if (tickCount % 3 == 0) updateNotifications();
            tickCount++;

            if (now - lastSeen > IDLE_STOP_MS) {
                stopSelf();
                return;
            }
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
        if (intent != null) {
            pkg = intent.getStringExtra(EXTRA_PKG);
            overlayEnabled = intent.getBooleanExtra(EXTRA_OVERLAY, true);
            stable = intent.getBooleanExtra(EXTRA_STABLE, true);
        }

        createChannels();
        startInForeground(buildStatus("Iniciando..."));

        long now = System.currentTimeMillis();
        lastSeen = now;
        outTicks = 0;
        if (!running) {
            running = true;
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            net = NetMonitor.acquire(this);
            lastQuery = now - 15000;
            if (stable) acquireWifiLock();
            handler.post(tick);
        }
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------ qual app está na tela
    @SuppressWarnings("deprecation")
    private void updateForeground(long now) {
        if (usm == null) return;
        try {
            UsageEvents ev = usm.queryEvents(lastQuery, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                if (t == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fgPkg = e.getPackageName();
                } else if (t == UsageEvents.Event.MOVE_TO_BACKGROUND
                        && e.getPackageName() != null && e.getPackageName().equals(fgPkg)) {
                    fgPkg = null;
                }
            }
            lastQuery = now - 1500;
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ overlay
    private void showOverlay() {
        if (overlayRoot != null) return;
        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding((int) (14 * d), (int) (7 * d), (int) (14 * d), (int) (7 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD90A0E1A);
        bg.setCornerRadius(18 * d);
        bg.setStroke((int) d, 0xFF2A3355);
        root.setBackground(bg);

        TextView fps = new TextView(this);
        fps.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        fps.setTextSize(13);
        fps.setTextColor(0xFF2EA8FF);
        fps.setText("FPS --");

        View sep = new View(this);
        sep.setBackgroundColor(0xFF2A3355);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams((int) d, (int) (16 * d));
        sp.leftMargin = (int) (12 * d);
        sp.rightMargin = (int) (12 * d);

        TextView ping = new TextView(this);
        ping.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        ping.setTextSize(13);
        ping.setTextColor(0xFF47E08F);
        ping.setText("PING --");

        root.addView(fps);
        root.addView(sep, sp);
        root.addView(ping);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (18 * d);

        try {
            root.setAlpha(0f);
            wm.addView(root, lp);
            root.animate().alpha(1f).setDuration(220).start();
        } catch (Exception e) {
            return;
        }
        overlayRoot = root;
        fpsText = fps;
        pingText = ping;
        fpsCounter.start();
    }

    private void updateOverlayText() {
        if (overlayRoot == null) return;
        int fps = fpsCounter.sample();
        int ping = net.pingMs();
        fpsText.setText("FPS " + fps);
        pingText.setText(ping >= 0 ? "PING " + ping + "ms" : "PING --");
        if (ping < 0 || ping >= 120) pingText.setTextColor(0xFFFF5C6C);
        else if (ping >= 60) pingText.setTextColor(0xFFFFC857);
        else pingText.setTextColor(0xFF47E08F);
    }

    private void hideOverlay() {
        if (overlayRoot == null) return;
        final View v = overlayRoot;
        overlayRoot = null;
        fpsCounter.stop();
        v.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }).start();
    }

    // ------------------------------------------------------------ notificações
    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH_STATUS, "Status da conexão", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CH_ALERT, "Avisos de conexão", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private Notification buildStatus(String text) {
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Parar", pi).build();

        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Game Launcher")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(stopAction)
                .build();
    }

    private void startInForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_STATUS, n);
        }
    }

    private void updateNotifications() {
        if (net == null) return;
        NetMonitor.Status s = net.status();
        int p = net.pingMs();
        String line = s.text + (p >= 0 ? "  •  Ping " + p + " ms" : "");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(ID_STATUS, buildStatus(line));

        if (s.level != lastLevel) {
            lastLevel = s.level;
            String msg;
            if (s.level == 0) msg = "Conectado à internet - Sincronizado";
            else if (s.level == 1) msg = "Conectado ao Wi-Fi, mas sem internet";
            else msg = "Sem conexão com a internet";
            Notification alert = new Notification.Builder(this, CH_ALERT)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Game Launcher")
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ID_ALERT, alert);
        }
    }

    // ------------------------------------------------------------ Wi-Fi estável
    @SuppressWarnings("deprecation")
    private void acquireWifiLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
            wifiLock = wifi.createWifiLock(mode, "GameLauncher:wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ fim
    @Override
    public void onDestroy() {
        boolean was = running;
        running = false;
        handler.removeCallbacksAndMessages(null);
        fpsCounter.stop();
        if (overlayRoot != null) {
            try {
                wm.removeView(overlayRoot);
            } catch (Exception ignored) {
            }
            overlayRoot = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            try {
                wifiLock.release();
            } catch (Exception ignored) {
            }
        }
        if (was) NetMonitor.release();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(ID_ALERT);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
