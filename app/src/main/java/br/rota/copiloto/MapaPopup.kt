package br.rota.copiloto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Popup pequeno com um desenho simples do trajeto (sem mapa de ruas, sem internet).
 * Não recebe toques e não pega o foco: a oferta e o semáforo continuam clicáveis por baixo.
 * Some sozinho depois de alguns segundos. Fica sempre do lado oposto ao semáforo.
 */
object MapaPopup {
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val sumir = Runnable { fechar() }

    fun mostrar(acess: Context, p: Prefs, l: Destino.Local, origem: Destino.Local?, duracaoMs: Long = 6000L) {
        fechar()
        val dp = acess.resources.displayMetrics.density
        val manager = acess.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // onde o semáforo está: em cima (distância do topo) ou embaixo (distância da base)
        val cardEmBaixo = p.compacto && p.cpI("ancora", 0) == 1
        val cardTopoDp: Int? = when {
            cardEmBaixo -> null
            p.compacto -> p.cpI("distTopo", p.overlayTopDp.toInt())
            else -> p.overlayTopDp.toInt()
        }
        // semáforo em cima -> popup embaixo; semáforo embaixo (ou no meio alto) -> popup em cima
        val popupEmbaixo = cardTopoDp != null && cardTopoDp < 170

        val lp = WindowManager.LayoutParams(
            (150 * dp).toInt(), (110 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (popupEmbaixo) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
            x = (10 * dp).toInt()
            y = (if (popupEmbaixo) 60 * dp else 36 * dp).toInt()
        }
        val v = Desenho(acess, l, origem)
        try {
            manager.addView(v, lp)
            atual = v
            handler.postDelayed(sumir, duracaoMs)
        } catch (_: Exception) {
            atual = null
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

    /** Desenho esquemático: dois pontos, um traço tracejado e os textos. */
    private class Desenho(ctx: Context, private val destino: Destino.Local, private val origem: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 255, 255, 255) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val trajeto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827"); style = Paint.Style.STROKE; strokeWidth = 2 * dp
            pathEffect = DashPathEffect(floatArrayOf(6 * dp, 5 * dp), 0f)
        }
        private val pontoOrigem = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7C3AED") }
        private val pontoDestino = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F97316") }
        private val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#111827"); textSize = 12 * dp; isFakeBoldText = true }
        private val legenda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#64748B"); textSize = 10 * dp }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 14 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)
            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)

            c.drawText(destino.nome.take(18), 12 * dp, 20 * dp, titulo)

            val area = RectF(16 * dp, 34 * dp, w - 16 * dp, h - 30 * dp)
            val o = origem
            if (o != null) {
                // escala que preserva a proporção: longitude corrigida pela latitude média
                val lat0 = Math.toRadians((o.lat + destino.lat) / 2)
                val minLon = minOf(o.lon, destino.lon); val maxLon = maxOf(o.lon, destino.lon)
                val minLat = minOf(o.lat, destino.lat); val maxLat = maxOf(o.lat, destino.lat)
                val rangeX = maxOf((maxLon - minLon) * Math.cos(lat0), 1e-5)
                val rangeY = maxOf(maxLat - minLat, 1e-5)
                val s = minOf(area.width() / rangeX, area.height() / rangeY).toFloat()
                val offX = area.centerX() - (rangeX * s).toFloat() / 2
                val offY = area.centerY() - (rangeY * s).toFloat() / 2
                fun xDe(lon: Double) = offX + ((lon - minLon) * Math.cos(lat0) * s).toFloat()
                fun yDe(lat: Double) = offY + ((maxLat - lat) * s).toFloat()
                val ox = xDe(o.lon); val oy = yDe(o.lat)
                val dx = xDe(destino.lon); val dy = yDe(destino.lat)
                c.drawLine(ox, oy, dx, dy, trajeto)
                c.drawCircle(ox, oy, 4 * dp, pontoOrigem)
                c.drawCircle(dx, dy, 5 * dp, pontoDestino)
                val km = Destino.kmEntre(o.lat, o.lon, destino.lat, destino.lon)
                c.drawText(String.format(java.util.Locale("pt", "BR"), "%.1f km em linha reta", km), 12 * dp, h - 12 * dp, legenda)
            } else {
                c.drawCircle(area.centerX(), area.centerY(), 5 * dp, pontoDestino)
                c.drawText(String.format(java.util.Locale("pt", "BR"), "%.1f km do centro", destino.km), 12 * dp, h - 12 * dp, legenda)
            }
        }
    }
}
