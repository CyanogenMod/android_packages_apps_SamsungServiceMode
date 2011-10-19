package com.cyanogenmod.samsungservicemode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SecretBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String code = intent.getData().getHost();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClass(context, SamsungServiceModeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(SamsungServiceModeActivity.EXTRA_SECRET_CODE, code);
        context.startActivity(i);
    }

}
