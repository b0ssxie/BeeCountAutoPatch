# BeeCountAutoPatch

把「自动记账」（AutoAccounting）采集到的账单，自动写入「蜜蜂记账」（BeeCount）的 LSPosed 模块。

它**不修改 AutoAccounting 源码**，而是在运行时 hook 它的记账软件适配层，把「蜜蜂记账」作为一个可选的目标记账软件注入进去。

当前版本：**1.2.2**

---

## 原理

- AutoAccounting 采集到账单后，会由用户在设置里选定的 `IAppAdapter` 实现把账单写到目标记账软件。
- 本模块 hook `net.ankio.auto.adapter.AppAdapterManager.adapterList()`，在返回的适配器列表末尾追加一个用 `java.lang.reflect.Proxy` 动态实现的 `IAppAdapter`（对 AutoAccounting 零编译期依赖，全部按类名从目标进程 ClassLoader 解析）。
- 该适配器用反射读取账单模型 `BillInfoModel` 的字段，映射为蜜蜂记账的深链：

  ```
  beecount://add?amount=..&type=..&category=..&note=..&account=..&to_account=..&tags=..&date=..&silent=1
  ```

  以 `ACTION_VIEW` Intent 拉起蜜蜂记账完成记账，并回调 `AppAdapterManager.markSynced()` 标记已同步。

代码：`BeeCountHook`（入口/hook）、`BeeCountAdapter`（动态代理）、`BillMapper`（账单→深链映射，纯 JVM，含单测）。

---

## 环境要求

- 已 Root 并安装 LSPosed（或其它兼容旧版 Xposed API 的框架）
- 自动记账（AutoAccounting）**4.0.x**（4.0 起才有这套适配器体系）
- 蜜蜂记账（BeeCount）：需含 `beecount://add` 深链的版本
- 本模块 minSdk 29

---

## 安装与使用

1. 安装本模块 APK。
2. 在 LSPosed 中启用本模块，**作用域勾选「自动记账」**（`net.ankio.auto`）。
3. **重启自动记账进程**（强制停止自动记账，或重启手机），让模块生效。
4. 打开自动记账 →「设置 → 记账设置 → **记账应用**」→ 选择「**蜜蜂记账**」。
5. 之后正常触发一笔账单，即会自动写入蜜蜂记账。

> ⚠️ 自动记账默认的记账应用是「钱迹」。**如果没有执行第 4 步选中「蜜蜂记账」，账单只会记进自动记账自己，不会同步到蜜蜂记账。**

---

## 日志与排查

桌面会出现「**蜜蜂记账自动记账补丁**」图标，点开即可查看日志，支持刷新 / 复制 / 清空 / 自检。

日志记录：hook 是否生效、适配器注入、解析到的蜜蜂记账包名、每次写入的类型/金额/分类/生成的 URI、Intent 是否可被处理等。
同样内容也会镜像到 LSPosed 日志，前缀 `[BeeCountAutoPatch]`。

日志是**从自动记账进程用广播回传到本 App** 的（跨进程，非写文件）。因为本 App 平时没有常驻进程，系统可能把它当作已停止的应用而**直接丢弃广播**；模块已带上 `FLAG_INCLUDE_STOPPED_PACKAGES` 规避。若仍收不到：

1. 点界面里的「**自检**」：能出现「自检」那一行，说明本 App 的接收与写入没问题，问题在跨进程投递；
2. 把本 App 的后台策略设为「**无限制 / 允许自启动**」（国产 ROM 常见的冻结策略会拦住广播）；
3. 也可以把本 App 留在前台，再去自动记账触发一笔账单，看得不得得到日志。

常见现象对照：

| 现象 | 原因 |
| --- | --- |
| 记账应用列表里看不到「蜜蜂记账」 | 模块未生效：LSPosed 未启用、作用域没勾「自动记账」，或自动记账不是 4.0.x |
| 点「蜜蜂记账」跳到 GitHub / 显示为灰色 | 包名未匹配（本模块已兼容正式版/dev/debug 三种包名，若仍不匹配请反馈） |
| 提示「未记账：分类「xxx」不存在」 | 见下方限制第 1 条 |
| 提示「未知的操作: add」 | 蜜蜂记账版本过旧，不含该深链 |

---

## 已知限制

1. **分类必须已存在于蜜蜂记账**（深链按分类**名称**匹配，不会自动创建分类），否则该笔记账会被拒绝。建议让自动记账规则的分类名与蜜蜂记账的分类名一致。
2. 深链只能记入蜜蜂记账的**当前账本**：不支持指定账本、资产同步、手续费、多币种。
3. 记账时会把蜜蜂记账**切到前台**。
4. 报销 / 借贷 / 还款 / 退款等类型会按收支大类回退为支出 / 收入。

---

## 构建

推荐用 Android Studio 打开项目根目录直接构建。

命令行（需 JDK 17、Android SDK 35、Gradle 8.11.1；仓库暂未包含 Gradle wrapper）：

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle assembleDebug
```

运行单测（`BillMapper` 的映射逻辑）：

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle testDebugUnitTest
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 兼容性备注

- 蜜蜂记账包名：正式版 `com.tntlikely.beecount`，dev 风味 `com.tntlikely.beecount.dev`，debug 构建再加 `.debug`；模块会自动解析实际安装的那个。
- 本模块依赖 AutoAccounting 的适配器方法名（`adapterList`、`markSynced`）与账单模型字段名（`getMoney`/`getType`/`getCateName`/`getRemark`/`getAccountNameFrom`/`getAccountNameTo`/`getTags`/`getTime`）。上游若改名需要同步调整。

---

## 许可与致谢

- 本模块**不包含** [AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting)（GPL-3.0）的任何源码，仅在运行时通过反射 / hook 与其交互；也**未复制** [BeeCount](https://github.com/TNT-Likely/BeeCount)（Business Source License）的代码。
- 使用与分发时请自行遵守上述上游项目的许可条款。
