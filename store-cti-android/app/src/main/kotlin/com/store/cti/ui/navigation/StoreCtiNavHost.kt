package com.store.cti.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.store.cti.ui.screens.customers.CustomerDetailScreen
import com.store.cti.ui.screens.customers.CustomerEditScreen
import com.store.cti.ui.screens.customers.CustomerListScreen
import com.store.cti.ui.screens.home.HomeScreen
import com.store.cti.ui.screens.intake.CallIntakeScreen
import com.store.cti.ui.screens.interactions.InteractionEditScreen
import com.store.cti.ui.screens.interactions.InteractionListScreen
import com.store.cti.ui.screens.reservations.ReservationEditScreen
import com.store.cti.ui.screens.reservations.ReservationListScreen
import com.store.cti.ui.screens.settings.SettingsScreen
import com.store.cti.ui.screens.sms.SmsComposeScreen
import com.store.cti.ui.screens.sms.SmsTemplateListScreen

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    defaultValue = ""
}

@Composable
fun StoreCtiNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(onNavigate = { navController.navigate(it) })
        }

        composable(Routes.CUSTOMER_LIST) {
            CustomerListScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) },
                onNewCustomer = { navController.navigate(Routes.customerEdit()) },
            )
        }

        composable(
            Routes.CUSTOMER_DETAIL,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) { entry ->
            val customerId = entry.arguments?.getString("customerId").orEmpty()
            CustomerDetailScreen(
                customerId = customerId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.customerEdit(customerId)) },
                onAddReservation = { navController.navigate(Routes.reservationEdit(customerId = customerId)) },
                onAddInteraction = { phone ->
                    navController.navigate(Routes.interactionEdit(customerId = customerId, phone = phone))
                },
                onComposeSms = { navController.navigate(Routes.smsCompose(customerId = customerId)) },
                onOpenReservation = { navController.navigate(Routes.reservationEdit(reservationId = it)) },
                onOpenInteraction = { navController.navigate(Routes.interactionEdit(interactionId = it)) },
            )
        }

        composable(
            Routes.CUSTOMER_EDIT,
            arguments = listOf(optionalString("customerId"), optionalString("phone")),
        ) { entry ->
            CustomerEditScreen(
                customerId = entry.arguments?.getString("customerId").orEmpty().ifBlank { null },
                initialPhone = entry.arguments?.getString("phone").orEmpty(),
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Routes.customerDetail(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(
            Routes.CALL_INTAKE,
            arguments = listOf(optionalString("phone")),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_INTAKE }),
        ) { entry ->
            CallIntakeScreen(
                initialPhone = entry.arguments?.getString("phone").orEmpty(),
                onBack = { navController.popBackStack() },
                onRegisterNew = { phone -> navController.navigate(Routes.customerEdit(phone = phone)) },
                onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) },
                onAddInteraction = { customerId, phone ->
                    navController.navigate(Routes.interactionEdit(customerId = customerId, phone = phone))
                },
            )
        }

        composable(
            Routes.INTERACTION_EDIT,
            arguments = listOf(
                optionalString("interactionId"),
                optionalString("customerId"),
                optionalString("phone"),
            ),
        ) { entry ->
            InteractionEditScreen(
                interactionId = entry.arguments?.getString("interactionId").orEmpty().ifBlank { null },
                customerId = entry.arguments?.getString("customerId").orEmpty().ifBlank { null },
                initialPhone = entry.arguments?.getString("phone").orEmpty(),
                onBack = { navController.popBackStack() },
                onCreateReservation = { customerId ->
                    navController.navigate(Routes.reservationEdit(customerId = customerId))
                },
            )
        }

        composable(Routes.INTERACTION_LIST) {
            InteractionListScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) },
                onOpenReservation = { navController.navigate(Routes.reservationEdit(reservationId = it)) },
                onOpenInteraction = { navController.navigate(Routes.interactionEdit(interactionId = it)) },
            )
        }

        composable(Routes.RESERVATION_LIST) {
            ReservationListScreen(
                onBack = { navController.popBackStack() },
                onNew = { navController.navigate(Routes.reservationEdit()) },
                onEdit = { navController.navigate(Routes.reservationEdit(reservationId = it)) },
                onOpenCustomer = { navController.navigate(Routes.customerDetail(it)) },
            )
        }

        composable(
            Routes.RESERVATION_EDIT,
            arguments = listOf(optionalString("reservationId"), optionalString("customerId")),
        ) { entry ->
            ReservationEditScreen(
                reservationId = entry.arguments?.getString("reservationId").orEmpty().ifBlank { null },
                customerId = entry.arguments?.getString("customerId").orEmpty().ifBlank { null },
                onBack = { navController.popBackStack() },
                onComposeSms = { cid, rid ->
                    navController.navigate(Routes.smsCompose(customerId = cid, reservationId = rid))
                },
            )
        }

        composable(Routes.SMS_TEMPLATES) {
            SmsTemplateListScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Routes.SMS_COMPOSE,
            arguments = listOf(
                optionalString("customerId"),
                optionalString("reservationId"),
                optionalString("phone"),
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_SMS }),
        ) { entry ->
            SmsComposeScreen(
                customerId = entry.arguments?.getString("customerId").orEmpty().ifBlank { null },
                reservationId = entry.arguments?.getString("reservationId").orEmpty().ifBlank { null },
                initialPhone = entry.arguments?.getString("phone").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTemplates = { navController.navigate(Routes.SMS_TEMPLATES) },
            )
        }
    }
}
