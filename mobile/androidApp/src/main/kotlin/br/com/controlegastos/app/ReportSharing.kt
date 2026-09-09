package br.com.controlegastos.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Entrega o relatório baixado a outro aplicativo.
 *
 * O arquivo fica no diretório privado do app e é exposto por [FileProvider],
 * o que dispensa permissão de armazenamento em qualquer versão suportada.
 */
fun Context.openReport(report: DownloadedReport) {
    val file = File(report.location)
    val uri = FileProvider.getUriForFile(this, "$packageName.relatorios", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeOf(report.fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, report.fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(share, "Compartilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun mimeTypeOf(fileName: String): String = when {
    fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
    fileName.endsWith(".xlsx", ignoreCase = true) ->
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    else -> "application/octet-stream"
}
