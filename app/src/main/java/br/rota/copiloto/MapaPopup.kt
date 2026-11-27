package br.rota.copiloto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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

/**
 * Popup pequeno à direita, logo abaixo do semáforo, na área livre ao lado do bloco de valores.
 * Mostra o mapa de regiões de Campo Grande em cores, sem texto, e dois marcadores iguais aos do 99:
 * seta verde para cima (partida, ida) e seta laranja para baixo (chegada, destino).
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

    /** Mapa vetorial: cada bairro é um ponto da cor da sua região; partida e chegada são marcadores do 99. */
    private class Mapa(ctx: Context, private val destino: Destino.Local, private val embarque: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val pontos = Destino.todosOsPontos()
        private val regioes = Regioes.regiaoDos(pontos)
        private val latMin = pontos.minOfOrNull { it.lat } ?: 0.0
        private val latMax = pontos.maxOfOrNull { it.lat } ?: 0.0
        private val lonMin = pontos.minOfOrNull { it.lon } ?: 0.0
        private val lonMax = pontos.maxOfOrNull { it.lon } ?: 0.0
        private val cosLat = Math.cos(Math.toRadians((latMin + latMax) / 2))
        private val margem = 8 * dp

        private var escala = 1f
        private var offX = 0f
        private var offY = 0f
        private var projetados: Array<PointF> = emptyArray()

        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 15, 23, 42) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val coresRegiao = Array(Regioes.CORES.size) { i ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Regioes.CORES[i] }
        }
        private val trajeto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * dp
            pathEffect = DashPathEffect(floatArrayOf(5 * dp, 4 * dp), 0f)
        }
        private val marcadorBorda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val partidaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A86B") }
        private val chegadaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8590C") }
        private val seta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            val aw = w - 2 * margem
            val ah = h - 2 * margem
            val rangeX = maxOf((lonMax - lonMin) * cosLat, 1e-5)
            val rangeY = maxOf(latMax - latMin, 1e-5)
            escala = minOf(aw / rangeX, ah / rangeY).toFloat()
            offX = margem + (aw - (rangeX * escala).toFloat()) / 2
            offY = margem + (ah - (rangeY * escala).toFloat()) / 2
            projetados = Array(pontos.size) { i -> px(pontos[i].lon, pontos[i].lat) }
        }

        /** Converte longitude/latitude em pixels da view. */
        private fun px(lon: Double, lat: Double) = PointF(
            offX + ((lon - lonMin) * cosLat * escala).toFloat(),
            offY + ((latMax - lat) * escala).toFloat()
        )

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 16 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)

            // bairros: pontos da cor da região, bem visíveis
            val r = 4.2f * dp
            for (i in projetados.indices) {
                val p = projetados[i]
                c.drawCircle(p.x, p.y, r, coresRegiao[regioes[i]])
            }

            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)

            val chegada = px(destino.lon, destino.lat)
            val partida = embarque?.let { px(it.lon, it.lat) }
            if (partida != null) {
                c.drawLine(partida.x, partida.y, chegada.x, chegada.y, trajeto)
                marcador(c, partida, partidaCor, cima = true)
            }
            marcador(c, chegada, chegadaCor, cima = false)
        }

        /** Círculo com borda branca e seta (para cima na partida, para baixo na chegada), como no app do 99. */
        private fun marcador(c: Canvas, p: PointF, cor: Paint, cima: Boolean) {
            val raio = 9 * dp
            c.drawCircle(p.x, p.y, raio + 2 * dp, marcadorBorda)
            c.drawCircle(p.x, p.y, raio, cor)
            val s = 5 * dp
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
