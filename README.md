# HyperOS Autofill Fix（小米 HyperOS 自动填充修复模块）

![LSPosed Module](https://img.shields.io/badge/LSPosed-Module-brightgreen.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-blue.svg)
![Version](https://img.shields.io/badge/version-2.1.0-orange.svg)
![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)

一个用于小米 **HyperOS / MIUI** 的 LSPosed / Xposed 模块，解决系统安全组件反复重置、清空、
覆盖第三方自动填充服务（Bitwarden、KeePass、1Password 等）的问题。**2.0.0 起自带一个 Miuix
（HyperOS 设计语言）界面**，可以直接在手机上看状态、看拦截日志、调设置；
**2.1.0 起底栏换成悬浮的液态玻璃条**。

---

## 📌 问题背景

在搭载 HyperOS 的设备上，小米安全组件（`com.miui.securitycenter`）经常会在后台把用户的第三方
密码管理器 / 自动填充服务（Autofill Service）清空，或强制还原成小米自家的填充服务，导致用户需要
反复回到设置里手动改回来。

本模块通过对 **系统框架 + 设置存储** 的多层 Hook 拦截，精准阻断非用户本意的重置行为，同时对
调用方伪造成「写入成功」，避免系统管家反复重试。

---

## ✨ 功能亮点

- **四层拦截**：比 1.0.0 多一层，补上了旧式 `ContentProvider` 写入通道；
- **参数自适应**：不写死参数下标，兼容 AOSP / MIUI 不同版本的方法重载；
- **自带界面**：概览 / 日志 / 设置三页，Miuix + HyperOS 观感，可滑动切换；
- **悬浮液态玻璃底栏**：2.1.0 起用 `FloatingNavigationBar`（圆角 + 阴影）配合 `miuix-blur`，
  页面内容从玻璃后面穿过；API < 33 自动退回不透明底栏；
- **直观 + 原文双视图**：日志既能看排版后的中文说明，也能看与 `adb logcat` 完全一致的原行；
- **完整日志链路**：被 Hook 进程通过显式广播把事件送到 App，本地落盘（最多 3000 条），
  支持搜索、级别筛选、复制、清空；
- **主题**：跟随系统 / 浅色 / 深色 / 动态取色（Monet，Android 12+）。

完整历史见 [CHANGELOG.md](CHANGELOG.md)。

---

## 🛠️ 工作原理与核心特性

### 四层拦截

| 层 | Hook 点 | 说明 |
| :--- | :--- | :--- |
| 1 | `android.provider.Settings$Secure.putString` / `putStringForUser` | 写入方进程内直接拦截 |
| 2 | `com.android.providers.settings.SettingsProvider.call("PUT_secure", …)` | 所有进程写入设置的汇聚点 |
| 3 | `SettingsState.insertSettingLocked(…)` | 设置存储最底层兜底，覆盖绕过 `call` 的直写 |
| 4 | `SettingsProvider.insert` / `update` | 旧式 ContentProvider 通道 |

### 判定规则（命中任一即丢弃本次写入）

1. 值为空 / `null` / `0`（清空偏好）；
2. 值不是合法的 `包名/类名`（`autofill_service` 必须含 `/`，不含一律视为脏数据）；
3. 值指向小米自家组件（`com.miui.*` / `com.xiaomi.*`）。

命中时对调用方**伪造成写入成功**（`Settings.Secure` 返回 `true`、`call` 返回空 `Bundle`），
但设置数据库保持不变，因此不会触发系统管家的重试风暴。

### 界面里的日志链路

模块运行在 system_server / 设置存储 / 安全中心等被 Hook 的进程里，界面在自己的进程里，两者无法
共享内存或私有文件，所以事件走**显式广播**：

```
Hook 命中 ──► ModuleLog ──┬─► XposedBridge.log（logcat，标签 HyperOSAutofillFix）
                         └─► sendBroadcast(ACTION_LOG, setPackage(本应用))
                                  └─► LogReceiver ──► LogStore（JSON Lines，最多 3000 条）
                                          └─► StateFlow ──► Compose 界面
```

- 广播显式指定包名，不受 Android 8.0+ 隐式广播限制影响，也不会被其它应用收到；
- 同一「层次 + 值」1 秒内只广播一次，避免系统管家重试时反复唤醒界面进程；
- 所有事件**只在本机落盘**，不联网、不上传。

---

## 📱 界面

| 页面 | 内容 |
| :--- | :--- |
| **概览** | 当前自动填充服务（异常时整卡切到错误配色）、拦截 / 放行 / 最近一条统计、模块已载入的进程、四层拦截说明、直达「密码与账户」的按钮 |
| **日志** | 搜索（进程 / 层次 / 写入值）、全部 / 拦截 / 放行 / 信息筛选、直观与 logcat 原文双视图、顶栏一键清空 |
| **设置** | 主题下拉、是否记录放行事件、默认视图、复制最近日志、清空日志、关于（含 GitHub 仓库链接）与隐私说明 |

底栏是悬浮的液态玻璃条：内容一直铺到屏幕底部并从玻璃后面穿过，滚动到末端时再让开玻璃条。

---

## 📦 作用域配置（Scope）

在 LSPosed 中勾选以下 **4 个作用域**（模块内置的 `xposed_scope` 通常会自动勾选）：

| 作用域包名 | 组件说明 | 拦截目的 |
| :--- | :--- | :--- |
| **`android`** | 系统框架（System Server） | 阻止系统框架层的自动清空调用 |
| **`com.android.providers.settings`** | 设置存储（Settings Provider） | 阻止底层数据库与内存被改写 |
| **`com.android.settings`** | 系统设置 | 保证设置界面侧的行为一致 |
| **`com.miui.securitycenter`** | 手机管家 / 安全中心 | 阻止安全中心后台清洗策略 |

---

## 🚀 安装与使用

1. **下载 APK**：从 [Releases](https://github.com/katiusu/HyperOS-Autofill-Fix/releases) 下载
   `HyperOS-Autofill-Fix-2.1.0.apk`。
2. **安装**：
   - 如果手机上装的是 **用别的签名** 的旧版（例如 1.0.0），需要先卸载旧版，否则会报
     `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；
   - 装完在 LSPosed 里重新启用一次本模块即可。
3. **启用模块**：打开 LSPosed 管理器 → 模块 → 勾选 **HyperOS Autofill Fix**。
4. **确认作用域**：确认上面 4 个作用域都已勾选。
5. **重启设备**（或重启系统界面 + 设置存储进程）。
6. **设置自动填充**：系统设置 → **语言与输入法 → 密码与账户** → 自动填充服务，
   选择你的第三方密码管理器（App 概览页的「打开密码与账户」按钮可直达这一页）。
7. **验证**：打开「自动填充修复」App，概览页能看到「已载入的进程」，日志页能看到拦截记录。

---

## 🔍 日志与排错

**方式一：App 内（推荐）**

「日志」页的「直观」视图按 `级别 / 时间 / 命中层次 / 写入值 / 来源进程` 排版；
点顶栏图标切到「原文」视图，显示与下面这条命令完全一致的行。

**方式二：logcat**

```bash
adb logcat -s HyperOSAutofillFix
```

- 拦截：`已拦截 [SettingsProvider.call(PUT_secure)] autofill_service = [com.miui.…]`
- 放行：`放行 [Secure.putString] autofill_service = [com.x8bit.bitwarden/…]`
- 模块载入：`信息 [ModuleInit]`

**常见问题**

| 现象 | 处理 |
| :--- | :--- |
| 概览页「已载入的进程」为空 | 模块没生效：确认 LSPosed 已启用、4 个作用域已勾选，然后重启手机 |
| 日志页一直空白 | 同上；也可能是系统管家还没尝试改写（正常用一段时间就会出现） |
| 安装时报签名冲突 | 先卸载旧版（版本 / 签名不同），再安装 |
| 日志页 / 设置页打不开（历史问题） | 2.0.0 已修复，原因是 `androidx.activity` < 1.13.0 缺少 Miuix 需要的导航事件宿主 |
| 底栏没有玻璃效果 | Android 12 及以下没有 `RuntimeShader`，底栏会按设计退回不透明配色 |

---

## 🧩 代码结构

| 文件 | 作用 |
| :--- | :--- |
| `MainHook.java` | Xposed 入口，四层拦截逻辑 |
| `ModuleLog.java` | 模块侧日志出口（logcat + 广播），含 Context 获取与限频 |
| `App.kt` / `LogReceiver.kt` | Application 与广播接收器 |
| `data/LogEvent.kt` / `data/LogStore.kt` | 事件模型与 JSON Lines 落盘仓库 |
| `data/AppPrefs.kt` | 界面设置持久化 |
| `ui/MainActivity.kt` | 只建立主题与界面状态持有者 |
| `ui/HafApp.kt` | 应用外壳：唯一的 `Scaffold` + 三页 `HorizontalPager` + 悬浮液态玻璃底栏（`GlassNavigationBar`） |
| `ui/OverviewScreen.kt` / `ui/LogsScreen.kt` / `ui/SettingsScreen.kt` | 概览 / 日志 / 设置三个页面 |
| `ui/UiPrefsState.kt` | 界面设置的唯一持有者（写回 SharedPreferences） |
| `ui/Theme.kt` | MiuixTheme 封装（跟随系统 / 浅色 / 深色 / 动态取色） |

---

## 🔧 实现要点

- **参数自适应扫描**：不依赖固定下标，兼容 AOSP / MIUI 不同版本的参数顺序与重载；
- **返回值类型安全**：`insertSettingLocked` 返回 `String`（旧值），不再返回 `Boolean`，
  避免调用方 `ClassCastException`；
- **热路径快速失败**：未命中时只做一次字符串比较即返回，零额外分配；
- **日志限频去重**：同一「层次 + 值」1 秒内只打一条日志 / 只广播一次；
- **悬浮毛玻璃底栏**：页面容器挂 `Modifier.layerBackdrop` 把内容录进 `GraphicsLayer`，
  底栏再用 `Modifier.textureBlur` 贴着同一个圆角形状（取组件的 `FloatingToolbarDefaults.CornerRadius`）做背景模糊，
  `color = Color.Transparent` 让玻璃透出；`isRuntimeShaderSupported()` 不通过时
  完全不创建 backdrop、底栏用 `surfaceContainer` 不透明配色；
- **界面即 Miuix 标准用法**：`ThemeController` 驱动 `MiuixTheme`，页面统一 `LazyColumn` +
  `SmallTitle` + `Card`，日志行用 `BasicComponent`，二次确认用 `WindowDialog`。

---

## 🏗️ 构建

```bash
./gradlew assembleDebug     # 或 gradle assembleDebug
./gradlew assembleRelease   # 产物未签名，用 apksigner 签一下即可
```

技术栈：Kotlin 2.4.20 + Compose（Kotlin Compose 插件）+ Miuix 0.9.3
（`miuix-ui` / `miuix-preference` / `miuix-icons` / `miuix-blur`）+
AGP 8.9.2 / Gradle 9.3.1 / JDK 17+，minSdk 26。

三个构建上的坑，改依赖前请先读：

1. **`androidx.activity` 必须 ≥ 1.13.0**：Miuix 0.9.3 的 `SearchBar`、对话框、下拉/列表弹窗内部都会
   调用 `NavigationBackHandler`，它需要 `LocalNavigationEventDispatcherOwner`；1.9.x / 1.10.x /
   1.11.x 的 `ComponentActivity` 没有实现该 owner，页面里一出现这些组件就抛
   `IllegalStateException: No NavigationEventDispatcher was provided`（表现为日志页 / 设置页打不开）。
2. **`gradle.properties` 里的 `android.experimental.disableCompileSdkChecks=true`**：
   Miuix 0.9.x 的 AAR 元数据声明 `minCompileSdk=37`，而 AGP 8.9.2 最高只支持 compileSdk 36，
   本工程界面没有用到 36/37 的新 API，因此关闭校验并用 `compileSdk = 34` 构建。
   升级到 AGP 9.1+ / Gradle 9.x 后可以删掉这一行并把 `compileSdk` 提到 37。
3. **`miuix-blur` 的 AAR 声明 `minSdk 33`**：它内部依赖 `RuntimeShader`（API 33+）。
   本工程保留 minSdk 26，在 `AndroidManifest.xml` 里用
   `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"` 放行，运行时用
   `isRuntimeShaderSupported()` 做能力检测（低于 33 既不创建 backdrop 也不挂 `textureBlur`）。
   如果哪天确认放弃 Android 12 及以下，可以直接把 `minSdk` 提到 33 并删掉那一行。

release 构建通过 `app/proguard-rules.pro` 保留 `MainHook` / `App` / `LogReceiver` 类名
（`assets/xposed_init` 以字符串引用 `MainHook`）。

---

## 📜 开源协议

本项目基于 [MIT License](LICENSE) 开源，仅供技术交流与学习使用。

界面基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）。
