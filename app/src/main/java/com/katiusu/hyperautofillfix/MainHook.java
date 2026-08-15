package com.katiusu.hyperautofillfix;

import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 目标进程判断
        boolean isTarget = "android".equals(lpparam.packageName)
                || "com.android.providers.settings".equals(lpparam.packageName)
                || "com.android.settings".equals(lpparam.packageName)
                || "com.miui.securitycenter".equals(lpparam.packageName);

        if (!isTarget) return;

        XposedBridge.log("HyperOSAutofillFix: 已成功载入进程 [" + lpparam.packageName + "]");

        // 1. 上层 Hook：动态拦截 Settings.Secure 的所有 putString / putStringForUser 重载方法
        hookSettingsSecure(lpparam);

        // 2. 底层 Hook：针对 设置存储 进程进行数据库与内存拦截
        if ("com.android.providers.settings".equals(lpparam.packageName)) {
            hookSettingsProvider(lpparam);
        }
    }

    private void hookSettingsSecure(LoadPackageParam lpparam) {
        try {
            Class<?> settingsSecureClass = XposedHelpers.findClass("android.provider.Settings$Secure", lpparam.classLoader);

            XC_MethodHook secureHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null) return;

                    // 动态扫描参数列表，精准匹配 "autofill_service"
                    for (int i = 0; i < param.args.length; i++) {
                        if ("autofill_service".equals(param.args[i])) {
                            // key 的下一个参数即为即将写入的 value
                            String newValue = null;
                            if (i + 1 < param.args.length && param.args[i + 1] instanceof String) {
                                newValue = (String) param.args[i + 1];
                            }

                            boolean isCleared = (newValue == null || newValue.trim().isEmpty() || "null".equalsIgnoreCase(newValue));
                            boolean isResetToMiui = (newValue != null && newValue.contains("com.miui"));

                            if (isCleared || isResetToMiui) {
                                XposedBridge.log("HyperOSAutofillFix [" + lpparam.packageName + "]: 🚨 上层拦截成功! 阻止清空/重置，原试图写入值: [" + newValue + "]");
                                param.setResult(true); // 假装写入成功，丢弃本次变更
                            } else {
                                XposedBridge.log("HyperOSAutofillFix [" + lpparam.packageName + "]: ✅ 放行用户合法变更 -> " + newValue);
                            }
                            break;
                        }
                    }
                }
            };

            // 注意：hookAllMethods 属于 XposedBridge 类
            XposedBridge.hookAllMethods(settingsSecureClass, "putStringForUser", secureHook);
            XposedBridge.hookAllMethods(settingsSecureClass, "putString", secureHook);

        } catch (Throwable t) {
            XposedBridge.log("HyperOSAutofillFix: Hook Settings.Secure 异常: " + t.getMessage());
        }
    }

    private void hookSettingsProvider(LoadPackageParam lpparam) {
        // 2.1 拦截 SettingsProvider.call 方法
        try {
            Class<?> providerClass = XposedHelpers.findClass("com.android.providers.settings.SettingsProvider", lpparam.classLoader);

            XposedBridge.hookAllMethods(providerClass, "call", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null) return;

                    boolean isPutSecure = false;
                    boolean isAutofillService = false;
                    Bundle extras = null;

                    // 遍历 call(...) 的所有参数，提取 Bundle
                    for (Object arg : param.args) {
                        if ("PUT_secure".equals(arg)) {
                            isPutSecure = true;
                        } else if ("autofill_service".equals(arg)) {
                            isAutofillService = true;
                        } else if (arg instanceof Bundle) {
                            extras = (Bundle) arg;
                        }
                    }

                    if (isPutSecure && isAutofillService) {
                        String newValue = null;
                        if (extras != null) {
                            newValue = extras.getString("value");
                        }

                        boolean isCleared = (newValue == null || newValue.trim().isEmpty() || "null".equalsIgnoreCase(newValue));
                        boolean isResetToMiui = (newValue != null && newValue.contains("com.miui"));

                        if (isCleared || isResetToMiui) {
                            XposedBridge.log("HyperOSAutofillFix [SettingsProvider]: 🚨 底层数据库拦截成功! 阻止写入: [" + newValue + "]");
                            param.setResult(new Bundle()); // 返回空 Bundle，中断数据库实际写入
                        } else {
                            XposedBridge.log("HyperOSAutofillFix [SettingsProvider]: ✅ 底层数据库放行 -> " + newValue);
                        }
                    }
                }
            });

        } catch (Throwable t) {
            XposedBridge.log("HyperOSAutofillFix: Hook SettingsProvider 异常: " + t.getMessage());
        }

        // 2.2 最底层防护：Hook SettingsState.insertSettingLocked
        try {
            Class<?> settingsStateClass = XposedHelpers.findClass("com.android.providers.settings.SettingsState", lpparam.classLoader);
            XposedBridge.hookAllMethods(settingsStateClass, "insertSettingLocked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length < 2) return;

                    if ("autofill_service".equals(param.args[0])) {
                        String newValue = param.args[1] != null ? String.valueOf(param.args[1]) : null;

                        boolean isCleared = (newValue == null || newValue.trim().isEmpty() || "null".equalsIgnoreCase(newValue));
                        boolean isResetToMiui = (newValue != null && newValue.contains("com.miui"));

                        if (isCleared || isResetToMiui) {
                            XposedBridge.log("HyperOSAutofillFix [SettingsState]: 🚨 最底层写入拦截成功! 阻止写入: [" + newValue + "]");
                            param.setResult(false);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
