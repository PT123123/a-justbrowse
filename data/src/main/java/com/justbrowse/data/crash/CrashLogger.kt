package com.justbrowse.data.crash

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闪退取证：把未捕获异常的堆栈 + 崩溃前的操作轨迹落到**应用外部私有目录**。
 *
 * 为什么选这个目录：release 包不是 debuggable，`adb shell run-as` 读不到内部目录，
 * 而 /sdcard/Android/data/<包名>/files/ 用 adb 可以直接读：
 *
 *     adb shell cat /sdcard/Android/data/com.justbrowse.app/files/crash/justbrowse-crash.log
 *
 * 不方便连 adb 时走「设置 → 导出崩溃日志」把它分享出去（FileProvider 见 file_paths.xml）。
 *
 * 局限：只能抓 Java/Kotlin 层崩溃。native 崩溃（例如 WebView 渲染进程被杀 SIGTRAP）
 * 仍需从系统侧 dumpsys dropbox / tombstone 查。
 */
object CrashLogger {

    private const val TAG = "JustBrowseCrash"
    private const val DIR_NAME = "crash"
    private const val FILE_NAME = "justbrowse-crash.log"

    /** 「崩溃前操作」保留条数 */
    private const val BREADCRUMB_LIMIT = 40

    /** 文件体积上限；超出后只保留最新一段（崩溃本身很少，够用） */
    private const val MAX_FILE_BYTES = 256 * 1024
    private const val KEEP_FILE_BYTES = 128 * 1024

    @Volatile
    private var installed = false

    private var appContext: Context? = null

    /** 崩溃前操作轨迹：只留内存，崩溃时才随报告落盘，避免每次操作都写磁盘 */
    private val breadcrumbs = ArrayDeque<String>()
    private val breadcrumbTimeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** 在 Application.onCreate 里调用一次 */
    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 取证失败绝不能影响系统原本的崩溃处理
            runCatching { writeReport(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 记录一条崩溃前的操作轨迹（如页面跳转、调起外部 App） */
    fun breadcrumb(tag: String, detail: String) {
        synchronized(breadcrumbs) {
            if (breadcrumbs.size >= BREADCRUMB_LIMIT) breadcrumbs.removeFirst()
            breadcrumbs.addLast("${breadcrumbTimeFormat.format(Date())} [$tag] $detail")
        }
    }

    /** 崩溃日志文件；没有记录时为 null */
    fun logFile(): File? = appContext?.let { logFile(it) }

    /** 一句话摘要（设置页副标题用）；null 表示还没有崩溃记录 */
    fun summary(): String? {
        val file = logFile() ?: return null
        if (!file.exists() || file.length() == 0L) return null
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
        return "最近一次 $time · ${(file.length() + 1023) / 1024} KB"
    }

    private fun logFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)
            .apply { mkdirs() }
            .resolve(FILE_NAME)

    private fun writeReport(thread: Thread, throwable: Throwable) {
        val context = appContext ?: return
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val report = StringBuilder().apply {
            appendLine("==================== 崩溃 ====================")
            appendLine("时间: ${timeFormat.format(Date())}")
            appendLine("开机时长: ${SystemClock.elapsedRealtime() / 1000}s（可与 logcat / dropbox 时间戳对齐）")
            appendLine("版本: ${versionLabel(context)}")
            appendLine(
                "设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT})"
            )
            appendLine("线程: ${thread.name}")
            appendLine("异常: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("堆栈:")
            appendLine(Log.getStackTraceString(throwable))
            append("崩溃前操作（旧 → 新）:")
            synchronized(breadcrumbs) {
                if (breadcrumbs.isEmpty()) {
                    appendLine(" 无")
                } else {
                    appendLine()
                    breadcrumbs.forEach { appendLine("  $it") }
                }
            }
            appendLine()
        }.toString()

        val file = logFile(context)
        FileOutputStream(file, true).use { out ->
            out.write(report.toByteArray())
            // 崩溃路径必须真的落盘：进程马上就会被系统杀掉
            out.flush()
            out.fd.sync()
        }
        trim(file)

        Log.e(TAG, "闪退日志已写入 $file")
        Log.e(TAG, "${throwable.javaClass.name}: ${throwable.message}", throwable)
    }

    /** 文件超限时只保留末尾一段，避免无限增长 */
    private fun trim(file: File) {
        if (file.length() <= MAX_FILE_BYTES) return
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOfRange(bytes.size - KEEP_FILE_BYTES, bytes.size))
    }

    @Suppress("DEPRECATION")
    private fun versionLabel(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        "${info.versionName}(versionCode ${info.versionCode}) ${if (debug) "debug" else "release"}"
    }.getOrDefault("未知")
}