package com.gamelauncher.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PASSWORD = "1223";
    private static final int BG = 0xFF181A21, FIELD = 0xFF282B36, TXT = 0xFFE6E8F0;
    private static final int GREEN = 0xFF6EFF96, YELLOW = 0xFFFFD25A, RED = 0xFFFF5A5A, ACCENT = 0xFF4C8DFF;

    private static final class AppItem {
        final String label, pkg;
        final boolean isGame;

        AppItem(String label, String pkg, boolean isGame) {
            this.label = label;
            this.pkg = pkg;
            this.isGame = isGame;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OverlayService.FpsCounter fpsCounter = new OverlayService.FpsCounter();
    private final List<AppItem> apps = new ArrayList<>();
    private NetMonitor net;
    private TextView netView, perfView, gameStatus;
    private Spinner spinner;
    private CheckBox showAll;
    private boolean mainShown = false;
    private boolean looping = false;

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mainShown) startLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLoop();
    }

    // ------------------------------------------------------------------ helpers de UI
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        l.setPadding(dp(24), dp(32), dp(24), dp(24));
        return l;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(TXT);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextColor(0xFFFFFFFF);
        bt.setBackgroundColor(ACCENT);
        return bt;
    }

    private LinearLayout.LayoutParams lp(int topDp, int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heightDp > 0 ? dp(heightDp) : LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    // ------------------------------------------------------------------ tela de login
    private void showLogin() {
        mainShown = false;
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_VERTICAL);

        root.addView(text("Game Launcher", 30, true), lp(0, 0));
        root.addView(text("Digite a senha para continuar", 16, false), lp(8, 0));

        final EditText pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pass.setHint("Senha");
        pass.setHintTextColor(0xFF8A8FA0);
        pass.setTextColor(TXT);
        pass.setBackgroundColor(FIELD);
        pass.setPadding(dp(14), dp(10), dp(14), dp(10));
        pass.setSingleLine(true);
        pass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(pass, lp(24, 52));

        final TextView err = text("", 14, false);
        err.setTextColor(RED);
        root.addView(err, lp(8, 0));

        final Runnable check = () -> {
            if (PASSWORD.equals(pass.getText().toString())) {
                showMain();
            } else {
                err.setText("Senha incorreta. Tente novamente.");
                pass.setText("");
            }
        };

        Button go = button("Entrar");
        go.setOnClickListener(v -> check.run());
        pass.setOnEditorActionListener((v, actionId, e) -> {
            check.run();
            return true;
        });
        root.addView(go, lp(16, 52));

        setContentView(root);
    }

    // ------------------------------------------------------------------ painel principal
    private void showMain() {
        mainShown = true;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        scroll.addView(root);

        root.addView(text("Painel Principal", 26, true), lp(0, 0));

        netView = text("Verificando conexão...", 16, false);
        root.addView(netView, lp(12, 0));

        root.addView(text("Jogo:", 15, false), lp(24, 0));
        spinner = new Spinner(this);
        root.addView(spinner, lp(4, 52));

        showAll = new CheckBox(this);
        showAll.setText("Mostrar todos os apps");
        showAll.setTextColor(TXT);
        showAll.setOnCheckedChangeListener((b, c) -> fillSpinner());
        root.addView(showAll, lp(4, 0));

        Button start = button("Entrar / Iniciar Jogo");
        start.setOnClickListener(v -> startGame());
        root.addView(start, lp(16, 56));

        Button stop = button("Parar overlay");
        stop.setBackgroundColor(FIELD);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            gameStatus.setText("Overlay parado.");
        });
        root.addView(stop, lp(10, 48));

        gameStatus = text("", 14, false);
        root.addView(gameStatus, lp(12, 0));

        perfView = text("FPS (tela): --\nPing: --", 24, true);
        perfView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        perfView.setTextColor(GREEN);
        root.addView(perfView, lp(20, 0));

        TextView note = text("O FPS mostrado é o da tela do aparelho. O Android não permite "
                + "ler o FPS interno de outro jogo.", 12, false);
        note.setTextColor(0xFF8A8FA0);
        root.addView(note, lp(12, 0));

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        loadApps();
        fillSpinner();
        startLoop();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            @SuppressWarnings("deprecation")
            boolean game = ai.category == ApplicationInfo.CATEGORY_GAME
                    || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            apps.add(new AppItem(ri.loadLabel(pm).toString(), pkg, game));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void fillSpinner() {
        boolean all = showAll.isChecked();
        List<AppItem> shown = new ArrayList<>();
        for (AppItem a : apps) if (all || a.isGame) shown.add(a);

        if (shown.isEmpty() && !all) {  // nenhum jogo detectado: mostra tudo
            showAll.setChecked(true);
            return;
        }
        ArrayAdapter<AppItem> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, shown);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(ad);
    }

    private void startGame() {
        AppItem sel = (AppItem) spinner.getSelectedItem();
        if (sel == null) {
            Toast.makeText(this, "Selecione um jogo na lista.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(sel.pkg);
        if (launch == null) {
            Toast.makeText(this, "Não foi possível abrir esse app.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ative \"Sobrepor a outros apps\" para este app e toque em Iniciar de novo.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        startActivity(launch);
        gameStatus.setText("Rodando: " + sel.label);
    }

    // ------------------------------------------------------------------ atualização em tempo real
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            NetMonitor.Status s = net.status();
            netView.setText(s.text);
            netView.setTextColor(s.level == 0 ? GREEN : s.level == 1 ? YELLOW : RED);
            int p = net.pingMs();
            perfView.setText("FPS (tela): " + fpsCounter.sample()
                    + "\nPing: " + (p >= 0 ? p + " ms" : "--"));
            handler.postDelayed(this, 1000);
        }
    };

    private void startLoop() {
        if (looping) return;
        looping = true;
        net = NetMonitor.acquire(this);
        fpsCounter.start();
        handler.post(tick);
    }

    private void stopLoop() {
        if (!looping) return;
        looping = false;
        handler.removeCallbacks(tick);
        fpsCounter.stop();
        NetMonitor.release();
    }
}
