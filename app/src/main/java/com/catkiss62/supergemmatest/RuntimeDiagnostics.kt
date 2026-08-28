package com.catkiss62.supergemmatest

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object RuntimeDiagnostics {
    private const val PREFS_NAME = "runtime_diagnostics"
    private const val KEY_LOAD_PENDING = "load_pending"
    private const val KEY_LOAD_STARTED_AT = "load_started_at"
    private const val KEY_LOAD_BACKEND = "load_backend"
    private const val KEY_LOAD_MODEL = "load_model"
    private const val KEY_LOAD_SIZE = "load_size"

    fun markLoadStarted(context: Context, model: ImportedModelInfo, backend: LocalBackend) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOAD_PENDING, true)
            .putLong(KEY_LOAD_STARTED_AT, System.currentTimeMillis())
            .putString(KEY_LOAD_BACKEND, backend.label)
            .putString(KEY_LOAD_MODEL, model.fileName)
            .putLong(KEY_LOAD_SIZE, model.sizeBytes)
            // commit() is intentional: the marker must reach disk before native initialization.
            .commit()
    }

    fun markLoadFinished(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOAD_PENDING, false)
            .commit()
    }

    fun consumeInterruptedLoad(context: Context): String? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_LOAD_PENDING, false)) return null
        val backend = preferences.getString(KEY_LOAD_BACKEND, "未知") ?: "未知"
        val model = preferences.getString(KEY_LOAD_MODEL, "未知") ?: "未知"
        val size = preferences.getLong(KEY_LOAD_SIZE, -1L)
        val startedAt = preferences.getLong(KEY_LOAD_STARTED_AT, 0L)
        preferences.edit().putBoolean(KEY_LOAD_PENDING, false).commit()

        return buildString {
            appendLine("检测到上一次模型加载没有正常返回，通常表示 LiteRT 原生进程崩溃或被系统终止。")
            appendLine("加载后端：$backend")
            appendLine("模型：$model · ${ModelImporter.humanSize(size)}")
            appendLine("尝试时间戳：$startedAt")
            latestExitSummary(context)?.let {
                appendLine()
                appendLine("Android 上次进程退出记录：")
                append(it)
            }
        }
    }

    private fun latestExitSummary(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exit = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 5).firstOrNull()
        }.getOrNull() ?: return null
        return buildString {
            appendLine("原因：${reasonLabel(exit.reason)}（${exit.reason}）")
            appendLine("说明：${exit.description ?: "无"}")
            appendLine("状态码：${exit.status}")
            appendLine("PSS/RSS：${exit.pss / 1024}MB / ${exit.rss / 1024}MB")
            append("退出时间戳：${exit.timestamp}")
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        1 -> "应用自行退出"
        2 -> "收到系统信号"
        3 -> "内存不足"
        4 -> "Java 崩溃"
        5 -> "原生代码崩溃"
        6 -> "应用无响应"
        7 -> "初始化失败"
        10 -> "用户请求停止"
        13 -> "其他"
        else -> "未知"
    }
}
