# BeeCountAutoPatch

把「自动记账」（AutoAccounting）采集到的账单，自动写入「蜜蜂记账」（BeeCount）的 LSPosed 模块。

它**不修改 AutoAccounting 源码**，而是在运行时 hook 它的记账软件适配层，把「蜜蜂记账」作为一个可选的目标记账软件注入进去。

当前版本：**1.2.4**

---

## 原理

- AutoAccounting 采集到账单后，会由用户在设置里选定的 `IAppAdapter` 实现把账单写到目标记账软件。
- 本模块 hook `net.ankio.auto.adapter.AppAdapterManager.adapterList()`，在返回的适配器列表末尾追加一个用 `java.lang.reflect.Proxy` 动态实现的 `IAppAdapter`（对 AutoAccounting 零编译期依赖，全部按类名从目标进程 ClassLoader 解析）。
- 该适配器用反射读取账单模型 `BillInfoModel` 的字段，映射为蜜蜂记账的深链：

  ```
  beecount://add?amount=..&type=..&category=..&note=..&account=..&to_account=..&tags=..&date=..&silent=1
  ```

  以 `ACTION_VIEW` Intent 拉起蜜蜂记账完成记账，并回调 `AppAdapterManager.markSynced()` 标记已同步。
- 发深链前会按「分类名单」做一次兜底：对不上的分类改成「其他」，原分类写进备注（见「分类兜底」一节）。

代码：`BeeCountHook`（入口/hook）、`BeeCountAdapter`（动态代理）、`BillMapper`（账单→深链映射与分类兜底，纯 JVM，含单测）、`CategoryStore`（分类名单解析，纯 JVM，含单测）。

---

## 环境要求

- 已 Root 并安装 LSPosed（或其它兼容旧版 Xposed API 的框架）
- 自动记账（AutoAccounting）**4.0.x**（4.0 起才有这套适配器体系）
- 蜜蜂记账（BeeCount）：需含 `beecount://add` 深链的版本
- 本模块 minSdk 29

> 保存「分类名单」要写自动记账的私有目录，需要 root 授权（与读日志同一套机制）；不填名单则完全不需要 root。

---

## 安装与使用

1. 安装本模块 APK。
2. 在 LSPosed 中启用本模块，**作用域勾选「自动记账」**（`net.ankio.auto`）。
3. **重启自动记账进程**（强制停止自动记账，或重启手机），让模块生效。
4. 打开自动记账 →「设置 → 记账设置 → **记账应用**」→ 选择「**蜜蜂记账**」。
5. 之后正常触发一笔账单，即会自动写入蜜蜂记账。
6. （可选）回本模块界面填「蜜蜂记账分类名单」，让对不上的分类自动归到「其他」，见下一节。

> ⚠️ 自动记账默认的记账应用是「钱迹」。**如果没有执行第 4 步选中「蜜蜂记账」，账单只会记进自动记账自己，不会同步到蜜蜂记账。**

---

## 分类兜底（找不到 → 其他）

蜜蜂记账的深链按分类**名称**匹配，对不上会**拒绝整笔记账**。本模块可以在发深链前把对不上的分类改成「其他」，避免丢单：

1. 先在蜜蜂记账里建一个名为 **其他** 的分类——兜底分类本身也必须存在，否则同样会被拒绝。
2. 打开本模块界面，在「蜜蜂记账分类名单」里**每行填一个蜜蜂记账已有的分类名**，点「保存名单」（文件写在自动记账私有目录，首次会弹 root 授权）。
3. 之后触发账单时按 **子类 → 父类 → 其他** 依次匹配：
   - 命中名单 → 按名单里的写法发送（忽略大小写与首尾空格）；
   - 子类没建、但父类建了 → 发父类（例如 `餐饮-夜宵` 发成 `餐饮`），避免整笔被拒；
   - 都没命中、或分类为空 → 改成「其他」，并把**原分类写进备注**（例如 `原分类:夜宵 | 小龙虾`）。

名单文件：`/data/data/net.ankio.auto/files/autopatch_categories.txt`（每行一个分类名，也可以直接用 root / adb 维护）。

> 名单为空时无法判断分类是否存在，此时**不做兜底**，仍按原分类发送（与旧版一致），避免把本来能记的分类误判成「其他」。

## 日志与排查

桌面会出现「**蜜蜂记账自动记账补丁**」图标，点开即可查看日志，支持刷新 / 复制 / 清空。

日志记录：hook 是否生效、适配器注入、解析到的蜜蜂记账包名、每次写入的类型/金额/分类/生成的 URI、Intent 是否可被处理等。
同样内容也会镜像到 LSPosed 日志，前缀 `[BeeCountAutoPatch]`。

日志是**由模块在自动记账进程内直接写进自动记账自己的私有目录**（`/data/data/net.ankio.auto/files/autopatch.log`），所以只要 hook 跑得到就一定落盘，**不依赖本 App 是否在运行**。本 App 打开时再读这个文件：

1. 优先**直接读**：写入时已放宽该文件与其目录的权限，多数机型不需要 root；
2. 直读不成则走 **root**（`su -c cat`），首次会弹一次授权框；
3. 都不行时状态栏会提示「读不到」，可用支持 root 的文件管理器直接看上面那个路径。

> v1.2.2 及更早版本是把日志用广播回传给本 App 的。本 App 平时没有进程、会被系统当成已停止/冻结的应用，广播被 AMS 直接丢弃（`Failed to broadcast to stopped app`），所以日志看不到；v1.2.3 起改成写文件。

常见现象对照：

| 现象 | 原因 |
| --- | --- |
| 记账应用列表里看不到「蜜蜂记账」 | 模块未生效：LSPosed 未启用、作用域没勾「自动记账」，或自动记账不是 4.0.x |
| 点「蜜蜂记账」跳到 GitHub / 显示为灰色 | 包名未匹配（本模块已兼容正式版/dev/debug 三种包名，若仍不匹配请反馈） |
| 提示「未记账：分类「xxx」不存在」 | 该分类在蜜蜂记账里没有。填好本模块的分类名单后会自动归到「其他」，见「分类兜底」一节 |
| 提示「未知的操作: add」 | 蜜蜂记账版本过旧，不含该深链 |

---

## 已知限制

1. 蜜蜂记账的深链**按分类名称匹配，不会自动创建分类**。可以在本模块界面填「分类名单」，让对不上的分类自动归到「其他」（见「分类兜底」一节）；不填名单时行为与旧版一致，对不上就会被蜜蜂记账拒绝。
2. 深链只能记入蜜蜂记账的**当前账本**：不支持指定账本、资产同步、手续费、多币种。
3. 记账时会把蜜蜂记账**切到前台**。
4. 报销 / 借贷 / 还款 / 退款等类型会按收支大类回退为支出 / 收入。

---

## 构建

推荐用 Android Studio 打开项目根目录直接构建。

命令行（需 JDK 17、Android SDK 35、Gradle 8.11.1；仓库暂未包含 Gradle wrapper）：

```bash
# 推荐：release 开了 R8 代码压缩 + 资源压缩，体积最小
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle assembleRelease

# 不混淆的版本，排查「是不是混淆导致的问题」时用
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle assembleDebug
```

运行单测（`BillMapper` / `CategoryStore` 的逻辑）：

```bash
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle testDebugUnitTest
```

产物：

- `app/build/outputs/apk/release/app-release.apk` —— 推荐安装，体积最小
- `app/build/outputs/apk/debug/app-debug.apk`

> release 复用 **debug 签名**（项目没有正式签名密钥）。CI 会缓存这把 keystore，所以各次构建出来的包签名一致、可以直接覆盖安装；本地构建用的是你机器上的 `~/.android/debug.keystore`，与 CI 的签名不同，两者混装需要先卸载。

### CI（GitHub Actions）

推送到 GitHub 后由 Actions 自动构建，本地不需要装 JDK / Android SDK：

- 任意分支 push、任意 PR → 在仓库 **Actions** 页面下载产物：`BeeCountAutoPatch-release`（推荐）或 `BeeCountAutoPatch-debug`，另有单测报告；
- 推 `v*` 标签 → 自动创建 Release 并附上 APK：

  ```bash
  git tag v1.2.4 && git push origin v1.2.4
  ```

CI 会缓存 debug 签名密钥，因此各次构建的包可以覆盖安装升级；缓存被清理后签名会变，需要先卸载旧版本。工作流见 `.github/workflows/build.yml`。

---

## 兼容性备注

- 蜜蜂记账包名：正式版 `com.tntlikely.beecount`，dev 风味 `com.tntlikely.beecount.dev`，debug 构建再加 `.debug`；模块会自动解析实际安装的那个。
- 本模块依赖 AutoAccounting 的适配器方法名（`adapterList`、`markSynced`）与账单模型字段名（`getMoney`/`getType`/`getCateName`/`getRemark`/`getAccountNameFrom`/`getAccountNameTo`/`getTags`/`getTime`）。上游若改名需要同步调整。

---

## 许可与致谢

- 本模块**不包含** [AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting)（GPL-3.0）的任何源码，仅在运行时通过反射 / hook 与其交互；也**未复制** [BeeCount](https://github.com/TNT-Likely/BeeCount)（Business Source License）的代码。
- 使用与分发时请自行遵守上述上游项目的许可条款。
