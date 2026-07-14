package com.store.cti.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2(実験機能): 通話スクリーニング役割のゲートウェイ。
 * 端末非対応の場合は isSupported=false になり、設定画面に
 * 「この端末では利用できません」と表示される(REQUIREMENTS.md 2.2)。
 */
interface CallScreeningGateway {
    val isSupported: Boolean
    fun hasRole(): Boolean
    fun roleRequestIntent(): Intent?
}

@Singleton
class RoleManagerCallScreeningGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : CallScreeningGateway {

    private val roleManager: RoleManager?
        get() = context.getSystemService(RoleManager::class.java)

    override val isSupported: Boolean
        get() = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true

    override fun hasRole(): Boolean =
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    override fun roleRequestIntent(): Intent? =
        if (isSupported) roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) else null
}

/**
 * Phase 3(本アプリでは未実装・別開発): 既定の電話アプリ(ROLE_DIALER / InCallService)用の
 * 交換可能インターフェース。詳細は docs/ARCHITECTURE.md 9章。
 */
interface DialerRoleGateway {
    val isSupported: Boolean
    fun hasRole(): Boolean
    fun roleRequestIntent(): Intent?
}

@Singleton
class NotImplementedDialerRoleGateway @Inject constructor() : DialerRoleGateway {
    override val isSupported: Boolean = false
    override fun hasRole(): Boolean = false
    override fun roleRequestIntent(): Intent? = null
}
