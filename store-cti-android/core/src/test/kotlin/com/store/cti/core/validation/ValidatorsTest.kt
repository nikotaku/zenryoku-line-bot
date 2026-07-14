package com.store.cti.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ValidatorsTest {

    // --- 予約時間 ---

    @Test
    fun `正常な予約時間`() {
        assertTrue(ReservationTimeValidator.validate(LocalTime.of(10, 0), LocalTime.of(11, 0)).isOk)
    }

    @Test
    fun `未入力はエラー`() {
        assertFalse(ReservationTimeValidator.validate(null, LocalTime.of(11, 0)).isOk)
        assertFalse(ReservationTimeValidator.validate(LocalTime.of(10, 0), null).isOk)
    }

    @Test
    fun `終了が開始と同時刻または前はエラー`() {
        assertFalse(ReservationTimeValidator.validate(LocalTime.of(10, 0), LocalTime.of(10, 0)).isOk)
        assertFalse(ReservationTimeValidator.validate(LocalTime.of(11, 0), LocalTime.of(10, 0)).isOk)
    }

    @Test
    fun `日時の境界値 深夜0時と23時59分`() {
        assertTrue(ReservationTimeValidator.validate(LocalTime.of(0, 0), LocalTime.of(23, 59)).isOk)
        assertTrue(ReservationTimeValidator.validate(LocalTime.of(23, 0), LocalTime.of(23, 59)).isOk)
        assertFalse(ReservationTimeValidator.validate(LocalTime.of(23, 59), LocalTime.of(0, 0)).isOk)
    }

    @Test
    fun `日時の境界値 うるう年と月末が正しく扱える`() {
        // Room には ISO-8601 文字列で保存する。文字列比較と時系列が一致することを確認する
        assertEquals(LocalDate.of(2028, 2, 29), LocalDate.parse("2028-02-29"))
        val endOfJan = LocalDateTime.parse("2026-01-31T23:59:59")
        val startOfFeb = LocalDateTime.parse("2026-02-01T00:00:00")
        assertTrue(endOfJan.toString() < startOfFeb.toString())
        assertTrue(LocalDate.of(2026, 12, 31).toString() < LocalDate.of(2027, 1, 1).toString())
    }

    // --- 電話番号 ---

    @Test
    fun `電話番号の検証`() {
        assertTrue(PhoneNumberValidator.validate("090-1234-5678").isOk)
        assertFalse(PhoneNumberValidator.validate("").isOk)
        assertFalse(PhoneNumberValidator.validate("123").isOk)
        assertTrue(PhoneNumberValidator.validate("", required = false).isOk)
    }

    // --- 顧客重複判定 ---

    @Test
    fun `重複する電話番号の顧客を検出する`() {
        val existing = mapOf(
            "customer-1" to "09012345678",
            "customer-2" to "08011112222",
        )
        assertEquals(
            listOf("customer-1"),
            CustomerDuplicateChecker.findDuplicates("+81 90 1234 5678", existing),
        )
    }

    @Test
    fun `自分自身は重複から除外する(編集時)`() {
        val existing = mapOf("customer-1" to "09012345678")
        assertTrue(
            CustomerDuplicateChecker.findDuplicates(
                "090-1234-5678", existing, selfCustomerId = "customer-1",
            ).isEmpty(),
        )
    }

    @Test
    fun `空番号は重複なし`() {
        val existing = mapOf("customer-1" to "")
        assertTrue(CustomerDuplicateChecker.findDuplicates("", existing).isEmpty())
    }
}
