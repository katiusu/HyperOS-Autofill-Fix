# 更新日志

本文件记录 HyperOS Autofill Fix 的所有重要变更。
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---

## [2.1.1] — 2026-10-01

底栏的模糊渲染换库，并修掉日志搜索的一个数据丢失缺陷。

### 修复

- **日志搜索：回车后关键词「消失」**。根因不在本页的状态管理，而在 Miuix 的 `InputField`：
  它在**收起搜索且仍有焦点**时会自己调 `onQueryChange("")` 把 query 清掉
  （`SearchBar.kt` 里 `LaunchedEffect(expanded)` 的 `else if (focused)` 分支）。
  本页原先把回车映射成「收起搜索」，于是刚输入的关键词被组件清空，列表也跟着恢复成全部。
  现在回车只收键盘、保持展开（在 Miuix 里 `expanded` 就是「正在搜索」），
  关键词与筛选结果都不再丢；**点建议项也是同一个坑**（收起 → 清空），一并修掉。
  退出搜索仍由返回键完成（`SearchBar` 内部的 `NavigationBackHandler`）。
- 没有改动搜索的其它交互：建议列表、清除按钮、四档筛选都保持原样。

### 变更

- **底栏模糊改用 AndroidLiquidGlass**（`io.github.kyant0:backdrop-android:2.0.0`），
  移除 `miuix-blur`：后者的模糊在边缘与降采样上有渲染问题，前者的 `vibrancy() + blur()`
  渲染更干净。底栏的形状 / 圆角 / 阴影仍取 Miuix 组件的 Defaults，半透明容器色叠在模糊之上，
  图标可读性不变。
  **观感仍是毛玻璃**（模糊 + 一点边缘高光，没有折射），所以照旧不叫"液态玻璃"。
- 能力门槛从 `RuntimeShader`（API 33）降到 `RenderEffect`（API 31）：API 31+ 都能吃到模糊；
  低于 31 时不建 backdrop、底栏退回不透明配色。
  `miuix-blur` 的 AAR 声明 minSdk 33 而引入的 `tools:overrideLibrary` 也随之删除（backdrop 的 AAR 是 minSdk 21）。
- `backdrop` 钉在 **2.0.0**：2.0.1 会把 Compose 抬到 1.12.0，而 androidx Compose 1.12.0 要求 AGP ≥ 9.1
  （本项目 AGP 8.9.2）。两个版本库源码一致，只差 Compose 依赖。
- **edge-to-edge（全屏）**：按 `android/skills` 的 `edge-to-edge` skill 逐条核对后补齐 ——
  - 系统栏图标改为跟随**应用内**的明暗选择。`ComponentActivity.enableEdgeToEdge()` 默认用
    `SystemBarStyle.auto()`，它只看系统 dark mode；而本应用允许在设置里强制浅色 / 深色，
    两者不一致时会出现「浅底 + 白图标」。现在 `HafTheme` 会按当前主题下发显式的
    `SystemBarStyle.light/dark`（`Theme.kt`）。
  - 顺带关掉了系统叠在导航栏上的半透明底：androidx 只在 `auto` 样式下打开
    `isNavigationBarContrastEnforced`（`EdgeToEdgeApi29.setUp` 的 `navStyle.nightMode == 0`），
    改成显式样式即自动关闭 —— 悬浮底栏的玻璃才能一路铺到屏幕底部。
  - 其余检查项本来就符合，未改动：唯一的 Activity 已在 `onCreate` 调 `enableEdgeToEdge()`；
    manifest 已有 `adjustResize`；列表用 `contentPadding` 让开系统栏（而不是给父容器加 padding）；
    顶栏玻璃带本身就覆盖状态栏区域，图标在其上对比充足；输入框在列表首项、键盘盖不到。
  - ⚠️ skill 的前置条件 **`targetSdk ≥ 35` 本容器无法满足**：唯一能跑通的 `aapt2` 是手工编的
    arm64 **2.19**，它解析不了 API 35 平台的 `resources.arsc`
    （实测 `error: illegal map type 'string'`），而 `build-tools;35.0.0` 自带的 aapt2 是 x86、
    被 arm64 加载器直接拒绝（`bad machine`）。因此 compileSdk / targetSdk 保持 34，
    代码层面已按 edge-to-edge 写好；构建环境能上 35 时改 `app/build.gradle.kts` 三行即可。
- 版本号 `2.1.0` → `2.1.1`（versionCode `2026100101` → `2026100102`）。

---

## [2.1.0] — 2026-10-01

底栏换成悬浮毛玻璃，并把「打开设置」的落点改成真正要改的那一页。

### 新增

- **悬浮毛玻璃底栏**：原本贴底的 `NavigationBar` 换成 `FloatingNavigationBar`
  （圆角与阴影取组件自带的 `FloatingToolbarDefaults.CornerRadius` /
  `FloatingNavigationBarDefaults.ShadowElevation`，不自己编尺寸），并引入 `miuix-blur`：
  页面内容先录进 `rememberLayerBackdrop` / `Modifier.layerBackdrop`，底栏再用 `Modifier.textureBlur`
  贴着同一圆角做背景模糊，`color` 设为透明让玻璃透出，滚动时内容从玻璃后面穿过。
  只做背景模糊 + 一层半透明底色，**没有边缘折射与高光描边**，所以是"毛玻璃"而不是"液态玻璃"
  （后者需要 AGSL 折射着色器 + 描边高光）。
- **「关于」里加入 GitHub 仓库**：显示 `github.com/katiusu/HyperOS-Autofill-Fix`，点击用浏览器打开。

### 变更

- **「打开系统设置」→「打开密码与账户」**：入口目标改为
  `com.android.settings.Settings$AccountDashboardActivity`（设置 → 语言与输入法 → 密码与账户，
  AOSP 里 `account_dashboard_title` = “Passwords & accounts”），自动填充服务开关就在这一页；
  设备上没有该入口时按 `android.settings.CREDENTIAL_PROVIDER` → 自动填充服务选择器 → 设置首页逐级退回。
- 三页的滚动内容不再用 `Scaffold` 的底栏内边距，改为各自接收 `contentBottomPadding`
  作为 `contentPadding`，让内容真正铺到屏幕底部（毛玻璃背后才有东西可模糊），
  同时在滚动末端把最后一条让到玻璃条上方。
- **低版本降级**：`RuntimeShader` 只有 API 33+ 才有，代码用 `isRuntimeShaderSupported()` 做能力检测；
  不支持时既不创建 backdrop 也不挂模糊，底栏退回 `surfaceContainer` 不透明配色。
  `miuix-blur` 的 AAR 把 minSdk 写成 33，本应用保留 **minSdk 26**，用
  `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"` 放行（官方 blur 指南对低 minSdk 应用的建议做法）。
- 版本号 `2.0.0` → `2.1.0`（versionCode `3` → `2026100101`）。
  该日期式 versionCode 会触发 lint 的 `HighAppVersionCode`（认为接近 Int 上限），
  但它仍在 Int 范围内，已在 `app/build.gradle.kts` 的 `lint {}` 里显式关闭这一条并注明原因。
- 界面依赖新增 `miuix-blur-android`。
- 概览页按钮文案随之改为「打开密码与账户」。

---

## [2.0.0] — 2026-09-30

从 1.0.0 以来的第一次大版本：模块逻辑重写 + 全新界面。

### 新增

- **全新的 Miuix（HyperOS 设计语言）界面**，三页可左右滑动切换：
  - **概览**：当前自动填充服务（异常时整张卡片切换到错误配色）、拦截 / 放行 / 最近一条统计、
    模块已载入的进程列表、四层拦截说明；
  - **日志**：搜索（进程 / 命中层次 / 写入值）、全部 / 拦截 / 放行 / 信息四档筛选、
    直观排版与 logcat 原文双视图、顶栏一键清空；
  - **设置**：主题、日志选项、复制 / 清空日志、关于与隐私说明。
- **完整的日志链路**：模块侧 `ModuleLog` 通过**显式广播**（`setPackage` 指定本应用）把结构化事件
  送到界面进程，`LogStore` 以 JSON Lines 落盘（最多 3000 条、超出自动裁剪），
  界面通过 `StateFlow` 实时刷新。
- **主题支持**：跟随系统 / 浅色 / 深色 / 动态取色（Monet，Android 12+），选中立即生效。
- **界面内自诊断**：概览页直接显示「哪些进程已载入 Hook」，日志页显示当前视图模式与筛选计数，
  日志为空时区分「模块未生效」和「筛选无结果」两种情况。
- 项目文档：本 `CHANGELOG.md`、重写的 `README.md`。

### 修复

- **日志页 / 设置页无法打开**：Miuix 0.9.3 的 `SearchBar`、对话框、下拉弹窗内部都会调用
  `NavigationBackHandler`，它需要 `LocalNavigationEventDispatcherOwner`；而 `androidx.activity`
  1.9.x / 1.10.x / 1.11.x 的 `ComponentActivity` 没有实现 `NavigationEventDispatcherOwner`，
  一进这两个页面就抛 `IllegalStateException: No NavigationEventDispatcher was provided`。
  现已把 `activity` / `activity-compose` 升级到 **1.13.0**。
- **设置项无内容**（1.0.x 遗留的同类问题）：早期版本用 `OverlayDialog` 承载主题选项，
  同一个缺失的 owner 导致对话框内容无法渲染，现在改为设置页内的下拉选项。

### 变更

- 版本号 `1.1.0` → `2.0.0`（versionCode `2` → `3`）。
- 界面依赖：Miuix 0.9.3（`miuix-ui` / `miuix-preference` / `miuix-icons`）、
  Compose（Kotlin Compose 插件）、`androidx.activity` 1.13.0。
- 构建：release 开启 R8 + 资源压缩，并通过 `proguard-rules.pro` 保留 Xposed 入口类名。

---

## [1.1.0] — 2026-08

模块逻辑重写：从「能拦」变成「拦得准、开销低」。

### 新增

- **第四层拦截**：补上旧式 `ContentProvider` 通道
  （`SettingsProvider.insert` / `update`）。
- **参数自适应扫描**：不再写死 `args[0]` / `args[1]`，改为扫描参数列表定位
  `autofill_service` 与对应值，兼容不同 Android / MIUI 版本的方法重载与参数顺序。
- **日志限频**：按「层次 + 值」去重，同一结果 1 秒内只输出一条，避免系统管家重试时刷爆 logcat。
- **release 混淆保留规则**：保证 `assets/xposed_init` 以字符串引用的入口类不被 R8 改名。

### 修复

- **`SettingsState.insertSettingLocked` 的返回值类型**：该方法返回 `String`（旧值），
  原实现返回 `Boolean`，会让调用方抛 `ClassCastException`；现在返回 `null` 表示「未发生变更」，
  并对返回 `void` 的版本自动退让。
- **`SettingsState.insertSettingLocked` 参数定位错误**：部分版本第一个参数不是设置项名，
  原实现会漏拦；改为扫描全部参数。
- **`SettingsProvider.call` 的 key 定位**：兼容设置项名出现在参数列表或 `Bundle` 里的两种形态。
- **判定过宽**：原来只拦「空值 / 含 `com.miui`」，现在额外拦截 `null`、`0`、
  以及任何不是合法 `包名/类名` 的脏数据。

### 移除

- 移除 `appcompat`、`material` 等界面依赖与模板测试、无用主题资源：
  模块本体不需要界面，release APK 从数 MB 降到约 20 KB。

---

## [1.0.0] — 2026-08（初版）

- 三层拦截：
  1. `android.provider.Settings$Secure.putString` / `putStringForUser`
  2. `SettingsProvider.call("PUT_secure", …)`
  3. `SettingsState.insertSettingLocked(…)`
- 判定规则：写入值为空，或包含 `com.miui` 时丢弃本次写入。
- 每次写入都输出日志，无去重与持久化。
- 没有界面，只能通过 logcat 观察。
