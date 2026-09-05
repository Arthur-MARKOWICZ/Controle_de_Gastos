package br.com.controlegastos.app

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authGateway = AndroidAuthGateway(applicationContext, BuildConfig.API_BASE_URL)
        val themeStore = AndroidThemePreferenceStore(applicationContext)
        setContent {
            VerbasApp(
                authGateway = authGateway,
                financeGateway = authGateway,
                themePreferenceStore = themeStore,
                onThemeResolved = { dark ->
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                },
                qrImageContent = { dataUri -> AndroidQrImage(dataUri) },
            )
        }
    }
}
