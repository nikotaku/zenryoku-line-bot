package com.store.cti.core.phone

/**
 * 日本国内の電話番号処理ユーティリティ(純Kotlin・副作用なし)。
 *
 * 保存時は [normalize] の結果(数字のみ・0始まりの国内形式)を検索用カラムに、
 * 入力されたままの文字列を表示用カラムに保持する(DATA_MODEL.md 0章)。
 */
object PhoneNumberUtils {

    /** 番号種別(簡易判定) */
    enum class NumberType { MOBILE, FIXED, IP_PHONE, FREEPHONE, UNKNOWN }

    private val SEPARATORS = setOf(
        ' ', '　', '-', 'ー', '−', '‐', '‑', '–', '—', '(', ')', '(', ')', '.', '/'
    )

    /**
     * 電話番号を検索用の正規形(数字のみ・国内0始まり)へ変換する。
     *
     * 対応: 空白除去 / ハイフン類除去 / 全角→半角 / tel: 除去 / +81・0081 → 0 変換。
     * 例: "+81 90 1234 5678" / "090-1234-5678" / "09012345678" → いずれも "09012345678"
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim().map { toHalfWidth(it) }.joinToString("")

        // tel: / tel:// スキームの除去(大文字小文字を問わない)
        if (s.lowercase().startsWith("tel:")) {
            s = s.substring(4).trimStart('/')
        }

        // 区切り記号を除去し、数字と + だけを残す
        s = s.filter { it.isDigit() || it == '+' }

        // 国際形式 (+81 / 0081) を国内形式へ
        when {
            s.startsWith("+81") -> s = toDomestic(s.removePrefix("+81"))
            s.startsWith("0081") -> s = toDomestic(s.removePrefix("0081"))
            s.startsWith("+") -> s = s.removePrefix("+") // その他の国は数字のみ残す(検証で弾かれる)
        }
        return s.filter { it.isDigit() }
    }

    private fun toDomestic(rest: String): String {
        val digits = rest.filter { it.isDigit() }
        // "+81-090-…" のように誤って 0 を残した入力にも対応する
        return if (digits.startsWith("0")) digits else "0$digits"
    }

    private fun toHalfWidth(c: Char): Char = when (c) {
        in '０'..'９' -> ('0' + (c - '０'))
        '＋' -> '+'
        else -> c
    }

    /** 日本国内番号としての簡易検証(0始まりの10桁または11桁) */
    fun isValidJapaneseNumber(raw: String?): Boolean {
        val n = normalize(raw)
        return n.length in 10..11 && n.startsWith("0") && n.all { it.isDigit() } &&
            !(n.length == 10 && n.startsWith("00"))
    }

    /** 番号種別の簡易判定 */
    fun numberType(raw: String?): NumberType {
        val n = normalize(raw)
        return when {
            Regex("^0[789]0\\d{8}$").matches(n) -> NumberType.MOBILE
            Regex("^050\\d{8}$").matches(n) -> NumberType.IP_PHONE
            Regex("^0120\\d{6}$").matches(n) || Regex("^0800\\d{7}$").matches(n) -> NumberType.FREEPHONE
            Regex("^0\\d{9}$").matches(n) -> NumberType.FIXED
            else -> NumberType.UNKNOWN
        }
    }

    /**
     * 表示用整形。携帯・050は 3-4-4、0120は 4-3-3、固定電話は市外局番2桁(03/06)は
     * 2-4-4、それ以外は 3-3-4 の簡易整形。整形できない場合は正規形をそのまま返す。
     */
    fun formatForDisplay(raw: String?): String {
        val n = normalize(raw)
        return when {
            n.length == 11 && (n.startsWith("070") || n.startsWith("080") || n.startsWith("090") || n.startsWith("050")) ->
                "${n.take(3)}-${n.substring(3, 7)}-${n.substring(7)}"
            n.length == 11 && n.startsWith("0800") ->
                "${n.take(4)}-${n.substring(4, 7)}-${n.substring(7)}"
            n.length == 10 && n.startsWith("0120") ->
                "${n.take(4)}-${n.substring(4, 7)}-${n.substring(7)}"
            n.length == 10 && (n.startsWith("03") || n.startsWith("06")) ->
                "${n.take(2)}-${n.substring(2, 6)}-${n.substring(6)}"
            n.length == 10 ->
                "${n.take(3)}-${n.substring(3, 6)}-${n.substring(6)}"
            else -> n
        }
    }

    /**
     * マスク表示(例: 090-****-5678)。ログ出力・マスク表示設定で使用する。
     * 整形できない番号は末尾4桁以外を * にする。
     */
    fun mask(raw: String?): String {
        val n = normalize(raw)
        if (n.isEmpty()) return ""
        val formatted = formatForDisplay(n)
        val parts = formatted.split("-")
        return if (parts.size == 3) {
            "${parts[0]}-${"*".repeat(parts[1].length)}-${parts[2]}"
        } else {
            val visible = n.takeLast(4)
            "*".repeat((n.length - 4).coerceAtLeast(0)) + visible
        }
    }

    /** 正規化した上での完全一致比較 */
    fun matchesExact(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        return na.isNotEmpty() && na == nb
    }

    /**
     * 共有されたテキストから最初の電話番号らしき並びを抽出する(経路C: 共有メニュー)。
     * 見つからない場合は null。
     */
    fun extractFirstPhoneNumber(text: String?): String? {
        if (text.isNullOrBlank()) return null
        // まず区切りをハイフン・空白に限定して探し(時刻などの誤結合を防ぐ)、
        // 見つからなければ 03(1234)5678 のような、かっこ・ドット区切りも許容する
        val patterns = listOf(
            Regex("[+＋]?[0-9０-９][0-9０-９\\-ー‐−–— 　]{7,}[0-9０-９]"),
            Regex("[+＋]?[0-9０-９][0-9０-９\\-ー‐−–— 　()()./]{7,}[0-9０-９]"),
        )
        for (pattern in patterns) {
            val hit = pattern.findAll(text)
                .map { normalize(it.value) }
                .firstOrNull { isValidJapaneseNumber(it) }
            if (hit != null) return hit
        }
        return null
    }

    /**
     * 末尾一致検索。部分入力(下4桁など)で顧客を探すために使う。
     * 誤ヒット防止のため、検索語は4桁以上を要求する。
     */
    fun matchesTail(stored: String?, query: String?): Boolean {
        val ns = normalize(stored)
        val nq = normalize(query)
        if (nq.length < 4) return false
        return ns.endsWith(nq)
    }
}
