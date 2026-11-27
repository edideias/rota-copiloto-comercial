package br.rota.copiloto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

/**
 * Escuta em segundo plano a palavra cadastrada (padrão "mila"). Depois dela, o mapa do bairro de destino abre.
 * O reconhecimento é offline (Vosk) e lê o microfone direto: não pede foco de áudio, então não pausa o som do carro.
 * Não fala nada. Fica ligado só quando você liga em Configurar > Voz.
 */
class VozService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var modelo: Model? = null
    private var reconhecedor: Recognizer? = null
    private var gravador: AudioRecord? = null
    private var captura: Thread? = null
    @Volatile private var ouvindo = false
    private var ultimaBusca = ""
    private var ultimaHora = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanais()
        val notif = notificacao("Escuta por voz ligada", "Diga a palavra e o mapa abre", null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(ID, notif)
        carregarModelo()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    /** Copia o modelo do APK para o armazenamento do app na primeira vez e carrega. */
    private fun carregarModelo() {
        modelo?.let { handler.post { iniciarCaptura() }; return }
        atualizar("Escuta por voz ligada", "Carregando a voz offline...", null)
        StorageService.unpack(this, "model-pt", "model",
            { m: Model ->
                modelo = m
                handler.post { iniciarCaptura() }
            },
            { e: IOException ->
                atualizar("Erro ao carregar a voz", e.message ?: "modelo não encontrado", null)
            })
    }

    /** Lê o microfone em uma thread e alimenta o reconhecedor, que só conhece a palavra-chave. */
    private fun iniciarCaptura() {
        if (ouvindo) return
        val prefs = Prefs(this)
        if (!prefs.vozOn) return
        val m = modelo ?: return
        val palavra = Destino.norm(prefs.vozPalavra)
        if (palavra.isEmpty()) return
        // gramática fechada: a palavra e [unk] (tudo que não é a palavra). Mais rápido e mais certo.
        val gramatica = JSONArray().apply {
            palavra.split(" ").filter { it.isNotEmpty() }.forEach { put(it) }
            put("[unk]")
        }.toString()
        val rec = Recognizer(m, 16000f, gramatica)
        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .let { if (it <= 0) 8000 else it }
        val g = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        if (g.state != AudioRecord.STATE_INITIALIZED) {
            g.release(); rec.close()
            atualizar("Microfone indisponível", "Não consegui abrir o microfone para a voz", null)
            return
        }
        reconhecedor = rec
        gravador = g
        ouvindo = true
        g.startRecording()
        captura = Thread({
            val buf = ByteArray(minBuf)
            while (ouvindo) {
                val n = g.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (rec.acceptWaveForm(buf, n)) {
                    val texto = JSONObject(rec.result).optString("text")
                    handler.post { tratar(texto, parcial = false) }
                } else {
                    val txt = JSONObject(rec.partialResult).optString("partial")
                    if (txt.isNotEmpty()) handler.post { tratar(txt, parcial = true) }
                }
            }
        }, "voz-captura").also { it.start() }
    }

    private fun pararCaptura() {
        ouvindo = false
        try { gravador?.stop() } catch (_: Exception) {}
        try { captura?.join(1500) } catch (_: InterruptedException) {}
        captura = null
        try { gravador?.release() } catch (_: Exception) {}
        gravador = null
        reconhecedor?.close()
        reconhecedor = null
    }

    /** Se a palavra-chave foi ouvida, abre o mapa do bairro de destino que está no semáforo. Roda na thread principal. */
    private fun tratar(texto: String, parcial: Boolean) {
        val palavra = Destino.norm(Prefs(this).vozPalavra)
        if (palavra.isEmpty() || texto.isBlank()) return
        if (!parcial) atualizar("Escuta por voz ligada", "Ouvi: \"$texto\"", null)
        if ((" ${Destino.norm(texto)} ").contains(" $palavra ")) abrirDaTela()
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
        abrirMapa(l, embarque, a.offer.pickupKm + a.offer.tripKm)
    }

    private fun abrirMapa(l: Destino.Local, embarque: Destino.Local?, kmTotal: Double) {
        val frase = if (embarque != null)
            String.format(BR, "De %s até %s: %.1f km em linha reta.", embarque.nome, l.nome,
                Destino.kmEntre(embarque.lat, embarque.lon, l.lat, l.lon))
        else "Chegada: ${l.nome}."
        // Popup pequeno sobre a tela da corrida, desenhado pelo serviço de acessibilidade (o mesmo do semáforo).
        // Não toma toques e some sozinho: não atrapalha aceitar a corrida.
        val acessibilidade = CopilotService.instance
        val motivo = if (acessibilidade != null) MapaPopup.mostrar(acessibilidade, Prefs(this), l, embarque, kmTotal)
            else "serviço de acessibilidade desligado"
        // se o popup não abrir, a notificação diz o motivo
        atualizar("Mapa: ${l.nome}", if (motivo == null) frase else "$frase · popup não abriu: $motivo", null)
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
        pararCaptura()
        modelo?.close()
        modelo = null
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
