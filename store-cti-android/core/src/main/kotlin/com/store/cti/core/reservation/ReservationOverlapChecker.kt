package com.store.cti.core.reservation

import java.time.LocalDate
import java.time.LocalTime

/**
 * 重複判定に必要な最小限の予約情報。
 * [occupiesSlot] が false(キャンセル・無断キャンセル・変更済み)の予約は判定対象外。
 */
data class ReservationSlot(
    val id: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val staffName: String?,
    val room: String?,
    val occupiesSlot: Boolean = true,
)

/** 重複の内容(どの予約と、担当者・部屋のどちらが衝突したか) */
data class OverlapConflict(
    val other: ReservationSlot,
    val sameStaff: Boolean,
    val sameRoom: Boolean,
)

/**
 * 同一日・時間帯が重なる予約のうち、同じ担当者または同じ部屋のものを警告対象として返す。
 * 時間の重なりは半開区間で判定する(10:00-11:00 と 11:00-12:00 は重複しない)。
 */
object ReservationOverlapChecker {

    fun findConflicts(
        candidate: ReservationSlot,
        existing: List<ReservationSlot>,
    ): List<OverlapConflict> {
        if (!candidate.occupiesSlot) return emptyList()
        return existing.mapNotNull { other ->
            if (other.id == candidate.id) return@mapNotNull null
            if (!other.occupiesSlot) return@mapNotNull null
            if (other.date != candidate.date) return@mapNotNull null
            if (!timesOverlap(candidate.startTime, candidate.endTime, other.startTime, other.endTime)) {
                return@mapNotNull null
            }
            val sameStaff = bothNotBlankAndEqual(candidate.staffName, other.staffName)
            val sameRoom = bothNotBlankAndEqual(candidate.room, other.room)
            if (sameStaff || sameRoom) OverlapConflict(other, sameStaff, sameRoom) else null
        }
    }

    fun timesOverlap(start1: LocalTime, end1: LocalTime, start2: LocalTime, end2: LocalTime): Boolean =
        start1 < end2 && start2 < end1

    private fun bothNotBlankAndEqual(a: String?, b: String?): Boolean =
        !a.isNullOrBlank() && !b.isNullOrBlank() && a.trim() == b.trim()
}
