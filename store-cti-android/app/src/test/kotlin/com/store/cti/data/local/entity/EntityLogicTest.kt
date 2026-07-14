package com.store.cti.data.local.entity

import com.store.cti.core.model.CustomerCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityLogicTest {

    // --- 論理削除 ---

    @Test
    fun `論理削除はdeletedAtの設定で表現される`() {
        val customer = CustomerEntity(id = "c-1", name = "山田 太郎")
        assertFalse(customer.isDeleted)

        val deleted = customer.copy(deletedAt = 1234L, updatedAt = 1234L, version = customer.version + 1)
        assertTrue(deleted.isDeleted)
        // 論理削除後もデータ自体は保持される
        assertEquals("山田 太郎", deleted.name)
        assertEquals(2, deleted.version)
    }

    @Test
    fun `有効データの絞り込み(deletedAt IS NULL 相当)`() {
        val list = listOf(
            CustomerEntity(id = "a", name = "有効"),
            CustomerEntity(id = "b", name = "削除済み", deletedAt = 1L),
        )
        assertEquals(listOf("有効"), list.filter { !it.isDeleted }.map { it.name })
    }

    // --- 顧客区分変更 ---

    @Test
    fun `顧客区分の変更と判定`() {
        val customer = CustomerEntity(id = "c-1", category = CustomerCategory.NEW.name)
        assertEquals(CustomerCategory.NEW, customer.categoryEnum)
        assertFalse(customer.categoryEnum.requiresAttention)

        val caution = customer.copy(category = CustomerCategory.CAUTION.name)
        assertEquals(CustomerCategory.CAUTION, caution.categoryEnum)
        assertTrue(caution.categoryEnum.requiresAttention)

        val banned = customer.copy(category = CustomerCategory.BANNED.name)
        assertTrue(banned.categoryEnum.requiresAttention)
    }

    @Test
    fun `未知の顧客区分は通常として扱う(データ互換)`() {
        val customer = CustomerEntity(id = "c-1", category = "FUTURE_CATEGORY")
        assertEquals(CustomerCategory.REGULAR, customer.categoryEnum)
    }

    // --- 折り返し ---

    @Test
    fun `折り返し未完了の判定`() {
        val base = CallInteractionEntity(id = "i-1")
        assertFalse(base.callbackPending)
        assertTrue(base.copy(callbackRequired = true).callbackPending)
        assertFalse(base.copy(callbackRequired = true, callbackDone = true).callbackPending)
    }
}
