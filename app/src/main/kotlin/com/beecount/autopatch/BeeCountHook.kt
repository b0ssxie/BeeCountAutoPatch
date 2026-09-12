package com.beecount.autopatch

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 入口：仅作用于自动记账(AutoAccounting)进程。
 *
 * hook `AppAdapterManager.adapterList()`，在返回的适配器列表末尾追加「蜜蜂记账」适配器，
 * 从而让 AutoAccounting 无需修改源码即可支持蜜蜂记账。
 */
class BeeCountHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        try {
            val managerClass = XposedHelpers.findClass(
                "net.ankio.auto.adapter.AppAdapterManager",
                lpparam.classLoader,
            )
            XposedHelpers.findAndHookMethod(
                managerClass,
                "adapterList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val original = param.result as? List<*> ?: return
                            // 原返回值是 listOf(...) 生成的定长列表，不能直接 add，需新建。
                            val injected = ArrayList<Any?>(original.size + 1)
                            injected.addAll(original)
                            injected.add(BeeCountAdapter.create(lpparam.classLoader))
                            param.result = injected
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                },
            )
            XposedBridge.log("[BeeCountAutoPatch] 已 hook AppAdapterManager.adapterList()")
        } catch (t: Throwable) {
            XposedBridge.log("[BeeCountAutoPatch] hook 失败: $t")
            XposedBridge.log(t)
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "net.ankio.auto"
    }
}
