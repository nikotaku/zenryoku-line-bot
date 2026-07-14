package com.store.cti.data.repository

import com.store.cti.core.reservation.OverlapConflict
import com.store.cti.core.reservation.ReservationOverlapChecker
import com.store.cti.core.reservation.ReservationSlot
import com.store.cti.data.local.dao.ReservationDao
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationRepository @Inject constructor(
    private val dao: ReservationDao,
    private val auditLogger: AuditLogger,
) {
    fun observeActive(): Flow<List<ReservationEntity>> = dao.observeActive()
    fun observeByDate(date: LocalDate): Flow<List<ReservationEntity>> =
        dao.observeByDate(date.toString())
    fun observeByCustomer(customerId: String): Flow<List<ReservationEntity>> =
        dao.observeByCustomer(customerId)

    suspend fun getById(id: String): ReservationEntity? = dao.getById(id)

    /**
     * 同一担当者・同一部屋の時間重複を検出する(警告のみ。保存は妨げない)。
     * 判定ロジックは :core の ReservationOverlapChecker(単体テスト済み)。
     */
    suspend fun findConflicts(candidate: ReservationEntity): List<OverlapConflict> {
        val slot = candidate.toSlot() ?: return emptyList()
        val sameDay = dao.listByDate(candidate.date).mapNotNull { it.toSlot() }
        return ReservationOverlapChecker.findConflicts(slot, sameDay)
    }

    suspend fun save(entity: ReservationEntity, isNew: Boolean): ReservationEntity {
        val now = nowEpochMillis()
        val stamped = entity.copy(
            createdAt = if (isNew) now else entity.createdAt,
            updatedAt = now,
            version = if (isNew) 1 else entity.version + 1,
        )
        dao.upsert(stamped)
        auditLogger.log(
            if (isNew) AuditLogger.ACTION_CREATE else AuditLogger.ACTION_UPDATE,
            AuditLogger.TARGET_RESERVATION,
            stamped.id,
        )
        return stamped
    }

    suspend fun softDelete(id: String) {
        val entity = dao.getById(id) ?: return
        val now = nowEpochMillis()
        dao.upsert(entity.copy(deletedAt = now, updatedAt = now, version = entity.version + 1))
        auditLogger.log(AuditLogger.ACTION_SOFT_DELETE, AuditLogger.TARGET_RESERVATION, id)
    }

    private fun ReservationEntity.toSlot(): ReservationSlot? {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val s = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
        val e = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null
        return ReservationSlot(
            id = id,
            date = d,
            startTime = s,
            endTime = e,
            staffName = staffName,
            room = room,
            occupiesSlot = statusEnum.occupiesSlot,
        )
    }
}
