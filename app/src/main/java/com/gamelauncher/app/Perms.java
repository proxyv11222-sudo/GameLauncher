package com.gamelauncher.app;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

/** Checagem de permissões especiais. */
final class Perms {
    private Perms() {}

    /** "Acesso de uso": permite saber qual app está na tela (para mostrar o overlay só no jogo). */
    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
