# ────────────────────────────────────────────────
# Dolby Tuner · R8 keep 规则
# 作者：016. · Aoi ｜ 许可：Apache-2.0
# ────────────────────────────────────────────────

# 保留行号 / 注解 / 泛型签名：崩溃栈可读，反射找方法也更稳
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# 隐藏 API 豁免库依赖反射访问 VMRuntime，必须整包保留
-keep class org.lsposed.hiddenapibypass.** { *; }

# 系统类不会被 R8 改名，但显式声明可以避免 don't warn 报错
-keep class android.media.audiofx.AudioEffect { *; }
-keep class android.media.audiofx.AudioEffect$* { *; }

# 实时层：内部用反射调用 AudioEffect#setParameter/getParameter
-keep class com.zero16.dolbytuner.DapRealtime { *; }
-keep class com.zero16.dolbytuner.DapRealtime$* { *; }

# 清单里声明的组件（Activity/Service/Receiver）由 AAPT 保留，这里再保险一次
-keep class com.zero16.dolbytuner.MainActivity { *; }
-keep class com.zero16.dolbytuner.DeviceAutomationService { *; }
-keep class com.zero16.dolbytuner.BootReceiver { *; }

# Compose / Kotlin 运行时的常见缺类警告（不影响运行）
-dontwarn org.jetbrains.annotations.**
-dontwarn org.lsposed.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
