package com.store.cti.core.reservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReservationOverlapCheckerTest {

    private val date = LocalDate.of(2026, 7, 20)

    private fun slot(
        id: String,
        start: String,
        end: String,
        staff: String? = "スタッフA",
        room: String? = null,
        date: LocalDate = this.date,
        occupies: Boolean = true,
    ) = ReservationSlot(
        id = id,
        date = date,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end),
        staffName = staff,
        room = room,
        occupiesSlot = occupies,
    )

    @Test
    fun `同一担当者で時間が重なると警告`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:30", "11:30"),
            listOf(slot("a", "10:00", "11:00")),
        )
        assertEquals(1, conflicts.size)
        assertTrue(conflicts[0].sameStaff)
        assertFalse(conflicts[0].sameRoom)
    }

    @Test
    fun `境界が接するだけ(終了=開始)は重複しない`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "11:00", "12:00"),
            listOf(slot("a", "10:00", "11:00")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `完全包含も重複と判定する`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "13:00"),
            listOf(slot("a", "11:00", "12:00")),
        )
        assertEquals(1, conflicts.size)
    }

    @Test
    fun `担当者も部屋も異なる場合は警告しない`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "11:00", staff = "スタッフB", room = "1号室"),
            listOf(slot("a", "10:00", "11:00", staff = "スタッフA", room = "2号室")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `同一部屋なら担当者が違っても警告`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "11:00", staff = "スタッフB", room = "1号室"),
            listOf(slot("a", "10:30", "11:30", staff = "スタッフA", room = "1号室")),
        )
        assertEquals(1, conflicts.size)
        assertFalse(conflicts[0].sameStaff)
        assertTrue(conflicts[0].sameRoom)
    }

    @Test
    fun `担当者・部屋が未入力同士は警告しない`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "11:00", staff = "", room = ""),
            listOf(slot("a", "10:00", "11:00", staff = null, room = " ")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `日付が異なれば重複しない`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "11:00"),
            listOf(slot("a", "10:00", "11:00", date = date.plusDays(1))),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `キャンセル済み予約とは重複しない`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "11:00"),
            listOf(slot("a", "10:00", "11:00", occupies = false)),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `自分自身(編集中の予約)は除外する`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("same-id", "10:00", "11:00"),
            listOf(slot("same-id", "10:00", "11:00")),
        )
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `複数の重複をすべて返す`() {
        val conflicts = ReservationOverlapChecker.findConflicts(
            slot("new", "10:00", "12:00"),
            listOf(
                slot("a", "09:30", "10:30"),
                slot("b", "11:00", "13:00"),
                slot("c", "12:00", "13:00"), // 接触のみ
            ),
        )
        assertEquals(listOf("a", "b"), conflicts.map { it.other.id })
    }
}
