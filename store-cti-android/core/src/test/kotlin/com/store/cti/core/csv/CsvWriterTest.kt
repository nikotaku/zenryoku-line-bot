package com.store.cti.core.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWriterTest {

    @Test
    fun `通常のセルはそのまま`() {
        assertEquals("山田 太郎", CsvWriter.sanitizeCell("山田 太郎"))
        assertEquals("09012345678", CsvWriter.sanitizeCell("09012345678"))
        assertEquals("", CsvWriter.sanitizeCell(null))
    }

    @Test
    fun `カンマ・改行・引用符を含むセルは引用する`() {
        assertEquals("\"a,b\"", CsvWriter.sanitizeCell("a,b"))
        assertEquals("\"1行目\n2行目\"", CsvWriter.sanitizeCell("1行目\n2行目"))
        assertEquals("\"彼は\"\"常連\"\"です\"", CsvWriter.sanitizeCell("彼は\"常連\"です"))
    }

    @Test
    fun `CSVインジェクション対策(数式トリガー文字)`() {
        assertEquals("'=SUM(A1:A9)", CsvWriter.sanitizeCell("=SUM(A1:A9)"))
        assertEquals("'+819012345678", CsvWriter.sanitizeCell("+819012345678"))
        assertEquals("'-100", CsvWriter.sanitizeCell("-100"))
        assertEquals("'@example", CsvWriter.sanitizeCell("@example"))
    }

    @Test
    fun `インジェクション対策と引用の組み合わせ`() {
        assertEquals("\"'=1+1,2\"", CsvWriter.sanitizeCell("=1+1,2"))
    }

    @Test
    fun `BOM付きCRLF区切りのCSVを生成する`() {
        val csv = CsvWriter.buildCsv(
            header = listOf("氏名", "電話番号"),
            rows = listOf(
                listOf("山田 太郎", "090-1234-5678"),
                listOf("=悪意", null),
            ),
        )
        assertTrue(csv.startsWith("﻿"))
        assertEquals(
            "﻿氏名,電話番号\r\n山田 太郎,090-1234-5678\r\n'=悪意,\r\n",
            csv,
        )
    }
}
