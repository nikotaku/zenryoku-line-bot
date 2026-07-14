package com.store.cti.telephony

import android.telecom.Call
import android.telecom.CallScreeningService
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 2(実験機能): 通話スクリーニングサービス。
 *
 * - ユーザーが設定画面で有効化し、ROLE_CALL_SCREENING を許可した場合のみシステムから呼ばれる
 * - 着信のブロック・無音化は一切行わない(常に許可で応答する)
 * - 応答後に非同期で顧客を検索し、通知として表示する
 */
@AndroidEntryPoint
class StoreCtiCallScreeningService : CallScreeningService() {

    @Inject lateinit var customerRepository: CustomerRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var callNotifier: CallNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // まず必ず「許可」で応答する(既定値のまま = 拒否しない)
        respondToCall(callDetails, CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val rawNumber = callDetails.handle?.schemeSpecificPart ?: return
        val normalized = PhoneNumberUtils.normalize(rawNumber)
        if (normalized.isEmpty()) return

        scope.launch {
            try {
                if (!settingsRepository.current().callScreeningEnabled) return@launch
                val customer = customerRepository.findByPhone(normalized).firstOrNull()
                AppLogger.d("screening: incoming ${PhoneNumberUtils.mask(normalized)} known=${customer != null}")
                callNotifier.notifyIncomingCall(normalized, customer)
            } catch (t: Throwable) {
                AppLogger.e("screening failed", t)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
