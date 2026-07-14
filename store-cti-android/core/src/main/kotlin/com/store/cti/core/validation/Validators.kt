package com.store.cti.core.validation

import com.store.cti.core.phone.PhoneNumberUtils
import java.time.LocalTime

/** 利用者向けメッセージを持つ検証結果 */
sealed class ValidationResult {
    data object Ok : ValidationResult()
    data class Error(val message: String) : ValidationResult()

    val isOk: Boolean get() = this is Ok
}

/** 予約時間の検証 */
object ReservationTimeValidator {

    fun validate(start: LocalTime?, end: LocalTime?): ValidationResult = when {
        start == null || end == null ->
            ValidationResult.Error("開始時間と終了時間を入力してください")
        !end.isAfter(start) ->
            ValidationResult.Error("終了時間は開始時間より後にしてください")
        else -> ValidationResult.Ok
    }
}

/** 電話番号入力の検証(空・形式不正) */
object PhoneNumberValidator {

    fun validate(raw: String?, required: Boolean = true): ValidationResult {
        val normalized = PhoneNumberUtils.normalize(raw)
        return when {
            normalized.isEmpty() ->
                if (required) ValidationResult.Error("電話番号を入力してください") else ValidationResult.Ok
            !PhoneNumberUtils.isValidJapaneseNumber(normalized) ->
                ValidationResult.Error("電話番号の形式が正しくありません(例: 090-1234-5678)")
            else -> ValidationResult.Ok
        }
    }
}

/**
 * 顧客電話番号の重複判定(純ロジック)。
 * [existingNormalizedNumbers] は有効な(論理削除されていない)他顧客の正規化済み番号。
 */
object CustomerDuplicateChecker {

    fun findDuplicates(
        candidateRaw: String?,
        existingNormalizedNumbers: Map<String, String>, // customerId -> normalizedPhoneNumber
        selfCustomerId: String? = null,
    ): List<String> {
        val candidate = PhoneNumberUtils.normalize(candidateRaw)
        if (candidate.isEmpty()) return emptyList()
        return existingNormalizedNumbers
            .filter { (id, number) -> id != selfCustomerId && number == candidate }
            .keys.toList()
    }
}
