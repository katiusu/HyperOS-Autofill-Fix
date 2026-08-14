# HyperOS Autofill Fix (小米 HyperOS 自动填充修复模块)

![LSPosed Module](https://img.shields.com/badge/LSPosed-Module-brightgreen.svg)
![Android](https://img.shields.com/badge/Android-14%2B-blue.svg)

一个用于小米 **HyperOS**  的 LSPosed/Xposed 模块，旨在解决系统安全组件自动重置、清空或覆盖第三方自动填充服务（如 Bitwarden, KeePass, 1Password 等）的顽疾。

---

## 📌 问题背景

在搭载 HyperOS 的设备上，小米安全组件 (com.miui.securitycenter) 经常会在后台强行将用户的第三方密码/自动填充服务（Autofill Service）清空或强制还原为小米密码管理器，导致用户需要频繁重新去设置中手动开启。

本模块通过对系统框架与设置存储底层进行多重 Hook 拦截，精准阻断非用户本意的重置行为。

---

## 🛠️ 工作原理与核心特性

- **多层级防御架构**：
  1. **上层拦截** (`android.provider.Settings$Secure`)：拦截所有 `putString` / `putStringForUser` 方法对 `autofill_service` 的写入请求。
  2. **数据库底层拦截** (`SettingsProvider`)：拦截 `PUT_secure` 类型的 `call` 方法，拦截包含非法清空值的 `Bundle` 传递。
  3. **内存/状态机硬拦截** (`SettingsState`)：Hook 最底层的 `insertSettingLocked`，确保即使绕过上层也不会写入内存和 XML 数据库。
- **兼容 Android 15 / 16 (HyperOS 2 / 3)**：
  - 使用动态参数扫描算法，完美适配 HyperOS 中新增的多参数重载函数。
- **智能过滤算法**：
  - 🚨 **阻止拦截**：试图写入 `null`、空字符串或小米内置包名 (`com.miui.*`) 的重置行为。
  - ✅ **放行用户正常修改**：用户主动在设置中选择其他合法的第三方密码管理器时，模块会自动放行。

---

## 📦 作用域配置 (Scope)

模块需要在 LSPosed 中勾选以下 **4 个关键作用域**（模块源码内置 `xposed_scope` 也会自动勾选）：

| 作用域包名 | 组件说明 | 拦截目的 |
| :--- | :--- | :--- |
| **`android`** | 系统框架 (System Server) | 阻止系统框架层的自动清空调用 |
| **`com.android.providers.settings`** | 设置存储 (Settings Provider) | 阻止底层数据库与内存的修改 |
| **`com.android.settings`** | 系统设置 | 确保设置界面逻辑一致性 |
| **`com.miui.securitycenter`** | 手机管家 / 安全中心 | 阻止安全中心后台清洗策略 |

---

## 🚀 安装与使用教程

1. **安装 APK**：编译或下载本模块的 APK 包并安装至手机。
2. **启用模块**：打开 **LSPosed 管理器**，在模块列表中找到 **HyperOS Autofill Fix** 并开启。
3. **确认作用域**：确保上述 4 个作用域均已勾选（通常会自动勾选）。
4. **重启设备**：重启手机或软重启系统框架，使 Hook 逻辑生效。
5. **设置自动填充**：前往手机 **系统设置 -> 密码与安全 -> 自动填充服务**，选择你的第三方密码管理器（如 Bitwarden）。

---

## 🔍 日志调试 (Logcat)

若想确认模块是否在后台成功拦截，可通过以下方式查看日志：

### 方式一：LSPosed 管理器 (推荐)
1. 打开 **LSPosed** -> 点击底部 **日志** 选项卡。
2. 搜索关键字：`HyperOSAutofillFix`
3. 日志示例：
   - 🚨 `HyperOSAutofillFix [android]: 🚨 上层拦截成功! 阻止清空/重置，原试图写入值: [null]`
   - ✅ `HyperOSAutofillFix [android]: ✅ 放行用户合法变更 -> com.x8bit.bitwarden`

### 方式二：命令行 / ADB
- **手机终端 (Root)**：`su -c "logcat | grep HyperOSAutofillFix"`
- **电脑终端 (ADB)**：`adb logcat | grep HyperOSAutofillFix`

---

## 🛠️ 项目编译环境

- **IDE**：Android Studio / JStudio (Android 端)
- **编译工具**：Gradle (Kotlin DSL / Groovy)
- **依赖库**：Xposed API 82+ (`de.robv.android.xposed:api:82`)

---

## 📜 开源协议

本项目基于 [MIT License](LICENSE) 协议开源，仅供技术交流与学习使用。
