import buildlogic.versioning.getAppDataDirName
import buildlogic.versioning.getAppName
import buildlogic.versioning.getAppVersionString
import buildlogic.versioning.getApplicationPackageName
import buildlogic.versioning.getPrettifiedAppName

plugins {
    id(MyPlugins.kotlinMultiplatform)
    id(MyPlugins.composeBase)
    id(Plugins.Kotlin.serialization)
    id(Plugins.Android.multiplatformLibrary)
    id(Plugins.buildConfig)
    id(Plugins.ksp)
}
kotlin {
    jvm("desktop")
    android {
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        namespace = "com.abdownloadmanager.shared"
        minSdk = 26
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)

            api(project(":downloader:core"))
            api(project(":downloader:monitor"))

            api(project(":shared:config"))
            api(project(":shared:utils"))
            api(project(":shared:compose-utils"))
            api(project(":shared:resources"))
            api(project(":shared:auto-start"))
            api(project(":shared:updater"))

            api(libs.kotlin.coroutines.core)
            api(libs.kotlin.serialization.json)

            api(libs.decompose)
            api(libs.essenty.lifecycleCoroutines)
            api(libs.koin.core)

            api(libs.androidx.datastore)

            api(libs.okhttp.dnsoverhttps)

            implementation(libs.kotlinFileWatcher)

            implementation(libs.compose.material.rippleEffect)

            api(libs.schemakt)

            api(libs.fastscroller.core)
            api(libs.markdownRenderer.core)
            api(libs.compose.reorderable)
            api(libs.qrose.qr)
        }
        androidMain {
            dependencies {
                api(libs.androidx.core.ktx)
                api(libs.androidx.activity.compose)
            }
        }
        val androidMain = getByName("androidMain")
        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(libs.nucleus.darkmodeDetector)
        }
    }
}

dependencies {
    add("kspAndroid", libs.arrow.opticKsp)
    add("kspDesktop", libs.arrow.opticKsp)
}

buildConfig {
    packageName = "com.abdownloadmanager.shared"
    buildConfigField(
        "PACKAGE_NAME",
        provider {
            getApplicationPackageName()
        }
    )
    buildConfigField(
        "APP_DISPLAY_NAME",
        provider { getPrettifiedAppName() }
    )
    buildConfigField(
        "DATA_DIR_NAME",
        provider { getAppDataDirName() }
    )
    buildConfigField(
        "APP_VERSION",
        provider { getAppVersionString() }
    )
    buildConfigField(
        "APP_NAME",
        provider { getAppName() }
    )
    buildConfigField(
        "PROJECT_WEBSITE",
        provider {
            "https://t.me/RoranoaxZORO"
        }
    )
    buildConfigField(
        "PROJECT_SOURCE_CODE",
        provider {
            "https://github.com/RoranoaxZORO/Zenin-Pro"
        }
    )
    buildConfigField(
        "DONATE_LINK",
        provider {
            "https://t.me/RoranoaxZORO"
        }
    )
    buildConfigField(
        "PROJECT_GITHUB_OWNER",
        provider {
            "RoranoaxZORO"
        }
    )
    buildConfigField(
        "PROJECT_GITHUB_REPO",
        provider {
            "Zenin-Pro"
        }
    )
    buildConfigField(
        "PROJECT_TRANSLATIONS",
        provider {
            "https://t.me/RoranoaxZORO"
        }
    )
    buildConfigField(
        "INTEGRATION_CHROME_LINK",
        provider {
            "https://chromewebstore.google.com/detail/ab-download-manager-brows/bbobopahenonfdgjgaleledndnnfhooj"
        }
    )
    buildConfigField(
        "INTEGRATION_FIREFOX_LINK",
        provider {
            "https://addons.mozilla.org/en-US/firefox/addon/ab-download-manager/"
        }
    )
    buildConfigField(
        "INTEGRATION_FIREFOX_EXTENSION_ID",
        provider {
            "firefox-integration@abdownloadmanager.com"
        }
    )
    buildConfigField(
        "INTEGRATION_CHROME_EXTENSION_ID",
        provider {
            "chrome-extension://bbobopahenonfdgjgaleledndnnfhooj/"
        }
    )
    buildConfigField(
        "TELEGRAM_GROUP",
        provider {
            "https://t.me/RoranoaxZORO"
        }
    )
    buildConfigField(
        "TELEGRAM_CHANNEL",
        provider {
            "https://t.me/RoranoaxZORO"
        }
    )
}
