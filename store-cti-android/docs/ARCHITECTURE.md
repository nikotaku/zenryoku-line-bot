# ARCHITECTURE.md — 店舗CTI アーキテクチャ設計書

## 1. モジュール構成

```
store-cti-android/
├── core/   … 純Kotlin JVMモジュール(Android非依存)
│   ├── phone/        PhoneNumberUtils(正規化・+81変換・検証・整形・マスク)
│   ├── csv/          CsvWriter(エスケープ・CSVインジェクション対策・BOM)
│   ├── sms/          SmsTemplateEngine(差し込み処理)
│   ├── reservation/  ReservationOverlapChecker(時間重複判定)
│   ├── model/        列挙型(顧客区分/対応結果/予約状態/予約経路/SMS状態/同期状態)
│   └── validation/   入力検証(電話番号/予約時間/日時境界)
└── app/    … Androidアプリモジュール
    ├── data/local/       Room(Entity 8種・DAO・Converter・Database)
    ├── data/settings/    DataStore(SettingsRepository)
    ├── data/repository/  Repository群
    ├── data/transfer/    バックアップ(JSON)・CSV出力
    ├── domain/           UseCase群
    ├── telephony/        電話・SMS連携(交換可能インターフェース)
    ├── di/               Hiltモジュール
    └── ui/               Compose(theme / navigation / 各画面 + ViewModel)
```

**coreを純Kotlinにした理由**: 単体テスト必須のロジック(電話番号処理・CSV・差し込み・重複判定)を
Android SDKなしでビルド・テストできるようにするため。CI高速化と再利用性(将来のサーバー実装)にも寄与。

## 2. レイヤー責務 (MVVM + Repository + UseCase)

```
Compose UI ← StateFlow ← ViewModel ← UseCase ← Repository ← DAO(Room) / DataStore
                                             ← core(純ロジック)
```

- **UI (Compose)**: 状態の描画とイベント送出のみ。ビジネスロジックを持たない
- **ViewModel**: 画面状態(UiState data class)を StateFlow で公開。Hiltで注入
- **UseCase**: 1機能1クラス。トランザクション境界・複合ロジック(重複判定+警告、監査ログ記録など)
- **Repository**: データソースの抽象化。タイムスタンプ・version・syncStatus の付与、論理削除
- **DAO/Room**: SQLのみ。Flowで変更監視
- **core**: 副作用なしの純関数群

## 3. 電話連携の交換可能構造(段階的設計)

```kotlin
// Phase 1(常時有効)
interface OutboundCallLauncher { fun openDialer(phoneNumber: String): LaunchResult }
interface SmsComposerLauncher { fun openSmsComposer(phoneNumber: String, body: String): LaunchResult }

// Phase 2(実験機能・機能フラグ)
interface CallScreeningGateway {
    val isSupported: Boolean
    fun hasRole(): Boolean
    fun buildRoleRequestIntent(): Intent?
}
// 実装: RoleManagerCallScreeningGateway(API 29+) / 非対応端末では isSupported=false

// Phase 3(未実装・別開発)
interface DialerRoleGateway { /* ROLE_DIALER / InCallService 用の空インターフェース */ }

// 録音(初期版では非対応)
interface RecordingProvider {
    val isSupported: Boolean            // Unsupported実装は常に false
    val unsupportedReason: String       // 「この構成では録音に対応していません」
}
```

Phase 2 の `StoreCtiCallScreeningService` は `onScreenCall()` で **常に着信を許可**(拒否・無音化しない)し、
番号を正規化して顧客を検索、結果を通知として表示する。設定フラグOFF・役割未取得時は何もしない。

## 4. ナビゲーション

Navigation Compose 単一Activity構成。ルート定義:

| route | 画面 |
|---|---|
| `home` | ホーム |
| `customers` | 顧客一覧 |
| `customer/{id}` | 顧客詳細 |
| `customer_edit?id={id}&phone={phone}` | 顧客登録・編集 |
| `call_intake?phone={phone}` | 着信・通話後対応 |
| `interaction_edit?customerId={id}&phone={phone}` | 対応履歴登録 |
| `interactions` | 対応履歴一覧 |
| `reservations` | 予約一覧 |
| `reservation_edit?id={id}&customerId={cid}` | 予約登録・編集 |
| `sms_templates` | SMSテンプレート管理 |
| `sms_compose?customerId={id}&reservationId={rid}` | SMS作成(差し込み+編集+起動) |
| `settings` ほか設定サブ画面 | 設定 |

ディープリンク: `storecti://intake?phone=...`(通知・ショートカット・共有受け取りから使用)

## 5. 非同期・状態管理

- Repository は `Flow<List<T>>` を返し、ViewModel が `stateIn(viewModelScope, WhileSubscribed(5s), initial)` で StateFlow 化
- 書き込みは `suspend` 関数 + `Dispatchers.IO`(Room が自動処理)
- 一覧検索は `combine(検索条件Flow, DAO Flow)` で宣言的に絞り込み

## 6. エラー処理方針

- Repository/UseCase は `Result<T>` または sealed class を返す。例外文をUIへ直接出さない
- UIには日本語の利用者向けメッセージ(例: 「保存に失敗しました。空き容量を確認してください」)
- 開発向け詳細は `Timber` 相当の自前 `AppLogger` へ。**リリースビルドでは no-op**、電話番号・氏名・メモは
  デバッグビルドでもマスクして出力
- インテント起動(電話/SMS)は `resolveActivity`/`ActivityNotFoundException` を処理し
  「電話アプリが見つかりません」等を表示

## 7. ビルド構成

- Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`)
- `settings.gradle.kts` は Gradleプロパティ `cti.includeAndroid=false` で `:app` を除外可能
  (Android SDK/googleリポジトリに到達できない環境でも `:core` のビルド・テストを可能にするため)
- リポジトリ: `google()` → `mavenCentral()` → `gradlePluginPortal()`
- debug ビルド: `applicationIdSuffix ".debug"`、サンプルデータ投入ボタン有効
- release ビルド: minify有効(R8)、詳細ログ無効。署名手順は INSTALL_GUIDE.md

## 8. パッケージ名・アプリ名の変更容易性

- `applicationId` は `app/build.gradle.kts` の1箇所
- アプリ表示名は `app/src/main/res/values/strings.xml` の `app_name`
- 店舗名・店舗電話番号はソースに埋め込まず設定画面(DataStore)から変更

## 9. Phase 3(既定電話アプリ化)を別開発とする設計判断

既定電話アプリ(ROLE_DIALER + InCallService)は着信UI・通話中UI・保留・複数SIM・Bluetooth・
緊急通報の取り扱いまで全責任を負う。これらの品質保証には実機マトリクステストが必須であり、
MVPの範囲外とする。本アプリは `DialerRoleGateway` の空実装と本設計書のみを用意し、
将来は別モジュール(`:feature-dialer`)として追加する。必要になる主要API:
`TelecomManager` / `InCallService` / `Call` / `CallAudioState` / `RoleManager.ROLE_DIALER`。
