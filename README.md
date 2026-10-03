# HyperOS Autofill Fix（小米 HyperOS 自动填充修复模块）

![LSPosed Module](https://img.shields.io/badge/LSPosed-Module-brightgreen.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-blue.svg)
![Version](https://img.shields.io/badge/version-2.3.0-orange.svg)
![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)

一个用于小米 **HyperOS / MIUI** 的 LSPosed / Xposed 模块，解决系统安全组件反复重置、清空、
覆盖第三方自动填充服务（Bitwarden、KeePass、1Password 等）的问题。**2.0.0 起自带一个 Miuix
（HyperOS 设计语言）界面**，可以直接在手机上看状态、看拦截日志、调设置；
**2.1.0 起底栏换成悬浮的毛玻璃条**，**2.2.0 起可以在设置里切回 2.0.0 那种贴底普通底栏**；
**2.3.0 起整个界面按 [MiuixGuiExample](https://github.com/katiusu/MiuixGuiExample) 的骨架重写**：
底栏三档（含 iOS 液态玻璃）、宽屏自动切侧栏、滚动模糊顶栏、声明式设置项管道 + 全局搜索，
并新增「关于」页。

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
- **自带界面**：概览 / 日志 / 设置 / 关于四页，Miuix + HyperOS 观感，点击底栏或左右滑动切换；
- **底栏三档**（设置 → 外观）：
  - **悬浮毛玻璃**（默认）：`FloatingNavigationBar` + `miuix-blur` 的 `Modifier.textureBlur`，
    悬浮胶囊 + 玻璃描边高光，页面内容从它后面穿过；
  - **贴底普通**：2.0.0 的形态，Miuix 的 `NavigationBar`，不透明、顶部分隔线、图标带文字标签；
  - **iOS 液态玻璃**：在悬浮底栏基础上换成 `IosLiquidGlassNavigationBar`（自绘高光、折射与内阴影，
    背景实时采样），宽度上限 440dp；
- **宽屏自动切侧栏**：窗口够宽（≥ 840dp，或 ≥ 600dp 且高宽比 < 1.2）时底栏变成 `NavigationRail`，
  ≥ 1200dp 自动展开，平板 / 折叠屏展开态不再被一条拉满宽度的底栏挤占高度；
- **滚动模糊顶栏**：顶栏在滚动时渐变为模糊玻璃（Haze），列表内容从下方穿过；
- **设置项全局搜索**：设置页顶栏搜索框按标题与摘要检索任意设置项，命中分区自动定位、命中二级页直接跳转；
- **设置导入 / 导出**：全量设置导出为 JSON 再导入，换机 / 备份不用重配；
- **直观 + 原文双视图**：日志既能看排版后的中文说明，也能看与 `adb logcat` 完全一致的原行；
- **完整日志链路**：被 Hook 进程通过显式广播把事件送到 App，本地落盘（最多 3000 条），
  支持搜索、级别筛选、复制、清空；
- **主题六档**：跟随系统 / 浅色 / 深色 / 动态取色 / 动态浅色 / 动态深色（Monet 需 Android 12+）。

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
| **概览** | **正方形状态卡**（是否正常工作，按「被改回小米服务 / 未设置 / 正常」三档语义色 + 右下角大装饰图标）、**自动填充服务名**单独成块、**两个按钮**（打开密码与账户 / 重新读取）独立成卡，另有拦截 / 放行 / 最近一条统计、模块已载入的进程、四层拦截说明 |
| **日志** | 搜索（进程 / 层次 / 写入值，附进程建议）、全部 / 拦截 / 放行 / 信息筛选、直观与 logcat 原文双视图、复制最近 300 条、清空 |
| **设置** | 外观（主题六档、悬浮底栏、液态玻璃底栏、界面模糊）、日志（记录放行事件、默认显示原文、复制最近日志）、数据（导出 / 导入设置、清空日志），顶栏可全局搜索设置项 |
| **关于** | 折叠渐显的应用图标头部 + 动态背景、模块信息（版本 / 主题 / GitHub 仓库 / Hook 入口 / 日志标签 / 生效方式）、隐私说明、界面依赖 |

底栏在「设置 → 外观」里切换三档形态：默认是悬浮毛玻璃条（内容一直铺到屏幕底部、从它后面穿过，
滚动到末端时再让开）；关掉「悬浮底栏」回到 2.0.0 的贴底普通底栏；打开「液态玻璃底栏」则是
iOS 风格的液态玻璃胶囊。窗口够宽时底栏自动换成侧边导航栏（见上）。

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
   `HyperOS-Autofill-Fix-2.3.0.apk`。
2. **安装**：
   - 从 **2.0.0 / 2.1.x / 2.2.0** 升级可以直接覆盖安装（同一个签名）；
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
| 底栏没有模糊 / 不是液态玻璃 | Android 12 及以下没有 `RenderEffect` / RuntimeShader，模糊会按设计退回不透明底栏；也可检查「设置 → 外观 → 界面模糊」是否被关掉 |

---

## 🧩 代码结构

| 文件 | 作用 |
| :--- | :--- |
| `MainHook.java` | Xposed 入口，四层拦截逻辑 |
| `ModuleLog.java` | 模块侧日志出口（logcat + 广播），含 Context 获取与限频 |
| `App.kt` / `LogReceiver.kt` | Application 与广播接收器 |
| `data/LogEvent.kt` / `data/LogStore.kt` | 事件模型与 JSON Lines 落盘仓库 |
| `AppSettings.kt` | 外壳级设置（主题档位 / 底栏形态 / 模糊开关）与旧版设置迁移 |
| `prefs/PrefsStore.kt` | SharedPreferences 存取（与 Hook 侧读的是同一份 `settings`） |
| `prefs/OptionSpec.kt` / `OptionType.kt` / `OptionRegistry.kt` / `ConfigState.kt` / `ConfigBackup.kt` | 声明式设置项管道：声明 → 注册与检索 → 响应式读写 → 导入导出 |
| `ui/MainActivity.kt` | 应用外壳：四页 `HorizontalPager` + 底栏 / 侧栏 + 主题下发 |
| `ui/component/AppBottomNavigationBar.kt` | 底栏三档（普通 / 悬浮毛玻璃 / 液态玻璃） |
| `ui/component/liquid/*` | 液态玻璃底栏实现（高光、折射 `Lens`、内阴影 `InnerShadow`、背景采样 `CombinedBackdrop`） |
| `ui/component/effect/*` | 关于页的动态背景特效 |
| `ui/component/pref/*` | 设置项渲染（`HookOptionView` 按类型分派到各卡片）与 `HookOptionsPage`（分区 / 二级页 / 全局搜索） |
| `ui/screen/overview` / `logs` / `settings` / `about` | 四个页面；设置项在 `settings/AppSettingSpecs.kt` 里声明 |
| `ui/screen/subpage/BaseSubPageActivity.kt` + `ui/component/SubPageScaffold.kt` | 二级页模板 |
| `ui/theme/Theme.kt` | `AppTheme`（六档 `ColorSchemeMode`） |
| `ui/util/BlurUtils.kt` | 顶栏 Haze 模糊、`blurSource`、`pageScrollModifiers`、宽屏判定 |
| `ui/util/WindowBackground.kt` / `MiuixAnimations.kt` | 窗口底色同步与公共动画规格 |

---

## 🔧 实现要点

- **参数自适应扫描**：不依赖固定下标，兼容 AOSP / MIUI 不同版本的参数顺序与重载；
- **返回值类型安全**：`insertSettingLocked` 返回 `String`（旧值），不再返回 `Boolean`，
  避免调用方 `ClassCastException`；
- **热路径快速失败**：未命中时只做一次字符串比较即返回，零额外分配；
- **日志限频去重**：同一「层次 + 值」1 秒内只打一条日志 / 只广播一次；
- **底栏三档共用一套「内容让开」逻辑**：三档都把底栏高度通过 `contentBottomPadding` 传给各页；
  悬浮与液态玻璃需要背景采样，才创建 backdrop（`rememberBlurBackdrop()`，API < 33 返回 `null`），
  贴底普通底栏**不创建** backdrop —— 它不透明，用不到每帧录一次图层的开销；
- **液态玻璃底栏**：`IosLiquidGlassNavigationBar` 自己绘制高光、边缘折射与内阴影，
  背景由 `CombinedBackdrop` 采样后再交给 `textureBlur` 做实时模糊，形状与圆角取组件默认值；
- **宽屏侧栏**：`shouldShowSplitPane()`（≥ 840dp，或 ≥ 600dp 且高宽比 < 1.2）决定底栏还是侧栏，
  `shouldExpandNavigationRail()`（≥ 1200dp）决定侧栏默认展开；
- **顶栏滚动模糊**：`BlurredBar` + Haze 的 `hazeEffect`（progressive 渐变）；`Modifier.blurSource`
  必须挂在**内容容器**上而不是 `LazyColumn` / `Scaffold` 上，否则滚动内容会把自己糊掉；
- **声明式设置项**：加一个设置项只需要在 `ui/screen/settings/AppSettingSpecs.kt` 里加一条
  `OptionSpec` —— 渲染、持久化、全局搜索都由管道接管；`dependsOn`（液态玻璃依赖悬浮底栏）、
  `dangerous`（清空日志用红色文字 + 二次确认）也由管道处理，不写死在页面里；
- **edge-to-edge（全屏）**：`Activity.onCreate` 里调 `enableEdgeToEdge()`，各页用
  `contentWindowInsets` 只保留水平方向、垂直方向交给 `Scaffold` 与底栏；系统栏图标跟随**应用内**的
  明暗选择，窗口底色在 `setContent` 之前用 `applyWindowBackground` 同步，避免切换主题时露白。
  （`targetSdk` 目前是 34，见「构建」一节的说明。）

---

## 🏗️ 构建

```bash
./gradlew assembleDebug
./gradlew assembleRelease   # 产物未签名，用 apksigner 签一下即可
```

技术栈：Kotlin 2.4.20（AGP 9 内置，不单独应用 `kotlin-android` 插件）+ Compose BOM 2026.09.00 +
Miuix 0.9.4（`core` / `ui` / `shader` / `blur` / `preference` / `icons` / `squircle` / `nav`）+
[Haze](https://github.com/chrisbanes/haze) 1.7.3（顶栏模糊）+
AGP 9.4.1 / Gradle 9.7.1 / JDK 21（源码与目标 Java 11），
`minSdk 26` / `targetSdk 34` / `compileSdk 37` / `buildToolsVersion 36.0.0`。

几个构建上的坑，改依赖或换环境前请先读：

1. **Miuix 0.9.x 的 AAR 元数据要求 `compileSdk ≥ 37`**：本工程已直接把 `compileSdk` 提到 37
   （AGP 9.4.1 支持），不再需要 `android.experimental.disableCompileSdkChecks`。
2. **aapt2 必须是 arm64 且能解析 android-37 的 `resources.arsc`**：容器里由
   `~/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride` 指向
   `/opt/android-sdk/build-tools/36.0.0/aapt2`；`build-tools;37.0.0` 的 aapt2 只有 x86_64 产物，
   在 arm64 上会被加载器直接拒绝（`bad machine`）。`app/build.gradle.kts` 因此固定
   `buildToolsVersion = "36.0.0"`。另外 AGP 默认从 Maven 下拉的 `aapt2` 也只有 x86_64。
3. **`android.enableResourceOptimizations=false`**：在上面这套 aapt2 组合下，AGP 的
   `optimizeReleaseResources` 会「构建成功」但产出 **0 个文件**，于是 release APK 里
   既没有 `AndroidManifest.xml` 也没有 `resources.arsc`（`aapt2 dump badging` 报
   `could not identify format of APK.`），而 Gradle 全程不报错。已在 `gradle.properties` 里关掉资源
   优化并写了说明；换到官方 x86_64 aapt2 的环境可以删掉这一行。
4. **`local.properties` 必须写 `sdk.dir`**：`ANDROID_HOME` 只在 login shell 里生效，
   非交互构建拿不到，缺了会报 `SDK location not found`。
5. **`androidx.activity` 必须 ≥ 1.13.0**：Miuix 的 `SearchBar`、对话框、下拉/列表弹窗内部都会
   调用 `NavigationBackHandler`，它需要 `LocalNavigationEventDispatcherOwner`；低版本的
   `ComponentActivity` 没有实现该 owner，页面里一出现这些组件就抛
   `IllegalStateException: No NavigationEventDispatcher was provided`（表现为日志页 / 设置页打不开）。
6. **图标用 `material-icons-core`，不要换 `material-icons-extended`**：只有 `ui/icons/StatusIcons.kt`
   用 materialIcon + materialPath 手写了几个图标，换成 extended 会让包体涨好几 MB。
7. **`targetSdk` 仍是 34**：`edge-to-edge` 的规范建议 `targetSdk ≥ 35`，但本模块要覆盖 8.0 起的
   老设备（模糊与液态玻璃在 API < 33 由代码自动降级），且未做 Android 15 强制 edge-to-edge 的
   真机验证，因此暂时保持 34。

release 构建通过 `app/proguard-rules.pro` 保留 `MainHook` / `App` / `LogReceiver` 类名
（`assets/xposed_init` 以字符串引用 `MainHook`）。

---

## 📜 开源协议

本项目基于 [MIT License](LICENSE) 开源，仅供技术交流与学习使用。

界面基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）、
[Haze](https://github.com/chrisbanes/haze)（Apache-2.0）与 Jetpack Compose（Apache-2.0）；
界面骨架参考 [MiuixGuiExample](https://github.com/katiusu/MiuixGuiExample)。
