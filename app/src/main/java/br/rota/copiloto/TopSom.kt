package br.rota.copiloto

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/** Toca a música que o próprio usuário escolheu (arquivo copiado para dentro do app) quando aparece corrida TOP. */
object TopSom {
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    fun arquivo(ctx: Context) = File(ctx.filesDir, "som_top")
    fun existe(ctx: Context) = arquivo(ctx).let { it.exists() && it.length() > 0 }

    fun tocar(ctx: Context, p: Prefs) {
        val f = arquivo(ctx)
        if (!f.exists() || f.length() == 0L) return
        parar()
        try {
            val m = MediaPlayer()
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            m.setDataSource(f.absolutePath)
            m.setOnPreparedListener { x ->
                try { x.seekTo(p.topSomIni * 1000) } catch (_: Exception) {}
                x.start()
                h.postDelayed({ parar() }, p.topSomDur * 1000L)
            }
            m.setOnCompletionListener { parar() }
            m.setOnErrorListener { _, _, _ -> parar(); true }
            m.prepareAsync()
            mp = m
        } catch (_: Exception) { parar() }
    }

    fun parar() {
        h.removeCallbacksAndMessages(null)
        try { mp?.stop() } catch (_: Exception) {}
        try { mp?.release() } catch (_: Exception) {}
        mp = null
    }
}
