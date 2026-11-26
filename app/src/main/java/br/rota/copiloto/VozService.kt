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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Escuta em segundo plano a palavra cadastrada (padrão "mila"). Depois dela, o nome do bairro:
 * o app fala a distância e deixa o mapa numa notificação, que você abre quando estiver parado.
 * Fica ligado só quando você liga em Configurar > Voz, com aviso fixo na barra de notificação.
 */
class VozService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var reconhecedor: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var ouvindo = false
    private var falando = false
    private var ultimaBusca = ""
    private var ultimaHora = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        val notif = notificacao("Escuta por voz ligada", "Diga a palavra e depois o bairro", null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(ID, notif)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(BR)
                ttsOk = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { falando = false; ouvir() } }
            override fun onError(id: String?) { handler.post { falando = false; ouvir() } }
        })
        ouvir()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    /** Ouve de novo assim que o reconhecedor para (sem falar por cima da resposta). */
    private fun ouvir() {
        if (!Prefs(this).vozOn || falando || ouvindo) return
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

    /** Procura a palavra nas frases ouvidas e, depois dela, o bairro. No parcial só responde com o nome completo. */
    private fun tratar(b: Bundle?, parcial: Boolean) {
        val frases = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val palavra = Destino.norm(Prefs(this).vozPalavra)
        if (palavra.isEmpty()) return
        val chave = " $palavra "
        for (f in frases) {
            val t = " ${Destino.norm(f)} "
            val pos = t.indexOf(chave)
            if (pos < 0) continue
            val busca = t.substring(pos + chave.length).trim()
            if (busca.length < 3) continue
            val achados = Destino.localizar(busca, 3)
            if (achados.isEmpty()) continue
            val top = achados[0]
            if (parcial && Destino.norm(top.nome) != busca) continue
            val agora = System.currentTimeMillis()
            if (busca == ultimaBusca && agora - ultimaHora < 8000L) return
            ultimaBusca = busca
            ultimaHora = agora
            responder(top)
            return
        }
    }

    private fun responder(l: Destino.Local) {
        val origem = minhaPosicao()
        val frase = when {
            origem != null -> String.format(BR, "%s fica a %.1f quilômetros de você, em linha reta.", l.nome,
                Destino.kmEntre(origem.lat, origem.lon, l.lat, l.lon))
            l.km < 0.3 -> "${l.nome} fica no centro da cidade."
            else -> String.format(BR, "%s fica a %.1f quilômetros do centro, para o %s.", l.nome, l.km, l.rumo)
        }
        val mapa = Intent(this, MapaActivity::class.java)
            .putExtra("nome", l.nome)
            .putExtra("lat", l.lat)
            .putExtra("lon", l.lon)
            .putExtra("frase", frase)
        if (origem != null) mapa.putExtra("ox", origem.lat).putExtra("oy", origem.lon)
        atualizar("Mapa: ${l.nome}", frase, mapa)
        if (Prefs(this).vozFalar && ttsOk) {
            falando = true
            ouvindo = false
            reconhecedor?.cancel()
            tts?.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "resposta")
            // se a voz não avisar o fim, volta a ouvir sozinho
            handler.postDelayed({ if (falando) { falando = false; ouvir() } }, 15000L)
        }
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

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CANAL, "Escuta por voz", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun notificacao(titulo: String, texto: String, intent: Intent?): Notification {
        val alvo = intent ?: Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, ID, alvo, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
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
        falando = false
        reconhecedor?.destroy()
        reconhecedor = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val ID = 4701
        private const val CANAL = "escuta_voz"
        const val ACAO_PARAR = "br.rota.copiloto.PARAR_VOZ"
        private val BR = Locale("pt", "BR")

        fun iniciar(ctx: Context) { ctx.startForegroundService(Intent(ctx, VozService::class.java)) }
        fun parar(ctx: Context) { ctx.stopService(Intent(ctx, VozService::class.java)) }
    }
}
