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
 * Popup pequeno no topo, na faixa livre acima do semáforo, com um desenho simples do trajeto.
 * Não recebe toques e não pega o foco: o botão Aceitar e o semáforo continuam clicáveis.
 * Se não couber acima do semáforo, não aparece (a notificação de voz continua mostrando a distância).
 */
object MapaPopup {
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val sumir = Runnable { fechar() }

    fun mostrar(acess: Context, p: Prefs, l: Destino.Local, origemPessoa: Destino.Local?, duracaoMs: Long = 6000L) {
        fechar()
        val res = acess.resources
        val dp = res.displayMetrics.density
        val manager = acess.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // faixa livre: do fim da barra de status até o topo do semáforo
        val statusId = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusPx = if (statusId > 0) res.getDimensionPixelSize(statusId) else (24 * dp).toInt()
        val inicio = statusPx + (4 * dp).toInt()
        val cardEmBaixo = p.compacto && p.cpI("ancora", 0) == 1
        val alturaPx: Int = if (cardEmBaixo) {
            (96 * dp).toInt()
        } else {
            val topoCardDp = if (p.compacto) p.cpI("distTopo", p.overlayTopDp.toInt()) else p.overlayTopDp.toInt()
            (topoCardDp * dp).toInt() - (6 * dp).toInt() - inicio
        }
        if (alturaPx < (48 * dp).toInt()) return
        val altura = minOf(alturaPx, (96 * dp).toInt())

        val lp = WindowManager.LayoutParams(
            (184 * dp).toInt(), altura,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (10 * dp).toInt()
            y = if (cardEmBaixo) (36 * dp).toInt() else inicio
        }
        val v = Desenho(acess, l, origemPessoa)
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

    /** Texto à esquerda (bairro e distância) e desenho à direita (dois pontos e o traço entre eles). */
    private class Desenho(ctx: Context, private val destino: Destino.Local, private val pessoa: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(236, 17, 24, 39) }
        private val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14 * dp; isFakeBoldText = true }
        private val legenda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textSize = 12 * dp }
        private val trajeto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 2 * dp
            pathEffect = DashPathEffect(floatArrayOf(5 * dp, 4 * dp), 0f)
        }
        private val pontoOrigem = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (pessoa != null) Color.parseColor("#A78BFA") else Color.parseColor("#94A3B8") }
        private val pontoDestino = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F97316") }
        private val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.5f * dp }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 14 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)

            // esquerda: bairro e distância
            c.drawText(destino.nome.take(13), 12 * dp, 26 * dp, titulo)
            val origem = pessoa ?: Destino.localDoBairro("Centro")
            val km = if (origem != null) Destino.kmEntre(origem.lat, origem.lon, destino.lat, destino.lon) else destino.km
            val textoKm = if (pessoa != null) String.format(java.util.Locale("pt", "BR"), "%.1f km de você", km)
                else String.format(java.util.Locale("pt", "BR"), "%.1f km do centro", km)
            c.drawText(textoKm, 12 * dp, h - 16 * dp, legenda)

            // direita: traço entre a origem (você ou centro) e o destino
            val area = RectF(96 * dp, 12 * dp, w - 12 * dp, h - 12 * dp)
            if (origem == null) {
                c.drawCircle(area.centerX(), area.centerY(), 6 * dp, pontoDestino)
                return
            }
            val lat0 = Math.toRadians((origem.lat + destino.lat) / 2)
            val minLon = minOf(origem.lon, destino.lon); val maxLon = maxOf(origem.lon, destino.lon)
            val minLat = minOf(origem.lat, destino.lat); val maxLat = maxOf(origem.lat, destino.lat)
            val rangeX = maxOf((maxLon - minLon) * Math.cos(lat0), 1e-5)
            val rangeY = maxOf(maxLat - minLat, 1e-5)
            val s = minOf(area.width() / rangeX, area.height() / rangeY).toFloat()
            val offX = area.centerX() - (rangeX * s).toFloat() / 2
            val offY = area.centerY() - (rangeY * s).toFloat() / 2
            fun xDe(lon: Double) = offX + ((lon - minLon) * Math.cos(lat0) * s).toFloat()
            fun yDe(lat: Double) = offY + ((maxLat - lat) * s).toFloat()
            val ox = xDe(origem.lon); val oy = yDe(origem.lat)
            val dx = xDe(destino.lon); val dy = yDe(destino.lat)
            c.drawLine(ox, oy, dx, dy, trajeto)
            c.drawCircle(ox, oy, 5 * dp, pontoOrigem)
            c.drawCircle(dx, dy, 6 * dp, pontoDestino)
            c.drawCircle(dx, dy, 6 * dp, contorno)
        }
    }
}
