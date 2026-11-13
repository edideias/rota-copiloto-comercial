package br.rota.copiloto

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Avisos da agenda (eventos e incentivos da 99) e posição do GPS para o Diário. */
object Alarmes {
    private const val CANAL = "agenda"
    private val hora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    fun notificar(ctx: Context, id: Int, titulo: String, texto: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CANAL, "Agenda e incentivos", NotificationManager.IMPORTANCE_HIGH))
            val abrir = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra("aba", "agenda"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(1000 + (id and 0xFFFF), Notification.Builder(ctx, CANAL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(titulo).setContentText(texto)
                .setStyle(Notification.BigTextStyle().bigText(texto))
                .setContentIntent(abrir).setAutoCancel(true).build())
        } catch (_: Exception) {}
    }

    private fun agendar(ctx: Context, quando: Long, req: Int, titulo: String, texto: String) {
        if (quando <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, req,
            Intent(ctx, AlarmeReceiver::class.java).putExtra("id", req).putExtra("titulo", titulo).putExtra("texto", texto),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        } catch (_: Exception) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        }
    }

    private fun cancelar(ctx: Context, req: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, AlarmeReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
    }

    private fun reqEvento(id: Long) = (id % 1_000_000L).toInt() * 4
    private fun reqIncentivo(id: Long) = (id % 1_000_000L).toInt() * 4 + 1
    private fun reqIncentivoFim(id: Long) = (id % 1_000_000L).toInt() * 4 + 2

    /** Reagenda tudo da agenda (ao abrir o app, ao salvar e depois de religar o celular). */
    fun reagendar(ctx: Context) {
        Diario.carregar(ctx)
        val agora = System.currentTimeMillis()
        Diario.eventos.forEach { e ->
            cancelar(ctx, reqEvento(e.id))
            if (e.fim > agora) agendar(ctx, e.fim - 30 * 60_000L, reqEvento(e.id),
                "${e.nome} termina às ${hora.format(Date(e.fim))}",
                "Bom momento para se posicionar perto de ${e.local.ifEmpty { "o local" }}: muita gente vai sair junto.")
        }
        Diario.incentivos.forEach { i ->
            cancelar(ctx, reqIncentivo(i.id)); cancelar(ctx, reqIncentivoFim(i.id))
            val nome = if (i.tipo == "taxa_zero") "Taxa zero" else "Missão"
            if (i.ini > agora) agendar(ctx, i.ini, reqIncentivo(i.id), "$nome começou: ${i.nome}",
                if (i.tipo == "taxa_zero") "Vai até as ${hora.format(Date(i.fim))}. Fique online."
                else "Meta: ${i.meta} corridas até ${hora.format(Date(i.fim))}" + (if (i.bonus > 0) String.format(Locale("pt", "BR"), " · bônus R$ %.2f", i.bonus) else "") + ".")
            if (i.tipo == "missao" && i.fim - 60 * 60_000L > agora) agendar(ctx, i.fim - 60 * 60_000L, reqIncentivoFim(i.id),
                "Falta 1 hora: ${i.nome}", "Abra o Diário para ver quantas corridas faltam.")
        }
    }

    /** Depois de registrar uma corrida: avisa quanto falta para cada missão ativa. */
    fun avisarMissoes(ctx: Context) {
        val agora = System.currentTimeMillis()
        Diario.incentivos.filter { it.tipo == "missao" && agora in it.ini..it.fim && it.meta > 0 }.forEach { i ->
            val feitas = Diario.feitasNaMissao(i)
            val faltam = (i.meta - feitas).coerceAtLeast(0)
            notificar(ctx, reqIncentivo(i.id), i.nome,
                if (faltam == 0) "Meta batida: $feitas de ${i.meta} corridas." else "$feitas de ${i.meta} corridas. Faltam $faltam até ${hora.format(Date(i.fim))}.")
        }
    }

    /** Bairro onde o celular está agora (última posição conhecida, sem gastar bateria). */
    fun bairroAqui(ctx: Context): String? {
        try {
            val ok = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!ok) return null
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .filter { System.currentTimeMillis() - it.time < 15 * 60_000L }.maxByOrNull { it.time } ?: return null
            return Destino.bairroPorGps(loc.latitude, loc.longitude)
        } catch (_: Exception) { return null }
    }
}

class AlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED || i.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            Alarmes.reagendar(ctx); return
        }
        Alarmes.notificar(ctx, i.getIntExtra("id", 0), i.getStringExtra("titulo") ?: "Rota Calculadora", i.getStringExtra("texto") ?: "")
    }
}
