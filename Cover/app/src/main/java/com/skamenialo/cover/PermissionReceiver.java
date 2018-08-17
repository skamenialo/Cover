package com.skamenialo.cover;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public final class PermissionReceiver extends DeviceAdminReceiver {

    @Override
    public void onDisabled(Context context, Intent intent ) {
        Toast.makeText( context, R.string.on_permission_disabled, Toast.LENGTH_LONG ).show();
    }
}
