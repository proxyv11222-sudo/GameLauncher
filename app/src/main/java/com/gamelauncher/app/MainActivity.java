package com.gamelauncher.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PASSWORD = "1223";
    private static final int REQ_PHOTO = 10;
    private static final int REQ_NOTIF = 11;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class AppItem {
        final String label, pkg;
        final boolean isGame;
        final Drawable icon;

        AppItem(String label, String pkg, boolean isGame, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.isGame = isGame;
            this.icon = icon;
        }
    }

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AppItem> apps = new ArrayList<>();
    private final List<AppItem> shown = new ArrayList<>();
    private int sel = -1;

    private NetMonitor net;
    private boolean looping = false;
    private boolean mainShown = false;
    private ObjectAnimator dotPulse;

    private ImageView photoView;
    private TextView photoHolder;
    private TextView nameView, netText, gameName, gameSub;
    private ImageView gameIcon;
    private View netDot;
    private TextView stOverlay, stUsage, stNotif;

    // ------------------------------------------------------------------ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("gl", MODE_PRIVATE);
        getWindow().setStatusBarColor(Ui.BG1);
        getWindow().setNavigationBarColor(Ui.BG2);
        showLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mainShown) {
            startLoop();
            updatePerms();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLoop();
    }

    // ------------------------------------------------------------------ helpers
    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private LinearLayout cardBox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(Ui.card(this));
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        return l;
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, s, 11.5f, Ui.SUB, true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private Button bigButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(0xFFFFFFFF);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(Ui.gradient(this, Ui.ACC1, Ui.ACC2, GradientDrawable.Orientation.LEFT_RIGHT, 18));
        Ui.pressable(b);
        return b;
    }

    private ViewOutlineProvider ovalOutline() {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        };
    }

    /** Foto de perfil redonda com aro colorido. ivOut[0] = imagem, phOut[0] = iniciais. */
    private FrameLayout avatar(int sizeDp, ImageView[] ivOut, TextView[] phOut) {
        FrameLayout ring = new FrameLayout(this);
        ring.setBackground(Ui.ovalGradient(Ui.ACC1, Ui.ACC2));
        int pad = dp(3);
        ring.setPadding(pad, pad, pad, pad);

        FrameLayout inner = new FrameLayout(this);
        inner.setBackground(Ui.oval(Ui.CARD));
        inner.setOutlineProvider(ovalOutline());
        inner.setClipToOutline(true);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        TextView ph = Ui.text(this, "GL", sizeDp / 4f, Ui.TXT, true);
        ph.setGravity(Gravity.CENTER);

        inner.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        inner.addView(ph, new FrameLayout.LayoutParams(MATCH, MATCH));
        ring.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        ivOut[0] = iv;
        phOut[0] = ph;
        return ring;
    }

    private void applyPhoto(ImageView iv, TextView ph) {
        File f = new File(getFilesDir(), "profile.jpg");
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
        if (b != null) {
            iv.setImageBitmap(b);
            ph.setVisibility(View.GONE);
        } else {
            iv.setImageDrawable(null);
            ph.setVisibility(View.VISIBLE);
        }
    }

    private String playerName() {
        return prefs.getString("name", "Jogador");
    }

    // ------------------------------------------------------------------ tela de login
    private void showLogin() {
        mainShown = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setBackground(Ui.gradient(this, Ui.BG1, Ui.BG2, GradientDrawable.Orientation.TOP_BOTTOM, 0));

        ImageView[] ivOut = new ImageView[1];
        TextView[] phOut = new TextView[1];
        FrameLayout av = avatar(112, ivOut, phOut);
        applyPhoto(ivOut[0], phOut[0]);
        root.addView(av, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = Ui.text(this, "Game Launcher", 28, Ui.TXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.lp(this, WRAP, WRAP, 22));

        TextView sub = Ui.text(this, "Olá, " + playerName() + ". Digite sua senha.", 15, Ui.SUB, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, Ui.lp(this, WRAP, WRAP, 6));

        final EditText pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pass.setHint("Senha");
        pass.setHintTextColor(Ui.SUB);
        pass.setTextColor(Ui.TXT);
        pass.setTextSize(18);
        pass.setGravity(Gravity.CENTER);
        pass.setSingleLine(true);
        pass.setBackground(Ui.box(this, Ui.FIELD, 16));
        pass.setPadding(dp(16), dp(14), dp(16), dp(14));
        pass.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(pass, Ui.lp(this, MATCH, WRAP, 30));

        final TextView err = Ui.text(this, "", 14, Ui.RED, false);
        err.setGravity(Gravity.CENTER);
        root.addView(err, Ui.lp(this, MATCH, WRAP, 10));

        Button go = bigButton("Entrar");
        root.addView(go, Ui.lp(this, MATCH, dp(56), 8));

        final Runnable check = () -> {
            if (PASSWORD.equals(pass.getText().toString())) {
                showMain();
            } else {
                err.setText("Senha incorreta. Tente novamente.");
                pass.setText("");
                Ui.shake(pass);
            }
        };
        go.setOnClickListener(v -> check.run());
        pass.setOnEditorActionListener((v, actionId, e) -> {
            check.run();
            return true;
        });

        Ui.enter(av, 0);
        Ui.enter(title, 90);
        Ui.enter(sub, 160);
        Ui.enter(pass, 230);
        Ui.enter(go, 300);

        setContentView(root);
    }

    // ------------------------------------------------------------------ painel principal
    private void showMain() {
        mainShown = true;
        loadApps();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.gradient(this, Ui.BG1, Ui.BG2, GradientDrawable.Orientation.TOP_BOTTOM, 0));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(14), dp(18), dp(18));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // ---------- perfil
        LinearLayout header = cardBox();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView[] ivOut = new ImageView[1];
        TextView[] phOut = new TextView[1];
        FrameLayout av = avatar(84, ivOut, phOut);
        photoView = ivOut[0];
        photoHolder = phOut[0];
        applyPhoto(photoView, photoHolder);
        av.setOnClickListener(v -> pickPhoto());
        Ui.pressable(av);
        header.addView(av, new LinearLayout.LayoutParams(dp(84), dp(84)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameView = Ui.text(this, playerName(), 21, Ui.TXT, true);
        nameView.setSingleLine(true);
        nameView.setOnClickListener(v -> editName());
        TextView vip = Ui.text(this, "VIP", 11, 0xFF2B1B00, true);
        vip.setGravity(Gravity.CENTER);
        vip.setPadding(dp(9), dp(2), dp(9), dp(2));
        vip.setBackground(Ui.gradient(this, 0xFFFFE08A, 0xFFFFB02E, GradientDrawable.Orientation.LEFT_RIGHT, 20));
        nameRow.addView(nameView, new LinearLayout.LayoutParams(WRAP, WRAP));
        nameRow.addView(vip, Ui.lp(this, WRAP, WRAP, 0));
        ((LinearLayout.LayoutParams) vip.getLayoutParams()).leftMargin = dp(8);
        info.addView(nameRow);
        info.addView(Ui.text(this, "Toque na foto ou no nome para mudar", 12, Ui.SUB, false),
                Ui.lp(this, WRAP, WRAP, 4));

        LinearLayout netRow = new LinearLayout(this);
        netRow.setGravity(Gravity.CENTER_VERTICAL);
        netDot = new View(this);
        netDot.setBackground(Ui.oval(Ui.SUB));
        netRow.addView(netDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        netText = Ui.text(this, "Verificando conexão...", 13.5f, Ui.SUB, true);
        LinearLayout.LayoutParams ntp = new LinearLayout.LayoutParams(WRAP, WRAP);
        ntp.leftMargin = dp(8);
        netRow.addView(netText, ntp);
        info.addView(netRow, Ui.lp(this, WRAP, WRAP, 10));

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, WRAP, 1f);
        ip.leftMargin = dp(16);
        header.addView(info, ip);
        col.addView(header, Ui.lp(this, MATCH, WRAP, 0));

        // ---------- jogo
        LinearLayout gameCard = cardBox();
        gameCard.addView(sectionLabel("JOGO"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.box(this, Ui.FIELD, 16));
        row.setPadding(dp(12), dp(12), dp(14), dp(12));
        gameIcon = new ImageView(this);
        row.addView(gameIcon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout gt = new LinearLayout(this);
        gt.setOrientation(LinearLayout.VERTICAL);
        gameName = Ui.text(this, "", 16, Ui.TXT, true);
        gameName.setSingleLine(true);
        gameSub = Ui.text(this, "Toque para escolher", 12, Ui.SUB, false);
        gt.addView(gameName);
        gt.addView(gameSub);
        LinearLayout.LayoutParams gtp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        gtp.leftMargin = dp(12);
        row.addView(gt, gtp);
        row.addView(Ui.text(this, "›", 26, Ui.SUB, true));
        row.setOnClickListener(v -> showGamePicker());
        Ui.pressable(row);
        gameCard.addView(row, Ui.lp(this, MATCH, WRAP, 10));
        gameCard.addView(toggleRow("Mostrar todos os apps",
                "Se o seu jogo não aparecer na lista", "showAll", false, () -> fillGames()),
                Ui.lp(this, MATCH, WRAP, 8));
        col.addView(gameCard, Ui.lp(this, MATCH, WRAP, 14));

        // ---------- modo jogo
        LinearLayout opt = cardBox();
        opt.addView(sectionLabel("MODO JOGO"));
        opt.addView(toggleRow("Limpar apps em segundo plano",
                "Fecha os outros apps ao iniciar, para sobrar memória", "gameMode", true, null),
                Ui.lp(this, MATCH, WRAP, 6));
        opt.addView(toggleRow("Rede estável",
                "Mantém o Wi-Fi em modo de baixa latência", "stable", true, null),
                Ui.lp(this, MATCH, WRAP, 6));
        opt.addView(toggleRow("Overlay dentro do jogo",
                "Mostra FPS e ping só com o jogo aberto", "overlay", true, null),
                Ui.lp(this, MATCH, WRAP, 6));
        col.addView(opt, Ui.lp(this, MATCH, WRAP, 14));

        // ---------- permissões
        LinearLayout perm = cardBox();
        perm.addView(sectionLabel("PERMISSÕES"));
        stOverlay = new TextView(this);
        stUsage = new TextView(this);
        stNotif = new TextView(this);
        perm.addView(permRow("Sobrepor a outros apps", "Necessária para o overlay", stOverlay, () ->
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))), Ui.lp(this, MATCH, WRAP, 6));
        perm.addView(permRow("Acesso de uso", "Detecta quando você está dentro do jogo", stUsage, () ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), Ui.lp(this, MATCH, WRAP, 6));
        perm.addView(permRow("Notificações", "Aviso de conexão e sincronização", stNotif, () -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        }), Ui.lp(this, MATCH, WRAP, 6));
        col.addView(perm, Ui.lp(this, MATCH, WRAP, 14));

        TextView note = Ui.text(this, "O FPS mostrado é o da tela do aparelho. O Android não deixa um app "
                + "ler o FPS interno de outro jogo.", 11.5f, Ui.SUB, false);
        col.addView(note, Ui.lp(this, MATCH, WRAP, 14));

        // ---------- barra de baixo (fixa)
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(18), dp(10), dp(18), dp(14));
        bar.setBackgroundColor(Ui.BG2);
        Button start = bigButton("Entrar / Iniciar Jogo");
        start.setOnClickListener(v -> startGame());
        bar.addView(start, new LinearLayout.LayoutParams(MATCH, dp(56)));
        TextView stop = Ui.text(this, "Parar overlay e notificação", 13.5f, Ui.SUB, true);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, dp(12), 0, dp(4));
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            toast("Parado.");
        });
        bar.addView(stop, new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));

        // animações de entrada
        Ui.enter(header, 0);
        Ui.enter(gameCard, 90);
        Ui.enter(opt, 180);
        Ui.enter(perm, 270);
        Ui.enter(bar, 360);

        setContentView(page);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        fillGames();
        updatePerms();
        startLoop();
    }

    // ---------- linha com interruptor
    private LinearLayout toggleRow(String title, String desc, final String key, boolean def, final Runnable after) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 15, Ui.TXT, true));
        t.addView(Ui.text(this, desc, 12, Ui.SUB, false));
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        Switch sw = new Switch(this);
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{Ui.ACC2, 0xFF8D96B5}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{0x662EA8FF, 0x338D96B5}));
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            if (after != null) after.run();
        });
        r.addView(sw);
        return r;
    }

    // ---------- linha de permissão
    private LinearLayout permRow(String title, String desc, TextView status, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 15, Ui.TXT, true));
        t.addView(Ui.text(this, desc, 12, Ui.SUB, false));
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));

        status.setTextSize(12.5f);
        status.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(14), dp(7), dp(14), dp(7));
        status.setOnClickListener(v -> action.run());
        Ui.pressable(status);
        r.addView(status);
        return r;
    }

    private void setPill(TextView t, boolean ok) {
        if (ok) {
            t.setText("Ativo ✓");
            t.setTextColor(Ui.GREEN);
            t.setBackground(Ui.box(this, 0x2247E08F, 20));
        } else {
            t.setText("Ativar");
            t.setTextColor(0xFFFFFFFF);
            t.setBackground(Ui.gradient(this, Ui.ACC1, Ui.ACC2, GradientDrawable.Orientation.LEFT_RIGHT, 20));
        }
    }

    private void updatePerms() {
        if (stOverlay == null) return;
        setPill(stOverlay, Settings.canDrawOverlays(this));
        setPill(stUsage, Perms.hasUsageAccess(this));
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        setPill(stNotif, notif);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        updatePerms();
    }

    // ------------------------------------------------------------------ lista de jogos
    @SuppressWarnings("deprecation")
    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            boolean game = ai.category == ApplicationInfo.CATEGORY_GAME
                    || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            apps.add(new AppItem(ri.loadLabel(pm).toString(), pkg, game, ri.loadIcon(pm)));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void fillGames() {
        boolean all = prefs.getBoolean("showAll", false);
        shown.clear();
        for (AppItem a : apps) if (all || a.isGame) shown.add(a);
        if (shown.isEmpty()) shown.addAll(apps);  // nenhum jogo detectado: mostra tudo

        String saved = prefs.getString("pkg", null);
        sel = -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).pkg.equals(saved)) sel = i;
        if (sel < 0 && !shown.isEmpty()) sel = 0;
        refreshGameRow();
    }

    private void refreshGameRow() {
        if (sel >= 0 && sel < shown.size()) {
            AppItem a = shown.get(sel);
            gameName.setText(a.label);
            gameSub.setText("Toque para trocar");
            gameIcon.setImageDrawable(a.icon);
        } else {
            gameName.setText("Nenhum app encontrado");
            gameSub.setText("");
            gameIcon.setImageDrawable(null);
        }
    }

    private void showGamePicker() {
        if (shown.isEmpty()) return;
        String[] names = new String[shown.size()];
        for (int i = 0; i < names.length; i++) names[i] = shown.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Escolha o jogo")
                .setItems(names, (d, which) -> {
                    sel = which;
                    prefs.edit().putString("pkg", shown.get(which).pkg).apply();
                    refreshGameRow();
                })
                .show();
    }

    // ------------------------------------------------------------------ iniciar jogo
    private void startGame() {
        if (sel < 0 || sel >= shown.size()) {
            toast("Selecione um jogo na lista.");
            return;
        }
        AppItem a = shown.get(sel);
        prefs.edit().putString("pkg", a.pkg).apply();
        Intent launch = getPackageManager().getLaunchIntentForPackage(a.pkg);
        if (launch == null) {
            toast("Não foi possível abrir esse app.");
            return;
        }

        boolean overlayOn = prefs.getBoolean("overlay", true);
        if (overlayOn && !Settings.canDrawOverlays(this)) {
            toast("Ative \"Sobrepor a outros apps\" e toque em Iniciar de novo.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (overlayOn && !Perms.hasUsageAccess(this)) {
            toast("Ative o \"Acesso de uso\" para o overlay aparecer só dentro do jogo e toque em Iniciar de novo.");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }

        if (prefs.getBoolean("gameMode", true)) {
            int n = GameBooster.boost(this, a.pkg);
            toast("Modo Jogo: " + n + " apps limpos.");
        }

        Intent svc = new Intent(this, OverlayService.class)
                .putExtra(OverlayService.EXTRA_PKG, a.pkg)
                .putExtra(OverlayService.EXTRA_OVERLAY, overlayOn)
                .putExtra(OverlayService.EXTRA_STABLE, prefs.getBoolean("stable", true));
        startForegroundService(svc);
        startActivity(launch);
    }

    // ------------------------------------------------------------------ foto e nome
    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_PHOTO);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PHOTO && res == RESULT_OK && data != null && data.getData() != null) {
            savePhoto(data.getData());
        }
    }

    private void savePhoto(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int s = 1;
            while (bounds.outWidth / (s * 2) >= 600 && bounds.outHeight / (s * 2) >= 600) s *= 2;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = s;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opt);
            }
            if (bmp == null) throw new IOException("imagem inválida");

            int rotation = 0;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                ExifInterface exif = new ExifInterface(in);
                int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            } catch (Exception ignored) {
            }
            if (rotation != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotation);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }

            int side = Math.min(bmp.getWidth(), bmp.getHeight());
            Bitmap sq = Bitmap.createBitmap(bmp, (bmp.getWidth() - side) / 2, (bmp.getHeight() - side) / 2, side, side);
            if (side > 512) sq = Bitmap.createScaledBitmap(sq, 512, 512, true);

            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "profile.jpg"))) {
                sq.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            if (photoView != null) applyPhoto(photoView, photoHolder);
            toast("Foto atualizada!");
        } catch (Exception e) {
            toast("Não foi possível usar essa foto.");
        }
    }

    private void editName() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(playerName());
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Seu nome")
                .setView(et)
                .setPositiveButton("Salvar", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "Jogador";
                    prefs.edit().putString("name", n).apply();
                    nameView.setText(n);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ status da conexão em tempo real
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            NetMonitor.Status s = net.status();
            int c = s.level == 0 ? Ui.GREEN : s.level == 1 ? Ui.YELLOW : Ui.RED;
            netText.setText(s.text);
            netText.setTextColor(c);
            netDot.setBackground(Ui.oval(c));
            if (dotPulse != null) dotPulse.cancel();
            netDot.setScaleX(1f);
            netDot.setScaleY(1f);
            netDot.setAlpha(1f);
            if (s.level == 0) dotPulse = Ui.pulse(netDot);
            handler.postDelayed(this, 2000);
        }
    };

    private void startLoop() {
        if (looping) return;
        looping = true;
        net = NetMonitor.acquire(this);
        handler.post(tick);
    }

    private void stopLoop() {
        if (!looping) return;
        looping = false;
        handler.removeCallbacks(tick);
        if (dotPulse != null) dotPulse.cancel();
        NetMonitor.release();
    }
}
