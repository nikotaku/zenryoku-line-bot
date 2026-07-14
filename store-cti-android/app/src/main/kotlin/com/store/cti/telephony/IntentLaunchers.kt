package com.store.cti.telephony

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** インテント起動の結果(利用者向けメッセージ) */
sealed class LaunchResult {
    data object Success : LaunchResult()
    data class Failure(val userMessage: String) : LaunchResult()
}

/** Phase 1: 発信は ACTION_DIAL(番号入力済みの標準電話アプリを開く。権限不要) */
interface OutboundCallLauncher {
    fun openDialer(rawPhone: String): LaunchResult
}

/** Phase 1: SMSは ACTION_SENDTO + smsto:(宛先・本文入力済みの標準SMSアプリを開く。権限不要) */
interface SmsComposerLauncher {
    fun openSmsComposer(rawPhone: String, body: String): LaunchResult
}

@Singleton
class IntentLaunchers @Inject constructor(
    @ApplicationContext private val context: Context,
) : OutboundCallLauncher, SmsComposerLauncher {

    override fun openDialer(rawPhone: String): LaunchResult {
        val normalized = PhoneNumberUtils.normalize(rawPhone)
        if (normalized.isEmpty()) return LaunchResult.Failure("電話番号が入力されていません")
        if (!PhoneNumberUtils.isValidJapaneseNumber(normalized)) {
            return LaunchResult.Failure("電話番号の形式が正しくありません(例: 090-1234-5678)")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            AppLogger.w("dialer not found")
            LaunchResult.Failure("電話アプリが見つかりません。この端末では発信できない可能性があります")
        }
    }

    override fun openSmsComposer(rawPhone: String, body: String): LaunchResult {
        val normalized = PhoneNumberUtils.normalize(rawPhone)
        if (normalized.isEmpty()) return LaunchResult.Failure("宛先の電話番号が入力されていません")
        if (!PhoneNumberUtils.isValidJapaneseNumber(normalized)) {
            return LaunchResult.Failure("宛先の電話番号の形式が正しくありません")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$normalized")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            AppLogger.w("sms app not found")
            LaunchResult.Failure("SMSアプリが見つかりません。この端末ではSMSを作成できない可能性があります")
        }
    }
}
