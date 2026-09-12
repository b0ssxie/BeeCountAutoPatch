package com.beecount.autopatch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 运行时实现 AutoAccounting 的 `net.ankio.auto.adapter.IAppAdapter`。
 *
 * 通过 [Proxy] 动态代理 + 反射读取账单模型，因此本模块对 AutoAccounting 零编译期依赖。
 */
class BeeCountAdapter private constructor() {

    companion object {
        /** 蜜蜂记账 prod/release 包名 */
        const val DEFAULT_PKG = "com.tntlikely.beecount"

        /**
         * 兼容不同构建（见 BeeCount android/app/build.gradle）：
         * prod 无后缀；dev 风味加 `.dev`；debug 构建再加 `.debug`。
         */
        val CANDIDATES = listOf(
            "com.tntlikely.beecount",
            "com.tntlikely.beecount.dev",
            "com.tntlikely.beecount.debug",
        )

        const val NAME = "蜜蜂记账"

        /** 生成一个实现了目标进程 `IAppAdapter` 接口的代理实例。 */
        fun create(classLoader: ClassLoader): Any {
            val iface = classLoader.loadClass("net.ankio.auto.adapter.IAppAdapter")
            return Proxy.newProxyInstance(classLoader, arrayOf(iface), Handler(classLoader))
        }
    }

    private class Handler(private val cl: ClassLoader) : InvocationHandler {

        @Volatile
        private var resolvedPkg: String? = null

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "getPkg" -> resolvePkg()
                "getName" -> name()
                "getLink" -> "https://github.com/TNT-Likely/BeeCount"
                "getIcon" -> ""
                "getDesc" -> "本地优先的开源记账应用；经 beecount://add 深链写入。"
                "supportSyncAssets" -> false
                "features" -> allFeatures()
                "sleep" -> 0L
                "syncAssets", "syncWaitBills" -> null
                "syncBill" -> {
                    args?.getOrNull(0)?.let { syncBill(it) }
                    null
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "BeeCountAdapter(proxy)"
                else -> null
            }
        }

        /** 解析用户实际安装的蜜蜂记账包名（prod/dev/debug），解析结果缓存，避免 UI 抖动。 */
        private fun resolvePkg(): String {
            resolvedPkg?.let { return it }
            val pm = application()?.packageManager
            if (pm != null) {
                for (candidate in CANDIDATES) {
                    if (isInstalled(pm, candidate)) {
                        resolvedPkg = candidate
                        RemoteLog.log(application(), "命中已安装包名: $candidate")
                        return candidate
                    }
                }
            }
            return DEFAULT_PKG
        }

        private fun name(): String = if (resolvePkg() == DEFAULT_PKG) NAME else "$NAME（测试版）"

        @Suppress("DEPRECATION")
        private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }

        /** 全量特性，避免 AutoAccounting 因能力判断裁掉账户/分类等字段。 */
        private fun allFeatures(): Any = try {
            val enumClass = cl.loadClass("net.ankio.auto.constant.BookFeatures")
            val constants: Array<out Any> = enumClass.enumConstants ?: emptyArray()
            ArrayList(constants.toList())
        } catch (t: Throwable) {
            ArrayList<Any>()
        }

        private fun syncBill(model: Any) {
            try {
                val amount = (get(model, "getMoney") as? Number)?.toDouble() ?: return
                val typeName = (get(model, "getType") as? Enum<*>)?.name ?: "Expend"
                val type = BillMapper.mapType(typeName)
                val tags = (get(model, "getTags") as? String)
                    .orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val bill = BillMapper.Bill(
                    amount = amount,
                    type = type,
                    category = BillMapper.categoryOf((get(model, "getCateName") as? String).orEmpty()),
                    note = (get(model, "getRemark") as? String)?.takeIf { it.isNotEmpty() },
                    account = (get(model, "getAccountNameFrom") as? String)?.takeIf { it.isNotEmpty() },
                    toAccount = (get(model, "getAccountNameTo") as? String)?.takeIf { it.isNotEmpty() },
                    tags = tags,
                    timeMillis = (get(model, "getTime") as? Number)?.toLong() ?: 0L,
                )
                val uri = BillMapper.buildUri(bill)
                RemoteLog.log(
                    application(),
                    "syncBill: type=$typeName money=$amount cate=${get(model, "getCateName")} uri=$uri",
                )

                val context = application()
                if (context == null) {
                    XposedBridge.log("[BeeCountAutoPatch] 无可用 Context，无法记账")
                    return
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                val resolvable = context.packageManager.resolveActivity(intent, 0) != null
                RemoteLog.log(application(), "beecount 可处理该 Intent: $resolvable")
                context.startActivity(intent)
                RemoteLog.log(application(), "已发送账单深链")
                markSynced(model)
            } catch (t: Throwable) {
                RemoteLog.log(application(), "syncBill 失败: ${android.util.Log.getStackTraceString(t)}")
            }
        }

        /** 通知 AutoAccounting 该账单已同步，避免停留在待同步状态。 */
        private fun markSynced(model: Any) {
            try {
                val managerClass = cl.loadClass("net.ankio.auto.adapter.AppAdapterManager")
                val instance = managerClass.getField("INSTANCE").get(null)
                val method = managerClass.methods.firstOrNull {
                    it.name == "markSynced" && it.parameterTypes.size == 1
                } ?: return
                method.invoke(instance, model)
            } catch (t: Throwable) {
                RemoteLog.log(application(), "markSynced 失败: $t")
            }
        }

        private fun application(): Context? {
            try {
                val app = cl.loadClass("net.ankio.auto.AppKt").getMethod("getAutoApp").invoke(null)
                if (app is Context) return app
            } catch (_: Throwable) {
                // 回退到 Xposed 的 AndroidAppHelper
            }
            return try {
                cl.loadClass("android.app.AndroidAppHelper")
                    .getMethod("currentApplication")
                    .invoke(null) as? Context
            } catch (_: Throwable) {
                null
            }
        }

        private fun get(target: Any, getter: String): Any? = try {
            target.javaClass.getMethod(getter).invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
