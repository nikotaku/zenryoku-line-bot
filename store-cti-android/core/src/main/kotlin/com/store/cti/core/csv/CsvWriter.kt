package com.store.cti.core.csv

/**
 * Excelで開きやすいCSV(UTF-8 BOM付き・CRLF)を組み立てるユーティリティ。
 * CSVインジェクション対策として、`=` `+` `-` `@` タブ CR で始まるセルには
 * 先頭にシングルクォートを付与して数式として解釈されないようにする。
 */
object CsvWriter {

    const val BOM: String = "\uFEFF"
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** 1セルを安全なCSV表現へ変換する */
    fun sanitizeCell(raw: String?): String {
        val value = raw ?: ""
        val guarded = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value
        val needsQuote = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"${guarded.replace("\"", "\"\"")}\"" else guarded
    }

    /** ヘッダー+データ行からCSV文字列(BOM付き・CRLF・末尾改行あり)を生成する */
    fun buildCsv(header: List<String>, rows: List<List<String?>>): String {
        val sb = StringBuilder(BOM)
        sb.append(header.joinToString(",") { sanitizeCell(it) }).append("\r\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { sanitizeCell(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
