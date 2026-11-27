package br.rota.copiloto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Popup pequeno à direita, logo abaixo do semáforo, na área livre ao lado do bloco de valores.
 * Mostra o mapa de regiões de Campo Grande em cores, sem nenhum texto, com dois pontos:
 * o embarque (ida, branco) e o destino (chegada, preto). Não recebe toques e não pega o foco.
 * Some sozinho depois de alguns segundos.
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
        val v = Mapa(servico, destino, embarque)
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

    /** Mapa de regiões em cores (sem texto) e os dois pontos da corrida. */
    private class Mapa(ctx: Context, private val destino: Destino.Local, private val embarque: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val pontos = Destino.todosOsPontos()
        private val regioes = Regioes.regiaoDos(pontos)
        private val lats = DoubleArray(pontos.size) { pontos[it].lat }
        private val lons = DoubleArray(pontos.size) { pontos[it].lon }
        private val latMin = lats.minOrNull() ?: 0.0
        private val latMax = lats.maxOrNull() ?: 0.0
        private val lonMin = lons.minOrNull() ?: 0.0
        private val lonMax = lons.maxOrNull() ?: 0.0
        private val cosLat = Math.cos(Math.toRadians((latMin + latMax) / 2))

        private var escala = 1f
        private var offX = 0f
        private var offY = 0f
        private var grade: Bitmap? = null
        private val margem = 8 * dp

        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 15, 23, 42) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val trajeto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * dp
            pathEffect = DashPathEffect(floatArrayOf(6 * dp, 5 * dp), 0f)
        }
        private val pontoIda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val contornoIda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A"); style = Paint.Style.STROKE; strokeWidth = 2 * dp }
        private val pontoChegada = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0F172A") }
        private val contornoChegada = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3 * dp }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            val aw = w - 2 * margem
            val ah = h - 2 * margem
            val rangeX = maxOf((lonMax - lonMin) * cosLat, 1e-5)
            val rangeY = maxOf(latMax - latMin, 1e-5)
            escala = minOf(aw / rangeX, ah / rangeY).toFloat()
            offX = margem + (aw - (rangeX * escala).toFloat()) / 2
            offY = margem + (ah - (rangeY * escala).toFloat()) / 2
            grade = construirGrade(w, h)
        }

        /** Converte longitude/latitude em pixels da view. */
        private fun px(lon: Double, lat: Double) = PointF(
            offX + ((lon - lonMin) * cosLat * escala).toFloat(),
            offY + ((latMax - lat) * escala).toFloat()
        )

        /** Pinta cada célula com a cor da região do bairro mais próximo; fora da cidade fica transparente. */
        private fun construirGrade(w: Int, h: Int): Bitmap? {
            if (w <= 0 || h <= 0 || pontos.isEmpty()) return null
            val celula = 3 * dp
            val gw = (w / celula).toInt() + 1
            val gh = (h / celula).toInt() + 1
            val bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
            val limiar = 0.02 * 0.02 // graus ao quadrado (~2 km): mais longe disso é fora da cidade
            for (gy in 0 until gh) {
                val y = gy * celula + celula / 2
                val lat = latMax - (y - offY) / escala
                for (gx in 0 until gw) {
                    val x = gx * celula + celula / 2
                    val lon = lonMin + (x - offX) / escala / cosLat
                    var melhor = -1
                    var melhorD = Double.MAX_VALUE
                    for (i in lats.indices) {
                        val dla = lats[i] - lat
                        val dlo = (lons[i] - lon) * cosLat
                        val d = dla * dla + dlo * dlo
                        if (d < melhorD) { melhorD = d; melhor = i }
                    }
                    val cor = if (melhor >= 0 && melhorD <= limiar) Regioes.CORES[regioes[melhor]] else Color.TRANSPARENT
                    bmp.setPixel(gx, gy, cor)
                }
            }
            return bmp
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 16 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)
            grade?.let { c.drawBitmap(it, null, RectF(0f, 0f, w, h), null) }
            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)

            val chegada = px(destino.lon, destino.lat)
            val ida = embarque?.let { px(it.lon, it.lat) }
            if (ida != null) {
                c.drawLine(ida.x, ida.y, chegada.x, chegada.y, trajeto)
                c.drawCircle(ida.x, ida.y, 5 * dp, pontoIda)
                c.drawCircle(ida.x, ida.y, 5 * dp, contornoIda)
            }
            c.drawCircle(chegada.x, chegada.y, 7 * dp, pontoChegada)
            c.drawCircle(chegada.x, chegada.y, 7 * dp, contornoChegada)
        }
    }
}
