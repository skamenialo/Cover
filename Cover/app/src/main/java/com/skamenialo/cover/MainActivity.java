package com.skamenialo.cover;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.skamenialo.cover.Helpers.PermissionCheckedListener;
import com.skamenialo.cover.Helpers.Utils;

public class MainActivity
        extends AppCompatActivity
        implements CompoundButton.OnCheckedChangeListener, View.OnClickListener, PermissionCheckedListener {
    //region Fields

    static final String TAG = "SKAMENIALO.MAIN";

    ToggleButton mToggleButton;
    Button mButton;

    //endregion

    //region Activity methods

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mToggleButton = (ToggleButton) findViewById(R.id.toggleButton);
        if(CoverService.getInstance()!=null){
            mToggleButton.setChecked(true);
        }
        mToggleButton.setOnCheckedChangeListener(this);

        mButton = (Button)findViewById(R.id.button2);
        mButton.setOnClickListener(this);
        CoverService.setOnPermissionChecked(this);
    }

    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent intent ) {
        super.onActivityResult( requestCode, resultCode, intent );

        if(requestCode == Utils.REQUEST_CODE_ENABLE_ADMIN) {
            if (resultCode == RESULT_OK) {
                onCheckedChanged(null, true);
                Log.i(TAG, "Got admin permission");
            } else {
                Log.i(TAG, "Dismiss admin permission");
                askUserToRetry();
            }
        }
    }

    //endregion

    //region Interfaces methods

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        Intent service = new Intent(this,CoverService.class);
        if(checked){
            startService(service);
        }else{
            stopService(service);
        }
    }

    @Override
    public void onClick(View view) {
        ComponentName adminComponentName = CoverService.getAdminComponent();
        if(adminComponentName==null)
            return;
        removePermission(adminComponentName);
    }

    @Override
    public void onPermissionChecked(ComponentName adminComponent, boolean isAdmin) {
        if(!isAdmin){
            requestPermission(adminComponent);
        }
    }

    //endregion

    //region private methods

    private void requestPermission( ComponentName aAdminComponent ) {
        String explanation = getResources().getString( R.string.request_permission_explanation );

        Intent intent = new Intent( DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN );
        intent.putExtra( DevicePolicyManager.EXTRA_DEVICE_ADMIN, aAdminComponent );
        intent.putExtra( DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation );

        startActivityForResult( intent, Utils.REQUEST_CODE_ENABLE_ADMIN );
        Log.i(TAG, "Requesting permission");
    }

    private void removePermission (ComponentName adminComponent){
        DevicePolicyManager mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDPM.removeActiveAdmin(adminComponent);
        Log.i(TAG, "Removing permission");
    }

    private void askUserToRetry() {
        AlertDialog.Builder builder;
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH ) {
            builder = new AlertDialog.Builder( this, AlertDialog.THEME_DEVICE_DEFAULT_DARK );
        }
        else {
            builder = new AlertDialog.Builder( this );
        }

        builder.setTitle( R.string.request_permission_title );
        builder.setMessage( R.string.request_permission_message );
        builder.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick( DialogInterface aDialog, int aButton ) {
                ComponentName adminComponentName = CoverService.getAdminComponent();
                if(adminComponentName==null)
                    return;
                requestPermission(adminComponentName);
                Log.i(TAG, "Requesting permission again");
            }
        } );

        builder.setNegativeButton( android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick( DialogInterface aDialog, int aButton ) {
                Log.i(TAG, "Requesting permission cancelled");
                aDialog.dismiss();
                if (CoverService.getInstance() != null)
                    CoverService.getInstance().stopSelf();
                mToggleButton.setOnCheckedChangeListener(null);
                mToggleButton.setChecked(false);
                mToggleButton.setOnCheckedChangeListener(MainActivity.this);
                //finish();
            }
        } );

        builder.create().show();
    }

    //endregion
}
