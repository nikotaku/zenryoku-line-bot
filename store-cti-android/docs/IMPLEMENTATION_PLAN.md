# IMPLEMENTATION_PLAN.md — 店舗CTI 実装計画書

## 0. 開発環境に関する重要な制約(記録)

本プロジェクトの初期実装はクラウド上のClaude Code環境で行った。この環境の
ネットワークポリシーでは `dl.google.com`(Android SDK本体および Google Maven
の実体配信ホスト)への接続が遮断されており、Maven Central / Gradle Plugin Portal /
services.gradle.org は利用可能だった。このため:

- **`:core`(純Kotlin JVM)モジュール**: この環境でビルド・単体テスト実行まで検証
- **`:app`(Android)モジュール**: GitHub Actions CI(Android SDK同梱ランナー)で
  `testDebugUnitTest` + `assembleDebug` を実行し、debug APK をCIアーティファクトとして生成
- `settings.gradle.kts` に `-Pcti.includeAndroid=false` スイッチを用意し、
  SDK非到達環境でも `:core` を単独ビルド可能にした

## 1. 実装順序(発注仕様書17章対応)

| 手順 | 内容 | 状態 |
|---|---|---|
| 1 | 公式資料・ライブラリバージョン確認(Maven Centralメタデータで実在確認) | 済 |
| 2 | Android制限の整理(REQUIREMENTS.md 2.4) | 済 |
| 3-7 | REQUIREMENTS / ARCHITECTURE / DATA_MODEL / SECURITY / 本書 | 済 |
| 8 | Gradleプロジェクト作成(Kotlin DSL + Version Catalog + wrapper) | 本書以降 |
| 9 | データ層(core enum → Room Entity/DAO/Database → Repository → DataStore) | |
| 10 | ドメイン(PhoneNumberUtils / SmsTemplateEngine / OverlapChecker / CSV / UseCase) | |
| 11 | 画面(theme → navigation → 10画面 + ViewModel) | |
| 12 | 電話・SMS連携(ACTION_DIAL / ACTION_SENDTO / 共有受け取り / ショートカット / Phase2 CallScreening) | |
| 13 | バックアップ・CSV(SAF + kotlinx.serialization) | |
| 14 | テスト(core単体テスト + app単体テスト) | |
| 15-16 | build / test 実行・修正(core=ローカル、app=CI) | |
| 17 | debug APK生成(CIアーティファクト) | |
| 18 | release署名手順(INSTALL_GUIDE.md) | |
| 19 | 導入説明書(INSTALL_GUIDE.md) | |
| 20 | 最終動作確認表(TEST_REPORT.md) | |

## 2. 採用バージョン(実在確認済みの安定版)

| 対象 | バージョン | 確認方法 |
|---|---|---|
| Gradle | 8.10.2 | services.gradle.org(到達可) |
| AGP | 8.7.3 | 既知の安定版(Gradle 8.9+要求を満たす) |
| Kotlin / compose plugin / serialization plugin | 2.0.21 | Maven Centralメタデータで実在確認 |
| KSP | 2.0.21-1.0.28 | 同上 |
| Hilt | 2.52 | Maven Central(Kotlin 2.0系対応) |
| kotlinx-serialization-json | 1.7.3 | 同上 |
| kotlinx-coroutines | 1.9.0 | 同上 |
| Room | 2.6.1 | 既知の安定版(KSP対応) |
| Compose BOM | 2024.12.01 | 既知の安定版(Material3安定) |
| Navigation Compose | 2.8.5 / Activity Compose 1.9.3 / Lifecycle 2.8.7 / core-ktx 1.15.0 / DataStore 1.1.1 / hilt-navigation-compose 1.2.0 | 既知の安定版 |
| compileSdk / targetSdk | 35 / minSdk 29 | AGP 8.7.3 の正式対応上限 |
| JDK | 17(ビルド) / Java 11 bytecode target | AGP 8.x 要求 |

2026年時点ではより新しい系列(Kotlin 2.3/2.4等)が存在するが、本体験環境から
Google Maven のメタデータを照会できず AndroidX との互換組み合わせを機械的に検証
できないため、**相互互換性が確実に既知である安定組み合わせ**を採用した
(README「採用した前提」参照)。バージョン更新は Version Catalog の書き換えのみで行える。

## 3. テスト計画

### 単体テスト(:core、この環境で実行)
- PhoneNumberUtilsTest: 正規化(空白/ハイフン/全角/tel:/+81)、検証、整形、マスク、末尾一致
- SmsTemplateEngineTest: 9種差し込み、欠損値、未知プレースホルダ
- ReservationOverlapCheckerTest: 境界値(接触は非重複)、担当者/部屋、除外状態
- CsvWriterTest: エスケープ、BOM、インジェクション対策
- ValidationTest: 予約時間逆転、日時境界(23:59跨ぎ、月末、うるう年)

### 単体テスト(:app、CIで実行)
- 顧客重複判定UseCase、論理削除の状態遷移、顧客区分変更、バックアップJSONラウンドトリップ

### 手動確認(実機、TEST_REPORT.mdのチェックリスト)
- 電話/SMSアプリ起動、共有受け取り、ショートカット、CallScreening(Phase 2)、
  SAFのファイル保存/読込、FLAG_SECURE、ダークモード、フォント拡大

## 4. リスクと対策

| リスク | 対策 |
|---|---|
| CI上でのみAndroidビルド可能なため修正サイクルが長い | :coreに主要ロジックを寄せ、ローカルで先に検証。バージョンは実在確認済みのみ使用 |
| CallScreeningの端末差 | 機能フラグ + isSupported判定 + 非対応表示。Phase 1に影響させない |
| SAFの機種差 | ACTION_CREATE_DOCUMENT/OPEN_DOCUMENTという最も互換性の高い口だけを使う |
| 復元による データ喪失 | 全置換前の自動プリバックアップ + 明示警告 |
