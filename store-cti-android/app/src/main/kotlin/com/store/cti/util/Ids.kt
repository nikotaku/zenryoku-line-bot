package com.store.cti.util

import java.util.UUID

/** 主要データの識別子は UUID 文字列(将来の複数端末同期用) */
fun newUuid(): String = UUID.randomUUID().toString()

fun nowEpochMillis(): Long = System.currentTimeMillis()
