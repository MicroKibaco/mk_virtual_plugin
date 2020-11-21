package com.github.microkibaco;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.stander.BroadcastReceiverInterface;

/**
 * @author Omooo
 * @version v1.0
 * @Date 2020/04/30
 * desc :
 */
public class ProxyReceiver extends BroadcastReceiver {

    private String mReceiverName;

    public ProxyReceiver(String receiverName) {
        mReceiverName = receiverName;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Class clazz = null;
        try {
            clazz = PluginManager.getInstance(context).getClassLoader().loadClass(mReceiverName);
            BroadcastReceiverInterface receiverInterface = (BroadcastReceiverInterface) clazz.newInstance();
            receiverInterface.onReceive(context, intent);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
