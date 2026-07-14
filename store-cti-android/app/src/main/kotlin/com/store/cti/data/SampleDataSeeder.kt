package com.store.cti.data

import com.store.cti.core.model.CallDirection
import com.store.cti.core.model.CallResult
import com.store.cti.core.model.CustomerCategory
import com.store.cti.core.model.ReservationChannel
import com.store.cti.core.model.ReservationStatus
import com.store.cti.data.local.AppDatabase
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.repository.AuditLogger
import com.store.cti.data.repository.StaffRepository
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.util.newUuid
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * テスト用サンプルデータ投入(架空データのみ)。
 * debugビルドの設定画面からのみ呼び出される(BuildConfig.DEBUG ガード)。
 */
@Singleton
class SampleDataSeeder @Inject constructor(
    private val database: AppDatabase,
    private val staffRepository: StaffRepository,
    private val auditLogger: AuditLogger,
) {
    suspend fun seed() {
        val now = nowEpochMillis()
        val today = LocalDate.now()

        // スタッフ
        val staffNames = listOf("スタッフA", "スタッフB")
        val existingStaff = database.staffDao().observeActive().first().map { it.name }.toSet()
        staffNames.filter { it !in existingStaff }.forEachIndexed { i, name ->
            staffRepository.add(name, i)
        }

        fun customer(
            name: String,
            kana: String,
            phone: String,
            category: CustomerCategory,
            visitCount: Int,
            lastStaff: String,
            caution: String = "",
        ): CustomerEntity {
            return CustomerEntity(
                id = newUuid(),
                customerNumber = "C${(1000..9999).random()}",
                name = name,
                kana = kana,
                displayPhoneNumber = phone,
                normalizedPhoneNumber = PhoneNumberUtils.normalize(phone),
                category = category.name,
                visitCount = visitCount,
                lastStaffName = lastStaff,
                lastVisitDate = today.minusDays((3..60).random().toLong()).toString(),
                firstVisitDate = today.minusDays((90..400).random().toLong()).toString(),
                cautionNotes = caution,
                createdAt = now,
                updatedAt = now,
            )
        }

        // 仕様書16章のサンプル顧客(架空データ)
        val yamada = customer(
            name = "山田 太郎", kana = "やまだ たろう", phone = "090-1234-5678",
            category = CustomerCategory.FREQUENT, visitCount = 5, lastStaff = "スタッフA",
            caution = "予約時間の確認を丁寧に行う",
        )
        val sato = customer(
            name = "佐藤 花子", kana = "さとう はなこ", phone = "080-2345-6789",
            category = CustomerCategory.REGULAR, visitCount = 2, lastStaff = "スタッフB",
        )
        val suzuki = customer(
            name = "鈴木 一郎", kana = "すずき いちろう", phone = "070-3456-7890",
            category = CustomerCategory.CAUTION, visitCount = 8, lastStaff = "スタッフA",
            caution = "キャンセルが多い。前日に確認の電話を入れる",
        )
        val tanaka = customer(
            name = "田中 美咲", kana = "たなか みさき", phone = "03-1234-5678",
            category = CustomerCategory.NEW, visitCount = 0, lastStaff = "",
        )
        val customers = listOf(yamada, sato, suzuki, tanaka)
        database.customerDao().upsertAll(customers)

        // 予約(本日・明日)
        val reservations = listOf(
            ReservationEntity(
                id = newUuid(), customerId = yamada.id, date = today.toString(),
                startTime = "14:00", endTime = "15:00", staffName = "スタッフA",
                courseName = "60分コース", price = 8000,
                channel = ReservationChannel.PHONE.name, status = ReservationStatus.CONFIRMED.name,
                room = "1号室", createdByStaffName = "スタッフA", createdAt = now, updatedAt = now,
            ),
            ReservationEntity(
                id = newUuid(), customerId = sato.id, date = today.toString(),
                startTime = "16:00", endTime = "17:30", staffName = "スタッフB",
                courseName = "90分コース", price = 12000,
                channel = ReservationChannel.SMS.name, status = ReservationStatus.TENTATIVE.name,
                room = "2号室", createdByStaffName = "スタッフA", createdAt = now, updatedAt = now,
            ),
            ReservationEntity(
                id = newUuid(), customerId = suzuki.id, date = today.plusDays(1).toString(),
                startTime = "11:00", endTime = "12:00", staffName = "スタッフA",
                courseName = "60分コース", price = 8000,
                channel = ReservationChannel.LINE.name, status = ReservationStatus.CONFIRMED.name,
                room = "1号室", cautionNotes = "前日確認の電話を入れること",
                createdByStaffName = "スタッフB", createdAt = now, updatedAt = now,
            ),
        )
        database.reservationDao().upsertAll(reservations)

        // 対応履歴
        val interactions = listOf(
            CallInteractionEntity(
                id = newUuid(), customerId = yamada.id,
                displayPhoneNumber = yamada.displayPhoneNumber,
                normalizedPhoneNumber = yamada.normalizedPhoneNumber,
                occurredAt = LocalDateTime.now().minusHours(2).withNano(0).toString(),
                direction = CallDirection.INCOMING.name, answered = true,
                result = CallResult.RESERVED.name, inquiry = "本日の空き状況の確認",
                guidanceGiven = "14時からの枠を案内", reservationCreated = true,
                reservationId = reservations[0].id, staffName = "スタッフA",
                createdAt = now, updatedAt = now,
            ),
            CallInteractionEntity(
                id = newUuid(), customerId = tanaka.id,
                displayPhoneNumber = tanaka.displayPhoneNumber,
                normalizedPhoneNumber = tanaka.normalizedPhoneNumber,
                occurredAt = LocalDateTime.now().minusHours(5).withNano(0).toString(),
                direction = CallDirection.INCOMING.name, answered = false,
                result = CallResult.NO_ANSWER.name,
                callbackRequired = true, callbackDone = false,
                staffName = "スタッフB", memo = "不在着信。折り返し待ち",
                createdAt = now, updatedAt = now,
            ),
        )
        database.callInteractionDao().upsertAll(interactions)

        auditLogger.log(AuditLogger.ACTION_SEED_SAMPLE, AuditLogger.TARGET_ALL, detail = "customers=${customers.size}")
    }
}
