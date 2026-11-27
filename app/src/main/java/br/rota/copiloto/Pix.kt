package br.rota.copiloto

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Aviso grande na tela quando o Sicredi notifica um Pix recebido: valor e nome de quem pagou (se vier).
 * Usa o mesmo caminho do semáforo (serviço de acessibilidade), então aparece por cima de qualquer app.
 * Ocupa a largura toda da tela e fica até você tocar nele (ou até o tempo configurado, se houver).
 */
object Pix {
    data class Recebido(val valor: String, val nome: String?, val bruto: String)

    private val VALOR = Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})*,[0-9]{2}|[0-9]+,[0-9]{2})""")
    // o nome vem depois do valor, e nunca começa com "R$" ou número
    private val NOME = Regex("""(?i)\bde\s+(?!R\$|\d)([^\n\r.,;!]{3,60})""")
    private val PALAVRAS_QUE_NAO_SAO_NOME = listOf("pagamento", "pix", "sicredi", "comprovante", "valor", "conta", "cooperativa", "cpf", "transfer")
    // frases de pagamento que eu envio; palavras soltas como "pagamento" ficam de fora porque podem aparecer num recebimento
    private val SAIDAS = listOf("pagamento enviado", "voce pagou", "voce enviou", "transferencia enviada")
    private val handler = Handler(Looper.getMainLooper())
    private var fundo: View? = null
    private var atual: View? = null
    private val sumir = Runnable { fechar() }
    private var ultimaChave = ""
    private var ultimaHora = 0L

    /** Lê o texto da notificação. Devolve null se não for um Pix recebido com valor. */
    fun ler(titulo: String, texto: String): Recebido? {
        val t = "$titulo $texto".replace(Regex("\\s+"), " ").trim()
        val n = Destino.norm(t)
        if (!n.contains("pix")) return null
        // só recebimento: pagamento enviado ou Pix que eu paguei não conta
        if (SAIDAS.any { n.contains(it) }) return null
        if (!(n.contains("recebeu") || n.contains("recebido"))) return null
        val achado = VALOR.find(t) ?: return null
        val depois = t.substring(achado.range.last + 1)
        val nome = NOME.find(depois)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { n -> PALAVRAS_QUE_NAO_SAO_NOME.none { Destino.norm(n).contains(it) } }
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
        val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val poppinsSemi = servico.resources.getFont(R.font.poppins_semibold)
        val poppinsBold = servico.resources.getFont(R.font.poppins_bold)
        val poppinsMedio = servico.resources.getFont(R.font.poppins_medium)
        val teal = "#0E7C6B"

        val card = LinearLayout(servico).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24 * dp
                setStroke((3 * dp).toInt(), Color.parseColor("#32BCAD"))
            }
            setPadding((20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt(), (26 * dp).toInt())
            addView(
                ImageView(servico).apply {
                    setImageResource(R.drawable.pix_logo)
                    contentDescription = "Pix"
                },
                LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply { bottomMargin = (6 * dp).toInt() }
            )
            addView(texto(servico, "PIX RECEBIDO", 14f, poppinsSemi, teal, 0.12f))
            // cifrão menor, valor grande em negrito
            addView(LinearLayout(servico).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                addView(texto(servico, "R$", 30f, poppinsSemi, teal, 0f).apply { setPadding(0, (8 * dp).toInt(), (4 * dp).toInt(), 0) })
                addView(texto(servico, r.valor, 84f, poppinsBold, teal, 0f))
            })
            if (r.nome != null) {
                addView(texto(servico, "de ${r.nome}", 20f, poppinsMedio, "#111827", 0f))
            }
        }

        // fundo escurecido atrás do cartão: o cartão branco não se confunde com a tela do app
        val escurecer = View(servico).apply { setBackgroundColor(Color.parseColor("#8C000000")) }
        val lpFundo = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // o aviso fica na tela até o toque; só some sozinho se o tempo configurado for maior que zero
        card.isClickable = true
        card.setOnClickListener { fechar() }

        val lp = WindowManager.LayoutParams(
            (servico.resources.displayMetrics.widthPixels - (24 * dp).toInt()), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            wm.addView(escurecer, lpFundo)
            fundo = escurecer
            wm.addView(card, lp)
            atual = card
            handler.removeCallbacks(sumir)
            val sozinho = Prefs(servico).pixSegundos
            if (sozinho > 0) handler.postDelayed(sumir, sozinho * 1000L)
        } catch (e: Exception) {
            atual = null
            Notificador.avisarPix(ctx, r)
        }
    }

    private fun texto(ctx: Context, t: String, sp: Float, fonte: Typeface, cor: String, espaco: Float): TextView =
        TextView(ctx).apply {
            text = t
            setTextColor(Color.parseColor(cor))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp)
            typeface = fonte
            gravity = Gravity.CENTER
            letterSpacing = espaco
            includeFontPadding = false
        }

    private fun vibrar(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 800), -1))
    }

    fun fechar() {
        handler.removeCallbacks(sumir)
        val v = atual
        val f = fundo
        atual = null
        fundo = null
        val wm = (v ?: f)?.context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try { if (v != null) wm.removeViewImmediate(v) } catch (_: Exception) {}
        try { if (f != null) wm.removeViewImmediate(f) } catch (_: Exception) {}
    }
}
