package br.rota.copiloto

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Aviso grande na tela quando o Sicredi notifica um Pix recebido: valor e nome de quem pagou.
 * Usa o mesmo caminho do semáforo (serviço de acessibilidade), então aparece por cima de qualquer app.
 * Não toma toques e some sozinho depois de alguns segundos.
 */
object Pix {
    data class Recebido(val valor: String, val nome: String?, val bruto: String)

    private val VALOR = Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})""")
    private val NOME = Regex("""(?i)\b(?:de|do|da)\s+([^\n\r.,;!]{3,60})""")
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val sumir = Runnable { fechar() }
    private var ultimaChave = ""
    private var ultimaHora = 0L

    /** Lê o texto da notificação. Devolve null se não for um Pix recebido com valor. */
    fun ler(titulo: String, texto: String): Recebido? {
        val t = "$titulo $texto".replace(Regex("\\s+"), " ").trim()
        val n = Destino.norm(t)
        if (!n.contains("pix")) return null
        if (!(n.contains("recebi") || n.contains("receb") || n.contains("pagou"))) return null
        val achado = VALOR.find(t) ?: return null
        val depois = t.substring(achado.range.last + 1)
        val nome = NOME.find(depois)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        return Recebido(achado.groupValues[1], nome, t)
    }

    /** Chamado pelo ouvinte de notificações. Evita repetir o mesmo aviso se o banco notificar duas vezes. */
    fun processar(ctx: Context, titulo: String, texto: String) {
        val r = ler(titulo, texto) ?: return
        val chave = r.valor + "|" + (r.nome ?: "")
        val agora = System.currentTimeMillis()
        if (chave == ultimaChave && agora - ultimaHora < 60_000L) return
        ultimaChave = chave
        ultimaHora = agora
        handler.post { mostrar(ctx.applicationContext, r) }
    }

    fun mostrar(ctx: Context, r: Recebido) {
        vibrar(ctx)
        val servico = CopilotService.instance
        if (servico == null) {
            // sem o serviço de acessibilidade ligado não dá para desenhar por cima: vai a notificação de alta prioridade
            Notificador.avisarPix(ctx, r)
            return
        }
        fechar()
        val dp = servico.resources.displayMetrics.density
        val largura = servico.resources.displayMetrics.widthPixels - (24 * dp).toInt()
        val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0E7C6B"))
                cornerRadius = 24 * dp
                setStroke((3 * dp).toInt(), Color.WHITE)
            }
        }
        card.addView(texto(servico, "PIX RECEBIDO", 26f, true, dp))
        card.addView(texto(servico, "R$ ${r.valor}", 84f, true, dp))
        if (r.nome != null) {
            card.addView(texto(servico, "de ${r.nome}", 34f, true, dp))
        } else {
            card.addView(texto(servico, "Nome não identificado", 22f, false, dp))
            card.addView(texto(servico, r.bruto, 14f, false, dp))
        }

        val lp = WindowManager.LayoutParams(
            largura, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(card, lp)
            atual = card
            handler.removeCallbacks(sumir)
            handler.postDelayed(sumir, 12_000L)
        } catch (e: Exception) {
            atual = null
            Notificador.avisarPix(ctx, r)
        }
    }

    private fun texto(ctx: Context, t: String, sp: Float, negrito: Boolean, dp: Float): TextView =
        TextView(ctx).apply {
            text = t
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            gravity = Gravity.CENTER
            if (negrito) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }

    private fun vibrar(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 800), -1))
    }

    fun fechar() {
        handler.removeCallbacks(sumir)
        val v = atual ?: return
        atual = null
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
        } catch (_: Exception) {}
    }
}
