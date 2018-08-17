package com.skamenialo.cover.Helpers;

import android.content.ComponentName;

public interface PermissionCheckedListener {
    void onPermissionChecked(ComponentName adminComponent, boolean isAdmin);
}
