package com.store.cti.util

import android.util.Log
import com.store.cti.BuildConfig

/**
 * アプリ共通ロガー。
 * - リリースビルドでは全て no-op(docs/SECURITY.md 2章)
 * - 電話番号・氏名・メモなどの個人情報は呼び出し側でマスクしてから渡すこと
 */
object AppLogger {
    private const val TAG = "StoreCti"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable)
    }
}
