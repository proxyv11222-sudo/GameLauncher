package com.gamelauncher.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

/** "Modo Jogo": pede ao Android para encerrar os apps que estão em segundo plano. */
final class GameBooster {
    private GameBooster() {}

    /** Devolve quantos apps foram limpos. O Android só encerra o que está mesmo em segundo plano. */
    static int boost(Context c, String keepPkg) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager();
        if (am == null) return 0;

        Set<String> skip = new HashSet<>();
        skip.add(c.getPackageName());
        if (keepPkg != null) skip.add(keepPkg);
        skip.add("android");
        skip.add("com.android.systemui");
        skip.add("com.android.phone");
        skip.add("com.android.settings");
        skip.add("com.google.android.gms");
        skip.add("com.google.android.gsf");

        // launcher (tela inicial)
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo h = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (h != null && h.activityInfo != null) skip.add(h.activityInfo.packageName);

        // teclado atual
        try {
            String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) skip.add(ime.substring(0, ime.indexOf('/')));
        } catch (Exception ignored) {
        }

        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        int count = 0;
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (skip.contains(pkg)) continue;
            try {
                am.killBackgroundProcesses(pkg);
                count++;
            } catch (Exception ignored) {
            }
        }
        return count;
    }
}
