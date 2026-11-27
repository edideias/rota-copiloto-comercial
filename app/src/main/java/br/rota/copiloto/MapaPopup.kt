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
import org.json.JSONArray
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

    /** Bairro real do IBGE: anéis externos em (lon, lat), no arquivo assets/campo_grande_bairros.json. */
    private class Bairro(val nome: String, val aneis: List<DoubleArray>)

    private object Bairros {
        @Volatile private var cache: List<Bairro>? = null

        fun carregar(ctx: Context): List<Bairro> {
            cache?.let { return it }
            val texto = ctx.assets.open("campo_grande_bairros.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(texto)
            val lista = ArrayList<Bairro>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rings = o.getJSONArray("p")
                val aneis = ArrayList<DoubleArray>()
                for (j in 0 until rings.length()) {
                    val ring = rings.getJSONArray(j)
                    val a = DoubleArray(ring.length() * 2)
                    for (k in 0 until ring.length()) {
                        val pt = ring.getJSONArray(k)
                        a[2 * k] = pt.getDouble(0)
                        a[2 * k + 1] = pt.getDouble(1)
                    }
                    aneis.add(a)
                }
                lista.add(Bairro(o.getString("n"), aneis))
            }
            cache = lista
            return lista
        }

        /** Ray casting: ponto (lon, lat) dentro do anel. */
        fun dentro(lon: Double, lat: Double, a: DoubleArray): Boolean {
            var c = false
            val n = a.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = a[2 * i]; val yi = a[2 * i + 1]
                val xj = a[2 * j]; val yj = a[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) c = !c
                j = i
            }
            return c
        }

        fun de(lista: List<Bairro>, lon: Double, lat: Double): Bairro? =
            lista.firstOrNull { b -> b.aneis.any { dentro(lon, lat, it) } }
    }

    /**
     * Mapa real: contornos dos bairros do IBGE, com o bairro de chegada em laranja e o de partida em verde.
     * Partida e chegada aparecem como os mesmos ícones do 99, ligadas por uma linha curva.
     * Se a partida não estiver na malha, só a chegada aparece.
     */
    private class Rota(ctx: Context, private val destino: Destino.Local, private val embarque: Destino.Local?) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val bairros: List<Bairro> = Bairros.carregar(ctx)
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(245, 15, 23, 42) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val preench = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 30, 41, 59); style = Paint.Style.FILL }
        private val preenchChegada = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 232, 89, 12); style = Paint.Style.FILL }
        private val preenchPartida = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 168, 107); style = Paint.Style.FILL }
        private val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(255, 100, 116, 139); style = Paint.Style.STROKE; strokeWidth = 0.6f * dp }
        private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 226, 232, 240); style = Paint.Style.STROKE
            strokeWidth = 3f * dp; strokeCap = Paint.Cap.ROUND
        }
        private val anelBranco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val partidaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A86B") }
        private val chegadaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8590C") }
        private val seta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

        private var pChegada = PointF()
        private var pPartida: PointF? = null
        private val caminhosNormais = ArrayList<Path>()
        private val caminhoChegada = ArrayList<Path>()
        private val caminhoPartida = ArrayList<Path>()

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            val margem = 10 * dp
            val lats = listOfNotNull(destino.lat, embarque?.lat)
            val lons = listOfNotNull(destino.lon, embarque?.lon)
            val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!
            val minLon = lons.minOrNull()!!; val maxLon = lons.maxOrNull()!!
            val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
            // zoom mínimo de ~2 km: corrida curta mostra o bairro e os vizinhos, não um borrão
            val rangeX = max((maxLon - minLon) * cosLat, 0.025)
            val rangeY = max(maxLat - minLat, 0.025)
            val cxLon = (minLon + maxLon) / 2
            val cyLat = (minLat + maxLat) / 2
            val aw = w - 2 * margem
            val ah = h - 2 * margem
            val escala = min(aw / rangeX, ah / rangeY)
            val offX = margem + (aw - rangeX * escala).toFloat() / 2
            val offY = margem + (ah - rangeY * escala).toFloat() / 2
            val xMin = cxLon - rangeX / (2 * cosLat)
            val yMax = cyLat + rangeY / 2
            fun px(lon: Double, lat: Double) = PointF(
                offX + ((lon - xMin) * cosLat * escala).toFloat(),
                offY + ((yMax - lat) * escala).toFloat()
            )
            pChegada = px(destino.lon, destino.lat)
            pPartida = embarque?.let { px(it.lon, it.lat) }

            val bairroChegada = Bairros.de(bairros, destino.lon, destino.lat)
            val bairroPartida = embarque?.let { Bairros.de(bairros, it.lon, it.lat) }
            caminhosNormais.clear(); caminhoChegada.clear(); caminhoPartida.clear()
            for (b in bairros) {
                val alvo = when (b) {
                    bairroChegada -> caminhoChegada
                    bairroPartida -> caminhoPartida
                    else -> caminhosNormais
                }
                for (anel in b.aneis) {
                    val p = Path()
                    for (k in 0 until anel.size / 2) {
                        val q = px(anel[2 * k], anel[2 * k + 1])
                        if (k == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                    }
                    p.close()
                    alvo.add(p)
                }
            }
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val raio = 16 * dp
            c.drawRoundRect(RectF(0f, 0f, w, h), raio, raio, fundo)
            val recorte = Path().apply { addRoundRect(RectF(0f, 0f, w, h), raio, raio, Path.Direction.CW) }
            c.save()
            c.clipPath(recorte)
            for (p in caminhosNormais) { c.drawPath(p, preench); c.drawPath(p, contorno) }
            for (p in caminhoChegada) { c.drawPath(p, preenchChegada); c.drawPath(p, contorno) }
            for (p in caminhoPartida) { c.drawPath(p, preenchPartida); c.drawPath(p, contorno) }
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
            }
            c.restore()
            c.drawRoundRect(RectF(0.5f * dp, 0.5f * dp, w - 0.5f * dp, h - 0.5f * dp), raio, raio, borda)
            if (partida != null) marcador(c, partida, partidaCor, cima = true)
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
