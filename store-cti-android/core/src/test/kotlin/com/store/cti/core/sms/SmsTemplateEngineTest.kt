package com.store.cti.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTemplateEngineTest {

    @Test
    fun `予約確定テンプレートの差し込み`() {
        val template = "ご予約ありがとうございます。{customerName}様のご予約は、{reservationDate} {startTime}から承りました。" +
            "変更やキャンセルがある場合は、お早めにご連絡ください。"
        val result = SmsTemplateEngine.merge(
            template,
            SmsMergeValues(
                customerName = "山田 太郎",
                reservationDate = "2026年7月20日",
                startTime = "14:00",
            ),
        )
        assertEquals(
            "ご予約ありがとうございます。山田 太郎様のご予約は、2026年7月20日 14:00から承りました。" +
                "変更やキャンセルがある場合は、お早めにご連絡ください。",
            result,
        )
    }

    @Test
    fun `全9種のプレースホルダを置換できる`() {
        val template = "{customerName}/{reservationDate}/{startTime}/{endTime}/{staffName}/{courseName}/{price}/{storeName}/{storePhone}"
        val result = SmsTemplateEngine.merge(
            template,
            SmsMergeValues("A", "B", "C", "D", "E", "F", "G", "H", "I"),
        )
        assertEquals("A/B/C/D/E/F/G/H/I", result)
    }

    @Test
    fun `値が無い項目は空文字に置換される`() {
        val result = SmsTemplateEngine.merge("こんにちは{customerName}様", SmsMergeValues())
        assertEquals("こんにちは様", result)
    }

    @Test
    fun `未知のプレースホルダはそのまま残す`() {
        val result = SmsTemplateEngine.merge("{unknownKey}と{customerName}", SmsMergeValues(customerName = "花子"))
        assertEquals("{unknownKey}と花子", result)
    }

    @Test
    fun `プレースホルダを含まない本文はそのまま`() {
        val body = "本日はご来店ありがとうございました。またのご利用をお待ちしております。"
        assertEquals(body, SmsTemplateEngine.merge(body, SmsMergeValues()))
    }

    @Test
    fun `未置換プレースホルダの検出`() {
        val body = "こんにちは{customerName}様、{unknownKey}"
        val remaining = SmsTemplateEngine.remainingPlaceholders(body)
        assertEquals(listOf("{customerName}", "{unknownKey}"), remaining)
        assertTrue(SmsTemplateEngine.remainingPlaceholders("プレーンな本文").isEmpty())
    }

    @Test
    fun `同じプレースホルダが複数回出ても全て置換される`() {
        val result = SmsTemplateEngine.merge(
            "{storeName}です。{storeName}へのご来店をお待ちしております。",
            SmsMergeValues(storeName = "テスト店"),
        )
        assertEquals("テスト店です。テスト店へのご来店をお待ちしております。", result)
    }
}
