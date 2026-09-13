package com.beecount.autopatch

import android.app.Application
import android.content.Context

/**
 * 目标应用（自动记账）的 Application 实例。
 *
 * 新版 libxposed API（LSPosed API 102）不再提供 `AndroidAppHelper.currentApplication()`，
 * 所以模块在 `onPackageReady` 里 hook 住 `Application#onCreate`，把实例存下来，
 * 后面写日志、发深链都从这里取 Context。
 *
 * 纯 JVM 侧无依赖，模块 App 进程加载它也没问题（在那边永远是 null）。
 */
object AppContext {

    @Volatile
    private var application: Application? = null

    fun set(app: Application) {
        application = app
    }

    fun get(): Context? = application
}