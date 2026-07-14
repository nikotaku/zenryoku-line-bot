package com.store.cti.core.model

/** 顧客区分 */
enum class CustomerCategory(val label: String) {
    NEW("新規"),
    REGULAR("通常"),
    FREQUENT("常連"),
    VIP("重要"),
    CAUTION("注意"),
    BANNED("利用停止");

    /** 注意・利用停止など、対応時に警告表示すべき区分か */
    val requiresAttention: Boolean
        get() = this == CAUTION || this == BANNED

    companion object {
        fun fromName(name: String?): CustomerCategory =
            entries.firstOrNull { it.name == name } ?: REGULAR
    }
}

/** 着信・発信の別 */
enum class CallDirection(val label: String) {
    INCOMING("着信"),
    OUTGOING("発信");

    companion object {
        fun fromName(name: String?): CallDirection =
            entries.firstOrNull { it.name == name } ?: INCOMING
    }
}

/** 通話対応の結果 */
enum class CallResult(val label: String) {
    RESERVED("予約成立"),
    CONSIDERING("検討中"),
    FULL("満了"),
    WAITING_CALLBACK("折り返し待ち"),
    NO_ANSWER("不在"),
    CANCELLED("キャンセル"),
    INQUIRY_ONLY("問い合わせのみ"),
    SALES_CALL("営業電話"),
    NUISANCE("迷惑電話"),
    OTHER("その他");

    companion object {
        fun fromName(name: String?): CallResult =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** 予約状態 */
enum class ReservationStatus(val label: String) {
    TENTATIVE("仮予約"),
    CONFIRMED("予約確定"),
    VISITED("来店済み"),
    CANCELLED("キャンセル"),
    NO_SHOW("無断キャンセル"),
    MODIFIED("変更済み");

    /** 時間枠を占有している(重複判定の対象になる)状態か */
    val occupiesSlot: Boolean
        get() = this == TENTATIVE || this == CONFIRMED || this == VISITED

    companion object {
        fun fromName(name: String?): ReservationStatus =
            entries.firstOrNull { it.name == name } ?: TENTATIVE
    }
}

/** 予約経路 */
enum class ReservationChannel(val label: String) {
    PHONE("電話"),
    SMS("SMS"),
    LINE("LINE"),
    WEB("Web"),
    REFERRAL("紹介"),
    OTHER("その他");

    companion object {
        fun fromName(name: String?): ReservationChannel =
            entries.firstOrNull { it.name == name } ?: PHONE
    }
}

/** SMS作成履歴の状態(実送信の成否はアプリからは断定できない) */
enum class SmsDraftStatus(val label: String) {
    DRAFT("下書き作成"),
    LAUNCHED("SMSアプリを起動"),
    CONFIRMED("スタッフ確認済み");

    companion object {
        fun fromName(name: String?): SmsDraftStatus =
            entries.firstOrNull { it.name == name } ?: DRAFT
    }
}

/** 将来のサーバー同期用の状態(MVPでは常に LOCAL_ONLY) */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED;

    companion object {
        fun fromName(name: String?): SyncStatus =
            entries.firstOrNull { it.name == name } ?: LOCAL_ONLY
    }
}
