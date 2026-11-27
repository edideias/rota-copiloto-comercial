package br.rota.copiloto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lê as notificações do Sicredi (aviso de Pix recebido) e chama o aviso grande. Precisa de acesso a notificações liberado. */
class PixListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        ).joinToString(" ")
        val doSicredi = listOf(sbn.packageName, titulo, texto).any { it.lowercase().contains("sicredi") }
        if (!doSicredi) return
        Pix.processar(this, titulo, texto)
    }
}

/** Notificação de reserva para quando não dá para desenhar o aviso grande. */
object Notificador {
    private const val CANAL = "pix_recebido"
    private const val ID = 4702

    fun avisarPix(ctx: Context, r: Pix.Recebido) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CANAL, "Pix recebido", NotificationManager.IMPORTANCE_HIGH))
        val texto = "R$ ${r.valor}" + (r.nome?.let { " de $it" } ?: "")
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CANAL) else Notification.Builder(ctx)
        n.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pix recebido")
            .setContentText(texto)
            .setAutoCancel(true)
        nm.notify(ID, n.build())
    }
}
