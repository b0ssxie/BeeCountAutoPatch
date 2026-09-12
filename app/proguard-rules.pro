# Xposed 通过反射与 assets/xposed_init 定位入口类，禁止裁剪/改名。
-keep class com.beecount.autopatch.** { *; }
-keep class de.robv.android.xposed.** { *; }
-keepattributes *Annotation*
