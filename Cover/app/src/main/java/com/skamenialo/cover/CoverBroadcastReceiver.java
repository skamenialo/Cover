package com.skamenialo.cover;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.skamenialo.cover.Helpers.BroadcastReceivedListener;

public class CoverBroadcastReceiver extends BroadcastReceiver {
    static final String TAG = "SKAMENIALO.RECEIVER";
    private BroadcastReceivedListener mListener;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if(mListener!=null)
                mListener.onBroadcastReceived(intent.getAction(), intent);
        }catch (Exception e){
            Log.e(TAG, e.toString());
        }
    }

    public void SetBroadcastReceivedListener(BroadcastReceivedListener listener){
        mListener = listener;
    }
}
