package com.skamenialo.cover.Helpers;

import android.content.Intent;

public interface BroadcastReceivedListener {
    void onBroadcastReceived(String action, Intent intent);
}
