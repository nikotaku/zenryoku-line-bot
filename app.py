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
#  Notion API連携
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
    # start日付が月末以前 AND (start日付が月初以降 OR end日付が月初以降)
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
                condition = rich_text[0]["plain_text"] if rich_text else ""

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


def parse_shift_to_calendar(shift_data, year, month):
    """シフトデータをカレンダー形式に変換
    戻り値: {day: [{"name": セラピスト名, "condition": 時間帯}, ...]}
    """
    cal_data = {}
    num_days = calendar.monthrange(year, month)[1]

    for shift in shift_data:
        name = shift["therapist"]
        condition = shift["condition"]
        start_str = shift["start_date"]
        end_str = shift["end_date"]

        if not start_str:
            continue

        try:
            start_d = date.fromisoformat(start_str)
        except ValueError:
            continue

        if end_str:
            try:
                end_d = date.fromisoformat(end_str)
            except ValueError:
                end_d = start_d
        else:
            end_d = start_d

        # 日付範囲をループ
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

    # フォント設定
    try:
        font_title = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 36)
        font_day_header = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 20)
        font_day_num = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", 18)
        font_name = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 13)
        font_legend = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 14)
    except Exception as e:
        logger.warning(f"Font loading error: {e}, using default")
        font_title = ImageFont.load_default()
        font_day_header = ImageFont.load_default()
        font_day_num = ImageFont.load_default()
        font_name = ImageFont.load_default()
        font_legend = ImageFont.load_default()

    # カレンダー情報
    num_days = calendar.monthrange(year, month)[1]
    first_weekday = calendar.monthrange(year, month)[0]  # 0=月曜
    # 日曜始まりに変換
    first_weekday_sun = (first_weekday + 1) % 7
    total_cells = first_weekday_sun + num_days
    num_rows = (total_cells + 6) // 7

    # セラピスト名のユニークリストと色マッピング
    all_therapists = set()
    for day_shifts in cal_data.values():
        for s in day_shifts:
            all_therapists.add(s["name"])
    therapist_list = sorted(all_therapists)
    therapist_color_map = {}
    for i, name in enumerate(therapist_list):
        therapist_color_map[name] = THERAPIST_COLORS[i % len(THERAPIST_COLORS)]

    # 画像サイズ計算
    cell_w = 150
    cell_h = 110
    header_h = 80
    day_header_h = 35
    legend_h = max(60, 30 + ((len(therapist_list) + 4) // 5) * 28)
    padding = 15
    img_w = cell_w * 7 + padding * 2
    img_h = header_h + day_header_h + cell_h * num_rows + legend_h + padding * 2

    # ダークテーマカラー
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

    # ヘッダー背景
    draw.rectangle([0, 0, img_w, header_h], fill=header_bg)

    # タイトル
    title_text = f"{year}年{month}月 シフトカレンダー"
    bbox = draw.textbbox((0, 0), title_text, font=font_title)
    tw = bbox[2] - bbox[0]
    draw.text(((img_w - tw) // 2, 20), title_text, fill="#f0e6d3", font=font_title)

    # 曜日ヘッダー
    weekdays = ["日", "月", "火", "水", "木", "金", "土"]
    y_start = header_h
    for i, wd in enumerate(weekdays):
        x = padding + i * cell_w
        # 曜日ヘッダー背景
        draw.rectangle([x, y_start, x + cell_w - 1, y_start + day_header_h], fill="#0a1628")
        bbox = draw.textbbox((0, 0), wd, font=font_day_header)
        tw = bbox[2] - bbox[0]
        if i == 0:  # 日曜
            color = sun_color
        elif i == 6:  # 土曜
            color = sat_color
        else:
            color = text_white
        draw.text((x + (cell_w - tw) // 2, y_start + 7), wd, fill=color, font=font_day_header)

    # 今日の日付
    today = date.today()

    # カレンダーセル描画
    y_base = header_h + day_header_h
    for day in range(1, num_days + 1):
        cell_index = first_weekday_sun + day - 1
        col = cell_index % 7
        row = cell_index // 7

        x = padding + col * cell_w
        y = y_base + row * cell_h

        # 今日のハイライト
        is_today = (year == today.year and month == today.month and day == today.day)

        if is_today:
            draw.rectangle([x + 1, y + 1, x + cell_w - 2, y + cell_h - 2], fill="#2a1a3e", outline=today_border, width=2)
        else:
            draw.rectangle([x + 1, y + 1, x + cell_w - 2, y + cell_h - 2], fill=cell_bg, outline=cell_border, width=1)

        # 日付番号
        day_str = str(day)
        if col == 0:  # 日曜
            day_color = sun_color
        elif col == 6:  # 土曜
            day_color = sat_color
        else:
            day_color = text_white

        if is_today:
            # 今日の日付は丸背景
            bbox = draw.textbbox((0, 0), day_str, font=font_day_num)
            tw = bbox[2] - bbox[0]
            th = bbox[3] - bbox[1]
            circle_r = max(tw, th) // 2 + 5
            cx = x + 18
            cy = y + 16
            draw.ellipse([cx - circle_r, cy - circle_r, cx + circle_r, cy + circle_r], fill=today_bg)
            draw.text((cx - tw // 2, cy - th // 2 - 2), day_str, fill=text_white, font=font_day_num)
        else:
            draw.text((x + 6, y + 4), day_str, fill=day_color, font=font_day_num)

        # シフト情報
        shifts = cal_data.get(day, [])
        name_y = y + 28
        max_display = 5  # 最大表示数
        for idx, shift in enumerate(shifts[:max_display]):
            name = shift["name"]
            color = therapist_color_map.get(name, "#ffffff")
            # 名前の短縮表示（セル幅に収まるように）
            display_name = name
            if len(display_name) > 5:
                display_name = display_name[:4] + ".."
            draw.text((x + 6, name_y), display_name, fill=color, font=font_name)
            name_y += 16
            if name_y > y + cell_h - 8:
                break

        if len(shifts) > max_display:
            draw.text((x + 6, name_y), f"+{len(shifts) - max_display}名", fill=text_gray, font=font_name)

    # 凡例（レジェンド）
    legend_y = y_base + num_rows * cell_h + 10
    draw.rectangle([padding, legend_y, img_w - padding, legend_y + legend_h - 10], fill="#0a1628", outline=cell_border)
    draw.text((padding + 10, legend_y + 6), "■ セラピスト凡例", fill=text_white, font=font_legend)

    legend_x = padding + 10
    legend_item_y = legend_y + 30
    col_width = (img_w - padding * 2 - 20) // 5

    for i, name in enumerate(therapist_list):
        col_idx = i % 5
        row_idx = i // 5
        lx = legend_x + col_idx * col_width
        ly = legend_item_y + row_idx * 24
        color = therapist_color_map[name]
        draw.rectangle([lx, ly + 2, lx + 12, ly + 14], fill=color)
        draw.text((lx + 16, ly), name, fill=color, font=font_legend)

    return img


# ═══════════════════════════════════════════
#  ヘルスチェック
# ═══════════════════════════════════════════

@app.route("/", methods=["GET"])
def index():
    return jsonify({"status": "ok", "bot": "全力エステ LINE Bot"})

# ─── 静的ファイル配信 ───
@app.route("/static/images/<path:filename>")
def serve_image(filename):
    return send_from_directory(UPLOAD_DIR, filename)

# ─── BASE_URL 設定用エンドポイント ───
@app.route("/set-base-url", methods=["POST"])
def set_base_url():
    global BASE_URL
    data = request.get_json()
    BASE_URL = data.get("base_url", "").rstrip("/")
    logger.info(f"BASE_URL set to: {BASE_URL}")
    return jsonify({"status": "ok", "base_url": BASE_URL})

# ─── Webhook ───
@app.route("/callback", methods=["POST"])
def callback():
    signature = request.headers.get("X-Line-Signature", "")
    body = request.get_data(as_text=True)
    logger.info(f"Webhook received: {body[:200]}")
    try:
        handler.handle(body, signature)
    except InvalidSignatureError:
        logger.error("Invalid signature")
        abort(400)
    except Exception as e:
        logger.error(f"Error handling webhook: {e}\n{traceback.format_exc()}")
    return "OK"


# ═══════════════════════════════════════════
#  メニュー構築
# ═══════════════════════════════════════════

def build_main_menu_flex():
    """メインメニューのFlex Messageを構築"""
    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "🏆 全力エステ",
                    "weight": "bold",
                    "size": "xl",
                    "color": "#1a1a2e",
                    "align": "center"
                },
                {
                    "type": "text",
                    "text": "仙台No.1を本気で狙うハイレベルサロン",
                    "size": "xs",
                    "color": "#666666",
                    "align": "center",
                    "margin": "sm"
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
                        make_menu_button("📰 ニュース投稿", "ニュース投稿"),
                        make_menu_button("📅 スケジュール確認", "スケジュール確認"),
                        make_menu_button("💆 セラピスト一覧", "セラピスト一覧"),
                        make_menu_button("🏠 店舗情報", "店舗情報"),
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


def build_therapist_flex():
    """セラピスト一覧のFlex Message"""
    therapist_boxes = []
    for t in SHOP_INFO["therapists"]:
        therapist_boxes.append({
            "type": "box",
            "layout": "horizontal",
            "contents": [
                {
                    "type": "text",
                    "text": "💆",
                    "size": "md",
                    "flex": 0
                },
                {
                    "type": "text",
                    "text": t,
                    "size": "md",
                    "color": "#1a1a2e",
                    "weight": "bold",
                    "margin": "md"
                }
            ],
            "margin": "md",
            "paddingAll": "5px"
        })

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "💆 セラピスト一覧",
                    "weight": "bold",
                    "size": "lg",
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
                    "text": f"在籍セラピスト（{len(SHOP_INFO['therapists'])}名）",
                    "size": "sm",
                    "color": "#888888",
                    "align": "center"
                },
                {
                    "type": "separator",
                    "margin": "md"
                },
                *therapist_boxes,
                {
                    "type": "separator",
                    "margin": "lg"
                },
                {
                    "type": "button",
                    "action": {
                        "type": "message",
                        "label": "🔙 メニューに戻る",
                        "text": "メニュー"
                    },
                    "style": "secondary",
                    "height": "sm",
                    "margin": "lg"
                }
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(
        alt_text="セラピスト一覧",
        contents=FlexContainer.from_dict(flex_json)
    )


def build_shop_info_flex():
    """店舗情報のFlex Message"""
    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "🏠 店舗情報",
                    "weight": "bold",
                    "size": "lg",
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
                info_row("店名", SHOP_INFO["name"]),
                {"type": "separator", "margin": "md"},
                info_row("所在地", SHOP_INFO["location"]),
                {"type": "separator", "margin": "md"},
                {
                    "type": "box",
                    "layout": "vertical",
                    "contents": [
                        {
                            "type": "text",
                            "text": "コンセプト",
                            "size": "xs",
                            "color": "#888888"
                        },
                        {
                            "type": "text",
                            "text": SHOP_INFO["concept"],
                            "size": "sm",
                            "color": "#1a1a2e",
                            "wrap": True,
                            "margin": "sm"
                        }
                    ],
                    "margin": "md"
                },
                {"type": "separator", "margin": "md"},
                info_row("在籍数", f"{len(SHOP_INFO['therapists'])}名"),
                {"type": "separator", "margin": "lg"},
                {
                    "type": "button",
                    "action": {
                        "type": "message",
                        "label": "🔙 メニューに戻る",
                        "text": "メニュー"
                    },
                    "style": "secondary",
                    "height": "sm",
                    "margin": "lg"
                }
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(
        alt_text="店舗情報",
        contents=FlexContainer.from_dict(flex_json)
    )


def info_row(label, value):
    return {
        "type": "box",
        "layout": "horizontal",
        "contents": [
            {"type": "text", "text": label, "size": "sm", "color": "#888888", "flex": 2},
            {"type": "text", "text": value, "size": "sm", "color": "#1a1a2e", "weight": "bold", "flex": 5}
        ],
        "margin": "md"
    }


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
        # JSON部分を抽出
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


def build_news_confirm_flex(news_data, session_id):
    """ニュース確認用のFlex Message"""
    title = news_data.get("title", "")
    body = news_data.get("body", "")
    # 本文が長い場合は表示用に切り詰め
    display_body = body[:300] + "..." if len(body) > 300 else body

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "📰 ニュース プレビュー",
                    "weight": "bold",
                    "size": "lg",
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
                    "text": f"📌 {title}",
                    "weight": "bold",
                    "size": "md",
                    "color": "#1a1a2e",
                    "wrap": True
                },
                {"type": "separator", "margin": "md"},
                {
                    "type": "text",
                    "text": display_body,
                    "size": "sm",
                    "color": "#333333",
                    "wrap": True,
                    "margin": "md"
                },
                {
                    "type": "text",
                    "text": f"（全{len(body)}文字）",
                    "size": "xs",
                    "color": "#888888",
                    "align": "end",
                    "margin": "md"
                },
                {"type": "separator", "margin": "md"},
                {
                    "type": "box",
                    "layout": "vertical",
                    "contents": [
                        {
                            "type": "button",
                            "action": {
                                "type": "message",
                                "label": "✅ このニュースを投稿する",
                                "text": "ニュース確定"
                            },
                            "style": "primary",
                            "color": "#1a1a2e",
                            "height": "sm"
                        },
                        {
                            "type": "button",
                            "action": {
                                "type": "message",
                                "label": "🔄 再生成する",
                                "text": "ニュース再生成"
                            },
                            "style": "secondary",
                            "height": "sm",
                            "margin": "sm"
                        },
                        {
                            "type": "button",
                            "action": {
                                "type": "message",
                                "label": "🔙 メニューに戻る",
                                "text": "メニュー"
                            },
                            "style": "secondary",
                            "height": "sm",
                            "margin": "sm"
                        }
                    ],
                    "margin": "lg"
                }
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(
        alt_text=f"ニュースプレビュー: {title}",
        contents=FlexContainer.from_dict(flex_json)
    )


def build_news_post_flex(news_data, image_urls=None):
    """投稿用ニュースのFlex Message"""
    title = news_data.get("title", "")
    body = news_data.get("body", "")

    contents = [
        {
            "type": "text",
            "text": f"📰 {title}",
            "weight": "bold",
            "size": "lg",
            "color": "#1a1a2e",
            "wrap": True
        },
        {
            "type": "text",
            "text": datetime.now().strftime("%Y年%m月%d日"),
            "size": "xs",
            "color": "#888888",
            "margin": "sm"
        },
        {"type": "separator", "margin": "md"},
        {
            "type": "text",
            "text": body,
            "size": "sm",
            "color": "#333333",
            "wrap": True,
            "margin": "md"
        }
    ]

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "🏆 全力エステ NEWS",
                    "weight": "bold",
                    "size": "lg",
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
            "contents": contents,
            "paddingAll": "15px"
        }
    }

    return FlexMessage(
        alt_text=f"全力エステNEWS: {title}",
        contents=FlexContainer.from_dict(flex_json)
    )


# ═══════════════════════════════════════════
#  スケジュール確認（月選択Flex）
# ═══════════════════════════════════════════

def build_schedule_month_select_flex():
    """スケジュール確認: 今月/来月の選択Flex Message"""
    now = datetime.now()
    this_month = now.strftime("%Y年%m月")
    if now.month == 12:
        next_month_dt = now.replace(year=now.year + 1, month=1, day=1)
    else:
        next_month_dt = now.replace(month=now.month + 1, day=1)
    next_month = next_month_dt.strftime("%Y年%m月")

    flex_json = {
        "type": "bubble",
        "size": "mega",
        "header": {
            "type": "box",
            "layout": "vertical",
            "contents": [
                {
                    "type": "text",
                    "text": "📅 スケジュール確認",
                    "weight": "bold",
                    "size": "lg",
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
                    "text": "確認したい月を選択してください",
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
                    "type": "button",
                    "action": {
                        "type": "message",
                        "label": f"📅 今月（{this_month}）",
                        "text": "スケジュール_今月"
                    },
                    "style": "primary",
                    "color": "#1a1a2e",
                    "height": "sm",
                    "margin": "lg"
                },
                {
                    "type": "button",
                    "action": {
                        "type": "message",
                        "label": f"📅 来月（{next_month}）",
                        "text": "スケジュール_来月"
                    },
                    "style": "primary",
                    "color": "#0f3460",
                    "height": "sm",
                    "margin": "sm"
                },
                {
                    "type": "button",
                    "action": {
                        "type": "message",
                        "label": "🔙 メニューに戻る",
                        "text": "メニュー"
                    },
                    "style": "secondary",
                    "height": "sm",
                    "margin": "lg"
                }
            ],
            "paddingAll": "15px"
        }
    }
    return FlexMessage(
        alt_text="スケジュール確認 - 月を選択",
        contents=FlexContainer.from_dict(flex_json)
    )


def process_schedule_request(year, month, event):
    """スケジュールリクエストを処理してカレンダー画像を送信"""
    line_api = get_messaging_api()
    push_target = get_push_target(event)

    # Notionからシフトデータを取得
    logger.info(f"Fetching shift data for {year}/{month}")
    shift_data = fetch_shift_data_from_notion(year, month)
    logger.info(f"Got {len(shift_data)} shift entries")

    if not shift_data:
        # データがない場合
        if push_target:
            line_api.push_message(
                PushMessageRequest(
                    to=push_target,
                    messages=[
                        TextMessage(text=f"📅 {year}年{month}月のシフトデータが見つかりませんでした。\n\nNotionにデータが登録されているか確認してください。"),
                        build_main_menu_flex()
                    ]
                )
            )
        return

    # カレンダーデータに変換
    cal_data = parse_shift_to_calendar(shift_data, year, month)

    # カレンダー画像を生成
    img = generate_calendar_image(year, month, cal_data)

    # 画像を保存
    filename = f"schedule_{year}_{month:02d}_{uuid.uuid4().hex[:8]}.png"
    filepath = os.path.join(UPLOAD_DIR, filename)
    img.save(filepath, "PNG")
    logger.info(f"Calendar image saved: {filepath}")

    # 画像URLを構築
    if BASE_URL:
        image_url = f"{BASE_URL}/static/images/{filename}"
    else:
        image_url = f"https://zenryoku-line-bot-production.up.railway.app/static/images/{filename}"

    # LINEに送信
    if push_target:
        line_api.push_message(
            PushMessageRequest(
                to=push_target,
                messages=[
                    TextMessage(text=f"📅 {year}年{month}月のシフトカレンダーです"),
                    ImageMessage(
                        original_content_url=image_url,
                        preview_image_url=image_url
                    )
                ]
            )
        )


# ═══════════════════════════════════════════
#  イベントハンドラ
# ═══════════════════════════════════════════

def get_session_key(event):
    """ユーザーIDまたはグループIDからセッションキーを取得"""
    source = event.source
    if hasattr(source, 'group_id') and source.group_id:
        return f"group_{source.group_id}_{source.user_id}"
    elif hasattr(source, 'room_id') and source.room_id:
        return f"room_{source.room_id}_{source.user_id}"
    else:
        return f"user_{source.user_id}"


def get_user_id(event):
    """ユーザーIDを取得"""
    return event.source.user_id


@handler.add(FollowEvent)
def handle_follow(event):
    """友だち追加時"""
    line_api = get_messaging_api()
    messages = [
        TextMessage(text="🏆 全力エステ公式LINEへようこそ！\n\n仙台のメンズエステ界における頂点を本気で狙うハイレベルサロンです。\n\n「メニュー」と入力するとメニューが表示されます。"),
        build_main_menu_flex()
    ]
    line_api.reply_message(
        ReplyMessageRequest(
            reply_token=event.reply_token,
            messages=messages
        )
    )


@handler.add(JoinEvent)
def handle_join(event):
    """グループ参加時"""
    line_api = get_messaging_api()
    messages = [
        TextMessage(text="🏆 全力エステBotがグループに参加しました！\n\n「メニュー」と入力するとメニューが表示されます。\nグループでもすべての機能をご利用いただけます。"),
        build_main_menu_flex()
    ]
    line_api.reply_message(
        ReplyMessageRequest(
            reply_token=event.reply_token,
            messages=messages
        )
    )


@handler.add(MessageEvent, message=TextMessageContent)
def handle_text_message(event):
    """テキストメッセージ処理"""
    text = event.message.text.strip()
    session_key = get_session_key(event)
    line_api = get_messaging_api()

    logger.info(f"Message from {session_key}: {text}")

    # セッション状態を確認
    session = user_sessions.get(session_key, {})
    state = session.get("state", "idle")

    # ─── メニュー表示 ───
    if text in ["メニュー", "menu", "Menu", "MENU", "めにゅー"]:
        user_sessions.pop(session_key, None)
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[build_main_menu_flex()]
            )
        )
        return

    # ─── ニュース投稿 ───
    if text == "ニュース投稿":
        user_sessions[session_key] = {"state": "news_topic"}
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[
                    TextMessage(text="📰 ニュース投稿\n\nニュースのテーマを入力してください。\n（例：新人セラピスト紹介、キャンペーン告知、季節のおすすめ）\n\n「おまかせ」と入力するとAIが自動でテーマを選びます。")
                ]
            )
        )
        return

    # ─── ニュース テーマ入力待ち ───
    if state == "news_topic":
        topic = None if text in ["おまかせ", "お任せ", "自動"] else text
        user_sessions[session_key] = {"state": "news_generating"}

        # 生成中メッセージ
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text="📝 ニュースを生成中です...\nしばらくお待ちください。")]
            )
        )

        # ニュース生成
        news = generate_news(topic)
        user_sessions[session_key] = {
            "state": "news_preview",
            "news": news,
            "topic": topic,
            "images": []
        }

        # プッシュメッセージでプレビュー送信
        push_target = get_push_target(event)
        if push_target:
            line_api.push_message(
                PushMessageRequest(
                    to=push_target,
                    messages=[build_news_confirm_flex(news, session_key)]
                )
            )
        return

    # ─── ニュース再生成 ───
    if text == "ニュース再生成" and state == "news_preview":
        topic = session.get("topic")
        user_sessions[session_key]["state"] = "news_generating"

        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text="🔄 ニュースを再生成中です...\nしばらくお待ちください。")]
            )
        )

        news = generate_news(topic)
        user_sessions[session_key] = {
            "state": "news_preview",
            "news": news,
            "topic": topic,
            "images": session.get("images", [])
        }

        push_target = get_push_target(event)
        if push_target:
            line_api.push_message(
                PushMessageRequest(
                    to=push_target,
                    messages=[build_news_confirm_flex(news, session_key)]
                )
            )
        return

    # ─── ニュース確定 ───
    if text == "ニュース確定" and state == "news_preview":
        news = session.get("news", {})
        images = session.get("images", [])

        # ニュースFlex + 画像を送信
        messages = [build_news_post_flex(news, images)]

        # 画像があれば添付
        if images and BASE_URL:
            for img_path in images[:3]:
                img_url = f"{BASE_URL}/static/images/{os.path.basename(img_path)}"
                messages.append(ImageMessage(
                    original_content_url=img_url,
                    preview_image_url=img_url
                ))

        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=messages[:5]  # LINE制限: 最大5メッセージ
            )
        )

        # 全文をテキストでも送信（Flexだと文字数制限があるため）
        push_target = get_push_target(event)
        if push_target:
            full_text = f"📰 {news.get('title', '')}\n{'─' * 20}\n{news.get('body', '')}\n{'─' * 20}\n🏆 全力エステ"
            line_api.push_message(
                PushMessageRequest(
                    to=push_target,
                    messages=[TextMessage(text=full_text)]
                )
            )

        user_sessions.pop(session_key, None)
        return

    # ─── 画像添付コマンド ───
    if text in ["画像添付", "画像追加"] and state == "news_preview":
        user_sessions[session_key]["state"] = "news_image_wait"
        current_count = len(session.get("images", []))
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text=f"🖼 画像を送信してください（最大3枚、現在{current_count}枚）\n\n画像を送信するか、「完了」と入力してプレビューに戻ります。")]
            )
        )
        return

    # ─── 画像添付完了 ───
    if text == "完了" and state == "news_image_wait":
        user_sessions[session_key]["state"] = "news_preview"
        news = session.get("news", {})
        images = session.get("images", [])
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[
                    TextMessage(text=f"🖼 画像{len(images)}枚が添付されています。"),
                    build_news_confirm_flex(news, session_key)
                ]
            )
        )
        return

    # ─── スケジュール確認（月選択表示） ───
    if text == "スケジュール確認":
        user_sessions.pop(session_key, None)
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[build_schedule_month_select_flex()]
            )
        )
        return

    # ─── スケジュール_今月 ───
    if text == "スケジュール_今月":
        user_sessions.pop(session_key, None)
        now = datetime.now()

        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text=f"📅 {now.year}年{now.month}月のシフトカレンダーを作成中です...\nしばらくお待ちください。")]
            )
        )

        # カレンダー画像を生成して送信
        process_schedule_request(now.year, now.month, event)
        return

    # ─── スケジュール_来月 ───
    if text == "スケジュール_来月":
        user_sessions.pop(session_key, None)
        now = datetime.now()
        if now.month == 12:
            target_year = now.year + 1
            target_month = 1
        else:
            target_year = now.year
            target_month = now.month + 1

        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text=f"📅 {target_year}年{target_month}月のシフトカレンダーを作成中です...\nしばらくお待ちください。")]
            )
        )

        # カレンダー画像を生成して送信
        process_schedule_request(target_year, target_month, event)
        return

    # ─── セラピスト一覧 ───
    if text == "セラピスト一覧":
        user_sessions.pop(session_key, None)
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[build_therapist_flex()]
            )
        )
        return

    # ─── 店舗情報 ───
    if text == "店舗情報":
        user_sessions.pop(session_key, None)
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[build_shop_info_flex()]
            )
        )
        return

    # ─── ヘルプ ───
    if text in ["ヘルプ", "help", "Help", "HELP"]:
        user_sessions.pop(session_key, None)
        help_text = """🏆 全力エステBot ヘルプ

【使い方】
「メニュー」→ メインメニュー表示
「ニュース投稿」→ AI自動生成ニュース
「スケジュール確認」→ 月別シフトカレンダー
「セラピスト一覧」→ 在籍セラピスト
「店舗情報」→ サロン情報

※グループでもすべての機能が使えます"""
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text=help_text)]
            )
        )
        return

    # ─── デフォルト応答 ───
    if state == "idle":
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[
                    TextMessage(text="「メニュー」と入力するとメニューが表示されます。"),
                    build_main_menu_flex()
                ]
            )
        )
        return


def get_push_target(event):
    """プッシュメッセージの送信先を取得"""
    source = event.source
    if hasattr(source, 'group_id') and source.group_id:
        return source.group_id
    elif hasattr(source, 'room_id') and source.room_id:
        return source.room_id
    else:
        return source.user_id


# ═══════════════════════════════════════════
#  画像メッセージハンドラ
# ═══════════════════════════════════════════

from linebot.v3.webhooks import ImageMessageContent

@handler.add(MessageEvent, message=ImageMessageContent)
def handle_image_message(event):
    """画像メッセージ処理"""
    session_key = get_session_key(event)
    session = user_sessions.get(session_key, {})
    state = session.get("state", "idle")
    line_api = get_messaging_api()

    if state == "news_image_wait":
        images = session.get("images", [])
        if len(images) >= 3:
            line_api.reply_message(
                ReplyMessageRequest(
                    reply_token=event.reply_token,
                    messages=[TextMessage(text="⚠️ 画像は最大3枚までです。「完了」と入力してプレビューに戻ってください。")]
                )
            )
            return

        # 画像をダウンロード
        try:
            from linebot.v3.messaging import MessagingApiBlob
            blob_api = MessagingApiBlob(ApiClient(configuration))
            message_content = blob_api.get_message_content(event.message.id)

            filename = f"{uuid.uuid4().hex}.jpg"
            filepath = os.path.join(UPLOAD_DIR, filename)
            with open(filepath, "wb") as f:
                f.write(message_content)

            images.append(filepath)
            user_sessions[session_key]["images"] = images

            remaining = 3 - len(images)
            if remaining > 0:
                msg = f"🖼 画像を受け取りました（{len(images)}/3枚）\nあと{remaining}枚追加できます。\n\n追加する場合は画像を送信、完了する場合は「完了」と入力してください。"
            else:
                msg = "🖼 画像を受け取りました（3/3枚）\n最大枚数に達しました。「完了」と入力してプレビューに戻ってください。"

            line_api.reply_message(
                ReplyMessageRequest(
                    reply_token=event.reply_token,
                    messages=[TextMessage(text=msg)]
                )
            )
        except Exception as e:
            logger.error(f"Image download error: {e}")
            line_api.reply_message(
                ReplyMessageRequest(
                    reply_token=event.reply_token,
                    messages=[TextMessage(text="⚠️ 画像の保存中にエラーが発生しました。もう一度お試しください。")]
                )
            )
    else:
        line_api.reply_message(
            ReplyMessageRequest(
                reply_token=event.reply_token,
                messages=[TextMessage(text="画像を受け取りましたが、現在画像を受け付ける状態ではありません。\n「メニュー」と入力してメニューを表示してください。")]
            )
        )


# ═══════════════════════════════════════════
#  メイン
# ═══════════════════════════════════════════

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    logger.info(f"Starting 全力エステ LINE Bot on port {port}")
    app.run(host="0.0.0.0", port=port, debug=False)
