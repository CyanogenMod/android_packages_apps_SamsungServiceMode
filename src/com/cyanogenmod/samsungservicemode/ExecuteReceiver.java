package com.cyanogenmod.samsungservicemode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class ExecuteReceiver extends BroadcastReceiver {

    private static final String TAG = "ExecuteReceiver";

    public static final String KEY_MODE_TYPE = "mode_type";
    public static final String KEY_SUB_TYPE = "sub_type";
    public static final String KEY_DATA = "data";

    private Phone mPhone;
    private Handler mHandler = new Handler();

    @Override
    public void onReceive(Context context, Intent intent) {
        // Read intent data
        int modeType = intent.getIntExtra(KEY_MODE_TYPE, OemCommands.OEM_SM_TYPE_TEST_MANUAL);
        int subType = intent.getIntExtra(KEY_SUB_TYPE, OemCommands.OEM_SM_TYPE_SUB_ENTER);
        String data = intent.getStringExtra(KEY_DATA);

        if (data == null) {
            Log.e(TAG, "Intent extra 'data' must not be null.");
            return;
        }

        // Initialize
        mPhone = PhoneFactory.getDefaultPhone();

        // Send requests
        sendRequest(OemCommands.getEnterServiceModeData(modeType, subType, OemCommands.OEM_SM_ACTION));

        for (char chr : data.toCharArray()) {
            sendRequest(OemCommands.getPressKeyData(chr, OemCommands.OEM_SM_ACTION));
        }

        sendRequest(OemCommands.getEndServiceModeData(modeType));
    }

    private void sendRequest(byte[] data) {
        mPhone.invokeOemRilRequestRaw(data, mHandler.obtainMessage());
    }

}
