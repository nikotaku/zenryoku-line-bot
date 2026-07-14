package com.store.cti.ui.screens.interactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 画面入力(yyyy-MM-dd HH:mm)とISO保存形式の相互変換 */
class DateTimeInputConversionTest {

    @Test
    fun `入力からISOへ変換する`() {
        assertEquals("2026-07-14T15:00", inputToIso("2026-07-14 15:00"))
        assertEquals("2026-12-31T23:59", inputToIso("2026-12-31 23:59"))
    }

    @Test
    fun `ISOから入力形式へ変換する`() {
        assertEquals("2026-07-14 15:00", isoToInput("2026-07-14T15:00"))
        assertEquals("2026-07-14 15:00", isoToInput("2026-07-14T15:00:30"))
        assertEquals("", isoToInput(null))
    }

    @Test
    fun `不正な入力はnull(空はnull=未設定)`() {
        assertNull(inputToIso(""))
        assertNull(inputToIso("2026/07/14 15:00"))
        assertNull(inputToIso("2026-02-30 10:00")) // 存在しない日付
        assertNull(inputToIso("あした"))
    }

    @Test
    fun `うるう年の境界値`() {
        assertEquals("2028-02-29T10:00", inputToIso("2028-02-29 10:00"))
        assertNull(inputToIso("2026-02-29 10:00")) // 平年
    }
}
