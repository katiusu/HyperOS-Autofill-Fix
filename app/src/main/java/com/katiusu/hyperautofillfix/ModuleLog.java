package com.katiusu.hyperautofillfix;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 模块侧日志出口：
 *   - 始终写 XposedBridge（logcat，标签 HyperOSAutofillFix）；
 *   - 同时把结构化事件广播给本模块 App，供界面里的「直观 / 原始」两种视图展示。
 *
 * 事件 JSON 字段：
 *   t        时间戳(ms)
 *   lvl      block / allow / info
 *   pkg      被 Hook 的进程名
 *   hook     拦截层次
 *   val      写入的值（allow/info 可能为空）
 *   raw      原始 logcat 行（含时间、pid、进程名、tag）
 *   blocked/allowed  该进程内的累计计数
 */
public final class ModuleLog {

    public static final String TAG = "HyperOSAutofillFix";
    public static final String ACTION_LOG = "com.katiusu.hyperautofillfix.action.LOG";
    public static final String EXTRA_EVENT = "event";
    public static final String MODULE_PACKAGE = "com.katiusu.hyperautofillfix";

    /** 同一结果最短广播间隔，避免系统管家重试时反复唤醒界面进程 */
    private static final long BROADCAST_THROTTLE_MS = 1000L;

    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static Context sContext;
    private static String sProcessName = "unknown";
    private static String sLastSignature;
    private static long sLastBroadcastAt;
    private static long sBlockedCount;
    private static long sAllowedCount;

    private ModuleLog() {
    }

    /** Hook 载入时调用：记录当前进程名并上报一条 info 事件。 */
    public static void attach(String processName) {
        sProcessName = processName == null ? "unknown" : processName;
        info("ModuleInit", null);
    }

    public static void blocked(String layer, String value) {
        event("block", layer, value);
    }

    public static void allowed(String layer, String value) {
        event("allow", layer, value);
    }

    public static void info(String layer, String value) {
        event("info", layer, value);
    }

    public static void failed(String layer, Throwable t) {
        event("info", layer, t == null ? "unknown" : String.valueOf(t));
    }

    // ------------------------------------------------------------------

    private static void event(String level, String layer, String value) {
        final String raw;
        final String json;
        final boolean throttled;

        synchronized (LOCK) {
            if ("block".equals(level)) {
                sBlockedCount++;
            } else if ("allow".equals(level)) {
                sAllowedCount++;
            }

            raw = buildRawLine(level, layer, value);
            json = buildJson(level, layer, value, raw);

            // 限频：同一「层次 + 值」在 1 秒内只广播一次
            String signature = level + '|' + layer + '|' + value;
            long now = SystemClock.elapsedRealtime();
            throttled = json == null || (signature.equals(sLastSignature)
                    && now - sLastBroadcastAt < BROADCAST_THROTTLE_MS);
            sLastSignature = signature;
            sLastBroadcastAt = now;
        }

        XposedBridge.log(raw);
        if (!throttled) {
            broadcast(json);
        }
    }

    private static String buildRawLine(String level, String layer, String value) {
        String action;
        if ("block".equals(level)) {
            action = "已拦截";
        } else if ("allow".equals(level)) {
            action = "放行";
        } else {
            action = "信息";
        }

        StringBuilder sb = new StringBuilder(96);
        sb.append(TIME_FORMAT.format(new Date()))
                .append(' ').append(Process.myPid())
                .append(' ').append(sProcessName)
                .append(' ').append(TAG).append(": ")
                .append(action).append(" [").append(layer).append(']');
        if (value != null) {
            sb.append(" autofill_service = [").append(value).append(']');
        }
        return sb.toString();
    }

    private static String buildJson(String level, String layer, String value, String raw) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("lvl", level);
            o.put("pkg", sProcessName);
            o.put("hook", layer);
            if (value != null) {
                o.put("val", value);
            }
            o.put("raw", raw);
            o.put("blocked", sBlockedCount);
            o.put("allowed", sAllowedCount);
            return o.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void broadcast(String json) {
        Context context = getContext();
        if (context == null) return;
        try {
            Intent intent = new Intent(ACTION_LOG);
            // 显式指定目标包：既绕过 Android 8.0+ 的隐式广播限制，也避免被其它应用收到
            intent.setPackage(MODULE_PACKAGE);
            intent.putExtra(EXTRA_EVENT, json);
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 广播日志失败: " + t);
        }
    }

    /**
     * 取一个可用 Context：
     *   普通应用进程   -> AndroidAppHelper.currentApplication()
     *   system_server -> ActivityThread.currentActivityThread().getSystemContext()
     */
    private static Context getContext() {
        if (sContext != null) return sContext;
        synchronized (LOCK) {
            if (sContext != null) return sContext;
            Context context = currentApplicationSafely();
            if (context == null) {
                context = systemContextSafely();
            }
            sContext = context;
            return context;
        }
    }

    private static Context currentApplicationSafely() {
        try {
            return AndroidAppHelperHolder.currentApplication();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context systemContextSafely() {
        try {
            // android.app.ActivityThread 是隐藏 API，只能按名字取
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object thread = XposedHelpers.callStaticMethod(
                    activityThreadClass, "currentActivityThread");
            if (thread == null) return null;
            Object context = XposedHelpers.callMethod(thread, "getSystemContext");
            return context instanceof Context ? (Context) context : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 单独一层，保证即使 Xposed 的 AndroidAppHelper 不可用也不会拖垮类加载。 */
    private static final class AndroidAppHelperHolder {
        static Context currentApplication() {
            return android.app.AndroidAppHelper.currentApplication();
        }
    }
}
