import jdk.internal.net.http.common.Log.channel
import org.jetbrains.kotlin.konan.properties.hasProperty
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}


android {
    namespace = "com.apk.claw.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            val props = Properties().apply {
                rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
            }
            // 固定签名密钥（Hermes 2026-10-14）
            // 背景：之前一直 assembleDebug，用的是 Android 临时 debug 密钥，
            // 每次编译都可能变 → 系统判定为不同应用 → 每次更新都要重新授权全部权限。
            // 改用项目内固定 keystore 后，安装即为覆盖更新，权限与数据全部保留。
            val ksFile = props.getProperty("KEYSTORE_FILE", "myclaw-release.jks")
            // file() 相对 app/ 模块解析，keystore 在项目根目录，需回退一级
            val ksPath = rootProject.file(ksFile)
            if (ksPath.exists()) {
                storeFile = ksPath
                storePassword = props.getProperty("KEYSTORE_PASSWORD", "myclaw2026")
                keyAlias = props.getProperty("KEY_ALIAS", "myclaw")
                keyPassword = props.getProperty("KEY_PASSWORD", "myclaw2026")
            }
        }
    }

    defaultConfig {
        applicationId = "com.apk.claw.android"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.0.3"
        buildConfigField("String", "VERSION_INFO", getVersionGit())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.gson)


    implementation(libs.oapi.sdk)
    implementation("org.slf4j:slf4j-android:1.7.36")  // 添加 SLF4J Android 日志
    implementation(libs.dingtalk)


    // LangChain4j (exclude JDK http-client, use OkHttp adapter for Android)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.openai) {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }
    implementation(libs.langchain4j.anthropic) {
        exclude(group = "dev.langchain4j", module = "langchain4j-http-client-jdk")
    }
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.utilcode)
    implementation(libs.ok2curl)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.mmkv)
    implementation(libs.adapter)
    implementation(libs.glide)
    implementation(libs.glide.transformations)
    implementation(libs.easyfloat)


    // ZXing 二维码/条形码扫描
    implementation(libs.zxing)

    // NanoHTTPD 嵌入式 HTTP 服务器（局域网配置服务）
    implementation(libs.nanohttpd)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val versionName = android.defaultConfig.versionName ?: "0.0.0"
                val fileName = "MyClaw_v${versionName}_${getDateTime()}.apk"
                println("output file name: $fileName")
                output.outputFileName.set(fileName)
            }
        }
    }
}

fun getVersionGit(): String {
    val process1 = Runtime.getRuntime().exec("git rev-parse --abbrev-ref HEAD")
    val reader1 = BufferedReader(InputStreamReader(process1.inputStream))
    val branch = reader1.readLine()?.trim()
    reader1.close()

    val process2 = Runtime.getRuntime().exec("git rev-parse HEAD")
    val reader2 = BufferedReader(InputStreamReader(process2.inputStream))
    val sha1 = reader2.readLine()?.trim()
    reader2.close()
    // 将数据拼接起来，如果只需要SHA-1 那么就可以不执行process1命令
    return "\"" + branch + "_" + sha1 + "\""
}

fun getDateTime(): String {
    val df = SimpleDateFormat("yyyyMMdd_HHmmss");
    return df.format(Date());
}

fun getParameter(key: String, defaultValue: String): String {
    var value = defaultValue
    val hasProperty = project.hasProperty(key)
    if (hasProperty) {
        val property = project.properties[key] as String?
        if (!property.isNullOrEmpty()) {
            value = property
            println("get property[$key]from project:$value")
            return value
        }
    }
    val localPropertiesFile = project.rootProject.file("local.properties")
    val localProperties = Properties()
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
        val hasLocalProperty = localProperties.hasProperty(key)
        if (hasLocalProperty) {
            val property = localProperties[key] as String?
            if (!property.isNullOrEmpty()) {
                value = property
                println("get property[$key]from local:$value")
                return value
            }
        }
    }
    println("get property[$key] from default:$value")
    return value
}
