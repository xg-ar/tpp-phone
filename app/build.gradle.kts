import com.android.build.api.variant.ResValue
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.gms.googleservices.GoogleServicesPlugin
import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.navigation)
}

// Default package of the base config; the tppphone flavor sets its own.
val packageName = "ar.net.xg.softphone.dev"
val tppPackageName = "ar.com.tpp.phone.dev"
val useDifferentPackageNameForDebugBuild = false

val sdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
val googleServices = File(projectDir.absolutePath + "/google-services.json")
val linphoneLibs = File("$sdkPath/libs/")
val linphoneDebugLibs = File("$sdkPath/libs-debug/")
val firebaseCloudMessagingAvailable = googleServices.exists()
val crashlyticsAvailable = googleServices.exists() && linphoneLibs.exists() && linphoneDebugLibs.exists()

if (firebaseCloudMessagingAvailable) {
    println("google-services.json found, enabling Firebase CloudMessaging feature")
    apply<GoogleServicesPlugin>()
} else {
    println("google-services.json not found, disabling Firebase CloudMessaging feature")
}
if (crashlyticsAvailable) {
    println("google-services.json found and Linphone SDK libs-debug folder found, enabling Crashlytics feature")
    apply<CrashlyticsPlugin>()
} else {
    println("Crashlytics has been disabled because either google-services.json file wasn't found or local Linphone SDK build folder isn't configured")
}

var gitVersion = "6.2.8"
var gitBranch = ""
try {
    val gitDescribe = ProcessBuilder()
        .command("git", "describe", "--abbrev=0")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git describe: $gitDescribe")

    val gitCommitsCount = ProcessBuilder()
        .command("git", "rev-list", "$gitDescribe..HEAD", "--count")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git commits count: $gitCommitsCount")

    val gitCommitHash = ProcessBuilder()
        .command("git", "rev-parse", "--short", "HEAD")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git commit hash: $gitCommitHash")

    gitBranch = ProcessBuilder()
        .command("git", "name-rev", "--name-only", "HEAD")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git branch name: $gitBranch")

    gitVersion =
        if (gitCommitsCount.toInt() == 0) {
            gitDescribe
        } else {
            "$gitDescribe.$gitCommitsCount+$gitCommitHash"
        }
} catch (e: Exception) {
    println("Git not found [$e], using $gitVersion")
}
println("Computed git version: $gitVersion")

configurations {
    implementation { isCanBeResolved = true }
}

tasks.register("linphoneSdkSource") {
    doLast {
        configurations.implementation.get().incoming.resolutionResult.allComponents.forEach {
            if (it.id.displayName.contains("linphone-sdk-android")) {
                println("Linphone SDK used is ${it.moduleVersion?.version}")
            }
        }
    }
}
project.tasks.preBuild.dependsOn("linphoneSdkSource")

android {
    namespace = "org.linphone"
    compileSdk = 37

    defaultConfig {
        applicationId = packageName
        minSdk = 28
        targetSdk = 37
        versionCode = 602008 // 6.02.008
        versionName = "6.2.8"

        manifestPlaceholders["appAuthRedirectScheme"] = packageName

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val prefix = if (variant.flavorName == "tppphone") "tpp-phone" else "linphone-android"
                output.outputFileName = "$prefix-${variant.buildType.name}-$gitVersion.apk"
            }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

    signingConfigs {
        create("release") {
            val keyStorePath = keystoreProperties["storeFile"] as String
            val keyStore = project.file(keyStorePath)
            if (keyStore.exists()) {
                storeFile = keyStore
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                println("Signing config release is using keystore [$storeFile]")
            } else {
                println("Keystore [$storeFile] doesn't exists!")
            }
        }
    }

    // XG: upload key for the Play release of TPP Phone. Values come from environment variables
    // (set by scripts/tpp-release.sh), never from the repo. Applied in androidComponents below.
    signingConfigs {
        create("tppUpload") {
            val ks = System.getenv("TPP_UPLOAD_KEYSTORE")
            val pw = System.getenv("TPP_UPLOAD_KEYSTORE_PASSWORD")
            if (!ks.isNullOrEmpty() && !pw.isNullOrEmpty()) {
                storeFile = file(ks)
                storePassword = pw
                keyAlias = System.getenv("TPP_UPLOAD_KEY_ALIAS") ?: "tpp-phone-upload"
                keyPassword = pw // PKCS12: key password == store password
            }
        }
    }

    flavorDimensions += "brand"
    productFlavors {
        create("tppphone") {
            dimension = "brand"
            applicationId = tppPackageName
            manifestPlaceholders["appAuthRedirectScheme"] = tppPackageName
            resValue("string", "file_provider", "$tppPackageName.fileprovider")
            resValue("string", "linphone_openid_callback_scheme", tppPackageName)
            // "Línea Virtual" WhatsApp support chat; wa.me expects no '+'.
            resValue("string", "tpp_whatsapp_help_url", "https://wa.me/5491162406120")
            // Signup API (provisioning server); debug and release share it: there is no test server.
            resValue("string", "tpp_prov_url", "https://prov.xg.net.ar")
            versionName = "1.0.0"
            buildConfigField("Boolean", "TPP_UI", "true")
        }
    }

    buildTypes {
        getByName("debug") {
            if (useDifferentPackageNameForDebugBuild) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            isJniDebuggable = true

            val appVersion = gitVersion
            val appBranch = gitBranch
            println("Debug flavor app version is [$appVersion], app branch is [$appBranch]")
            resValue("string", "linphone_app_version", appVersion)
            resValue("string", "linphone_app_branch", appBranch)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")

            val appVersion = gitVersion
            val appBranch = gitBranch
            println("Release flavor app version is [$appVersion], app branch is [$appBranch]")
            resValue("string", "linphone_app_version", appVersion)
            resValue("string", "linphone_app_branch", appBranch)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
        resValues = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation(libs.junit)
    "tppphoneImplementation"(libs.androidx.work.runtime.ktx)
    "tppphoneImplementation"(libs.libphonenumber)
    // Android's org.json is a stub in JVM unit tests
    "testTppphoneImplementation"(libs.org.json)

    implementation(libs.androidx.annotations)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.telecom)
    implementation(libs.androidx.media)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.window)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.car)

    // https://github.com/google/flexbox-layout/blob/main/LICENSE Apache v2.0
    implementation(libs.google.flexbox)
    // https://github.com/material-components/material-components-android/blob/master/LICENSE Apache v2.0
    implementation(libs.google.material)
    // To be able to parse native crash tombstone and print them with SDK logs the next time the app will start
    implementation(libs.google.protobuf)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.messaging)
    if (crashlyticsAvailable) {
        implementation(libs.google.firebase.crashlytics)
    } else {
        compileOnly(libs.google.firebase.crashlytics)
    }

    // https://github.com/coil-kt/coil/blob/main/LICENSE.txt Apache v2.0
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    // https://github.com/tommybuonomo/dotsindicator/blob/master/LICENSE Apache v2.0
    implementation(libs.dots.indicator)
    // https://github.com/Baseflow/PhotoView/blob/master/LICENSE Apache v2.0
    implementation(libs.photoview)
    // https://github.com/openid/AppAuth-Android/blob/master/LICENSE Apache v2.0
    implementation(libs.openid.appauth)

    implementation(libs.linphone)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    android.set(true)
    ignoreFailures.set(true)
    additionalEditorconfig.set(
        mapOf(
            "max_line_length" to "120",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_no-blank-line-before-rbrace" to "disabled",
            "ktlint_standard_no-empty-class-body" to "disabled",
            "ktlint_standard_annotation-spacing" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_function-type-modifier-spacing" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_argument-list-wrapping" to "disabled",
            "ktlint_standard_trailing-comma-on-call-site" to "disabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
            "ktlint_standard_no-empty-first-line-in-class-body" to "disabled",
            "ktlint_standard_no-empty-first-line-in-method-block" to "disabled",
            "ktlint_standard_no-trailing-spaces" to "disabled",
            "ktlint_standard_no-blank-line-in-list" to "disabled",
            "ktlint_standard_no-multi-spaces" to "disabled",
            "ktlint_standard_try-catch-finally-spacing" to "disabled",
            "ktlint_standard_block-comment-initial-star-alignment" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_parameter-list-wrapping" to "disabled",
            "ktlint_standard_comment-wrapping" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_string-template-indent" to "disabled",
            "ktlint_standard_parameter-list-spacing" to "disabled",
            "ktlint_standard_statement-wrapping" to "disabled",
            "ktlint_standard_import-ordering" to "disabled",
            "ktlint_standard_paren-spacing" to "disabled",
            "ktlint_standard_curly-spacing" to "disabled",
            "ktlint_standard_indent" to "disabled",
        )
    )
}
project.tasks.preBuild.dependsOn("ktlintFormat")

if (crashlyticsAvailable) {
    afterEvaluate {
        tasks.getByName("assembleDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("packageDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("assembleRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
        tasks.getByName("packageRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
    }
}

// XG: Play release of TPP Phone (final id ar.com.tpp.phone). Debug keeps ".dev" so both coexist.
val tppReleasePackage = "ar.com.tpp.phone"
val tppUploadReady =
    !System.getenv("TPP_UPLOAD_KEYSTORE").isNullOrEmpty() &&
        !System.getenv("TPP_UPLOAD_KEYSTORE_PASSWORD").isNullOrEmpty()
val tppCommitCount: Int by lazy {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        workingDir = rootDir
    }.standardOutput.asText.get().trim().toInt()
}

androidComponents {
    onVariants(selector().withFlavor("brand" to "tppphone").withBuildType("release")) { variant ->
        variant.applicationId.set(tppReleasePackage)
        variant.manifestPlaceholders.put("appAuthRedirectScheme", tppReleasePackage)
        variant.resValues.put(
            variant.makeResValueKey("string", "file_provider"),
            ResValue("$tppReleasePackage.fileprovider"),
        )
        variant.resValues.put(
            variant.makeResValueKey("string", "linphone_openid_callback_scheme"),
            ResValue(tppReleasePackage),
        )
        // Every release from main gets a higher code than the previous one.
        val code = tppCommitCount
        println("TPP Phone release versionCode (commit count) is [$code]")
        variant.outputs.forEach { it.versionCode.set(code) }
        if (tppUploadReady) {
            variant.signingConfig.setConfig(android.signingConfigs.getByName("tppUpload"))
        }
    }
}

// XG: never build a tppphone release signed with Linphone's sample key.
if (!tppUploadReady) {
    tasks.configureEach {
        if (name == "preTppphoneReleaseBuild") {
            doFirst {
                throw GradleException(
                    "Faltan las variables de la clave de subida de TPP Phone: usá scripts/tpp-release.sh",
                )
            }
        }
    }
}
