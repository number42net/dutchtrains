package net.number42.dutchtrains

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.ui.navigation.AppNavigation
import net.number42.dutchtrains.ui.navigation.Screen
import net.number42.dutchtrains.ui.theme.DutchTrainsTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CTX_RECON = "open_ctx_recon"
    }

    @Inject lateinit var appPreferences: AppPreferences

    private fun resolveStartDestination(hasApiKey: Boolean, openCtxRecon: String?): String {
        return if (!openCtxRecon.isNullOrBlank()) {
            Screen.TripDetail.createRoute(openCtxRecon)
        } else if (hasApiKey) {
            Screen.Home.route
        } else {
            Screen.Settings.route
        }
    }

    private fun render(startDestination: String) {
        setContent {
            DutchTrainsTheme {
                AppNavigation(startDestination = startDestination)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val hasApiKey = appPreferences.apiKeyFlow.first().isNotBlank()
            val startDestination = resolveStartDestination(
                hasApiKey = hasApiKey,
                openCtxRecon = intent?.getStringExtra(EXTRA_OPEN_CTX_RECON),
            )
            render(startDestination)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            val hasApiKey = appPreferences.apiKeyFlow.first().isNotBlank()
            val startDestination = resolveStartDestination(
                hasApiKey = hasApiKey,
                openCtxRecon = intent.getStringExtra(EXTRA_OPEN_CTX_RECON),
            )
            render(startDestination)
        }
    }
}
