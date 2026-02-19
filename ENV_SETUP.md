# 環境変数設定ガイド

## Railway環境変数設定

以下の環境変数をRailwayのプロジェクト設定で追加してください。

### LINE Bot設定
- `LINE_CHANNEL_SECRET`: LINEチャネルシークレット
- `LINE_CHANNEL_ACCESS_TOKEN`: LINEチャネルアクセストークン
- `LINE_ADMIN_USER_ID`: 管理者のLINE User ID

### Notion API設定
- `NOTION_API_KEY`: Notion APIキー（提供されたキーを設定）
- `NOTION_DATABASE_ID`: シフト管理データベースID（既存）
- `NOTION_NEWS_DATABASE_ID`: ニュースデータベースID `74dde0685a7a4ee09aeb67e53658e63e`

### X (Twitter) API設定
- `X_API_KEY`: X API Key
- `X_API_KEY_SECRET`: X API Key Secret
- `X_ACCESS_TOKEN`: X Access Token
- `X_ACCESS_TOKEN_SECRET`: X Access Token Secret

### その他
- `BASE_URL`: `https://zenryoku-line-bot-production.up.railway.app`
- `PORT`: Railwayが自動設定（通常は設定不要）

## ニュースデータベース情報

- データベースURL: https://www.notion.so/1b90848cb6e543bfb1c8163e133df971
- データソースID: 74dde068-5a7a-4ee0-9aeb-67e53658e63e

## 機能

### ニュース作成
1. LINEボットで「ニュース作成」を選択
2. カテゴリを選択（お知らせ、キャンペーン、新メニュー、セラピスト紹介、その他）
3. テーマを入力（または「おまかせ」でAI自動選択）
4. AI生成されたニュースを確認
5. 「この内容で保存」でNotionに保存

### ニュース一覧
- Notionに保存されたニュースを一覧表示
- 配信済み/未配信のステータス確認
- 詳細表示で全文確認

### ニュース配信
- 保存済みニュースを選択
- LINE Broadcast APIでフォロワーに一斉配信
- 配信後、Notionで配信済みにマーク

### X（Twitter）投稿
1. LINEボットのメニューから「X投稿」を選択
2. 投稿したい内容を入力（最大280文字）
3. プレビューを確認
4. 「投稿する」でXに投稿実行
5. 投稿成功時はツイートURLが表示される
