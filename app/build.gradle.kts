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
        versionCode = 4
        versionName = "0.0.4"
        buildConfigField("String", "VERSION_INFO", getVersionGit())
        // ===== 密钥类配置一律从 local.properties / Gradle property 注入，不写进源码 =====
        // 原因：源码入库 = 密钥入库。CI 或他人 clone 后按 local.properties.example 填写即可。
        // 读取优先级：-P 参数 > local.properties > 空串（由用户在 App 设置页手动填写）。
        // getSecretParameter 返回已加引号的 Java 字面量，可直接喂给 buildConfigField。
        buildConfigField("String", "DEFAULT_LLM_API_KEY", getSecretParameter("DEFAULT_LLM_API_KEY"))
        buildConfigField("String", "BACKUP_LLM_API_KEY", getSecretParameter("BACKUP_LLM_API_KEY"))
        buildConfigField("String", "DEFAULT_QQ_APP_SECRET", getSecretParameter("DEFAULT_QQ_APP_SECRET"))
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
    // 用 ProcessBuilder 而不是 Runtime.exec(String)：后者在新版 JDK 已废弃，
    // 在 Gradle 9 + AGP 9 下会因 -Werror 语义直接编译失败。
    fun runGit(vararg args: String): String {
        val proc = ProcessBuilder(listOf("git") + args)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()
        return out.trim()
    }

    val branch = runGit("rev-parse", "--abbrev-ref", "HEAD")
    val sha1 = runGit("rev-parse", "HEAD")
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
            // 不打印值本身：构建日志会进 CI，密钥必须不落到日志里。
            println("get property[$key] from project (value hidden)")
            return value
        }
    }
    val localPropertiesFile = project.rootProject.file("local.properties")
    val localProperties = Properties()
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
        // 注意：不能用 localProperties.hasProperty(...) —— 那是 Kotlin/Native 的扩展，
        // JVM 上不存在，会导致脚本编译失败。这里用 Java 原生的 containsKey。
        val hasLocalProperty = localProperties.containsKey(key)
        if (hasLocalProperty) {
            val property = localProperties[key] as String?
            if (!property.isNullOrEmpty()) {
                value = property
                // 同上：不打印值本身
                println("get property[$key] from local (value hidden)")
                return value
            }
        }
    }
    println("get property[$key] from default (value hidden)")
    return value
}

/**
 * 读取密钥类参数，返回**已加引号并转义**的 Java 字符串字面量。
 *
 * 用途：给 `buildConfigField("String", NAME, value)` 用。
 * buildConfigField 要求参数是完整的 Java 字面量（含引号），而 local.properties
 * 里的值是裸字符串 —— 直接塞进去会生成 `String X = abc` 这种非法 Java 代码，
 * 表现为编译期 "Unresolved reference" 之类的诡异报错。
 *
 * @param key     参数名
 * @param default 未配置时的默认值（裸字符串，会被同样加引号）
 */
fun getSecretParameter(key: String, default: String = ""): String {
    val raw = getParameter(key, default)
    val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
    // 不打印密钥内容本身，避免 CI 日志泄漏
    println("get secret parameter[$key] length=${raw.length}")
    return "\"$escaped\""
}
