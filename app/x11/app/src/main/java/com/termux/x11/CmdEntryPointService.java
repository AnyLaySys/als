package com.termux.x11;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.system.Os;

public class CmdEntryPointService extends Service {
    public static final String ACTION_START = "com.termux.x11.action.START";
    public static final String ACTION_STOP  = "com.termux.x11.action.STOP";
    public static final String EXTRA_ARGS = "args";
    public static final String EXTRA_ENV_KEYS = "env_keys";
    public static final String EXTRA_ENV_VALUES = "env_values";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            Process.killProcess(Process.myPid());
            return START_NOT_STICKY;
        }

        String[] args = intent.getStringArrayExtra(EXTRA_ARGS);
        if (args == null) return START_NOT_STICKY;

        // Set environment variables before CmdEntryPoint class loads
        String[] keys = intent.getStringArrayExtra(EXTRA_ENV_KEYS);
        String[] vals = intent.getStringArrayExtra(EXTRA_ENV_VALUES);
        if (keys != null && vals != null) {
            for (int i = 0; i < keys.length && i < vals.length; i++) {
                try {
                    Os.setenv(keys[i], vals[i], true);
                } catch (android.system.ErrnoException ignored) {
                    // Environment variable may already be set; not critical
                }
            }
        }

        new Thread(() -> CmdEntryPoint.main(args)).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
