package com.gamelauncher.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.InetSocketAddress;
import java.net.Socket;

/** Monitora o estado da rede e mede o ping real (tempo de conexão TCP) a cada segundo. */
public final class NetMonitor {

    /** level: 0 = ok, 1 = aviso, 2 = sem conexão */
    public static final class Status {
        public final String text;
        public final int level;

        Status(String text, int level) {
            this.text = text;
            this.level = level;
        }
    }

    private static NetMonitor instance;
    private static int users = 0;

    public static synchronized NetMonitor acquire(Context ctx) {
        if (instance == null) instance = new NetMonitor(ctx.getApplicationContext());
        users++;
        if (users == 1) instance.start();
        return instance;
    }

    public static synchronized void release() {
        if (users > 0) {
            users--;
            if (users == 0 && instance != null) instance.stop();
        }
    }

    private final ConnectivityManager cm;
    private volatile Thread thread;
    private volatile boolean running;
    private volatile int pingMs = -1;

    private NetMonitor(Context c) {
        cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void start() {
        running = true;
        Thread t = new Thread(this::loop, "net-ping");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    private void stop() {
        running = false;
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    private void loop() {
        Thread me = Thread.currentThread();
        while (running && thread == me) {
            pingMs = measure();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private int measure() {
        String[] hosts = {"1.1.1.1", "8.8.8.8"};
        int[] ports = {443, 53};
        for (int i = 0; i < hosts.length; i++) {
            try (Socket s = new Socket()) {
                long t0 = System.nanoTime();
                s.connect(new InetSocketAddress(hosts[i], ports[i]), 2000);
                return (int) ((System.nanoTime() - t0) / 1_000_000L);
            } catch (Exception ignored) {
                // tenta o próximo servidor
            }
        }
        return -1;
    }

    public int pingMs() {
        return pingMs;
    }

    public Status status() {
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = (n == null) ? null : cm.getNetworkCapabilities(n);
        if (c == null) return new Status("Sem conexão", 2);

        String via;
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) via = "Wi-Fi";
        else if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) via = "dados móveis";
        else if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) via = "cabo";
        else via = "rede";

        boolean online = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || pingMs >= 0;
        if (online) return new Status("Conectado via " + via + " - Sincronizado", 0);
        return new Status("Conectado via " + via + ", mas sem internet", 1);
    }
}
