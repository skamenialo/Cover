package com.skamenialo.cover;

import android.telephony.PhoneStateListener;

import com.skamenialo.cover.Helpers.CallStateListener;

public class CoverPhoneStateListener extends PhoneStateListener {
    private CallStateListener mListener;

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        if(mListener != null)
            mListener.onCallStateChanged(state);
        super.onCallStateChanged(state, incomingNumber);
    }

    public void setCallStateListener(CallStateListener listener){
        mListener = listener;
    }
}
