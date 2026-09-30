package com.katiusu.hyperautofillfix;

import android.content.ContentValues;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * HyperOS Autofill Fix —— 阻止系统管家/安全中心把「自动填充服务」改回小米自家服务或清空。
 *
 * 拦截层次（从外到内，任一层次命中即丢弃本次写入）：
 *   1. 调用方进程：android.provider.Settings$Secure.putString / putStringForUser
 *   2. 设置存储进程：com.android.providers.settings.SettingsProvider.call("PUT_secure", ...)
 *   3. 设置存储底层：SettingsState.insertSettingLocked(...)（兜底，覆盖绕过 call 的直写）
 *   4. 旧式 ContentProvider 通道：SettingsProvider.insert / update
 *
 * 设计要点：
 *   - 参数按签名自适应扫描，不依赖固定下标，兼容 AOSP / MIUI 不同版本的参数顺序；
 *   - 返回值类型与真实方法保持一致（insertSettingLocked 返回 String，绝不能返回 Boolean）；
 *   - 所有判定都走“快速失败”路径，热路径上只做一次字符串比较；
 *   - 日志统一交给 {@link ModuleLog}：写 logcat 的同时广播给 App，界面里可看「直观 / 原始」两种视图。
 */
public final class MainHook implements IXposedHookLoadPackage {

    /** 目标设置项 */
    private static final String KEY = "autofill_service";
    /** Settings.Secure 的写入 RPC 方法名（AOSP 常量 Settings.CALL_METHOD_PUT_SECURE） */
    private static final String PUT_SECURE = "PUT_secure";
    /** Bundle / ContentValues 中承载值的键 */
    private static final String EXTRA_VALUE = "value";
    private static final String EXTRA_NAME = "name";

    /** 命中即视为“被改回自家服务”的包名前缀 */
    private static final String[] BLOCKED_PREFIXES = {"com.miui", "com.xiaomi"};

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        final boolean isProviderProcess = "com.android.providers.settings".equals(pkg);
        final boolean isSystemServer = "android".equals(pkg);
        final boolean isCallerProcess = "com.android.settings".equals(pkg)
                || "com.miui.securitycenter".equals(pkg);

        // 不在作用域内的进程直接返回，零开销
        if (!isProviderProcess && !isSystemServer && !isCallerProcess) return;

        ModuleLog.attach(pkg);

        // 第一道防线：在写入方进程内截住
        hookSettingsSecure(lpparam, pkg);

        // 第二道防线：所有进程的写入最终都会汇聚到设置存储进程
        if (isProviderProcess) {
            hookSettingsProvider(lpparam);
        }
    }

    // ------------------------------------------------------------------
    // 1. android.provider.Settings$Secure
    // ------------------------------------------------------------------

    private void hookSettingsSecure(LoadPackageParam lpparam, final String pkg) {
        try {
            Class<?> secureClass = XposedHelpers.findClass(
                    "android.provider.Settings$Secure", lpparam.classLoader);

            XC_MethodHook secureHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    final Object[] args = param.args;
                    // putString(resolver, name, value) 至少 3 个参数
                    if (args == null || args.length < 3) return;

                    // 热路径：先找 key，找不到立刻返回，不做任何额外分配
                    for (int i = 0; i < args.length - 1; i++) {
                        if (!KEY.equals(args[i])) continue;

                        String newValue = asString(args[i + 1]);
                        String layer = "Secure." + param.method.getName();
                        if (isBlocked(newValue)) {
                            // 放行用户合法变更，拦截清空 / 回退到小米服务
                            param.setResult(Boolean.TRUE);
                            ModuleLog.blocked(layer, newValue);
                        } else {
                            ModuleLog.allowed(layer, newValue);
                        }
                        return;
                    }
                }
            };

            XposedBridge.hookAllMethods(secureClass, "putString", secureHook);
            XposedBridge.hookAllMethods(secureClass, "putStringForUser", secureHook);
        } catch (Throwable t) {
            ModuleLog.failed("Hook Settings$Secure", t);
        }
    }

    // ------------------------------------------------------------------
    // 2. com.android.providers.settings
    // ------------------------------------------------------------------

    private void hookSettingsProvider(LoadPackageParam lpparam) {
        Class<?> providerClass = null;
        try {
            providerClass = XposedHelpers.findClass(
                    "com.android.providers.settings.SettingsProvider", lpparam.classLoader);
        } catch (Throwable t) {
            ModuleLog.failed("Hook SettingsProvider", t);
        }

        // 2.1 call() —— Android 8.0+ 写入 Settings 的统一入口
        if (providerClass != null) {
            try {
                XposedBridge.hookAllMethods(providerClass, "call", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        final Object[] args = param.args;
                        if (args == null) return;

                        // 先判断是不是 secure 写入，不是就直接走人
                        boolean isPutSecure = false;
                        Bundle extras = null;
                        boolean keyInArgs = false;
                        for (Object arg : args) {
                            if (PUT_SECURE.equals(arg)) {
                                isPutSecure = true;
                            } else if (arg instanceof Bundle) {
                                extras = (Bundle) arg;
                            } else if (KEY.equals(arg)) {
                                keyInArgs = true;
                            }
                        }
                        if (!isPutSecure) return;

                        // 设置项名可能在参数里，也可能在 extras 里
                        if (!keyInArgs) {
                            if (extras == null || !KEY.equals(extras.getString(EXTRA_NAME))) return;
                        }

                        String newValue = extras == null ? null : extras.getString(EXTRA_VALUE);
                        if (isBlocked(newValue)) {
                            // 返回空 Bundle：调用方认为写入成功，数据库实际未被改动
                            param.setResult(new Bundle());
                            ModuleLog.blocked("SettingsProvider.call(PUT_secure)", newValue);
                        } else {
                            ModuleLog.allowed("SettingsProvider.call(PUT_secure)", newValue);
                        }
                    }
                });
            } catch (Throwable t) {
                ModuleLog.failed("Hook SettingsProvider.call", t);
            }

            // 2.2 旧式 ContentProvider 通道（部分 ROM / 应用仍在用）
            hookLegacyContentProvider(providerClass);
        }

        // 2.3 最底层兜底：SettingsState.insertSettingLocked
        try {
            Class<?> settingsStateClass = XposedHelpers.findClass(
                    "com.android.providers.settings.SettingsState", lpparam.classLoader);

            XposedBridge.hookAllMethods(settingsStateClass, "insertSettingLocked", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    final Object[] args = param.args;
                    if (args == null || args.length < 2) return;

                    // insertSettingLocked 的返回类型是 String（旧值）。
                    // 若某个版本改成 void，则此处无法拦截，交给上层通道。
                    if (param.method instanceof Method
                            && ((Method) param.method).getReturnType() == void.class) {
                        return;
                    }

                    for (int i = 0; i < args.length - 1; i++) {
                        if (!KEY.equals(args[i])) continue;

                        String newValue = asString(args[i + 1]);
                        if (isBlocked(newValue)) {
                            // 返回 null 表示“无旧值 / 未发生变更”，写入被丢弃
                            param.setResult(null);
                            ModuleLog.blocked("SettingsState.insertSettingLocked", newValue);
                        } else {
                            ModuleLog.allowed("SettingsState.insertSettingLocked", newValue);
                        }
                        return;
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.failed("Hook SettingsState.insertSettingLocked", t);
        }
    }

    private void hookLegacyContentProvider(Class<?> providerClass) {
        try {
            XposedBridge.hookAllMethods(providerClass, "insert", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentValues values = findContentValues(param.args);
                    if (values == null || !values.containsKey(KEY)) return;

                    String newValue = values.getAsString(KEY);
                    if (isBlocked(newValue)) {
                        param.setResult(null); // insert 返回 Uri
                        ModuleLog.blocked("SettingsProvider.insert", newValue);
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.failed("Hook SettingsProvider.insert", t);
        }

        try {
            XposedBridge.hookAllMethods(providerClass, "update", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    ContentValues values = findContentValues(param.args);
                    if (values == null || !values.containsKey(KEY)) return;

                    String newValue = values.getAsString(KEY);
                    if (isBlocked(newValue)) {
                        param.setResult(0); // update 返回受影响行数
                        ModuleLog.blocked("SettingsProvider.update", newValue);
                    }
                }
            });
        } catch (Throwable t) {
            ModuleLog.failed("Hook SettingsProvider.update", t);
        }
    }

    // ------------------------------------------------------------------
    // 判定与工具
    // ------------------------------------------------------------------

    /**
     * 是否需要拦截。以下情况全部视为“被系统管家改回默认”：
     *   1. 值为空 / "null" / "0"（清空偏好）；
     *   2. 值不是合法的 ComponentName（合法值必须是 包名/类名，不含 '/' 一律视为脏数据）；
     *   3. 值指向小米自家组件（com.miui.* / com.xiaomi.*）。
     */
    private static boolean isBlocked(String value) {
        if (value == null) return true;
        String v = value.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v) || "0".equals(v)) return true;
        if (v.indexOf('/') < 0) return true;

        String lower = v.toLowerCase(Locale.ROOT);
        for (String prefix : BLOCKED_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    private static String asString(Object o) {
        if (o instanceof String) return (String) o;
        if (o instanceof CharSequence) return o.toString();
        return null;
    }

    private static ContentValues findContentValues(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof ContentValues) return (ContentValues) arg;
        }
        return null;
    }
}
