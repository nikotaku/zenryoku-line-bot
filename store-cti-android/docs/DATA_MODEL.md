# DATA_MODEL.md — 店舗CTI データモデル設計書

Room データベース名: `store_cti.db` / schema version: 1 / exportSchema: true (`app/schemas`)

## 0. 共通設計

### 共通カラム(全主要Entity)

| カラム | 型 | 説明 |
|---|---|---|
| `id` | TEXT (PK) | UUID v4 文字列 |
| `createdAt` | INTEGER | epoch millis |
| `updatedAt` | INTEGER | epoch millis(更新のたびに書き換え) |
| `deletedAt` | INTEGER? | 論理削除日時。NULL = 有効 |
| `syncStatus` | TEXT | `LOCAL_ONLY` / `PENDING` / `SYNCED`(MVPでは常に LOCAL_ONLY) |
| `version` | INTEGER | 更新のたびに +1(将来の同期競合検出用) |

### 型変換 (TypeConverter)

- `LocalDate` ↔ TEXT (ISO-8601 `yyyy-MM-dd`) — 日付順ソートが文字列比較で成立
- `LocalTime` ↔ TEXT (`HH:mm`)
- `LocalDateTime` ↔ TEXT (ISO-8601) — 範囲検索が文字列比較で成立
- enum ↔ TEXT (name)

### 電話番号の二重保存

- `displayPhoneNumber`: 入力されたままの表示用(例 `090-1234-5678`)
- `normalizedPhoneNumber`: 検索用。数字のみ・国内0始まり形式(例 `09012345678`)。
  `+81 90 1234 5678` も `090-1234-5678` も同じ値になる(core/PhoneNumberUtils)

## 1. CustomerEntity (`customers`)

| カラム | 型 | 備考 |
|---|---|---|
| customerNumber | TEXT | 店舗独自の顧客番号(任意) |
| name | TEXT | 氏名(必須) |
| kana | TEXT | ふりがな |
| displayPhoneNumber / normalizedPhoneNumber | TEXT | 主番号 |
| displayPhoneNumber2 / normalizedPhoneNumber2 | TEXT | 予備番号 |
| lineName | TEXT | LINE名 |
| firstVisitDate | TEXT(LocalDate?) | 初回来店日 |
| lastVisitDate | TEXT(LocalDate?) | 最終来店日 |
| visitCount | INTEGER | 利用回数 |
| lastStaffName | TEXT | 前回担当者 |
| preferredStaffName | TEXT | 希望担当者 |
| category | TEXT | CustomerCategory (下記) |
| cautionNotes | TEXT | 注意事項 |
| servicePreferences | TEXT | 接客上の好み |
| prohibitedActions | TEXT | 禁止事項 |
| memo | TEXT | メモ |
| +共通カラム | | |

Index: `normalizedPhoneNumber`, `normalizedPhoneNumber2`, `kana`, `customerNumber`, `deletedAt`

`CustomerCategory`: `NEW`(新規) / `REGULAR`(通常) / `FREQUENT`(常連) / `VIP`(重要) / `CAUTION`(注意) / `BANNED`(利用停止)

重複判定: 保存時に `normalizedPhoneNumber` が他の有効顧客(deletedAt IS NULL)と一致したら警告
(保存は可能。強制ブロックしない — 家族で同番号を共有するケースがあるため)。

## 2. CallInteractionEntity (`call_interactions`)

| カラム | 型 | 備考 |
|---|---|---|
| customerId | TEXT? | FK → customers.id(未登録客はNULL) |
| displayPhoneNumber / normalizedPhoneNumber | TEXT | |
| occurredAt | TEXT(LocalDateTime) | 対応日時 |
| direction | TEXT | `INCOMING`(着信) / `OUTGOING`(発信) |
| answered | INTEGER(Bool) | 電話に出たか |
| result | TEXT | CallResult(下記) |
| inquiry | TEXT | 問い合わせ内容 |
| desiredDateTime | TEXT | 希望日時(自由記述) |
| desiredStaffName | TEXT | 希望担当者 |
| guidanceGiven | TEXT | 案内した内容 |
| reservationCreated | INTEGER(Bool) | 予約成立の有無 |
| reservationId | TEXT? | FK → reservations.id |
| callbackRequired | INTEGER(Bool) | 折り返しの要否 |
| callbackDone | INTEGER(Bool) | 折り返し完了(絞り込み「折り返し未完了」用) |
| nextActionAt | TEXT(LocalDateTime?) | 次回対応予定日時 |
| staffName | TEXT | 対応スタッフ名 |
| memo | TEXT | 自由メモ |
| +共通カラム | | |

Index: `customerId`, `normalizedPhoneNumber`, `occurredAt`, `deletedAt`

`CallResult`: `RESERVED`(予約成立) / `CONSIDERING`(検討中) / `FULL`(満了) / `WAITING_CALLBACK`(折り返し待ち) / `NO_ANSWER`(不在) / `CANCELLED`(キャンセル) / `INQUIRY_ONLY`(問い合わせのみ) / `SALES_CALL`(営業電話) / `NUISANCE`(迷惑電話) / `OTHER`(その他)

## 3. ReservationEntity (`reservations`)

| カラム | 型 | 備考 |
|---|---|---|
| customerId | TEXT? | FK → customers.id |
| date | TEXT(LocalDate) | 予約日 |
| startTime / endTime | TEXT(LocalTime) | 開始・終了 |
| staffName | TEXT | 担当者 |
| courseName | TEXT | コース名 |
| price | INTEGER? | 料金(円) |
| channel | TEXT | ReservationChannel(下記) |
| status | TEXT | ReservationStatus(下記) |
| room | TEXT | 部屋 |
| requests | TEXT | 要望 |
| cautionNotes | TEXT | 注意事項 |
| createdByStaffName | TEXT | 登録スタッフ |
| +共通カラム | | |

Index: `customerId`, `date`, `status`, `deletedAt`

`ReservationStatus`: `TENTATIVE`(仮予約) / `CONFIRMED`(予約確定) / `VISITED`(来店済み) / `CANCELLED`(キャンセル) / `NO_SHOW`(無断キャンセル) / `MODIFIED`(変更済み)

`ReservationChannel`: `PHONE`(電話) / `SMS` / `LINE` / `WEB` / `REFERRAL`(紹介) / `OTHER`(その他)

重複判定(core/ReservationOverlapChecker): 同一日で `start < other.end && other.start < end` かつ
(同一担当者 または 同一部屋)で、状態が `CANCELLED`/`NO_SHOW`/`MODIFIED` 以外のものと重なる場合に警告。
MVPでは警告のみで保存は許可(ダブルブッキングを承知で受ける運用があるため)。

## 4. SmsDraftEntity (`sms_drafts`) — SMS作成履歴

| カラム | 型 | 備考 |
|---|---|---|
| customerId | TEXT? | |
| reservationId | TEXT? | |
| displayPhoneNumber / normalizedPhoneNumber | TEXT | 宛先 |
| templateId | TEXT? | 使用テンプレート |
| templateName | TEXT | スナップショット(テンプレ削除後も表示可能に) |
| body | TEXT | 実際に生成・編集された本文 |
| status | TEXT | `DRAFT`(下書き作成) / `LAUNCHED`(SMSアプリを起動) / `CONFIRMED`(スタッフ確認済み) |
| staffName | TEXT | |
| +共通カラム | | |

**送信済みとは記録しない。** 標準SMSアプリでの実送信可否はアプリからは確実に検知できないため、
状態は上記3段階のみ。`CONFIRMED` はスタッフが「送信した」と手動確認した状態。

## 5. SmsTemplateEntity (`sms_templates`)

| カラム | 型 | 備考 |
|---|---|---|
| name | TEXT | テンプレート名 |
| body | TEXT | 本文({placeholder} 含む) |
| sortOrder | INTEGER | 並び順 |
| enabled | INTEGER(Bool) | 無効化フラグ |
| +共通カラム | | |

初期データ(初回起動時に投入): 「予約確定」「折り返し」「来店後のお礼」(本文は仕様書どおり)。

差し込み項目: `{customerName}` `{reservationDate}` `{startTime}` `{endTime}` `{staffName}`
`{courseName}` `{price}` `{storeName}` `{storePhone}`。
値が無い項目は空文字に置換し、未知の `{xxx}` はそのまま残す(気付けるように)。

## 6. StaffEntity (`staff`)

| カラム | 型 | 備考 |
|---|---|---|
| name | TEXT | スタッフ名 |
| sortOrder | INTEGER | |
| active | INTEGER(Bool) | 退職等での無効化 |
| +共通カラム | | |

既定スタッフは DataStore(`defaultStaffName`)に保持。

## 7. AuditLogEntity (`audit_logs`)

| カラム | 型 | 備考 |
|---|---|---|
| occurredAt | INTEGER | epoch millis |
| actorStaffName | TEXT | 操作スタッフ(既定スタッフ設定値) |
| action | TEXT | `CREATE` / `UPDATE` / `SOFT_DELETE` / `RESTORE_DATA` / `EXPORT_CSV` / `EXPORT_BACKUP` / `IMPORT_BACKUP` / `WIPE_ALL` / `SEED_SAMPLE` |
| targetType | TEXT | `CUSTOMER` / `RESERVATION` / `INTERACTION` / `SMS_DRAFT` / `TEMPLATE` / `STAFF` / `ALL` |
| targetId | TEXT? | 対象UUID |
| detail | TEXT | 個人情報を含めない要約(件数など) |

監査ログ自体は論理削除対象外(追記専用)。「データ初期化」実行時のみ全消去される。

## 8. 設定 (DataStore Preferences — `settings.preferences_pb`)

| キー | 型 | 既定値 |
|---|---|---|
| storeName | String | "" (未設定時は差し込みで空文字) |
| storePhone | String | "" |
| defaultStaffName | String | "" |
| businessHoursStart / businessHoursEnd | String "HH:mm" | 10:00 / 22:00 |
| defaultSlotMinutes | Int | 60 |
| phoneDisplayFormat | String | `HYPHEN`(自動ハイフン) / `RAW`(入力どおり) |
| maskPhoneNumbers | Boolean | false(一覧で `090-****-5678` 形式にマスク) |
| blockScreenshots | Boolean | false(FLAG_SECURE) |
| callScreeningEnabled | Boolean | false(Phase 2 実験機能) |
| seededInitialTemplates | Boolean | false(初期テンプレ投入済みフラグ) |

## 9. リレーション図

```
customers 1 ──< call_interactions >── 0..1 reservations
customers 1 ──< reservations
customers 1 ──< sms_drafts >── 0..1 sms_templates(templateIdは弱参照)
```

外部キーは Room の `ForeignKey` を使わず**弱参照(手動整合)**とする。
理由: 論理削除主体のため参照先が「削除済みでも残る」ことが正しく、
CASCADE や制約違反がバックアップ復元の妨げになるため。整合性は Repository 層で保つ。

## 10. バックアップ形式 (JSON)

```json
{
  "formatVersion": 1,
  "appVersion": "0.1.0",
  "exportedAt": "2026-07-14T12:00:00",
  "customers": [...], "callInteractions": [...], "reservations": [...],
  "smsDrafts": [...], "smsTemplates": [...], "staff": [...], "auditLogs": [...],
  "settings": { "storeName": "...", ... }
}
```

- kotlinx.serialization による全テーブルのダンプ(論理削除済みも含む)
- 復元は**全置換**(復元前に「現在のデータは上書きされます」警告 + 内部ストレージへ自動プリバックアップ作成)
- `formatVersion` 不一致・JSON破損時は復元を中止しエラーメッセージを表示(現行データは保持)

## 11. CSV出力

- 対象: 顧客一覧 / 予約一覧 / 対応履歴 / SMS作成履歴(いずれも有効データのみ。ヘッダー行あり)
- UTF-8 **BOM付き**(Excel対応)、改行 CRLF
- エスケープ: `"` を `""` に、カンマ/改行/`"` を含むセルは `"..."` で囲む
- CSVインジェクション対策: セルが `=` `+` `-` `@` またはタブ/CRで始まる場合、先頭にシングルクォート `'` を付与
