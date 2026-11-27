package br.rota.copiloto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Escuta em segundo plano a palavra cadastrada (padrão "mila"). Depois dela, o nome do bairro:
 * abre a tela do mapa. Não fala nada. Fica ligado só quando você liga em Configurar > Voz.
 */
class VozService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var reconhecedor: SpeechRecognizer? = null
    private var ouvindo = false
    private var ultimaBusca = ""
    private var ultimaHora = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanais()
        val notif = notificacao("Escuta por voz ligada", "Diga a palavra e depois o bairro", null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(ID, notif)
        ouvir()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    /** Ouve de novo assim que o reconhecedor para. */
    private fun ouvir() {
        if (!Prefs(this).vozOn || ouvindo) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            atualizar("Sem reconhecimento de voz", "Este aparelho não tem o serviço de voz", null)
            return
        }
        val r = reconhecedor ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(ouvinte)
            reconhecedor = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        ouvindo = true
        try { r.startListening(intent) } catch (_: Exception) {
            ouvindo = false
            handler.postDelayed({ ouvir() }, 1500L)
        }
    }

    private val ouvinte = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(tipo: Int, b: Bundle?) {}
        override fun onError(erro: Int) {
            ouvindo = false
            handler.postDelayed({ ouvir() }, if (erro == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 400L)
        }
        override fun onPartialResults(b: Bundle?) { tratar(b, true) }
        override fun onResults(b: Bundle?) {
            ouvindo = false
            tratar(b, false)
            handler.postDelayed({ ouvir() }, 200L)
        }
    }

    /** Se a palavra-chave foi ouvida, abre o mapa do bairro de destino que está no semáforo. */
    private fun tratar(b: Bundle?, parcial: Boolean) {
        val frases = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val palavra = Destino.norm(Prefs(this).vozPalavra)
        if (palavra.isEmpty()) return
        val chave = " $palavra "
        if (!parcial && frases.isNotEmpty()) {
            atualizar("Escuta por voz ligada", "Ouvi: \"${frases[0]}\"", null)
        }
        for (f in frases) {
            if (!(" ${Destino.norm(f)} ").contains(chave)) continue
            abrirDaTela()
            return
        }
    }

    /** Abre o mapa do bairro de destino da corrida que está (ou acabou de estar) no semáforo. */
    private fun abrirDaTela() {
        val agora = System.currentTimeMillis()
        val a = CopilotService.ultimaAnalise
        if (a == null || agora - CopilotService.ultimaQuando > 180_000L) {
            atualizar("Nenhuma corrida na tela", "Fale a palavra com a oferta aberta no semáforo", null)
            return
        }
        val info = Destino.analisar(a.offer.destText, Prefs(this), a.offer.pickupText)
        val l = Destino.localDoBairro(info.bairro)
        if (l == null) {
            atualizar("Bairro do destino não encontrado", "No semáforo: \"${info.bairro ?: a.offer.destText}\"", null)
            return
        }
        if (l.nome == ultimaBusca && agora - ultimaHora < 8000L) return
        ultimaBusca = l.nome
        ultimaHora = agora
        // ida = embarque da própria corrida; chegada = destino
        val embarque = Destino.analisar(a.offer.pickupText, Prefs(this)).bairro?.let { Destino.localDoBairro(it) }
        abrirMapa(l, embarque)
    }

    private fun abrirMapa(l: Destino.Local, embarque: Destino.Local?) {
        val frase = if (embarque != null)
            String.format(BR, "De %s até %s: %.1f km em linha reta.", embarque.nome, l.nome,
                Destino.kmEntre(embarque.lat, embarque.lon, l.lat, l.lon))
        else "Chegada: ${l.nome}."
        // Popup pequeno sobre a tela da corrida, desenhado pelo serviço de acessibilidade (o mesmo do semáforo).
        // Não toma toques e some sozinho: não atrapalha aceitar a corrida.
        val acessibilidade = CopilotService.instance
        val motivo = if (acessibilidade != null) MapaPopup.mostrar(acessibilidade, Prefs(this), l, embarque)
            else "serviço de acessibilidade desligado"
        // se o popup não abrir, a notificação diz o motivo
        atualizar("Mapa: ${l.nome}", if (motivo == null) frase else "$frase · popup não abriu: $motivo", null)
    }

    /** Última posição conhecida pela rede. Sem permissão ou sem fix, devolve null. */
    private fun minhaPosicao(): Destino.Local? {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) { null } ?: return null
        return Destino.Local("Você", loc.latitude, loc.longitude, 0.0, "")
    }

    private fun criarCanais() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CANAL, "Escuta por voz", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CANAL_ALERTA, "Mapa do bairro", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun notificacao(titulo: String, texto: String, intent: Intent?): Notification {
        val alvo = intent ?: Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, ID, alvo, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun atualizar(titulo: String, texto: String, intent: Intent?) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notificacao(titulo, texto, intent))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ouvindo = false
        reconhecedor?.destroy()
        reconhecedor = null
        super.onDestroy()
    }

    companion object {
        private const val ID = 4701
        private const val CANAL = "escuta_voz"
        private const val CANAL_ALERTA = "mapa_bairro"
        const val ACAO_PARAR = "br.rota.copiloto.PARAR_VOZ"
        private val BR = Locale("pt", "BR")

        fun iniciar(ctx: Context) { ctx.startForegroundService(Intent(ctx, VozService::class.java)) }
        fun parar(ctx: Context) { ctx.stopService(Intent(ctx, VozService::class.java)) }
    }
}
