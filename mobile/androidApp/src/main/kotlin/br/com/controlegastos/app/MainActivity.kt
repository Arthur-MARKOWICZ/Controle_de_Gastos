package br.com.controlegastos.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth as JavaYearMonth

private val REFERENCE_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

class MainActivity : ComponentActivity() {

    /** App Link do login social; a activity é singleTask e pode recebê-lo já aberta. */
    private var pendingOAuthCallback by mutableStateOf<OAuthCallback?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        oauthCallbackOf(intent)?.let { pendingOAuthCallback = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOAuthCallback = oauthCallbackOf(intent)
        val client = AndroidApiClient(applicationContext, BuildConfig.API_BASE_URL)
        val gateway = AndroidApiGateway(applicationContext, client)
        val themeStore = AndroidThemePreferenceStore(applicationContext)
        setContent {
            VerbasApp(
                gateways = VerbasGateways(
                    auth = gateway,
                    envelopes = gateway,
                    ledger = gateway,
                    income = gateway,
                    reports = gateway,
                ),
                // Data e mês de referência vêm da plataforma, no mesmo fuso que o
                // backend usa para resolver o mês corrente.
                currentMonth = JavaYearMonth.now(REFERENCE_ZONE)
                    .let { YearMonth(it.year, it.monthValue) },
                today = LocalDate.now(REFERENCE_ZONE).toString(),
                themePreferenceStore = themeStore,
                onThemeResolved = { dark ->
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                },
                qrImageContent = { dataUri -> AndroidQrImage(dataUri) },
                onOpenReport = { report -> openReport(report) },
                onOpenInBrowser = { url -> openOAuthInBrowser(url) },
                oauthCallback = pendingOAuthCallback,
                onOAuthCallbackHandled = { pendingOAuthCallback = null },
            )
        }
    }
}
