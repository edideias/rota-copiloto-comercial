package br.rota.copiloto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Popup pequeno à direita, logo abaixo do semáforo, na área livre ao lado do bloco de valores.
 * Mostra só a rota da corrida: partida (seta verde para cima) e chegada (seta laranja para baixo),
 * os mesmos ícones do 99, ligadas por uma linha curva. Sem textos.
 * Não recebe toques e não pega o foco. Some sozinho depois de alguns segundos.
 */
object MapaPopup {
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val sumir = Runnable { fechar() }

    /** Devolve null se o popup abriu; senão, o motivo (aparece na notificação para diagnóstico). */
    fun mostrar(servico: CopilotService, p: Prefs, destino: Destino.Local, embarque: Destino.Local?, duracaoMs: Long = 6000L): String? {
        fechar()
        val res = servico.resources
        val dp = res.displayMetrics.density
        val alturaTela = res.displayMetrics.heightPixels
        val manager = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val fimSemaforo = servico.fimDoSemaforoPx()
        val cardEmCima = fimSemaforo != null && fimSemaforo < alturaTela * 0.6
        val topo: Int
        val alturaMax: Int
        if (cardEmCima) {
            topo = fimSemaforo!! + (12 * dp).toInt()
            alturaMax = (200 * dp).toInt()
        } else {
            // semáforo embaixo ou ainda não desenhado: usa a faixa de cima, logo abaixo da barra de status
            val statusId = res.getIdentifier("status_bar_height", "dimen", "android")
            val statusPx = if (statusId > 0) res.getDimensionPixelSize(statusId) else (24 * dp).toInt()
            topo = statusPx + (36 * dp).toInt()
            alturaMax = (120 * dp).toInt()
        }
        val alturaDisponivel = minOf(alturaMax, alturaTela - topo - (24 * dp).toInt())
        if (alturaDisponivel < (100 * dp).toInt()) {
            val motivo = "sem espaço ao lado do semáforo (${(alturaDisponivel / dp).toInt()} dp)"
            DebugLog.add("Popup do mapa não abriu: $motivo")
            return motivo
        }

        // estreito, para não cobrir o preço, o multiplicador e o texto da oferta
        val lp = WindowManager.LayoutParams(
            (110 * dp).toInt(), alturaDisponivel,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * dp).toInt()
            y = topo
        }
        val v = Rota(servico, destino, embarque)
        return try {
            manager.addView(v, lp)
            atual = v
            handler.postDelayed(sumir, duracaoMs)
            DebugLog.add("Popup do mapa aberto: ${destino.nome}")
            null
        } catch (e: Exception) {
            atual = null
            val motivo = "erro ao mostrar (${e.javaClass.simpleName}: ${e.message ?: ""})"
            DebugLog.add("Popup do mapa não abriu: $motivo")
            motivo
        }
    }

    fun fechar() {
        handler.removeCallbacks(sumir)
        val v = atual ?: return
        atual = null
        try {
            (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
        } catch (_: Exception) {}
    }

    /** Rota da corrida enquadrada no card: partida, chegada e uma linha curva entre elas. */
    private class Rota(ctx: Context, private val destino: Destino.Local, private val embarque: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 15, 23, 42) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 226, 232, 240); style = Paint.Style.STROKE
            strokeWidth = 3.5f * dp; strokeCap = Paint.Cap.ROUND
        }
        private val anelBranco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val partidaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A86B") }
        private val chegadaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8590C") }
        private val seta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

        private var pChegada = PointF()
        private var pPartida: PointF? = null

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            val margem = 26 * dp
            val lats = listOfNotNull(destino.lat, embarque?.lat)
            val lons = listOfNotNull(destino.lon, embarque?.lon)
            val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!
            val minLon = lons.minOrNull()!!; val maxLon = lons.maxOrNull()!!
            val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
            // zoom mínimo: corridas curtas não viram um borrão de tão ampliadas
            val rangeX = max((maxLon - minLon) * cosLat, 0.012)
            val rangeY = max(maxLat - minLat, 0.012)
            val aw = w - 2 * margem
            val ah = h - 2 * margem
            val escala = min(aw / rangeX, ah / rangeY)
            val offX = margem + (aw - rangeX * escala).toFloat() / 2
            val offY = margem + (ah - rangeY * escala).toFloat() / 2
            fun px(lon: Double, lat: Double) = PointF(
                offX + ((lon - minLon) * cosLat * escala).toFloat(),
                offY + ((maxLat - lat) * escala).toFloat()
            )
            pChegada = px(destino.lon, destino.lat)
            pPartida = embarque?.let { px(it.lon, it.lat) }
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 16 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)
            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)

            val partida = pPartida
            if (partida != null) {
                // linha curva: o ponto de controle sai para o lado, para o trajeto não parecer uma reta seca
                val dx = pChegada.x - partida.x
                val dy = pChegada.y - partida.y
                val comp = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val desvio = comp * 0.18f
                val cx = (partida.x + pChegada.x) / 2 + (-dy / max(comp, 1f)) * desvio
                val cy = (partida.y + pChegada.y) / 2 + (dx / max(comp, 1f)) * desvio
                val caminho = Path().apply {
                    moveTo(partida.x, partida.y)
                    quadTo(cx, cy, pChegada.x, pChegada.y)
                }
                c.drawPath(caminho, linha)
                marcador(c, partida, partidaCor, cima = true)
            }
            marcador(c, pChegada, chegadaCor, cima = false)
        }

        /** Círculo com borda branca e seta (para cima na partida, para baixo na chegada), como no app do 99. */
        private fun marcador(c: Canvas, p: PointF, cor: Paint, cima: Boolean) {
            val raio = 10 * dp
            c.drawCircle(p.x, p.y, raio + 2 * dp, anelBranco)
            c.drawCircle(p.x, p.y, raio, cor)
            val s = 5.5f * dp
            val caminho = Path().apply {
                if (cima) {
                    moveTo(p.x, p.y - s); lineTo(p.x + s, p.y + s * 0.4f); lineTo(p.x - s, p.y + s * 0.4f)
                } else {
                    moveTo(p.x, p.y + s); lineTo(p.x + s, p.y - s * 0.4f); lineTo(p.x - s, p.y - s * 0.4f)
                }
                close()
            }
            c.drawPath(caminho, seta)
        }
    }
}
