# Railway環境変数設定手順

## X（Twitter）API環境変数の設定

GitHubへのコードプッシュは完了しました。次にRailwayで以下の環境変数を設定してください。

### 設定が必要な環境変数

以下の4つの環境変数をRailwayプロジェクトに追加してください：

```
X_API_KEY=SzbjV9XePQTwvvWpfkmt8NLdy
X_API_KEY_SECRET=spSI6WfP7FJnzUoj8vClQnvKW9gFjDLRgxjkqDmheKLbuQJdx5
X_ACCESS_TOKEN=196351498271034l632-TXLhiNVNkzzFFNb0xYNCVhzS1SWB8q
X_ACCESS_TOKEN_SECRET=03pRTiATuTKaVW5qqNTPRAMSf2WQDh046FlLk08DN2lgZ
```

### Railwayでの設定手順

#### 方法1: Railwayダッシュボード（推奨）

1. Railwayにログイン
   - URL: https://railway.app/dashboard
   - GitHubアカウントでログイン（nikotaku）

2. プロジェクトを選択
   - プロジェクト名: `zenryoku-line-bot-production`

3. サービス（Service）を選択
   - デプロイされているサービスをクリック

4. Variables（変数）タブを開く
   - 画面上部のタブから「Variables」を選択

5. 環境変数を追加
   - 「New Variable」または「+ Add Variable」ボタンをクリック
   - 各環境変数を1つずつ追加：
     - Variable Name: `X_API_KEY`
     - Value: `SzbjV9XePQTwvvWpfkmt8NLdy`
   - 同様に残りの3つも追加

6. デプロイを確認
   - 環境変数を追加すると自動的に再デプロイが開始されます
   - Deploymentsタブで正常にデプロイされたか確認

#### 方法2: Railway CLI

```bash
# Railway CLIでログイン
railway login

# プロジェクトにリンク
cd /path/to/zenryoku-line-bot
railway link

# 環境変数を設定
railway variables set X_API_KEY=SzbjV9XePQTwvvWpfkmt8NLdy
railway variables set X_API_KEY_SECRET=spSI6WfP7FJnzUoj8vClQnvKW9gFjDLRgxjkqDmheKLbuQJdx5
railway variables set X_ACCESS_TOKEN=196351498271034l632-TXLhiNVNkzzFFNb0xYNCVhzS1SWB8q
railway variables set X_ACCESS_TOKEN_SECRET=03pRTiATuTKaVW5qqNTPRAMSf2WQDh046FlLk08DN2lgZ
```

### 設定確認

環境変数設定後、以下を確認してください：

1. **デプロイログの確認**
   - Railwayのデプロイログで `tweepy` がインストールされているか確認
   - エラーがないか確認

2. **LINEボットでの動作確認**
   - LINEボットのメニューに「🐦 X投稿」ボタンが表示されるか確認
   - ボタンを押して投稿フローが動作するか確認
   - テスト投稿を実行して、Xに正常に投稿されるか確認

### トラブルシューティング

#### 環境変数が反映されない場合

1. Railwayのデプロイログを確認
2. 手動で再デプロイを実行：
   - Deploymentsタブ → 最新のデプロイ → 「Redeploy」

#### X API認証エラーが出る場合

1. 環境変数の値が正しいか確認（スペースや改行が入っていないか）
2. X APIのキーが有効か確認（Twitter Developer Portalで確認）
3. アプリケーションログで詳細なエラーメッセージを確認

### 実装内容

今回追加された機能：

- **メニューボタン**: 「🐦 X投稿」
- **投稿フロー**:
  1. ボタンを押すと投稿内容の入力を促すメッセージが表示
  2. テキストを入力（最大280文字）
  3. 確認画面で内容をプレビュー
  4. 「投稿する」で実行、「修正する」で再入力、「キャンセル」で中止
  5. 投稿成功時はツイートURLを返信

- **使用ライブラリ**: `tweepy` (X API v2対応)
- **実装ファイル**:
  - `app.py`: X投稿機能の追加
  - `requirements.txt`: tweepyの追加
  - `ENV_SETUP.md`: 環境変数の説明を更新

### 参考リンク

- Railway Dashboard: https://railway.app/dashboard
- Railway Docs: https://docs.railway.com/
- Tweepy Documentation: https://docs.tweepy.org/
