package com.store.cti.domain

import com.store.cti.core.reservation.OverlapConflict
import com.store.cti.core.sms.SmsMergeValues
import com.store.cti.core.sms.SmsTemplateEngine
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.ReservationRepository
import com.store.cti.data.settings.SettingsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 顧客保存(重複電話番号の警告付き) */
class SaveCustomerUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
) {
    data class Result(
        val saved: CustomerEntity,
        val duplicateNames: List<String>,
    )

    /** 保存前に重複を調べる(警告表示用。保存はしない) */
    suspend fun findDuplicateNames(rawPhone: String, selfId: String?): List<String> =
        customerRepository.findDuplicatesByPhone(rawPhone, selfId).map { it.name }

    suspend operator fun invoke(entity: CustomerEntity, isNew: Boolean): Result {
        val duplicates = customerRepository.findDuplicatesByPhone(entity.displayPhoneNumber, entity.id)
        val saved = customerRepository.save(entity, isNew)
        return Result(saved, duplicates.map { it.name })
    }
}

/** 予約保存(担当者・部屋の時間重複チェック付き) */
class SaveReservationUseCase @Inject constructor(
    private val reservationRepository: ReservationRepository,
) {
    suspend fun findConflicts(entity: ReservationEntity): List<OverlapConflict> =
        reservationRepository.findConflicts(entity)

    suspend operator fun invoke(entity: ReservationEntity, isNew: Boolean): ReservationEntity =
        reservationRepository.save(entity, isNew)
}

/** SMS本文の差し込み生成 */
class BuildSmsBodyUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        templateBody: String,
        customer: CustomerEntity?,
        reservation: ReservationEntity?,
    ): String {
        val settings = settingsRepository.current()
        val values = SmsMergeValues(
            customerName = customer?.name ?: "",
            reservationDate = reservation?.date?.let(::formatJapaneseDate) ?: "",
            startTime = reservation?.startTime ?: "",
            endTime = reservation?.endTime ?: "",
            staffName = reservation?.staffName?.ifBlank { settings.defaultStaffName }
                ?: settings.defaultStaffName,
            courseName = reservation?.courseName ?: "",
            price = reservation?.price?.let { "%,d円".format(it) } ?: "",
            storeName = settings.storeName,
            storePhone = settings.storePhone,
        )
        return SmsTemplateEngine.merge(templateBody, values)
    }

    private fun formatJapaneseDate(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }.getOrDefault(isoDate)
}
