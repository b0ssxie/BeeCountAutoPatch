# ---- 新版 libxposed API 官方要求的规则 ----
# 注解是 compileOnly 带来的，运行时没有这些类，缺类告警要忽略。
-dontwarn io.github.libxposed.annotation.**
# 入口类被混淆时，把 META-INF/xposed/java_init.list 里的类名一起改写。
-adaptresourcefilecontents META-INF/xposed/java_init.list
# 保证 XposedModule 子类（模块入口）不被裁掉。
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# ---- 本模块的加固 ----
# 双保险：入口类不改名，java_init.list 里写的名字永远对得上。
-keep class com.beecount.autopatch.BeeCountHook { *; }

# 保留注解信息，反射读取时不会丢。
-keepattributes *Annotation*

# 保留行号，线上崩溃堆栈还能定位到具体行。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile