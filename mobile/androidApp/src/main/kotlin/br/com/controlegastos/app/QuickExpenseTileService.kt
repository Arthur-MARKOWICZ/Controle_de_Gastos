package br.com.controlegastos.app

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tile de Quick Settings: abre o lançamento rápido (ou a activity de auth se
 * sem sessão — a MainActivity mostra a superfície de autenticação).
 */
class QuickExpenseTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            label = "Novo gasto"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = QuickExpenseIntents.openQuickExpense(this)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
