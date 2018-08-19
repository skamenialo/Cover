package com.skamenialo.cover;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootUpBroadcastReceiver extends BroadcastReceiver {
    static final String TAG = "SKAMENIALO.BOOTUP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) && CoverService.getInstance() == null) {
            Intent service = new Intent(context, CoverService.class);
            Log.i(TAG, "Start service");
            context.startService(service);
        }
    }
}
