package com.katiusu.hyperautofillfix.data

import org.json.JSONObject

/** 日志级别。 */
enum class LogLevel(val key: String) {
    BLOCK("block"),
    ALLOW("allow"),
    INFO("info");

    companion object {
        fun of(key: String?): LogLevel = when (key) {
            "block" -> BLOCK
            "allow" -> ALLOW
            else -> INFO
        }
    }
}

/**
 * 一条拦截/放行事件。
 *
 * [raw] 是模块写进 logcat 的原始行（时间 + pid + 进程 + tag），
 * 界面里的「原始」视图直接展示它，「直观」视图则用 [hook] / [value] / [pkg] 重新排版。
 */
data class LogEvent(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val pkg: String,
    val hook: String,
    val value: String?,
    val raw: String,
    val blocked: Long,
    val allowed: Long,
) {
    companion object {
        fun fromJson(json: String, id: Long): LogEvent? = try {
            val o = JSONObject(json)
            LogEvent(
                id = id,
                time = o.optLong("t", System.currentTimeMillis()),
                level = LogLevel.of(o.optString("lvl")),
                pkg = o.optString("pkg", "unknown"),
                hook = o.optString("hook", "-"),
                value = if (o.has("val")) o.optString("val") else null,
                raw = o.optString("raw", ""),
                blocked = o.optLong("blocked", 0),
                allowed = o.optLong("allowed", 0),
            )
        } catch (t: Throwable) {
            null
        }
    }
}
