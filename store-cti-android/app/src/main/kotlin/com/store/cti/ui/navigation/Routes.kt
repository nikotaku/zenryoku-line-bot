package com.store.cti.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val CUSTOMER_LIST = "customers"
    const val CUSTOMER_DETAIL = "customer/{customerId}"
    const val CUSTOMER_EDIT = "customer_edit?customerId={customerId}&phone={phone}"
    const val CALL_INTAKE = "call_intake?phone={phone}"
    const val INTERACTION_EDIT =
        "interaction_edit?interactionId={interactionId}&customerId={customerId}&phone={phone}"
    const val INTERACTION_LIST = "interactions"
    const val RESERVATION_LIST = "reservations"
    const val RESERVATION_EDIT = "reservation_edit?reservationId={reservationId}&customerId={customerId}"
    const val SMS_TEMPLATES = "sms_templates"
    const val SMS_COMPOSE = "sms_compose?customerId={customerId}&reservationId={reservationId}&phone={phone}"
    const val SETTINGS = "settings"

    fun customerDetail(customerId: String) = "customer/$customerId"
    fun customerEdit(customerId: String? = null, phone: String = "") =
        "customer_edit?customerId=${customerId ?: ""}&phone=${Uri.encode(phone)}"
    fun callIntake(phone: String = "") = "call_intake?phone=${Uri.encode(phone)}"
    fun interactionEdit(interactionId: String? = null, customerId: String? = null, phone: String = "") =
        "interaction_edit?interactionId=${interactionId ?: ""}&customerId=${customerId ?: ""}&phone=${Uri.encode(phone)}"
    fun reservationEdit(reservationId: String? = null, customerId: String? = null) =
        "reservation_edit?reservationId=${reservationId ?: ""}&customerId=${customerId ?: ""}"
    fun smsCompose(customerId: String? = null, reservationId: String? = null, phone: String = "") =
        "sms_compose?customerId=${customerId ?: ""}&reservationId=${reservationId ?: ""}&phone=${Uri.encode(phone)}"

    /** ディープリンク(通知・ショートカット・共有受け取り) */
    const val DEEP_LINK_INTAKE = "storecti://intake?phone={phone}"
    const val DEEP_LINK_SMS = "storecti://sms"
}
