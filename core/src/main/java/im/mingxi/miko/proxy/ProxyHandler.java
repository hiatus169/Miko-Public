package im.mingxi.miko.proxy;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class ProxyHandler implements Handler.Callback {

    private final Handler.Callback mDefault;

    public ProxyHandler(Handler.Callback defaultCallback) {
        mDefault = defaultCallback;
    }

    private static void processLaunchActivityItem(Object clientTransaction, Object item)
            throws NoSuchFieldException,
            IllegalAccessException,
            InvocationTargetException,
            NoSuchMethodException,
            ClassNotFoundException {
        Class<?> clz = item.getClass();
        Field fmIntent = clz.getDeclaredField("mIntent");
        fmIntent.setAccessible(true);
        Intent wrapper = (Intent) fmIntent.get(item);
        Log.d("ParasiticsUtils:", "handleMessage: target wrapper =" + wrapper);
        assert wrapper != null;
        Bundle bundle = null;
        try {
            Field fExtras = Intent.class.getDeclaredField("mExtras");
            fExtras.setAccessible(true);
            bundle = (Bundle) fExtras.get(wrapper);
        } catch (Exception e) {
        }
        if (bundle != null) {
            bundle.setClassLoader(ActivityProxyManager.HostClassLoader);
            if (wrapper.hasExtra("miko_activity_proxy_intent")) {
                Intent realIntent = wrapper.getParcelableExtra("miko_activity_proxy_intent");
                fmIntent.set(item, realIntent);
                if (Build.VERSION.SDK_INT >= 31) {
                    IBinder token =
                            (IBinder)
                                    clientTransaction
                                            .getClass()
                                            .getMethod("getActivityToken")
                                            .invoke(clientTransaction);
                    Class<?> cActivityThread = Class.forName("android.app.ActivityThread");
                    Method currentActivityThread =
                            cActivityThread.getDeclaredMethod("currentActivityThread");
                    currentActivityThread.setAccessible(true);
                    Object activityThread = currentActivityThread.invoke(null);
                    assert activityThread != null;
                    try {
                        Object acr =
                                activityThread
                                        .getClass()
                                        .getMethod("getLaunchingActivity", IBinder.class)
                                        .invoke(activityThread, token);
                        if (acr != null) {
                            Field fAcrIntent = acr.getClass().getDeclaredField("intent");
                            fAcrIntent.setAccessible(true);
                            fAcrIntent.set(acr, realIntent);
                        }
                    } catch (NoSuchMethodException e) {
                        if (Build.VERSION.SDK_INT == 33) {
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    @Override
    public boolean handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case 100 -> {
                try {
                    Object record = msg.obj;
                    Field fIntent = record.getClass().getDeclaredField("intent");
                    fIntent.setAccessible(true);
                    Intent intent = (Intent) fIntent.get(record);
                    assert intent != null;
                    // 获取bundle
                    Bundle bundle = null;
                    try {
                        Field fExtras = Intent.class.getDeclaredField("mExtras");
                        fExtras.setAccessible(true);
                        bundle = (Bundle) fExtras.get(intent);
                    } catch (Exception e) {
                    }
                    // 设置
                    if (bundle != null) {
                        bundle.setClassLoader(ActivityProxyManager.HostClassLoader);
                        if (intent.hasExtra("miko_activity_proxy_intent")) {
                            Intent rIntent = intent.getParcelableExtra("miko_activity_proxy_intent");
                            fIntent.set(record, rIntent);
                        }
                    }
                } catch (Exception e) {
                }
            }
            case 159 -> {
                Object clientTransaction = msg.obj;
                try {
                    if (clientTransaction != null) {
                        Method getCallbacksMethod =
                                Class.forName("android.app.servertransaction.ClientTransaction")
                                        .getDeclaredMethod("getCallbacks");
                        getCallbacksMethod.setAccessible(true);
                        List<?> clientTransactionItems =
                                (List<?>) getCallbacksMethod.invoke(clientTransaction);
                        if (clientTransactionItems == null && clientTransactionItems.isEmpty())
                            break;
                        for (Object item : clientTransactionItems) {
                            Class<?> clz = item.getClass();
                            if (clz.getName().contains("LaunchActivityItem")) {
                                processLaunchActivityItem(clientTransaction, item);
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        return mDefault != null && mDefault.handleMessage(msg);
    }
}