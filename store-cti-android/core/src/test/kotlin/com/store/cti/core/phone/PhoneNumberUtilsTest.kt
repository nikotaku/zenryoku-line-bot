package com.store.cti.core.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    // --- 正規化 ---

    @Test
    fun `仕様書の3表記が同一の正規形になる`() {
        assertEquals("09012345678", PhoneNumberUtils.normalize("+81 90 1234 5678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("090-1234-5678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("09012345678"))
    }

    @Test
    fun `空白とハイフン類を除去する`() {
        assertEquals("09012345678", PhoneNumberUtils.normalize(" 090 1234 5678 "))
        assertEquals("09012345678", PhoneNumberUtils.normalize("090‐1234−5678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("090ー1234ー5678"))
    }

    @Test
    fun `全角数字を半角へ変換する`() {
        assertEquals("09012345678", PhoneNumberUtils.normalize("０９０-１２３４-５６７８"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("＋８１ ９０ １２３４ ５６７８"))
    }

    @Test
    fun `telスキームを除去する`() {
        assertEquals("09012345678", PhoneNumberUtils.normalize("tel:090-1234-5678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("TEL:09012345678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("tel://09012345678"))
    }

    @Test
    fun `+81形式を国内形式へ変換する`() {
        assertEquals("09012345678", PhoneNumberUtils.normalize("+819012345678"))
        assertEquals("09012345678", PhoneNumberUtils.normalize("+81-90-1234-5678"))
        // 誤って 0 を残した国際表記
        assertEquals("09012345678", PhoneNumberUtils.normalize("+81-090-1234-5678"))
        // 0081 形式
        assertEquals("09012345678", PhoneNumberUtils.normalize("00819012345678"))
        // 固定電話
        assertEquals("0312345678", PhoneNumberUtils.normalize("+81-3-1234-5678"))
    }

    @Test
    fun `空やnullは空文字を返す`() {
        assertEquals("", PhoneNumberUtils.normalize(null))
        assertEquals("", PhoneNumberUtils.normalize(""))
        assertEquals("", PhoneNumberUtils.normalize("   "))
    }

    // --- 検証 ---

    @Test
    fun `日本国内番号の簡易検証`() {
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("090-1234-5678"))
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("070-1234-5678"))
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("080-1234-5678"))
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("03-1234-5678"))
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("0120-123-456"))
        assertTrue(PhoneNumberUtils.isValidJapaneseNumber("+81 90 1234 5678"))
        assertFalse(PhoneNumberUtils.isValidJapaneseNumber("12345"))
        assertFalse(PhoneNumberUtils.isValidJapaneseNumber("9012345678"))   // 0始まりでない
        assertFalse(PhoneNumberUtils.isValidJapaneseNumber("090123456789")) // 12桁
        assertFalse(PhoneNumberUtils.isValidJapaneseNumber(""))
        assertFalse(PhoneNumberUtils.isValidJapaneseNumber("あいうえお"))
    }

    @Test
    fun `番号種別を判定する`() {
        assertEquals(PhoneNumberUtils.NumberType.MOBILE, PhoneNumberUtils.numberType("090-1234-5678"))
        assertEquals(PhoneNumberUtils.NumberType.MOBILE, PhoneNumberUtils.numberType("070-1234-5678"))
        assertEquals(PhoneNumberUtils.NumberType.FIXED, PhoneNumberUtils.numberType("03-1234-5678"))
        assertEquals(PhoneNumberUtils.NumberType.IP_PHONE, PhoneNumberUtils.numberType("050-1234-5678"))
        assertEquals(PhoneNumberUtils.NumberType.FREEPHONE, PhoneNumberUtils.numberType("0120-123-456"))
        assertEquals(PhoneNumberUtils.NumberType.UNKNOWN, PhoneNumberUtils.numberType("123"))
    }

    // --- 整形・マスク ---

    @Test
    fun `表示用整形`() {
        assertEquals("090-1234-5678", PhoneNumberUtils.formatForDisplay("+819012345678"))
        assertEquals("03-1234-5678", PhoneNumberUtils.formatForDisplay("0312345678"))
        assertEquals("045-123-4567", PhoneNumberUtils.formatForDisplay("0451234567"))
        assertEquals("0120-123-456", PhoneNumberUtils.formatForDisplay("0120123456"))
        assertEquals("050-1234-5678", PhoneNumberUtils.formatForDisplay("05012345678"))
    }

    @Test
    fun `マスク表示`() {
        assertEquals("090-****-5678", PhoneNumberUtils.mask("090-1234-5678"))
        assertEquals("03-****-5678", PhoneNumberUtils.mask("0312345678"))
        assertEquals("", PhoneNumberUtils.mask(""))
    }

    // --- 比較・検索 ---

    @Test
    fun `完全一致比較は表記ゆれを吸収する`() {
        assertTrue(PhoneNumberUtils.matchesExact("+81 90 1234 5678", "090-1234-5678"))
        assertTrue(PhoneNumberUtils.matchesExact("09012345678", "０９０-１２３４-５６７８"))
        assertFalse(PhoneNumberUtils.matchesExact("09012345678", "09012345679"))
        assertFalse(PhoneNumberUtils.matchesExact("", ""))
    }

    @Test
    fun `共有テキストから電話番号を抽出する`() {
        assertEquals(
            "09012345678",
            PhoneNumberUtils.extractFirstPhoneNumber("着信あり: 090-1234-5678 (13:45)"),
        )
        assertEquals(
            "09012345678",
            PhoneNumberUtils.extractFirstPhoneNumber("tel +81 90 1234 5678 より"),
        )
        assertEquals(
            "0312345678",
            PhoneNumberUtils.extractFirstPhoneNumber("店舗 03(1234)5678 まで"),
        )
        assertEquals(null, PhoneNumberUtils.extractFirstPhoneNumber("番号なしのテキスト"))
        assertEquals(null, PhoneNumberUtils.extractFirstPhoneNumber("2026-07-14 15:00 予約"))
        assertEquals(null, PhoneNumberUtils.extractFirstPhoneNumber(null))
    }

    @Test
    fun `末尾一致検索`() {
        assertTrue(PhoneNumberUtils.matchesTail("09012345678", "5678"))
        assertTrue(PhoneNumberUtils.matchesTail("09012345678", "1234-5678"))
        assertFalse(PhoneNumberUtils.matchesTail("09012345678", "567"))   // 4桁未満は不可
        assertFalse(PhoneNumberUtils.matchesTail("09012345678", "9999"))
    }
}
