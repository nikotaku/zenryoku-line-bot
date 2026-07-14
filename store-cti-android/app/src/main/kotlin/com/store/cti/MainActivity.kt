package com.store.cti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.ui.navigation.StoreCtiNavHost
import com.store.cti.ui.theme.StoreCtiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 共有(ACTION_SEND)で受け取ったテキストをディープリンクへ変換してから
        // NavHost に渡す(コールドスタート時のディープリンク処理を利用する)
        mapShareIntent(intent)?.let { setIntent(it) }

        // スクリーンショット制限(設定と連動)
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            StoreCtiTheme {
                val controller = rememberNavController()
                navController = controller
                StoreCtiNavHost(navController = controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val mapped = mapShareIntent(intent) ?: intent
        navController?.handleDeepLink(mapped)
    }

    /** ACTION_SEND(text/plain) を storecti://intake ディープリンクへ変換する */
    private fun mapShareIntent(intent: Intent?): Intent? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val phone = PhoneNumberUtils.extractFirstPhoneNumber(text) ?: ""
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("storecti://intake?phone=${Uri.encode(phone)}"),
        ).apply {
            setPackage(packageName)
        }
    }
}
