package com.store.cti.telephony

/**
 * 通話録音の交換可能インターフェース(docs/REQUIREMENTS.md 2.4)。
 *
 * SIM回線通話の録音は Android バージョン・端末・地域・通信会社・既定電話アプリの
 * 状態によって制限されるため、初期版では実装しない。
 * 将来の外部CTI・通信会社サービス・VoIP・録音API連携のための差し替え口のみ用意する。
 */
interface RecordingProvider {
    val isSupported: Boolean
    val unsupportedReason: String
}

class UnsupportedRecordingProvider @javax.inject.Inject constructor() : RecordingProvider {
    override val isSupported: Boolean = false
    override val unsupportedReason: String = "この構成では録音に対応していません"
}
