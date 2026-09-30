# 更新日志

本文件记录 HyperOS Autofill Fix 的所有重要变更。
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
