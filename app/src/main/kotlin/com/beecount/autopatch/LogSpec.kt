package com.beecount.autopatch

/**
 * 日志文件的约定位置：写在自动记账（目标应用）自己的私有目录里。
 *
 * 目标进程写、模块进程读，两端共享同一份路径定义。这个类不含任何 Xposed 依赖，
 * 模块进程加载它不会有问题。
 */
object LogSpec {

    /** 目标应用包名（模块作用域）。 */
    const val TARGET_PKG = "net.ankio.auto"

    const val FILE_NAME = "autopatch.log"

    /** 目标应用私有目录下的日志路径。 */
    fun path(): String = "/data/data/$TARGET_PKG/files/$FILE_NAME"
}
