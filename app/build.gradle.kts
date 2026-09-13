plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.beecount.autopatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beecount.autopatch"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.4"
    }

    buildTypes {
        release {
            // 体积优化：删无用代码（R8）+ 删无用资源。
            // Xposed 入口类是运行时按名字反射加载的，靠 proguard-rules.pro 里的 keep 规则保住。
            isMinifyEnabled = true
            isShrinkResources = true
            // 本项目没有发布用签名密钥，release 复用 debug 签名。
            // CI 会缓存这把 keystore，所以各次构建出来的包签名一致、可以直接覆盖安装。
            // 以后要用正式签名，把这里换成自己的 signingConfig 即可。
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}