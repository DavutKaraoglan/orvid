package com.orvid.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class Reaper extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        OrvidApp.get().wipeAndExit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
