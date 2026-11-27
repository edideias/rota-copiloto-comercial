package br.rota.copiloto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
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
    fun mostrar(servico: CopilotService, p: Prefs, destino: Destino.Local, embarque: Destino.Local?, kmTotal: Double, duracaoMs: Long = 6000L): String? {
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
        val v = Rota(servico, destino, embarque, kmTotal)
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
    private class Rota(ctx: Context, private val destino: Destino.Local, private val embarque: Destino.Local?, private val kmTotal: Double) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val bairros: List<Bairro> = Bairros.carregar(ctx)
        private val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(250, 250, 247, 240) }
        private val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 184, 176, 160); style = Paint.Style.STROKE; strokeWidth = 1 * dp }
        private val preench = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(233, 228, 216); style = Paint.Style.FILL }
        private val preenchChegada = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 232, 89, 12); style = Paint.Style.FILL }
        private val preenchPartida = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 0, 168, 107); style = Paint.Style.FILL }
        private val contorno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(184, 176, 160); style = Paint.Style.STROKE; strokeWidth = 0.6f * dp }
        private val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 25, 23); style = Paint.Style.STROKE
            strokeWidth = 3f * dp; strokeCap = Paint.Cap.ROUND
        }
        private val anelBranco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val partidaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00A86B") }
        private val chegadaCor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8590C") }
        private val seta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val rotuloFundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(240, 250, 247, 240); style = Paint.Style.FILL }

        private var pChegada = PointF()
        private var pPartida: PointF? = null
        private val caminhosNormais = ArrayList<Path>()
        private val caminhoChegada = ArrayList<Path>()
        private val caminhoPartida = ArrayList<Path>()
        private val nomesBairros = ArrayList<Pair<String, PointF>>()
        private var kmMeio: PointF? = null

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            val margem = 10 * dp
            val lats = listOfNotNull(destino.lat, embarque?.lat)
            val lons = listOfNotNull(destino.lon, embarque?.lon)
            val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!
            val minLon = lons.minOrNull()!!; val maxLon = lons.maxOrNull()!!
            val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
            // zoom mínimo de ~4 km: a corrida aparece com os bairros vizinhos, sem ficar longe demais
            val rangeX = max((maxLon - minLon) * cosLat, 0.035)
            val rangeY = max(maxLat - minLat, 0.035)
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

            // bairros que o trajeto atravessa: amostra pontos ao longo da linha entre partida e chegada
            val origem = embarque ?: destino
            val passam = LinkedHashSet<Bairro>()
            val amostras = 120
            for (k in 0..amostras) {
                val t = k / amostras.toDouble()
                val lon = origem.lon + (destino.lon - origem.lon) * t
                val lat = origem.lat + (destino.lat - origem.lat) * t
                Bairros.de(bairros, lon, lat)?.let { passam.add(it) }
            }
            nomesBairros.clear()
            for (b in passam) {
                val anel = b.aneis.maxByOrNull { it.size } ?: continue
                var sx = 0.0; var sy = 0.0; val n = anel.size / 2
                for (k in 0 until n) { sx += anel[2 * k]; sy += anel[2 * k + 1] }
                nomesBairros.add(b.nome to px(sx / n, sy / n))
            }

            // km total no meio da linha curva (ponto da curva em t = 0,5)
            if (embarque != null) {
                val dx = pChegada.x - pPartida!!.x
                val dy = pChegada.y - pPartida!!.y
                val comp = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val desvio = comp * 0.18f
                val cx = (pPartida!!.x + pChegada.x) / 2 + (-dy / max(comp, 1f)) * desvio
                val cy = (pPartida!!.y + pChegada.y) / 2 + (dx / max(comp, 1f)) * desvio
                kmMeio = PointF(0.25f * pPartida!!.x + 0.5f * cx + 0.25f * pChegada.x, 0.25f * pPartida!!.y + 0.5f * cy + 0.25f * pChegada.y)
            } else {
                kmMeio = null
            }

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

            // textos: cada um só entra se não encostar em outro texto ou em um marcador
            val ocupados = ArrayList<RectF>()
            fun obstaculo(p: PointF) { ocupados.add(RectF(p.x - 13 * dp, p.y - 13 * dp, p.x + 13 * dp, p.y + 13 * dp)) }
            obstaculo(pChegada); partida?.let { obstaculo(it) }
            fun cabe(r: RectF) = ocupados.none { RectF.intersects(it, r) }

            val rChegada = caixa("CHEGADA", pChegada.x, yRotulo(pChegada), 8f)
            ocupados.add(rChegada)
            desenhar(c, "CHEGADA", rChegada, Color.parseColor("#C2410C"), 8f)
            if (partida != null) {
                val rPartida = caixa("PARTIDA", partida.x, yRotulo(partida), 8f)
                ocupados.add(rPartida)
                desenhar(c, "PARTIDA", rPartida, Color.parseColor("#047857"), 8f)
            } else {
                val rSem = caixa("SEM PARTIDA", w / 2f, h - 14 * dp, 8f)
                ocupados.add(rSem)
                desenhar(c, "SEM PARTIDA", rSem, Color.parseColor("#1C1917"), 8f)
            }
            kmMeio?.let { m ->
                val rKm = caixa(kmTxt, m.x, m.y, 9f)
                if (cabe(rKm)) { ocupados.add(rKm); desenhar(c, kmTxt, rKm, Color.parseColor("#1C1917"), 9f) }
            }
            for ((nome, p) in nomesBairros) {
                val r = caixa(nome, p.x, p.y, 6.5f)
                if (cabe(r)) { ocupados.add(r); desenhar(c, nome, r, Color.parseColor("#1C1917"), 6.5f) }
            }
        }

        private val kmTxt: String get() = String.format(java.util.Locale("pt", "BR"), "%.1f km", kmTotal)

        /** Altura do rótulo: abaixo do marcador se ele está na metade de cima, acima se está na de baixo. */
        private fun yRotulo(p: PointF): Float = if (p.y > height / 2f) p.y - 22 * dp else p.y + 22 * dp

        /** Retângulo da pílula, mantido dentro do card. */
        private fun caixa(texto: String, x: Float, y: Float, tamanho: Float): RectF {
            val fonte = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = tamanho * dp; typeface = Typeface.DEFAULT_BOLD }
            val larg = fonte.measureText(texto)
            val pad = 5 * dp
            val alt = 14 * dp
            val cx = x.coerceIn(larg / 2 + pad + 2 * dp, width - larg / 2 - pad - 2 * dp)
            val cy = y.coerceIn(alt / 2 + 2 * dp, height - alt / 2 - 2 * dp)
            return RectF(cx - larg / 2 - pad, cy - alt / 2, cx + larg / 2 + pad, cy + alt / 2)
        }

        private fun desenhar(c: Canvas, texto: String, r: RectF, cor: Int, tamanho: Float) {
            val alt = r.height()
            c.drawRoundRect(r, alt / 2, alt / 2, rotuloFundo)
            val fonte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = tamanho * dp; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = cor
            }
            c.drawText(texto, r.centerX(), r.centerY() + fonte.textSize * 0.35f, fonte)
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
