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
 * Popup pequeno à direita, logo abaixo do semáforo, na área livre ao lado do bloco de valores.
 * Não recebe toques e não pega o foco: Aceitar e o semáforo continuam clicáveis.
 * Some sozinho depois de alguns segundos.
 */
object MapaPopup {
    private val handler = Handler(Looper.getMainLooper())
    private var atual: View? = null
    private val sumir = Runnable { fechar() }

    /** Devolve null se o popup abriu; senão, o motivo (aparece na notificação para diagnóstico). */
    fun mostrar(servico: CopilotService, p: Prefs, l: Destino.Local, origemPessoa: Destino.Local?, duracaoMs: Long = 6000L): String? {
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

        val lp = WindowManager.LayoutParams(
            (132 * dp).toInt(), alturaDisponivel,
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
        val v = Desenho(servico, l, origemPessoa)
        return try {
            manager.addView(v, lp)
            atual = v
            handler.postDelayed(sumir, duracaoMs)
            DebugLog.add("Popup do mapa aberto: ${l.nome}")
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

    /** Layout vertical: nome do bairro no topo, desenho do trajeto no meio, distância embaixo. */
    private class Desenho(ctx: Context, private val destino: Destino.Local, private val pessoa: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(236, 17, 24, 39) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13 * dp; isFakeBoldText = true }
        private val km = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 16 * dp; isFakeBoldText = true }
        private val legenda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textSize = 11 * dp }
        private val rotulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textSize = 10 * dp; textAlign = Paint.Align.CENTER }
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
            val raio = 16 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)
            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)

            val pad = 12 * dp
            // nome do bairro, cortado se for longo demais
            var nome = destino.nome
            while (nome.length > 3 && titulo.measureText(nome) > w - 2 * pad) nome = nome.dropLast(1)
            if (nome != destino.nome) nome = nome.trimEnd() + "…"
            c.drawText(nome, pad, 24 * dp, titulo)

            val origem = pessoa ?: Destino.localDoBairro("Centro")
            val distancia = if (origem != null) Destino.kmEntre(origem.lat, origem.lon, destino.lat, destino.lon) else destino.km

            // desenho do trajeto na área do meio
            val area = RectF(pad + 4 * dp, 40 * dp, w - pad - 4 * dp, h - 64 * dp)
            if (origem == null) {
                c.drawCircle(area.centerX(), area.centerY(), 6 * dp, pontoDestino)
            } else {
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
                c.drawText(if (pessoa != null) "você" else "centro", ox, oy + 16 * dp, rotulo)
                c.drawText("destino", dx, dy - 10 * dp, rotulo)
            }

            // distância embaixo
            c.drawText(String.format(java.util.Locale("pt", "BR"), "%.1f km", distancia), pad, h - 30 * dp, km)
            c.drawText(if (pessoa != null) "em linha reta" else "do centro", pad, h - 13 * dp, legenda)
        }
    }
}
