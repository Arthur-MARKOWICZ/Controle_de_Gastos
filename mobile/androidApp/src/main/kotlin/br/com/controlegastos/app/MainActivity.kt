package br.com.controlegastos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authGateway = AndroidAuthGateway(applicationContext, BuildConfig.API_BASE_URL)
        setContent { VerbasApp(authGateway, authGateway) }
    }
}
