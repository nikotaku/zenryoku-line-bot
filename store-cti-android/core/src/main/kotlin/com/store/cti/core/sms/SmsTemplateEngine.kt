package com.store.cti.core.sms

/** SMSテンプレートへ差し込む値。未指定の項目は空文字に置換される。 */
data class SmsMergeValues(
    val customerName: String = "",
    val reservationDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val staffName: String = "",
    val courseName: String = "",
    val price: String = "",
    val storeName: String = "",
    val storePhone: String = "",
) {
    fun toMap(): Map<String, String> = mapOf(
        "customerName" to customerName,
        "reservationDate" to reservationDate,
        "startTime" to startTime,
        "endTime" to endTime,
        "staffName" to staffName,
        "courseName" to courseName,
        "price" to price,
        "storeName" to storeName,
        "storePhone" to storePhone,
    )
}

/**
 * `{customerName}` 形式のプレースホルダを置換する差し込みエンジン。
 * 未知のプレースホルダは(入力ミスに気付けるよう)置換せずそのまま残す。
 */
object SmsTemplateEngine {

    val SUPPORTED_PLACEHOLDERS: List<String> = listOf(
        "{customerName}", "{reservationDate}", "{startTime}", "{endTime}",
        "{staffName}", "{courseName}", "{price}", "{storeName}", "{storePhone}",
    )

    private val PLACEHOLDER_REGEX = Regex("\\{([a-zA-Z0-9_]+)\\}")

    fun merge(template: String, values: SmsMergeValues): String =
        merge(template, values.toMap())

    fun merge(template: String, values: Map<String, String>): String =
        PLACEHOLDER_REGEX.replace(template) { m ->
            val key = m.groupValues[1]
            if (values.containsKey(key)) values.getValue(key) else m.value
        }

    /** 本文中に残っている未置換のプレースホルダ一覧(送信前確認の警告表示用) */
    fun remainingPlaceholders(body: String): List<String> =
        PLACEHOLDER_REGEX.findAll(body).map { it.value }.distinct().toList()
}
