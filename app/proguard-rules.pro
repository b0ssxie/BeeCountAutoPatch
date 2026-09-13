# Xposed 通过 assets/xposed_init 里写死的类名反射加载入口，
# 所以本包下的类（尤其 BeeCountHook）不能被 R8 删掉或改名。
-keep class com.beecount.autopatch.** { *; }

# Xposed API 是 compileOnly，运行时由框架提供；缺类告警要忽略，否则 R8 会报错。
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留注解信息，反射读取时不会丢。
-keepattributes *Annotation*

# 保留行号，线上崩溃堆栈还能定位到具体行。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile