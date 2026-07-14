package com.store.cti.data.transfer

import com.store.cti.core.model.CustomerCategory
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.data.local.entity.StaffEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** バックアップJSONのラウンドトリップ(書き出し→読み込みで完全一致すること) */
class BackupRoundTripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun sampleBackup() = BackupFile(
        formatVersion = BackupFile.CURRENT_FORMAT_VERSION,
        appVersion = "0.1.0",
        exportedAt = "2026-07-14T12:00:00",
        customers = listOf(
            CustomerEntity(
                id = "c-1", name = "山田 太郎", kana = "やまだ たろう",
                displayPhoneNumber = "090-1234-5678", normalizedPhoneNumber = "09012345678",
                category = CustomerCategory.FREQUENT.name, visitCount = 5,
                cautionNotes = "予約時間の確認を丁寧に行う",
                createdAt = 1000L, updatedAt = 2000L,
            ),
            CustomerEntity(id = "c-2", name = "削除済み顧客", deletedAt = 3000L),
        ),
        callInteractions = listOf(
            CallInteractionEntity(
                id = "i-1", customerId = "c-1", occurredAt = "2026-07-14T10:00",
                displayPhoneNumber = "090-1234-5678", callbackRequired = true,
            ),
        ),
        reservations = listOf(
            ReservationEntity(
                id = "r-1", customerId = "c-1", date = "2026-07-20",
                startTime = "14:00", endTime = "15:00", price = 8000,
            ),
        ),
        smsDrafts = listOf(
            SmsDraftEntity(id = "d-1", customerId = "c-1", body = "テスト本文, \"引用\" 改行\nあり"),
        ),
        smsTemplates = listOf(
            SmsTemplateEntity(id = "t-1", name = "予約確定", body = "{customerName}様"),
        ),
        staff = listOf(StaffEntity(id = "s-1", name = "スタッフA")),
        settings = BackupSettings(storeName = "テスト店", storePhone = "03-1234-5678"),
    )

    @Test
    fun `バックアップのラウンドトリップで全データが一致する`() {
        val original = sampleBackup()
        val text = json.encodeToString(BackupFile.serializer(), original)
        val decoded = json.decodeFromString(BackupFile.serializer(), text)
        assertEquals(original, decoded)
    }

    @Test
    fun `未知のキーを含むJSONも読み込める(前方互換)`() {
        val text = json.encodeToString(BackupFile.serializer(), sampleBackup())
            .replaceFirst("{", "{\"unknownFutureField\": 123,")
        val decoded = json.decodeFromString(BackupFile.serializer(), text)
        assertEquals("山田 太郎", decoded.customers.first().name)
    }

    @Test
    fun `壊れたJSONは例外になる(呼び出し側でエラー表示する)`() {
        val result = runCatching {
            json.decodeFromString(BackupFile.serializer(), "{ this is broken")
        }
        assertTrue(result.isFailure)
    }
}
