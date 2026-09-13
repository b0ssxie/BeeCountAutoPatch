package com.beecount.autopatch

/**
 * 日志出口。
 *
 * 新版 libxposed API 没有全局的 `XposedBridge.log`，模块的 `log()` 会把内容写进
 * LSPosed 自带的日志页（LSPosed → 日志，搜 `[BeeCountAutoPatch]`）。
 *
 * [BeeCountHook] 在 `onModuleLoaded` 时把出口注入到 [frameworkLog]，
 * [BeeCountAdapter] 这类没持有模块实例的地方就通过它打日志。
 *
 * 只做转发：不写文件、不读文件、不需要 root。
 */
object RemoteLog {

    /** (消息, 异常或 null) -> Unit，由模块入口注入；模块 App 进程里一直是 null。 */
    @Volatile
    var frameworkLog: ((String, Throwable?) -> Unit)? = null

    fun log(line: String) {
        logToFramework(line, null)
    }

    fun log(line: String, tr: Throwable) {
        logToFramework(line, tr)
    }

    private fun logToFramework(line: String, tr: Throwable?) {
        try {
            frameworkLog?.invoke(line, tr)
        } catch (_: Throwable) {
            // 日志失败不能影响记账主流程
        }
    }
}