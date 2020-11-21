package com.github.microkibaco;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.widget.Toast;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Author: Omooo Date: 2020/4/29 Version: Desc:
 */
public class PluginManager {

    private volatile static PluginManager sPluginManager;
    private Context mContext;

    // 插件包名称
    public String mApkName = "plug.apk";
    private DexClassLoader mPluginClassLoader;
    private Resources mPluginResources;

    public static PluginManager getInstance(Context context) {
        if (sPluginManager == null) {
            synchronized (PluginManager.class) {
                if (sPluginManager == null) {
                    sPluginManager = new PluginManager(context);
                }
            }
        }
        return sPluginManager;
    }

    public PluginManager(Context context) {
        mContext = context;
    }

    /**
     * 安装插件
     */
    public void loadPlugin() {
        // 把Assets里面得文件复制到 /data/data/files 目录下
        Utils.extractAssets(mContext, mApkName);
        // 获取插件目录下的文件
        File extractFile = mContext.getFileStreamPath(mApkName);
        // 获取插件包路径
        String dexPath = extractFile.getPath();
        // 创建Dex输出路径
        File fileRelease = mContext.getDir("dex", Context.MODE_PRIVATE);

        // 构建 DexClassLoader 生成目录
        mPluginClassLoader = new DexClassLoader(dexPath,
                fileRelease.getAbsolutePath(), null, mContext.getClassLoader());
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method method = AssetManager.class.getMethod("addAssetPath", String.class);
            method.invoke(assetManager, dexPath);
            mPluginResources = new Resources(assetManager, mContext.getResources().getDisplayMetrics(),
                    mContext.getResources().getConfiguration());
        } catch (Exception e) {
            Toast.makeText(mContext, "加载 Plugin 失败", Toast.LENGTH_SHORT).show();
        }
    }

    public DexClassLoader getClassLoader() {
        return mPluginClassLoader;
    }

    public Resources getResources() {
        return mPluginResources;
    }

    @SuppressLint("PrivateApi")
    public void parserApkAction() {
        try {
            // 获取包名解析器
            Class packageParserClass = Class.forName("android.content.pm.PackageParser");

            // 通过.newInstance拿到解析器实体类
            Object packageParser = packageParserClass.newInstance();
            // 通过反射获取parsePackage方法
            Method method = packageParserClass.getMethod("parsePackage", File.class, int.class);
            // 获取插件文件
            File extractFile = mContext.getFileStreamPath(mApkName);
            // 通过反射获取包名
            Object packageObject = method.invoke(packageParser, extractFile, PackageManager.GET_RECEIVERS);
            // 通过反射获取receivers字段
            Field receiversFields = packageObject.getClass().getDeclaredField("receivers");
            // 通过反射获取packageObject集合
            ArrayList arrayList = (ArrayList) receiversFields.get(packageObject);

            Class packageUserStateClass = Class.forName("android.content.pm.PackageUserState");
            Class userHandleClass = Class.forName("android.os.UserHandle");
            int userId = (int) userHandleClass.getMethod("getCallingUserId").invoke(null);

            for (Object activity : arrayList) {
                Class component = Class.forName("android.content.pm.PackageParser$Component");
                Field intents = component.getDeclaredField("intents");
                // 1.获取 Intent-Filter
                ArrayList<IntentFilter> intentFilterList = (ArrayList<IntentFilter>) intents.get(activity);
                // 2.需要获取到广播的全类名，通过 ActivityInfo 获取
                // ActivityInfo generateActivityInfo(Activity a, int flags, PackageUserState state, int userId)
                Method generateActivityInfoMethod = packageParserClass
                        .getMethod("generateActivityInfo", activity.getClass(), int.class,
                                packageUserStateClass, int.class);
                ActivityInfo activityInfo = (ActivityInfo) generateActivityInfoMethod.invoke(null, activity, 0,
                        packageUserStateClass.newInstance(), userId);
                Class broadcastReceiverClass = getClassLoader().loadClass(activityInfo.name);
                BroadcastReceiver broadcastReceiver = (BroadcastReceiver) broadcastReceiverClass.newInstance();
                for (IntentFilter intentFilter : intentFilterList) {
                    mContext.registerReceiver(broadcastReceiver, intentFilter);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
