package com.beecount.autopatch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import de.robv.android.xposed.XposedBridge
import java.io.File
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

        /** 分类名单文件大小上限（1 MiB）。 */
        private const val MAX_CATEGORY_FILE_BYTES = 1L shl 20

        /** 生成一个实现了目标进程 `IAppAdapter` 接口的代理实例。 */
        fun create(classLoader: ClassLoader): Any {
            val iface = classLoader.loadClass("net.ankio.auto.adapter.IAppAdapter")
            return Proxy.newProxyInstance(classLoader, arrayOf(iface), Handler(classLoader))
        }
    }

    private class Handler(private val cl: ClassLoader) : InvocationHandler {

        @Volatile
        private var resolvedPkg: String? = null

        /** 分类名单缓存：避免每笔账单都读一次文件。文件 mtime 变化时自动失效。 */
        @Volatile
        private var categoryCache: Set<String>? = null

        @Volatile
        private var categoryCacheStamp: Long = Long.MIN_VALUE

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

                val rawCate = (get(model, "getCateName") as? String).orEmpty()
                val rawNote = (get(model, "getRemark") as? String)?.takeIf { it.isNotEmpty() }

                // 转账没有分类；只有收支账单才做「找不到 → 其他」的兜底。
                val resolution = if (type == "transfer") {
                    null
                } else {
                    BillMapper.resolveCategory(rawCate, loadKnownCategories())
                }
                val note = if (resolution?.fallbackUsed == true) {
                    BillMapper.mergeNoteWithOriginalCategory(rawNote, resolution.original)
                } else {
                    rawNote
                }

                val bill = BillMapper.Bill(
                    amount = amount,
                    type = type,
                    category = resolution?.category,
                    note = note,
                    account = (get(model, "getAccountNameFrom") as? String)?.takeIf { it.isNotEmpty() },
                    toAccount = (get(model, "getAccountNameTo") as? String)?.takeIf { it.isNotEmpty() },
                    tags = tags,
                    timeMillis = (get(model, "getTime") as? Number)?.toLong() ?: 0L,
                )
                val uri = BillMapper.buildUri(bill)
                RemoteLog.log(
                    application(),
                    "syncBill: type=$typeName money=$amount cate=$rawCate " +
                        "resolved=${resolution?.category} fallback=${resolution?.fallbackUsed == true} uri=$uri",
                )
                if (resolution?.fallbackUsed == true) {
                    RemoteLog.log(
                        application(),
                        "分类「$rawCate」不在蜜蜂记账分类名单内（或为空），已回退为「${resolution.category}」，" +
                            "原分类已写进备注",
                    )
                } else if (resolution != null && resolution.category != resolution.original) {
                    // 子类没建、父类建了：发父类，比直接归到「其他」更贴近原意。
                    RemoteLog.log(application(), "分类「$rawCate」改用名单里的「${resolution.category}」发送")
                }

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

        /**
         * 读取蜜蜂记账分类名单（可选，由用户在模块界面维护）。
         *
         * 文件：`/data/data/net.ankio.auto/files/autopatch_categories.txt`，每行一个分类名。
         * - 文件不存在/为空 → 返回 null，表示名单未知：保持原分类发送，只有空分类才兜底为「其他」。
         * - 文件存在 → 返回名单：命中的分类按蜜蜂记账里的写法发送，没命中的（含空分类）兜底为「其他」，
         *   原始分类写进备注，避免丢信息。
         *
         * 带 mtime 缓存，账单连续写入时不会反复读文件。
         */
        private fun loadKnownCategories(): Set<String>? = try {
            val ctx = application()
            val f = File(CategoryStore.path(ctx?.filesDir?.absolutePath))
            // 名单异常大（用户手写坏了）时直接当未知处理，别把目标进程读崩。
            val usable = f.isFile && f.length() in 1..MAX_CATEGORY_FILE_BYTES
            val stamp = if (usable) f.lastModified() else -1L
            if (stamp != categoryCacheStamp) {
                val parsed = if (usable) CategoryStore.parse(f.readText()) else emptySet()
                categoryCache = parsed.ifEmpty { null }
                categoryCacheStamp = stamp
            }
            categoryCache
        } catch (_: Throwable) {
            null
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