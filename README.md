# BeeCountAutoPatch

把「自动记账」（AutoAccounting）采集到的账单，自动写进「蜜蜂记账」（BeeCount）的 LSPosed 模块。

它**不改 AutoAccounting 的源码**，而是在运行时 hook 它的记账应用适配层，把「蜜蜂记账」作为可选的目标记账应用注入进去。

当前版本：**1.4.0**

## 它能做什么

- 让自动记账的「记账应用」列表里多出一个「蜜蜂记账」，选中后每一笔账单自动写入蜜蜂记账。
- **分类对不上不丢单**：蜜蜂记账里没有的分类自动归到「其他」，原分类写进备注（见[分类兜底](#分类兜底找不到--其他)）。
- 不修改上游两个 App 的任何文件，纯运行时注入；不用的时候在 LSPosed 里关掉即可。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 系统 | Android 10+（模块 minSdk 29） |
| 框架 | 已 Root，并安装**支持新版 libxposed API 的 LSPosed**（本模块 `minApiVersion=101` / `targetApiVersion=102`；不支持现代 API 的旧框架看不到本模块） |
| 自动记账 | AutoAccounting **4.0.x**（4.0 起才有这套适配器体系） |
| 蜜蜂记账 | 需含 `beecount://add` 深链的版本 |

## 下载与安装

到 [Releases](https://github.com/b0ssxie/BeeCountAutoPatch/releases) 页面下载 APK：

| 文件 | 说明 |
| --- | --- |
| `BeeCountAutoPatch-v*-release.apk` | **推荐**。开了 R8 代码压缩 + 资源压缩，体积约 1 MB |
| `BeeCountAutoPatch-v*-debug.apk` | 不混淆，用来排查「是不是混淆导致的问题」 |

也可以从 [Actions](https://github.com/b0ssxie/BeeCountAutoPatch/actions) 里下载某次构建的产物。

安装步骤：

1. 装好上表的 APK。
2. 在 LSPosed 里启用本模块。作用域已由模块自己声明为「自动记账」（`net.ankio.auto`），**不需要手工勾选**。
3. **强制停止一次自动记账**（或重启手机），让模块生效。
4. 打开自动记账 →「设置 → 记账设置 → **记账应用**」→ 选择「**蜜蜂记账**」。
5. 正常触发一笔账单，确认已经记进蜜蜂记账。
6. （可选，但建议）回本模块界面填「蜜蜂记账分类名单」，见下一节。

> ⚠️ 自动记账默认的记账应用是「钱迹」。**没做第 4 步的话，账单只会记进自动记账自己，不会同步到蜜蜂记账。**

> 从 1.2.x 升级到 1.3.0 起，模块从旧版 Xposed API 换成了新版 libxposed API：作用域改由模块声明，升级后建议去 LSPosed 确认模块已启用，并强制停止一次自动记账。

## 分类兜底（找不到 → 其他）

蜜蜂记账的深链按分类**名称**精确匹配，而且**不会自动创建分类**；名字对不上会**拒绝整笔记账**（不是只丢分类，是整笔没记上）。

补丁跑在自动记账的进程里，看不到蜜蜂记账的分类表，所以需要你在模块界面告诉它「蜜蜂记账里有哪些分类」：

1. 先在蜜蜂记账里建一个名为 **其他** 的分类——兜底目标本身也必须存在，否则一样会被拒。
2. 打开本模块，在「蜜蜂记账分类名单」里**每行填一个蜜蜂记账已有的分类名**，点「保存名单」（首次会弹 root 授权）。
3. 之后每笔账单按 **子类 → 父类 → 其他** 依次匹配：

   | 情况 | 结果 |
   | --- | --- |
   | 分类名命中名单 | 按名单里的写法发送（忽略大小写与首尾空格） |
   | 子类没建、父类建了 | 发父类，例如 `餐饮-夜宵` 发成 `餐饮` |
   | 都没命中，或分类为空 | 改成「其他」，原分类写进备注（例如 `原分类:夜宵 \| 小龙虾`） |

名单文件：`/data/data/net.ankio.auto/files/autopatch_categories.txt`（每行一个分类名，也可以用 root / adb 直接维护）。

> **名单为空时不做兜底**：无法判断分类是否存在，就按原分类照发（和旧版一致）。此时对不上的账单仍会被蜜蜂记账拒绝。

## 日志

模块日志直接进 **LSPosed 自带的日志页**，不写文件、不需要 root：

1. 打开 LSPosed →「日志」；
2. 搜 `[BeeCountAutoPatch]`。

能看到：hook 是否生效、适配器有没有注入、解析到的蜜蜂记账包名、每次写入的类型 / 金额 / 分类 / 生成的 URI、Intent 能不能被处理。

> v1.2.x 曾把日志写进自动记账的私有目录、再由模块 App 读，那条路依赖 root 且经常读不到（表现为「日志没用」）。v1.4.0 起改为直接输出到 LSPosed 日志，模块 App 界面只保留分类名单维护。

## 常见问题

| 现象 | 原因与解决 |
| --- | --- |
| LSPosed 日志里搜不到 `[BeeCountAutoPatch]` | 模块没被加载：LSPosed 里没启用，或框架不支持新版 API（需要 `minApiVersion=101` 及以上） |
| 记账应用列表里看不到「蜜蜂记账」 | 模块没生效，或自动记账不是 4.0.x；改完设置记得强制停止一次自动记账 |
| 点「蜜蜂记账」跳到 GitHub / 显示为灰色 | 包名没匹配上（模块已兼容正式版 / dev / debug 三种包名，仍不行请反馈） |
| 蜜蜂记账提示「未记账：分类「xxx」不存在」 | 该分类在蜜蜂记账里没有。填好分类名单后会自动归到「其他」，见[分类兜底](#分类兜底找不到--其他) |
| 蜜蜂记账提示「未知的操作: add」 | 蜜蜂记账版本过旧，不含该深链 |
| 保存分类名单失败 | 写的是自动记账的私有目录，需要 root 授权；没授权就会保存失败 |

## 已知限制

1. 深链只能记进蜜蜂记账的**当前账本**：不支持指定账本、资产同步、手续费、多币种。
2. 记账时会把蜜蜂记账**切到前台**。
3. 报销 / 借贷 / 还款 / 退款等类型会按收支大类回退为支出 / 收入。
4. 分类**按名称匹配**，不会自动创建分类；对不上的靠「分类兜底」进「其他」。

## 工作原理

- AutoAccounting 采集到账单后，由用户在设置里选定的 `IAppAdapter` 实现把账单写到目标记账软件。
- 本模块 hook `net.ankio.auto.adapter.AppAdapterManager.adapterList()`，在返回的列表末尾追加一个用 `java.lang.reflect.Proxy` 动态实现的 `IAppAdapter`。对 AutoAccounting **零编译期依赖**，全部按类名从目标进程的 ClassLoader 解析。
- 适配器用反射读取账单模型 `BillInfoModel` 的字段，映射成蜜蜂记账深链：

  ```
  beecount://add?amount=..&type=..&category=..&note=..&account=..&to_account=..&tags=..&date=..&silent=1
  ```

  以 `ACTION_VIEW` Intent 拉起蜜蜂记账完成记账，再回调 `AppAdapterManager.markSynced()` 标记已同步。
- 发深链前按「分类名单」做一次兜底（见上文）。

代码结构：

| 文件 | 职责 |
| --- | --- |
| `BeeCountHook` | 模块入口（新版 API 的 `XposedModule` 子类），注入 hook |
| `BeeCountAdapter` | 动态代理实现的 `IAppAdapter`，负责发深链 |
| `BillMapper` | 账单 → 深链映射、分类兜底（纯 JVM，含单测） |
| `CategoryStore` | 分类名单解析（纯 JVM，含单测） |
| `MainActivity` / `CategoryListFile` | 模块界面与分类名单读写 |

模块基于**新版 libxposed API**（LSPosed API 102）编写：入口类写在 `META-INF/xposed/java_init.list`，作用域写在 `META-INF/xposed/scope.list`，配置写在 `META-INF/xposed/module.prop`；不再使用 `assets/xposed_init` 与 `xposedmodule` 之类的旧版 meta-data。

## 构建

推荐用 Android Studio 打开项目根目录直接构建。命令行需要 JDK 17、Android SDK 35、Gradle 8.11.1（仓库暂未包含 Gradle wrapper）：

```bash
# 推荐：release 开了 R8 + 资源压缩，体积最小
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle assembleRelease

# 不混淆的版本，排查「是不是混淆导致的问题」时用
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle assembleDebug

# 单测（BillMapper / CategoryStore）
JAVA_HOME=<jdk17> ANDROID_HOME=<android-sdk> gradle testDebugUnitTest
```

产物：`app/build/outputs/apk/release/app-release.apk`（推荐）、`app/build/outputs/apk/debug/app-debug.apk`。

> release 复用 **debug 签名**（项目没有正式签名密钥）。CI 会缓存这把 keystore，所以各次 CI 构建的包签名一致、可以直接覆盖安装；本地构建用的是你机器上的 `~/.android/debug.keystore`，与 CI 的签名不同，两者混装需要先卸载。

### CI（GitHub Actions）

推送到 GitHub 后由 Actions 自动构建，本地不需要装 JDK / Android SDK：

- 任意分支 push、任意 PR → 构建并上传产物（`BeeCountAutoPatch-release` / `-debug`）与单测报告；
- 推 `v*` 标签 → 自动创建 Release 并附上这两个 APK：

  ```bash
  git tag v1.4.0 && git push origin v1.4.0
  ```

工作流见 `.github/workflows/build.yml`。构建后还会校验 APK 里确实打进了 `META-INF/xposed/*` 元数据，避免出现「能编译、装上去框架却不认模块」的情况。

## 兼容性备注

- 蜜蜂记账包名：正式版 `com.tntlikely.beecount`，dev 风味加 `.dev`，debug 构建再加 `.debug`；模块会自动解析实际安装的那个。
- 本模块依赖 AutoAccounting 的适配器方法名（`adapterList`、`markSynced`）与账单模型字段名（`getMoney` / `getType` / `getCateName` / `getRemark` / `getAccountNameFrom` / `getAccountNameTo` / `getTags` / `getTime`）；上游若改名需要同步调整。
- 新版 API 不再提供 `XposedHelpers`、`XposedBridge.log`、`AndroidAppHelper`：本模块改用 `hook()` 拦截器链、模块自带的 `log()`，并自行 hook `Application#onCreate` 拿 Context。

## 许可与致谢

- 本模块**不包含** [AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting)（GPL-3.0）的任何源码，仅在运行时通过反射 / hook 与其交互；也**未复制** [BeeCount](https://github.com/TNT-Likely/BeeCount)（Business Source License）的代码。
- 使用与分发时请自行遵守上述上游项目的许可条款。