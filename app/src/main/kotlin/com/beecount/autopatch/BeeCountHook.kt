package com.beecount.autopatch

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 入口：新版 libxposed API（LSPosed API 102）的模块类。
 *
 * 只作用于自动记账（net.ankio.auto）：hook `AppAdapterManager.adapterList()`，
 * 在返回的适配器列表末尾追加「蜜蜂记账」适配器，从而让 AutoAccounting
 * 无需修改源码即可支持蜜蜂记账。
 *
 * 入口类名写在 `META-INF/xposed/java_init.list`，作用域写在 `scope.list`，
 * 模块配置（minApiVersion/targetApiVersion）写在 `module.prop`。
 */
class BeeCountHook : XposedModule() {

    /** hook 掉 adapterList() 后置位；onPackageLoaded / onPackageReady 都可能来调一次。 */
    @Volatile
    private var adapterListHooked = false

    private val hookLock = Any()

    /** 适配器实例：缓存下来，避免每次取列表都新建一个代理。 */
    @Volatile
    private var adapter: Any? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        // 新版 API 没有全局的 XposedBridge.log，日志出口由模块自己提供给 RemoteLog。
        RemoteLog.frameworkLog = { msg, tr ->
            if (tr == null) log(Log.INFO, TAG, msg) else log(Log.ERROR, TAG, msg, tr)
        }
        log(Log.INFO, TAG, "模块已加载: process=${param.processName}, framework=$frameworkName, api=$apiVersion")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return
        hookAdapterList(param.defaultClassLoader, "onPackageLoaded")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return
        // 这个时机正好在 Application 创建之前，适合拿 Context。
        hookApplication()
        // 自定义 AppComponentFactory 会让类加载器与 onPackageLoaded 时不同，这里再兜一次（幂等）。
        hookAdapterList(param.classLoader, "onPackageReady")
    }

    /**
     * 抓一份 Application 实例当 Context 用。
     *
     * 后面写日志、发深链都需要 Context，而新版 API 不再提供
     * `AndroidAppHelper.currentApplication()`，所以自己 hook `Application#onCreate`。
     */
    private fun hookApplication() {
        try {
            val onCreate = Application::class.java.getDeclaredMethod("onCreate")
            hook(onCreate)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    (chain.thisObject as? Application)?.let { AppContext.set(it) }
                    chain.proceed()
                }
            log(Log.INFO, TAG, "已 hook Application#onCreate（用于取 Context）")
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "hook Application#onCreate 失败", t)
        }
    }

    /**
     * hook `AppAdapterManager.adapterList()`，在返回值末尾追加蜜蜂记账适配器。
     *
     * 用 [ExceptionMode.PROTECTIVE]：hook 里出错时框架会当作没 hook 继续走原逻辑，
     * 不会把自动记账本身搞崩。
     */
    private fun hookAdapterList(classLoader: ClassLoader, from: String) {
        if (adapterListHooked) return
        synchronized(hookLock) {
            if (adapterListHooked) return
            try {
                val managerClass = classLoader.loadClass("net.ankio.auto.adapter.AppAdapterManager")
                val method = managerClass.declaredMethods.firstOrNull {
                    it.name == "adapterList" && it.parameterCount == 0
                }
                if (method == null) {
                    log(Log.ERROR, TAG, "没找到 AppAdapterManager.adapterList()（$from），自动记账版本可能不兼容")
                    return
                }
                hook(method)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val original = chain.proceed()
                        if (original is List<*>) {
                            // 原返回值可能是 listOf(...) 生成的定长列表，不能直接 add，需新建。
                            val injected = ArrayList<Any?>(original.size + 1)
                            injected.addAll(original)
                            injected.add(adapter ?: BeeCountAdapter.create(classLoader).also {
                                adapter = it
                                log(Log.INFO, TAG, "已注入适配器到 adapterList()")
                            })
                            injected
                        } else {
                            original
                        }
                    }
                adapterListHooked = true
                log(Log.INFO, TAG, "已 hook AppAdapterManager.adapterList()（$from）")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "hook adapterList() 失败（$from）", t)
            }
        }
    }

    private companion object {
        const val TAG = "[BeeCountAutoPatch]"
        const val TARGET_PACKAGE = "net.ankio.auto"
    }
}