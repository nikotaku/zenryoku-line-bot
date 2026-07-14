pluginManagement {
    repositories {
        // mavenCentral/portal を先頭に置く: Google Maven へ到達できない
        // ネットワーク環境でも :core のビルドを可能にするため(ARCHITECTURE.md 7章)
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "store-cti"

include(":core")

// -Pcti.includeAndroid=false で Android モジュールを除外できる。
// Android SDK / Google Maven に到達できない環境で :core の単体テストだけを
// 実行するためのスイッチ(既定は含める)。
if (providers.gradleProperty("cti.includeAndroid").orNull != "false") {
    include(":app")
}
