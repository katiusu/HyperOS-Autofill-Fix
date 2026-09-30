package com.katiusu.hyperautofillfix.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 事件仓库：模块通过广播把事件送到这里，落盘为 JSON Lines，并向上层暴露 StateFlow。
 * 新的在前（倒序），最多保留 [MAX_EVENTS] 条。
 */
object LogStore {

    private const val FILE_NAME = "events.jsonl"
    const val MAX_EVENTS = 3000

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "haf-log-io").apply { isDaemon = true }
    }
    private val idGen = AtomicLong(0)

    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    @Volatile
    private var file: File? = null

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        file = File(context.filesDir, FILE_NAME)
        io.execute { loadFromDisk() }
    }

    private fun loadFromDisk() {
        val f = file ?: return
        val list = ArrayList<LogEvent>()
        try {
            if (f.exists()) {
                f.forEachLine { line ->
                    if (line.isNotBlank()) {
                        LogEvent.fromJson(line, idGen.incrementAndGet())?.let { list.add(it) }
                    }
                }
            }
        } catch (_: Throwable) {
            // 文件损坏时忽略，重新开始
        }
        list.sortByDescending { it.time }
        _events.value = list.take(MAX_EVENTS)
    }

    /** 广播里拿到的 JSON 事件。 */
    fun append(json: String) {
        val event = LogEvent.fromJson(json, idGen.incrementAndGet()) ?: return
        io.execute {
            val current = _events.value
            val next = ArrayList<LogEvent>(current.size + 1).apply {
                add(event)
                addAll(current)
            }
            val trimmed = if (next.size > MAX_EVENTS) {
                next.subList(0, MAX_EVENTS).toList().also { rewriteFile(it) }
            } else {
                appendToFile(event)
                next
            }
            _events.value = trimmed
        }
    }

    fun clear() {
        io.execute {
            _events.value = emptyList()
            try {
                file?.delete()
            } catch (_: Throwable) {
            }
        }
    }

    private fun appendToFile(event: LogEvent) {
        val f = file ?: return
        try {
            synchronized(JSON_LINE_LOCK) {
                f.appendText(event.toJson() + "\n")
            }
        } catch (_: Throwable) {
        }
    }

    private fun rewriteFile(list: List<LogEvent>) {
        val f = file ?: return
        try {
            synchronized(JSON_LINE_LOCK) {
                f.writeText(buildString {
                    list.asReversed().forEach { append(it.toJson()).append('\n') }
                })
            }
        } catch (_: Throwable) {
        }
    }

    private val JSON_LINE_LOCK = Any()
}

private fun LogEvent.toJson(): String = org.json.JSONObject().apply {
    put("t", time)
    put("lvl", level.key)
    put("pkg", pkg)
    put("hook", hook)
    value?.let { put("val", it) }
    put("raw", raw)
    put("blocked", blocked)
    put("allowed", allowed)
}.toString()
