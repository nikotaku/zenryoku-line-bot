#!/usr/bin/env python3.11
"""
全力エステ LINE Bot - メンズエステサロン「全力エステ」公式LINEボット
Flask + LINE Messaging API v3
"""

import os
import io
import json
import uuid
import logging
import traceback
import calendar
import requests as http_requests
from datetime import datetime, timedelta, date

from flask import Flask, request, abort, send_from_directory, jsonify
from linebot.v3 import WebhookHandler
from linebot.v3.messaging import (
    MessagingApi,
    Configuration,
    ApiClient,
)
from linebot.v3.messaging.models import (
    TextMessage,
    ImageMessage,
    TemplateMessage,
    ButtonsTemplate,
    MessageAction,
    FlexMessage,
    FlexContainer,
    ReplyMessageRequest,
    PushMessageRequest,
    BroadcastRequest,
)
from linebot.v3.webhooks import (
    MessageEvent,
    TextMessageContent,
    FollowEvent,
    JoinEvent,
)
from linebot.v3.exceptions import InvalidSignatureError

from openai import OpenAI
from PIL import Image, ImageDraw, ImageFont
import tweepy

# ─── ログ設定 ───
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# ─── 環境変数 ───
CHANNEL_SECRET = os.environ.get("LINE_CHANNEL_SECRET", "8ede866d50f47c485febdbb69a4008c6")
CHANNEL_ACCESS_TOKEN = os.environ.get(
    "LINE_CHANNEL_ACCESS_TOKEN",
    "6cPZ0W6arhy1odKsdbt1U5o0AjQ2WxiDtw7qIwrK2IVDBWnhaYl+GYyjvZpoGz/v6Yc+idHkYsyFqQ2DjpmoS7L5F8PUdOxoDJwLha01/JfD7t0bn7WGrO0d6Ic+L8bPUpAEDCbYrgI2UDqQiaXokQdB04t89/1O/w1cDnyilFU=",
)
ADMIN_USER_ID = os.environ.get("LINE_ADMIN_USER_ID", "U485fac63c62459cb069c64a1a9846595")

# Notion API
NOTION_API_KEY = os.environ.get("NOTION_API_KEY", "")
NOTION_DATABASE_ID = os.environ.get("NOTION_DATABASE_ID", "256f9507f0cf8076931fed70fc040520")
NOTION_NEWS_DATABASE_ID = os.environ.get("NOTION_NEWS_DATABASE_ID", "74dde0685a7a4ee09aeb67e53658e63e")

# X (Twitter) API
X_API_KEY = os.environ.get("X_API_KEY", "").strip()
X_API_KEY_SECRET = os.environ.get("X_API_KEY_SECRET", "").strip()
X_ACCESS_TOKEN = os.environ.get("X_ACCESS_TOKEN", "").strip()
X_ACCESS_TOKEN_SECRET = os.environ.get("X_ACCESS_TOKEN_SECRET", "").strip()

# ─── Flask ───
app = Flask(__name__)

# ─── LINE SDK v3 ───
configuration = Configuration(access_token=CHANNEL_ACCESS_TOKEN)
handler = WebhookHandler(CHANNEL_SECRET)

def get_messaging_api():
    api_client = ApiClient(configuration)
    return MessagingApi(api_client)

# ─── OpenAI ───
openai_client = OpenAI()

# ─── 店舗情報 ───
SHOP_INFO = {
    "name": "全力エステ",
    "location": "仙台",
    "concept": "仙台のメンズエステ界における頂点を本気で狙うハイレベルサロン",
    "therapists": ["なの", "さな", "しほ", "しいな", "みさき", "らむ", "MOMO", "まりの", "りの"],
}

# ─── セッション管理 ───
user_sessions = {}

# ─── 画像保存ディレクトリ ───
UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "static", "images")
os.makedirs(UPLOAD_DIR, exist_ok=True)

# ─── BASE_URL（トンネル公開後に設定） ───
BASE_URL = os.environ.get("BASE_URL", "https://zenryoku-line-bot-production.up.railway.app")

# ─── 日付パースヘルパー ───
def parse_date_safe(date_str):
    """Notionの日付文字列を安全にdateオブジェクトに変換する
    '2026-02-20' や '2026-02-06T11:00:00.000+00:00' の両方に対応
    """
    if not date_str:
        return None
    try:
        return date.fromisoformat(date_str[:10])
    except (ValueError, TypeError):
        return None


# ─── セラピスト色分け ───
THERAPIST_COLORS = [
    "#FF6B9D",  # ピンク
    "#C084FC",  # パープル
    "#60A5FA",  # ブルー
    "#34D399",  # グリーン
    "#FBBF24",  # イエロー
    "#FB923C",  # オレンジ
    "#F87171",  # レッド
    "#A78BFA",  # バイオレット
    "#2DD4BF",  # ティール
    "#E879F9",  # マゼンタ
    "#FCA5A5",  # ライトレッド
    "#86EFAC",  # ライトグリーン
    "#93C5FD",  # ライトブルー
    "#FDE68A",  # ライトイエロー
    "#FDBA74",  # ライトオレンジ
]


# ═══════════════════════════════════════════
#  X (Twitter) API連携
# ═══════════════════════════════════════════

# 起動時にX API認証情報の設定状況をログ出力
logger.info("=== X API credentials check ===")
logger.info(f"X_API_KEY: {'SET (' + str(len(X_API_KEY)) + ' chars)' if X_API_KEY else 'NOT SET'}")
logger.info(f"X_API_KEY_SECRET: {'SET (' + str(len(X_API_KEY_SECRET)) + ' chars)' if X_API_KEY_SECRET else 'NOT SET'}")
logger.info(f"X_ACCESS_TOKEN: {'SET (' + str(len(X_ACCESS_TOKEN)) + ' chars)' if X_ACCESS_TOKEN else 'NOT SET'}")
logger.info(f"X_ACCESS_TOKEN_SECRET: {'SET (' + str(len(X_ACCESS_TOKEN_SECRET)) + ' chars)' if X_ACCESS_TOKEN_SECRET else 'NOT SET'}")
logger.info("===============================")


def get_x_client():
    """Tweepy Client (API v2) を取得"""
    if not all([X_API_KEY, X_API_KEY_SECRET, X_ACCESS_TOKEN, X_ACCESS_TOKEN_SECRET]):
        missing = []
        if not X_API_KEY: missing.append("X_API_KEY")
        if not X_API_KEY_SECRET: missing.append("X_API_KEY_SECRET")
        if not X_ACCESS_TOKEN: missing.append("X_ACCESS_TOKEN")
        if not X_ACCESS_TOKEN_SECRET: missing.append("X_ACCESS_TOKEN_SECRET")
        logger.error(f"X API credentials missing: {', '.join(missing)}")
        return None
    try:
        client = tweepy.Client(
            consumer_key=X_API_KEY,
            consumer_secret=X_API_KEY_SECRET,
            access_token=X_ACCESS_TOKEN,
            access_token_secret=X_ACCESS_TOKEN_SECRET,
        )
        logger.info("X client created successfully")
        return client
    except Exception as e:
        logger.error(f"Failed to create X client: {e}\n{traceback.format_exc()}")
        return None


def post_to_x(text):
    """Xにテキストを投稿する（tweepy + HTTPフォールバック）"""
    logger.info(f"post_to_x called with text length: {len(text)}")

    if not all([X_API_KEY, X_API_KEY_SECRET, X_ACCESS_TOKEN, X_ACCESS_TOKEN_SECRET]):
        missing = []
        if not X_API_KEY: missing.append("X_API_KEY")
        if not X_API_KEY_SECRET: missing.append("X_API_KEY_SECRET")
        if not X_ACCESS_TOKEN: missing.append("X_ACCESS_TOKEN")
        if not X_ACCESS_TOKEN_SECRET: missing.append("X_ACCESS_TOKEN_SECRET")
        error_detail = f"未設定の環境変数: {', '.join(missing)}"
        logger.error(f"X API credentials not fully set. {error_detail}")
        return False, f"X APIの認証情報が設定されていません。\n{error_detail}"

    # まずtweepyで試行
    client = get_x_client()
    if client:
        try:
            logger.info("Attempting to post tweet via tweepy...")
            response = client.create_tweet(text=text)
            tweet_id = response.data["id"]
            logger.info(f"Tweet posted successfully via tweepy: {tweet_id}")
            return True, tweet_id
        except tweepy.Unauthorized as e:
            logger.error(f"Tweepy 401 Unauthorized: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response status: {e.response.status_code}")
                logger.error(f"Response body: {e.response.text[:500]}")
            logger.info("Falling back to direct HTTP API call...")
        except tweepy.Forbidden as e:
            logger.error(f"Tweepy 403 Forbidden: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response status: {e.response.status_code}")
                logger.error(f"Response body: {e.response.text[:500]}")
            logger.info("Falling back to direct HTTP API call...")
        except tweepy.TweepyException as e:
            logger.error(f"Tweepy failed to post tweet: {e}")
            if hasattr(e, 'response') and e.response is not None:
                logger.error(f"Response status: {e.response.status_code}")
                logger.error(f"Response body: {e.response.text[:500]}")
            logger.info("Falling back to direct HTTP API call...")
        except Exception as e:
            logger.error(f"Unexpected error with tweepy: {e}\n{traceback.format_exc()}")
            logger.info("Falling back to direct HTTP API call...")

    # フォールバック: OAuth 1.0aで直接HTTP呼び出し
    try:
        from requests_oauthlib import OAuth1
        logger.info("Attempting direct HTTP API call with OAuth 1.0a...")
        auth = OAuth1(
            X_API_KEY,
            client_secret=X_API_KEY_SECRET,
            resource_owner_key=X_ACCESS_TOKEN,
            resource_owner_secret=X_ACCESS_TOKEN_SECRET,
        )
        resp = http_requests.post(
            "https://api.x.com/2/tweets",
            json={"text": text},
            auth=auth,
            timeout=30
        )
        logger.info(f"Direct X API response: {resp.status_code} {resp.text[:500]}")
        if resp.status_code in (200, 201):
            data = resp.json()
            tweet_id = data.get("data", {}).get("id", "")
            logger.info(f"Tweet posted successfully via direct API: {tweet_id}")
            return True, tweet_id
        else:
            error_msg = resp.text
            logger.error(f"Direct X API failed: {resp.status_code} {error_msg}")
            # ユーザー向けのわかりやすいエラーメッセージ
            if resp.status_code == 401:
                return False, "X API認証エラー(401)。API KeyまたはAccess Tokenが無効です。X Developer Portalでトークンを再生成してください。"
            elif resp.status_code == 403:
                return False, "X API権限エラー(403)。アプリの権限が不足しています。X Developer Portalで「読み取りと書き込み」権限を設定し、Access Tokenを再生成してください。"
            elif resp.status_code == 429:
                return False, "X APIレート制限(429)。しばらく時間をおいてから再試行してください。"
            else:
                return False, f"X APIエラー ({resp.status_code}): {error_msg[:200]}"
    except Exception as e:
        logger.error(f"Direct X API call failed: {e}\n{traceback.format_exc()}")
        return False, f"X投稿に失敗しました: {str(e)[:200]}"


# ═══════════════════════════════════════════
#  Notion API連携 - シフト管理
# ═══════════════════════════════════════════

def fetch_shift_data_from_notion(year, month):
    """NotionのシフトDBから指定月のシフトデータを取得"""
    if not NOTION_API_KEY:
        logger.error("NOTION_API_KEY is not set")
        return []

    # 月の初日と翌月の初日を計算
    first_day = date(year, month, 1)
    if month == 12:
        next_month_first = date(year + 1, 1, 1)
    else:
        next_month_first = date(year, month + 1, 1)

    url = f"https://api.notion.com/v1/databases/{NOTION_DATABASE_ID}/query"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28",
    }

    # 日付フィルター: 月の範囲内のシフトを取得
    payload = {
        "filter": {
            "and": [
                {
                    "property": "日付",
                    "date": {
                        "on_or_before": (next_month_first - timedelta(days=1)).isoformat()
                    }
                },
                {
                    "property": "日付",
                    "date": {
                        "on_or_after": first_day.isoformat()
                    }
                }
            ]
        },
        "page_size": 100,
    }

    all_results = []
    has_more = True
    start_cursor = None

    while has_more:
        if start_cursor:
            payload["start_cursor"] = start_cursor

        try:
            resp = http_requests.post(url, headers=headers, json=payload, timeout=30)
            resp.raise_for_status()
            data = resp.json()

            for page in data.get("results", []):
                props = page.get("properties", {})

                # タイトル（セラピスト名）
                title_prop = props.get("タイトル", {})
                title_arr = title_prop.get("title", [])
                therapist_name = title_arr[0]["plain_text"] if title_arr else ""

                # 日付
                date_prop = props.get("日付", {})
                date_obj = date_prop.get("date", {})
                if not date_obj:
                    continue
                start_date = date_obj.get("start", "")
                end_date = date_obj.get("end", "")

                # 条件（出勤時間帯）
                condition_prop = props.get("条件", {})
                rich_text = condition_prop.get("rich_text", [])
                condition = "".join(t.get("plain_text", "") for t in rich_text) if rich_text else ""

                # ルーム
                room_prop = props.get("ルーム", {})
                room_select = room_prop.get("select", {})
                room = room_select.get("name", "") if room_select else ""

                all_results.append({
                    "therapist": therapist_name,
                    "start_date": start_date,
                    "end_date": end_date,
                    "condition": condition,
                    "room": room,
                })

            has_more = data.get("has_more", False)
            start_cursor = data.get("next_cursor")

        except Exception as e:
            logger.error(f"Notion API error: {e}\n{traceback.format_exc()}")
            break

    return all_results


def fetch_upcoming_shifts(days=7):
    """今日から指定日数分の出勤情報を取得"""
    if not NOTION_API_KEY:
        return []

    today = date.today()
    end_date = today + timedelta(days=days-1)

    url = f"https://api.notion.com/v1/databases/{NOTION_DATABASE_ID}/query"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28",
    }

    payload = {
        "filter": {
            "and": [
                {
                    "property": "日付",
                    "date": {
                        "on_or_after": today.isoformat()
                    }
                },
                {
                    "property": "日付",
                    "date": {
                        "on_or_before": end_date.isoformat()
                    }
                }
            ]
        },
        "sorts": [{"property": "日付", "direction": "ascending"}],
    }

    shifts = []
    try:
        resp = http_requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        for page in data.get("results", []):
            props = page.get("properties", {})
            
            title_prop = props.get("タイトル", {})
            title_arr = title_prop.get("title", [])
            name = title_arr[0]["plain_text"] if title_arr else ""

            date_prop = props.get("日付", {})
            date_obj = date_prop.get("date", {})
            if not date_obj: continue
            start_date = date_obj.get("start", "")

            condition_prop = props.get("条件", {})
            rich_text = condition_prop.get("rich_text", [])
            condition = "".join(t.get("plain_text", "") for t in rich_text) if rich_text else ""

            room_prop = props.get("ルーム", {})
            room_select = room_prop.get("select", {})
            room = room_select.get("name", "") if room_select else ""

            shifts.append({
                "name": name,
                "date": start_date,
                "condition": condition,
                "room": room
            })
    except Exception as e:
        logger.error(f"Failed to fetch upcoming shifts: {e}")
    
    return shifts


# ═══════════════════════════════════════════
#  Notion API連携 - ニュース管理
# ═══════════════════════════════════════════

def save_news_to_notion(title, body, category):
    """ニュースをNotionデータベースに保存"""
    if not NOTION_API_KEY:
        logger.error("NOTION_API_KEY is not set")
        return None

    url = "https://api.notion.com/v1/pages"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28",
    }

    now = datetime.now()
    payload = {
        "parent": {"database_id": NOTION_NEWS_DATABASE_ID},
        "properties": {
            "タイトル": {
                "title": [{"text": {"content": title}}]
            },
            "本文": {
                "rich_text": [{"text": {"content": body}}]
            },
            "カテゴリ": {
                "select": {"name": category}
            },
            "作成日時": {
                "date": {"start": now.isoformat()}
            },
            "配信済み": {
                "checkbox": False
            }
        }
    }

    try:
        resp = http_requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        logger.info(f"News saved to Notion: {data.get('id')}")
        return data.get("id")
    except Exception as e:
        logger.error(f"Failed to save news to Notion: {e}\n{traceback.format_exc()}")
        return None


def fetch_news_from_notion(limit=10):
    """Notionからニュース一覧を取得"""
    if not NOTION_API_KEY:
        logger.error("NOTION_API_KEY is not set")
        return []

    url = f"https://api.notion.com/v1/databases/{NOTION_NEWS_DATABASE_ID}/query"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28",
    }

    payload = {
        "sorts": [{"property": "作成日時", "direction": "descending"}],
        "page_size": limit,
    }

    try:
        resp = http_requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()

        news_list = []
        for page in data.get("results", []):
            props = page.get("properties", {})
            page_id = page.get("id")

            # タイトル
            title_prop = props.get("タイトル", {})
            title_arr = title_prop.get("title", [])
            title = title_arr[0]["plain_text"] if title_arr else ""

            # 本文
            body_prop = props.get("本文", {})
            body_arr = body_prop.get("rich_text", [])
            body = body_arr[0]["plain_text"] if body_arr else ""

            # カテゴリ
            category_prop = props.get("カテゴリ", {})
            category_select = category_prop.get("select", {})
            category = category_select.get("name", "") if category_select else ""

            # 作成日時
            created_prop = props.get("作成日時", {})
            created_obj = created_prop.get("date", {})
            created = created_obj.get("start", "") if created_obj else ""

            # 配信済み
            delivered_prop = props.get("配信済み", {})
            delivered = delivered_prop.get("checkbox", False)

            news_list.append({
                "id": page_id,
                "title": title,
                "body": body,
                "category": category,
                "created": created,
                "delivered": delivered,
            })

        return news_list
    except Exception as e:
        logger.error(f"Failed to fetch news from Notion: {e}\n{traceback.format_exc()}")
        return []


def mark_news_as_delivered(page_id):
    if not NOTION_API_KEY:
        logger.error("NOTION_API_KEY is not set")
        return False

    url = f"https://api.notion.com/v1/pages/{page_id}"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28",
    }

    now = datetime.now()
    payload = {
        "properties": {
            "配信済み": {"checkbox": True},
            "配信日時": {"date": {"start": now.isoformat()}}
        }
    }

    try:
        resp = http_requests.patch(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        logger.info(f"News marked as delivered: {page_id}")
        return True
    except Exception as e:
        logger.error(f"Failed to mark news as delivered: {e}\n{traceback.format_exc()}")
        return False


def parse_shift_to_calendar(shift_data, year, month):
    """シフトデータをカレンダー形式に変換"""
    cal_data = {}
    for shift in shift_data:
        name = shift["therapist"]
        condition = shift["condition"]
        start_str = shift["start_date"]
        end_str = shift["end_date"]

        if not start_str:
            continue

        start_d = parse_date_safe(start_str)
        if not start_d:
            continue

        if end_str:
            end_d = parse_date_safe(end_str)
            if not end_d:
                end_d = start_d
        else:
            end_d = start_d

        current = start_d
        while current <= end_d:
            if current.year == year and current.month == month:
                day = current.day
                if day not in cal_data:
                    cal_data[day] = []
                cal_data[day].append({
                    "name": name,
                    "condition": condition,
                })
            current += timedelta(days=1)

    return cal_data


def generate_calendar_image(year, month, cal_data):
    """Pillowでカレンダー画像を生成（ダークテーマ）"""

    # フォント設定（複数パスを試行）
    font_bold_path = None
    font_reg_path = None
    font_candidates_bold = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/noto-cjk/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    font_candidates_reg = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/fonts-japanese-gothic.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for p in font_candidates_bold:
        if os.path.exists(p):
            font_bold_path = p
            break
    for p in font_candidates_reg:
        if os.path.exists(p):
            font_reg_path = p
            break

    try:
        if font_bold_path and font_reg_path:
            font_title = ImageFont.truetype(font_bold_path, 36)
            font_day_header = ImageFont.truetype(font_bold_path, 20)
            font_day_num = ImageFont.truetype(font_bold_path, 18)
            font_name = ImageFont.truetype(font_reg_path, 13)
            font_legend = ImageFont.truetype(font_reg_path, 14)
        else:
            raise FileNotFoundError("No suitable font found")
    except Exception as e:
        logger.warning(f"Font loading error: {e}, using default")
        font_title = ImageFont.load_default()
        font_day_header = ImageFont.load_default()
        font_day_num = ImageFont.load_default()
        font_name = ImageFont.load_default()
        font_legend = ImageFont.load_default()

    num_days = calendar.monthrange(year, month)[1]
    first_weekday = calendar.monthrange(year, month)[0]
    first_weekday_sun = (first_weekday + 1) % 7
    total_cells = first_weekday_sun + num_days
    num_rows = (total_cells + 6) // 7

    all_therapists = set()
    for day_shifts in cal_data.values():
        for s in day_shifts:
            all_therapists.add(s["name"])
    therapist_list = sorted(all_therapists)
    therapist_color_map = {}
    for i, name in enumerate(therapist_list):
        therapist_color_map[name] = THERAPIST_COLORS[i % len(THERAPIST_COLORS)]

    cell_w = 150
    cell_h = 110
    header_h = 80
    day_header_h = 35
    legend_h = max(60, 30 + ((len(therapist_list) + 4) // 5) * 28)
    padding = 15
    img_w = cell_w * 7 + padding * 2
    img_h = header_h + day_header_h + cell_h * num_rows + legend_h + padding * 2

    bg_color = "#1a1a2e"
    cell_bg = "#16213e"
    cell_border = "#0f3460"
    today_bg = "#e94560"
    today_border = "#ff6b6b"
    text_white = "#ffffff"
    text_gray = "#a0a0a0"
    sat_color = "#60A5FA"
    sun_color = "#F87171"
    header_bg = "#0f3460"

    img = Image.new("RGB", (img_w, img_h), bg_color)
    draw = ImageDraw.Draw(img)

    draw.rectangle([0, 0, img_w, header_h], fill=header_bg)

    title_text = f"{year}年{month}月 シフトカレンダー"
    bbox = draw.textbbox((0, 0), title_text, font=font_title)
    tw = bbox[2] - bbox[0]
    draw.text(((img_w - tw) // 2, 20), title_text, fill="#f0e6d3", font=font_title)

    weekdays = ["日", "月", "火", "水", "木", "金", "土"]
    y_offset = header_h
    for i, wd in enumerate(weekdays):
        x = padding + i * cell_w
        color = sun_color if i == 0 else (sat_color if i == 6 else text_white)
        bbox = draw.textbbox((0, 0), wd, font=font_day_header)
        tw = bbox[2] - bbox[0]
        draw.text((x + (cell_w - tw) // 2, y_offset + 7), wd, fill=color, font=font_day_header)

    y_offset += day_header_h

    today_val = date.today()
    day_num = 1
    for row in range(num_rows):
        for col in range(7):
            cell_index = row * 7 + col
            if cell_index < first_weekday_sun or day_num > num_days:
                continue

            x = padding + col * cell_w
            y = y_offset + row * cell_h

            is_today = (year == today_val.year and month == today_val.month and day_num == today_val.day)
            bg = today_bg if is_today else cell_bg
            border = today_border if is_today else cell_border

            draw.rectangle([x, y, x + cell_w - 1, y + cell_h - 1], fill=bg, outline=border, width=2)

            day_str = str(day_num)
            bbox = draw.textbbox((0, 0), day_str, font=font_day_num)
            tw = bbox[2] - bbox[0]
            draw.text((x + (cell_w - tw) // 2, y + 5), day_str, fill=text_white, font=font_day_num)

            shifts = cal_data.get(day_num, [])
            name_y = y + 30
            for shift in shifts[:3]:
                name = shift["name"]
                condition = shift["condition"]
                color = therapist_color_map.get(name, text_white)
                text = f"{name} {condition}"
                draw.text((x + 5, name_y), text, fill=color, font=font_name)
                name_y += 18

            if len(shifts) > 3:
                draw.text((x + 5, name_y), f"+{len(shifts) - 3}名", fill=text_gray, font=font_name)

            day_num += 1

    legend_y = y_offset + num_rows * cell_h + 10
    draw.text((padding, legend_y), "セラピスト凡例:", fill=text_white, font=font_legend)
    legend_y += 25

    col_count = 5
    for i, name in enumerate(therapist_list):
        col = i % col_count
        row = i // col_count
        x = padding + col * (img_w // col_count)
        y = legend_y + row * 28
        color = therapist_color_map[name]
        draw.rectangle([x, y, x + 15, y + 15], fill=color)
        draw.text((x + 20, y), name, fill=text_white, font=font_legend)

    return img


# ═══════════════════════════════════════════
#  Flask ルート
# ═══════════════════════════════════════════

@app.route("/callback", methods=["POST"])
def callback():
    signature = request.headers.get("X-Line-Signature", "")
    body = request.get_data(as_text=True)
    logger.info(f"Request body: {body}")

    try:
        handler.handle(body, signature)
    except InvalidSignatureError:
        logger.error("Invalid signature")
        abort(400)

    return "OK"


@app.route("/")
def index():
    return jsonify({
        "status": "running",
        "bot_name": "全力エステ LINE Bot",
        "version": "2.1"
    })


@app.route("/static/images/<path:filename>")
def serve_image(filename):
    return send_from_directory(UPLOAD_DIR, filename)


# ═══════════════════════════════════════════
#  Flex Message構築
# ═══════════════════════════════════════════

def build_main_menu_flex():
    """メインメニューのFlex Message"""
    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "🏆 全力エステ オーナー用",
                    "weight": "bold",
                    "size": "xl",
                    "color": "#1a1a2e",
                    "align": "center"
                }
            ],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "メニューを選択してください",
                    "size": "sm",
                    "color": "#888888",
                    "align": "center",
                    "margin": "md"
                },
                {
                    "type": "separator",
                    "margin": "lg"
                },
                {
                    "type": "box",
                    "layout": "vertical",
                    "contents": [
                        make_menu_button("📅 スケジュール確認", "スケジュール確認"),
                        make_menu_button("🚶 直近の出勤情報", "出勤情報"),
                        make_menu_button("📰 ニュース作成", "ニュース作成"),
                        make_menu_button("📋 ニュース一覧", "ニュース一覧"),
                        make_menu_button("📢 ニュース配信", "ニュース配信"),
                        make_menu_button("🐦 X投稿", "X投稿"),
                    ],
                    "margin": "lg",
                    "spacing": "sm"
                }
            ],
            "paddingAll": "15px"
        },
        "styles": {
            "header": {"separator": False},
            "body": {"separator": False}
        }
    }
    return FlexMessage(
        alt_text="全力エステ メインメニュー",
        contents=FlexContainer.from_dict(flex_json)
    )


def make_menu_button(label, text):
    """メニューボタン1つを作成"""
    return {
        "type": "button",
        "action": {
            "type": "message",
            "label": label,
            "text": text
        },
        "style": "primary",
        "color": "#1a1a2e",
        "height": "sm",
        "margin": "sm"
    }


def build_upcoming_shifts_flex(shifts):
    """直近の出勤情報のFlex Message"""
    if not shifts:
        content = [{"type": "text", "text": "直近の出勤予定はありません", "align": "center", "margin": "md"}]
    else:
        content = []
        current_date = ""
        for s in shifts:
            if s["date"] != current_date:
                current_date = s["date"]
                dt_parsed = parse_date_safe(current_date)
                dt = datetime.combine(dt_parsed, datetime.min.time()) if dt_parsed else datetime.now()
                content.append({
                    "type": "text",
                    "text": f"📅 {dt.strftime('%m/%d')}({['月','火','水','木','金','土','日'][dt.weekday()]})",
                    "weight": "bold",
                    "size": "sm",
                    "margin": "lg",
                    "color": "#0f3460"
                })
                content.append({"type": "separator", "margin": "xs"})
            
            content.append({
                "type": "box",
                "layout": "horizontal",
                "contents": [
                    {"type": "text", "text": s["name"], "weight": "bold", "size": "sm", "flex": 3},
                    {"type": "text", "text": s["condition"], "size": "sm", "flex": 3},
                    {"type": "text", "text": s["room"], "size": "xs", "color": "#888888", "flex": 4, "align": "end"}
                ],
                "margin": "sm"
            })

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "🚶 直近1週間の出勤情報", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": content + [
                {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "xl"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="出勤情報", contents=FlexContainer.from_dict(flex_json))


# ═══════════════════════════════════════════
#  ニュース生成
# ═══════════════════════════════════════════

def generate_news(topic=None):
    """OpenAI APIでニュース文面を自動生成"""
    prompt = f"""あなたはメンズエステサロン「全力エステ」の広報担当です。
エステ魂（メンズエステ情報サイト）向けのニュース記事を作成してください。

【店舗情報】
- 店名: {SHOP_INFO['name']}
- 所在地: {SHOP_INFO['location']}
- コンセプト: {SHOP_INFO['concept']}
- 在籍セラピスト: {', '.join(SHOP_INFO['therapists'])}

【要件】
- タイトル: 30文字以内（魅力的で目を引くもの）
- 本文: 1000〜1500文字
- トーン: 高級感がありつつも親しみやすい
- 内容: サロンの魅力、セラピストの技術力、お客様への特別な体験を訴求
{f'- テーマ/トピック: {topic}' if topic else '- テーマ: 季節やトレンドに合わせた内容を自由に選択'}

【出力形式】
以下のJSON形式で出力してください。他の文字は含めないでください。
{{"title": "タイトル", "body": "本文"}}
"""
    try:
        response = openai_client.chat.completions.create(
            model="gpt-4.1-mini",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.8,
            max_tokens=2000,
        )
        content = response.choices[0].message.content.strip()
        if "```json" in content:
            content = content.split("```json")[1].split("```")[0].strip()
        elif "```" in content:
            content = content.split("```")[1].split("```")[0].strip()
        result = json.loads(content)
        return result
    except Exception as e:
        logger.error(f"News generation error: {e}")
        return {
            "title": "全力エステからのお知らせ",
            "body": "ニュースの生成中にエラーが発生しました。もう一度お試しください。"
        }


def build_news_category_select_flex():
    """ニュースカテゴリ選択のFlex Message"""
    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📰 ニュース作成", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": "カテゴリを選択してください", "size": "sm", "color": "#888888", "align": "center", "margin": "md"},
                {"type": "separator", "margin": "lg"},
                {"type": "box", "layout": "vertical", "contents": [
                    make_menu_button("📢 お知らせ", "カテゴリ_お知らせ"),
                    make_menu_button("🎉 キャンペーン", "カテゴリ_キャンペーン"),
                    make_menu_button("✨ 新メニュー", "カテゴリ_新メニュー"),
                    make_menu_button("💆 セラピスト紹介", "カテゴリ_セラピスト紹介"),
                    make_menu_button("📝 その他", "カテゴリ_その他"),
                ], "margin": "lg", "spacing": "sm"},
                {"type": "separator", "margin": "lg"},
                {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="ニュースカテゴリ選択", contents=FlexContainer.from_dict(flex_json))


def build_news_confirm_flex(news_data, category):
    """ニュース確認用のFlex Message"""
    title = news_data.get("title", "")
    body = news_data.get("body", "")
    display_body = body[:200] + "..." if len(body) > 200 else body

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📰 ニュース プレビュー", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": f"📌 {title}", "weight": "bold", "size": "md", "wrap": True},
                {"type": "separator", "margin": "md"},
                {"type": "text", "text": f"カテゴリ: {category}", "size": "xs", "color": "#888888", "margin": "md"},
                {"type": "separator", "margin": "md"},
                {"type": "text", "text": display_body, "size": "sm", "wrap": True, "margin": "md"},
                {"type": "text", "text": f"（全{len(body)}文字）", "size": "xs", "color": "#888888", "align": "right", "margin": "sm"},
                {"type": "separator", "margin": "lg"},
                {"type": "box", "layout": "vertical", "contents": [
                    {"type": "button", "action": {"type": "message", "label": "✅ この内容で保存", "text": "ニュース保存"}, "style": "primary", "color": "#1a1a2e"},
                    {"type": "button", "action": {"type": "message", "label": "🔄 再生成", "text": "ニュース再生成"}, "style": "secondary", "margin": "sm"},
                    {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "sm"}
                ], "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="ニュース プレビュー", contents=FlexContainer.from_dict(flex_json))


def build_news_list_flex(news_list):
    """ニュース一覧のFlex Message"""
    if not news_list:
        flex_json = {
            "type": "bubble",
            "size": "mega",
            "header": {
                "type": "box",
                "layout": "vertical",
                "contents": [{"type": "text", "text": "📋 ニュース一覧", "weight": "bold", "size": "lg", "align": "center"}],
                "backgroundColor": "#f0e6d3",
                "paddingAll": "15px"
            },
            "body": {
                "type": "box",
                "layout": "vertical",
                "contents": [
                    {"type": "text", "text": "保存されたニュースがありません", "size": "sm", "color": "#888888", "align": "center", "margin": "md"},
                    {"type": "separator", "margin": "lg"},
                    {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
                ],
                "paddingAll": "15px"
            }
        }
        return FlexMessage(alt_text="ニュース一覧", contents=FlexContainer.from_dict(flex_json))

    news_items = []
    for i, news in enumerate(news_list[:5]):
        status = "✅ 配信済み" if news["delivered"] else "📝 未配信"
        news_items.append({
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": f"{i+1}. {news['title']}", "weight": "bold", "size": "sm", "wrap": True},
                {"type": "text", "text": f"{news['category']} | {status}", "size": "xs", "color": "#888888", "margin": "xs"},
                {"type": "button", "action": {"type": "message", "label": "詳細を見る", "text": f"ニュース詳細_{i}"}, "style": "link", "height": "sm"}
            ],
            "margin": "md", "paddingAll": "10px", "backgroundColor": "#f5f5f5", "cornerRadius": "md"
        })
        if i < len(news_list) - 1:
            news_items.append({"type": "separator", "margin": "md"})

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📋 ニュース一覧", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": f"保存されたニュース（{len(news_list)}件）", "size": "sm", "color": "#888888", "align": "center"},
                {"type": "separator", "margin": "md"},
                *news_items,
                {"type": "separator", "margin": "lg"},
                {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="ニュース一覧", contents=FlexContainer.from_dict(flex_json))


def build_news_detail_flex(news):
    """ニュース詳細のFlex Message"""
    title = news.get("title", "")
    body = news.get("body", "")
    category = news.get("category", "")
    delivered = news.get("delivered", False)
    status = "✅ 配信済み" if delivered else "📝 未配信"
    display_body = body[:300] + "..." if len(body) > 300 else body

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📰 ニュース詳細", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": f"📌 {title}", "weight": "bold", "size": "md", "wrap": True},
                {"type": "separator", "margin": "md"},
                {"type": "text", "text": f"カテゴリ: {category} | {status}", "size": "xs", "color": "#888888", "margin": "md"},
                {"type": "separator", "margin": "md"},
                {"type": "text", "text": display_body, "size": "sm", "wrap": True, "margin": "md"},
                {"type": "text", "text": f"（全{len(body)}文字）", "size": "xs", "color": "#888888", "align": "right", "margin": "sm"},
                {"type": "separator", "margin": "lg"},
                {"type": "button", "action": {"type": "message", "label": "🔙 一覧に戻る", "text": "ニュース一覧"}, "style": "secondary", "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="ニュース詳細", contents=FlexContainer.from_dict(flex_json))


def build_news_delivery_select_flex(news_list):
    """ニュース配信選択のFlex Message"""
    if not news_list:
        flex_json = {
            "type": "bubble",
            "size": "mega",
            "header": {
                "type": "box",
                "layout": "vertical",
                "contents": [{"type": "text", "text": "📢 ニュース配信", "weight": "bold", "size": "lg", "align": "center"}],
                "backgroundColor": "#f0e6d3",
                "paddingAll": "15px"
            },
            "body": {
                "type": "box",
                "layout": "vertical",
                "contents": [
                    {"type": "text", "text": "配信可能なニュースがありません", "size": "sm", "color": "#888888", "align": "center", "margin": "md"},
                    {"type": "separator", "margin": "lg"},
                    {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
                ],
                "paddingAll": "15px"
            }
        }
        return FlexMessage(alt_text="ニュース配信", contents=FlexContainer.from_dict(flex_json))

    news_items = []
    for i, news in enumerate(news_list[:5]):
        news_items.append({
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": f"{i+1}. {news['title']}", "weight": "bold", "size": "sm", "wrap": True},
                {"type": "text", "text": f"{news['category']}", "size": "xs", "color": "#888888", "margin": "xs"},
                {"type": "button", "action": {"type": "message", "label": "📢 このニュースを配信", "text": f"配信実行_{i}"}, "style": "primary", "color": "#1a1a2e"}
            ],
            "margin": "md", "paddingAll": "10px", "backgroundColor": "#f5f5f5", "cornerRadius": "md"
        })
        if i < len(news_list) - 1:
            news_items.append({"type": "separator", "margin": "md"})

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📢 ニュース配信", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": "配信するニュースを選択してください", "size": "sm", "color": "#888888", "align": "center"},
                {"type": "separator", "margin": "md"},
                *news_items,
                {"type": "separator", "margin": "lg"},
                {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="ニュース配信", contents=FlexContainer.from_dict(flex_json))


def build_x_post_confirm_flex(post_text):
    """X投稿確認用のFlex Message"""
    display_text = post_text[:200] + "..." if len(post_text) > 200 else post_text
    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "\ud83d\udc26 X投稿 確認", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": "以下の内容でXに投稿します", "size": "sm", "color": "#888888", "align": "center", "margin": "md"},
                {"type": "separator", "margin": "lg"},
                {"type": "text", "text": display_text, "size": "sm", "wrap": True, "margin": "md"},
                {"type": "text", "text": f"（{len(post_text)}文字）", "size": "xs", "color": "#888888", "align": "right", "margin": "sm"},
                {"type": "separator", "margin": "lg"},
                {"type": "box", "layout": "vertical", "contents": [
                    {"type": "button", "action": {"type": "message", "label": "\u2705 投稿する", "text": "X投稿実行"}, "style": "primary", "color": "#1a1a2e"},
                    {"type": "button", "action": {"type": "message", "label": "\u270f\ufe0f 修正する", "text": "X投稿修正"}, "style": "secondary", "margin": "sm"},
                    {"type": "button", "action": {"type": "message", "label": "\ud83d\udd19 キャンセル", "text": "X投稿キャンセル"}, "style": "secondary", "margin": "sm"}
                ], "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="X投稿 確認", contents=FlexContainer.from_dict(flex_json))


def build_schedule_month_select_flex():
    """スケジュール月選択のFlex Message"""
    now = datetime.now()
    this_month = f"{now.month}月"
    next_month = "1月" if now.month == 12 else f"{now.month + 1}月"

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [{"type": "text", "text": "📅 スケジュール確認", "weight": "bold", "size": "lg", "align": "center"}],
            "backgroundColor": "#f0e6d3",
            "paddingAll": "15px"
        },
        "body": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {"type": "text", "text": "表示する月を選択してください", "size": "sm", "color": "#888888", "align": "center", "margin": "md"},
                {"type": "separator", "margin": "lg"},
                make_menu_button(f"📅 今月（{this_month}）", "スケジュール_今月"),
                make_menu_button(f"📅 来月（{next_month}）", "スケジュール_来月"),
                {"type": "button", "action": {"type": "message", "label": "🔙 メニューに戻る", "text": "メニュー"}, "style": "secondary", "margin": "lg"}
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(alt_text="スケジュール確認 - 月を選択", contents=FlexContainer.from_dict(flex_json))


def process_schedule_request(year, month, event):
    """スケジュールリクエストを処理してカレンダー画像を送信"""
    line_api = get_messaging_api()
    push_target = get_push_target(event)

    shift_data = fetch_shift_data_from_notion(year, month)
    
    if not shift_data:
        if push_target:
            line_api.push_message(PushMessageRequest(to=push_target, messages=[
                TextMessage(text=f"📅 {year}年{month}月のシフトデータが見つかりませんでした。"),
                build_main_menu_flex()
            ]))
        return

    # 今日のスケジュールをテキストで構築
    today_val = date.today()
    today_shifts = [s for s in shift_data if parse_date_safe(s["start_date"]) == today_val]
    
    today_text = f"📅 本日({date.today().strftime('%m/%d')})のスケジュール\n"
    if today_shifts:
        for s in today_shifts:
            today_text += f"・{s['therapist']}: {s['condition']} ({s['room']})\n"
    else:
        today_text += "本日の出勤予定はありません。"

    cal_data = parse_shift_to_calendar(shift_data, year, month)
    img = generate_calendar_image(year, month, cal_data)
    filename = f"schedule_{year}_{month:02d}_{uuid.uuid4().hex[:8]}.png"
    filepath = os.path.join(UPLOAD_DIR, filename)
    img.save(filepath, "PNG")

    image_url = f"{BASE_URL}/static/images/{filename}"

    if push_target:
        line_api.push_message(PushMessageRequest(to=push_target, messages=[
            TextMessage(text=today_text),
            TextMessage(text=f"📅 {year}年{month}月のシフトカレンダーです"),
            ImageMessage(original_content_url=image_url, preview_image_url=image_url)
        ]))


# ═══════════════════════════════════════════
#  イベントハンドラ
# ═══════════════════════════════════════════

def get_session_key(event):
    source = event.source
    if hasattr(source, 'group_id') and source.group_id:
        return f"group_{source.group_id}_{source.user_id}"
    elif hasattr(source, 'room_id') and source.room_id:
        return f"room_{source.room_id}_{source.user_id}"
    else:
        return f"user_{source.user_id}"


@handler.add(FollowEvent)
def handle_follow(event):
    line_api = get_messaging_api()
    line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
        TextMessage(text="🏆 全力エステ オーナー用LINEへようこそ！\n\n「メニュー」と入力するとオーナー専用メニューが表示されます。"),
        build_main_menu_flex()
    ]))


@handler.add(JoinEvent)
def handle_join(event):
    line_api = get_messaging_api()
    line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
        TextMessage(text="🏆 全力エステ オーナーBotが参加しました！\n\n「メニュー」と入力するとオーナー専用メニューが表示されます。"),
        build_main_menu_flex()
    ]))


@handler.add(MessageEvent, message=TextMessageContent)
def handle_text_message(event):
    text = event.message.text.strip()
    session_key = get_session_key(event)
    line_api = get_messaging_api()

    session = user_sessions.get(session_key, {})
    state = session.get("state", "idle")

    if text in ["メニュー", "menu", "Menu", "MENU", "めにゅー"]:
        user_sessions.pop(session_key, None)
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_main_menu_flex()]))
        return

    if text == "出勤情報":
        user_sessions.pop(session_key, None)
        shifts = fetch_upcoming_shifts(days=7)
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_upcoming_shifts_flex(shifts)]))
        return

    if text == "ニュース作成":
        user_sessions[session_key] = {"state": "news_category_select"}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_news_category_select_flex()]))
        return

    if text.startswith("カテゴリ_") and state == "news_category_select":
        category = text.replace("カテゴリ_", "")
        user_sessions[session_key] = {"state": "news_topic", "category": category}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            TextMessage(text=f"📰 ニュース作成（カテゴリ: {category}）\n\nニュースのテーマを入力してください。\n（例：新人セラピスト紹介、春のキャンペーン）\n\n「おまかせ」と入力するとAIが自動でテーマを選びます。")
        ]))
        return

    if state == "news_topic":
        topic = None if text in ["おまかせ", "お任せ", "自動"] else text
        category = session.get("category", "その他")
        user_sessions[session_key] = {"state": "news_generating", "category": category}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text="📝 ニュースを生成中です...\nしばらくお待ちください。")]))
        news = generate_news(topic)
        user_sessions[session_key] = {"state": "news_preview", "news": news, "category": category, "topic": topic}
        push_target = get_push_target(event)
        if push_target:
            line_api.push_message(PushMessageRequest(to=push_target, messages=[build_news_confirm_flex(news, category)]))
        return

    if text == "ニュース再生成" and state == "news_preview":
        topic = session.get("topic")
        category = session.get("category", "その他")
        user_sessions[session_key]["state"] = "news_generating"
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text="🔄 ニュースを再生成中です...")] ))
        news = generate_news(topic)
        user_sessions[session_key] = {"state": "news_preview", "news": news, "category": category, "topic": topic}
        push_target = get_push_target(event)
        if push_target:
            line_api.push_message(PushMessageRequest(to=push_target, messages=[build_news_confirm_flex(news, category)]))
        return

    if text == "ニュース保存" and state == "news_preview":
        news = session.get("news", {})
        category = session.get("category", "その他")
        page_id = save_news_to_notion(news.get("title", ""), news.get("body", ""), category)
        msg = "✅ ニュースを保存しました！" if page_id else "⚠️ 保存に失敗しました。"
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text=msg), build_main_menu_flex()]))
        user_sessions.pop(session_key, None)
        return

    if text == "ニュース一覧":
        user_sessions[session_key] = {"state": "news_list"}
        news_list = fetch_news_from_notion(limit=10)
        user_sessions[session_key]["news_list"] = news_list
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_news_list_flex(news_list)]))
        return

    if text.startswith("ニュース詳細_") and state == "news_list":
        try:
            index = int(text.replace("ニュース詳細_", ""))
            news_list = session.get("news_list", [])
            if 0 <= index < len(news_list):
                news = news_list[index]
                full_text = f"📰 {news['title']}\n{'─' * 20}\nカテゴリ: {news['category']}\n{'─' * 20}\n{news['body']}"
                line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text=full_text), build_news_detail_flex(news)]))
        except: pass
        return

    if text == "ニュース配信":
        user_sessions[session_key] = {"state": "news_delivery"}
        news_list = fetch_news_from_notion(limit=10)
        user_sessions[session_key]["news_list"] = news_list
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_news_delivery_select_flex(news_list)]))
        return

    if text.startswith("配信実行_") and state == "news_delivery":
        try:
            index = int(text.replace("配信実行_", ""))
            news_list = session.get("news_list", [])
            if 0 <= index < len(news_list):
                news = news_list[index]
                line_api.broadcast(BroadcastRequest(messages=[TextMessage(text=f"📰 {news['title']}\n{'─' * 20}\n{news['body']}\n{'─' * 20}\n🏆 全力エステ")]))
                mark_news_as_delivered(news["id"])
                line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text="✅ ニュースを配信しました！"), build_main_menu_flex()]))
        except: pass
        user_sessions.pop(session_key, None)
        return

    # ─── X投稿フロー ───
    if text == "X投稿":
        user_sessions[session_key] = {"state": "x_post_input"}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            TextMessage(text="🐦 X（Twitter）投稿\n\n投稿したい内容を入力してください。\n（最大280文字）\n\n「キャンセル」でメニューに戻ります。")
        ]))
        return

    if state == "x_post_input":
        if text in ["キャンセル", "cancel"]:
            user_sessions.pop(session_key, None)
            line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
                TextMessage(text="X投稿をキャンセルしました。"),
                build_main_menu_flex()
            ]))
            return
        # 文字数チェック
        if len(text) > 280:
            line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
                TextMessage(text=f"⚠️ 投稿内容が280文字を超えています（{len(text)}文字）。\n280文字以内に修正して再入力してください。")
            ]))
            return
        # 確認フローへ
        user_sessions[session_key] = {"state": "x_post_confirm", "x_post_text": text}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            build_x_post_confirm_flex(text)
        ]))
        return

    if text == "X投稿実行" and state == "x_post_confirm":
        post_text = session.get("x_post_text", "")
        if not post_text:
            line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
                TextMessage(text="⚠️ 投稿内容が見つかりません。もう一度やり直してください。"),
                build_main_menu_flex()
            ]))
            user_sessions.pop(session_key, None)
            return
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            TextMessage(text="🐦 Xに投稿中...")
        ]))
        push_target = get_push_target(event)
        try:
            success, result = post_to_x(post_text)
            if push_target:
                if success:
                    tweet_url = f"https://x.com/i/status/{result}"
                    line_api.push_message(PushMessageRequest(to=push_target, messages=[
                        TextMessage(text=f"✅ Xに投稿しました！\n\n🔗 {tweet_url}"),
                        build_main_menu_flex()
                    ]))
                else:
                    line_api.push_message(PushMessageRequest(to=push_target, messages=[
                        TextMessage(text=f"❌ X投稿に失敗しました。\n\nエラー: {result}"),
                        build_main_menu_flex()
                    ]))
        except Exception as e:
            logger.error(f"X post handler error: {e}\n{traceback.format_exc()}")
            if push_target:
                line_api.push_message(PushMessageRequest(to=push_target, messages=[
                    TextMessage(text=f"❌ X投稿処理中にエラーが発生しました。\n\nエラー: {str(e)[:200]}"),
                    build_main_menu_flex()
                ]))
        user_sessions.pop(session_key, None)
        return

    if text == "X投稿キャンセル" and state == "x_post_confirm":
        user_sessions.pop(session_key, None)
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            TextMessage(text="X投稿をキャンセルしました。"),
            build_main_menu_flex()
        ]))
        return

    if text == "X投稿修正" and state == "x_post_confirm":
        user_sessions[session_key] = {"state": "x_post_input"}
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[
            TextMessage(text="🐦 投稿内容を再入力してください。\n（最大280文字）")
        ]))
        return

    if text == "スケジュール確認":
        user_sessions.pop(session_key, None)
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[build_schedule_month_select_flex()]))
        return

    if text == "スケジュール_今月":
        user_sessions.pop(session_key, None)
        now = datetime.now()
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text=f"📅 {now.year}年{now.month}月のシフトカレンダーを作成中です...")] ))
        process_schedule_request(now.year, now.month, event)
        return

    if text == "スケジュール_来月":
        user_sessions.pop(session_key, None)
        now = datetime.now()
        target_year, target_month = (now.year + 1, 1) if now.month == 12 else (now.year, now.month + 1)
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text=f"📅 {target_year}年{target_month}月のシフトカレンダーを作成中です...")] ))
        process_schedule_request(target_year, target_month, event)
        return

    if state == "idle":
        line_api.reply_message(ReplyMessageRequest(reply_token=event.reply_token, messages=[TextMessage(text="「メニュー」と入力するとメニューが表示されます。"), build_main_menu_flex()]))


def get_push_target(event):
    source = event.source
    if hasattr(source, 'group_id') and source.group_id: return source.group_id
    if hasattr(source, 'room_id') and source.room_id: return source.room_id
    return source.user_id


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    logger.info(f"Starting 全力エステ LINE Bot on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
