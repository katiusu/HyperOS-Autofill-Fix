# Xposed 模块入口类由 assets/xposed_init 以字符串形式引用，
# R8 无法感知，必须显式保留类名与方法名。
-keep class com.katiusu.hyperautofillfix.MainHook { public *; }
-keepnames class com.katiusu.hyperautofillfix.MainHook

# Xposed API 只在编译期提供（compileOnly），运行时由框架注入，禁止警告与混淆
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 清单里声明的组件（R8 一般会自动保留，这里再兜底一次）
-keep class com.katiusu.hyperautofillfix.App { *; }
-keep class com.katiusu.hyperautofillfix.LogReceiver { *; }

# 模块侧日志类会被 Hook 进程直接调用，保留名字便于排查
-keepnames class com.katiusu.hyperautofillfix.ModuleLog

# Compose 运行时在反射/内联场景下会引用一些可选类，缺失时不应中断构建
-dontwarn androidx.compose.**
-dontwarn kotlinx.**
