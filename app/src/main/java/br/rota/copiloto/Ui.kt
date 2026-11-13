package br.rota.copiloto

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Paleta branco + roxo. Verde/amarelo/vermelho ficam só para o semáforo. */
object C {
    /** Escala dos cantos arredondados (0 = reto). Muda com o modelo escolhido. */
    var RS = 1f
    var PURPLE = Color.parseColor("#6D28D9")
    var PURPLE_DARK = Color.parseColor("#4C1D95")
    var PURPLE_LIGHT = Color.parseColor("#EDE9FE")
    var BG = Color.parseColor("#F6F4FB")
    var WHITE = Color.WHITE
    var TEXT = Color.parseColor("#1E1B3A")
    var MUTED = Color.parseColor("#6B7280")
    var LINE = Color.parseColor("#E5E1F0")
    val GOOD = Color.parseColor("#16A34A")
    val MID = Color.parseColor("#F2B600")
    val BAD = Color.parseColor("#DC2626")
}

object Fonts {
    private val cache = HashMap<Int, Typeface>()
    private var family = 0

    /** Família de fonte do modelo: 0 Poppins, 1 serifada, 2 monoespaçada, 3 preta (pesada), 4 condensada. */
    fun setFamily(f: Int) {
        if (f != family) {
            family = f
            cache.clear()
        }
    }

    /** 0 regular, 1 medium, 2 semibold, 3 bold. */
    fun get(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        val bold = weight >= 2
        when (family) {
            1 -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            2 -> Typeface.create("monospace", if (bold) Typeface.BOLD else Typeface.NORMAL)
            3 -> if (bold) Typeface.create("sans-serif-black", Typeface.NORMAL) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            4 -> Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
            else -> try {
                ctx.resources.getFont(
                    when (weight) {
                        0 -> R.font.poppins_regular
                        1 -> R.font.poppins_medium
                        2 -> R.font.poppins_semibold
                        else -> R.font.poppins_bold
                    }
                )
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
    }
}

class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density).toInt()
    fun dpf(v: Float): Float = v * density
    fun font(w: Int): Typeface = Fonts.get(ctx, w)

    fun tv(s: CharSequence, sp: Float = 14f, color: Int = C.TEXT, w: Int = 0, center: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = font(w)
            if (center) gravity = Gravity.CENTER
        }

    fun round(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(radius.toFloat() * C.RS)
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    fun vbox(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun hbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(): LinearLayout = vbox().apply {
        background = round(C.WHITE, 20)
        elevation = dpf(2f)
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).also { it.topMargin = dp(top) }

    fun space(h: Int): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    fun button(label: String, filled: Boolean = true, onClick: () -> Unit): TextView =
        tv(label, 15f, if (filled) C.WHITE else C.PURPLE, 2, true).apply {
            background = round(if (filled) C.PURPLE else C.PURPLE_LIGHT, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
            layoutParams = lp(top = 10)
        }

    fun toggle(checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(ctx).apply {
        isChecked = checked
        val on = intArrayOf(android.R.attr.state_checked)
        val off = intArrayOf()
        thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(C.WHITE, C.WHITE))
        trackTintList = ColorStateList(arrayOf(on, off), intArrayOf(C.PURPLE, Color.parseColor("#D1D5DB")))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun checkbox(checked: Boolean, enabled: Boolean = true): TextView =
        tv(if (checked) "✓" else "", 16f, C.WHITE, 3, true).apply {
            background = if (checked) round(C.PURPLE, 8) else round(C.WHITE, 8, 2, Color.parseColor("#C9C4D8"))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            alpha = if (enabled) 1f else 0.5f
        }

    fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout =
        hbox().apply {
            background = round(C.PURPLE_LIGHT, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            options.forEachIndexed { i, label ->
                val on = i == selected
                addView(
                    tv(label, 13f, if (on) C.WHITE else C.PURPLE, if (on) 2 else 1, true).apply {
                        background = if (on) round(C.PURPLE, 11) else null
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        setOnClickListener { onSelect(i) }
                    },
                    lp(0, weight = 1f)
                )
            }
        }

    fun circleNumber(n: Int): TextView =
        tv(n.toString(), 14f, C.PURPLE, 2, true).apply {
            background = round(C.PURPLE_LIGHT, 16)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }

    /** Lista numerada de passos. */
    fun numbered(items: List<String>): LinearLayout = vbox().apply {
        items.forEachIndexed { i, s ->
            val row = hbox()
            row.addView(circleNumber(i + 1))
            row.addView(tv(s, 14f, C.TEXT, 0).apply { setPadding(dp(12), 0, 0, 0) }, lp(0, weight = 1f))
            addView(row, lp(top = 12))
        }
    }

    fun statusPill(text: String, ok: Boolean): TextView =
        tv((if (ok) "✓  " else "!  ") + text, 14f, if (ok) C.GOOD else Color.parseColor("#B45309"), 2, true).apply {
            background = round(if (ok) Color.parseColor("#E8F7EE") else Color.parseColor("#FEF3C7"), 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = lp(top = 16)
        }
}

/**
 * Barra de faixa com duas alças: abaixo da primeira = vermelho (ruim),
 * entre as duas = amarelo (média), acima da segunda = verde (bom).
 */
class RangeBar(
    ctx: Context,
    private val min: Double,
    private val max: Double,
    private val step: Double,
    private var low: Double,
    private var high: Double,
    private val fmt: (Double) -> String,
    private val onDone: (Double, Double) -> Unit
) : View(ctx) {

    private val ui = Ui(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ui.font(2)
        textSize = ui.dpf(15f)
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private var active = 0 // 1 = ruim, 2 = bom

    private val padX get() = ui.dpf(26f)
    private val trackY get() = height / 2f
    private fun xOf(v: Double): Float = padX + ((v.coerceIn(min, max) - min) / (max - min)).toFloat() * (width - 2 * padX)
    private fun vOf(x: Float): Double {
        val f = ((x - padX) / (width - 2 * padX)).coerceIn(0f, 1f)
        val raw = min + f * (max - min)
        return Math.round(raw / step) * step
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), ui.dp(130))
    }

    override fun onDraw(c: Canvas) {
        val th = ui.dpf(9f)
        val xl = xOf(low)
        val xh = xOf(high)
        val left = padX
        val right = width - padX
        paint.style = Paint.Style.FILL
        val r = th / 2
        paint.color = C.BAD
        c.drawRoundRect(RectF(left, trackY - th / 2, xl + r, trackY + th / 2), r, r, paint)
        paint.color = C.MID
        c.drawRect(xl, trackY - th / 2, xh, trackY + th / 2, paint)
        paint.color = C.GOOD
        c.drawRoundRect(RectF(xh - r, trackY - th / 2, right, trackY + th / 2), r, r, paint)
        thumb(c, xl, C.BAD)
        thumb(c, xh, C.GOOD)
        pill(c, xh, trackY - ui.dpf(48f), fmt(high), C.GOOD, true)
        pill(c, xl, trackY + ui.dpf(48f), fmt(low), C.BAD, false)
    }

    private fun thumb(c: Canvas, x: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(x, trackY, ui.dpf(13f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ui.dpf(4f)
        paint.color = color
        c.drawCircle(x, trackY, ui.dpf(11f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun pill(c: Canvas, cx: Float, cy: Float, text: String, color: Int, above: Boolean) {
        val w = textPaint.measureText(text) + ui.dpf(28f)
        val h = ui.dpf(36f)
        val x = cx.coerceIn(w / 2, width - w / 2)
        paint.color = color
        paint.style = Paint.Style.FILL
        c.drawRoundRect(RectF(x - w / 2, cy - h / 2, x + w / 2, cy + h / 2), ui.dpf(10f), ui.dpf(10f), paint)
        val ty = if (above) cy + h / 2 else cy - h / 2
        val path = android.graphics.Path()
        val tipY = if (above) ty + ui.dpf(8f) else ty - ui.dpf(8f)
        path.moveTo(cx - ui.dpf(7f), ty)
        path.lineTo(cx + ui.dpf(7f), ty)
        path.lineTo(cx, tipY)
        path.close()
        c.drawPath(path, paint)
        val fm = textPaint.fontMetrics
        c.drawText(text, x, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                active = if (Math.abs(e.x - xOf(low)) <= Math.abs(e.x - xOf(high))) 1 else 2
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onDone(low, high)
                active = 0
            }
        }
        return true
    }

    private fun move(x: Float) {
        val v = vOf(x)
        if (active == 1) low = v.coerceAtMost(high - step) else if (active == 2) high = v.coerceAtLeast(low + step)
        invalidate()
    }
}

/** Cartão que aparece por cima do Uber/99 (e na prévia das configurações). */
object OverlayCard {
    /** Quadro completo; com parada, ganha a moldura piscante em volta de tudo. */
    fun build(ctx: Context, a: Analysis, p: Prefs): View {
        val v = montar(ctx, a, p)
        if (a.offer.stops <= 0 || !p.paradaOn) return v
        val ui = Ui(ctx)
        val esp = ui.dp(p.paradaEsp)
        val cor = p.paradaCorInt
        val bg = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = ui.dpf(30f); setStroke(esp, cor) }
        val fr = android.widget.FrameLayout(ctx).apply { background = bg; setPadding(esp + ui.dp(2), esp + ui.dp(2), esp + ui.dp(2), esp + ui.dp(2)) }
        fr.addView(v)
        val anim = android.animation.ValueAnimator.ofFloat(1f, 0.15f).apply {
            duration = p.paradaMs.toLong()
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { bg.setStroke(esp, Color.argb((255 * (it.animatedValue as Float)).toInt(), Color.red(cor), Color.green(cor), Color.blue(cor))) }
        }
        fr.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) { if (!anim.isStarted) anim.start() }
            override fun onViewDetachedFromWindow(x: View) { anim.cancel() }
        })
        anim.start()
        return fr
    }

    val TAG_CORES = intArrayOf(Color.parseColor("#7C3AED"), Color.parseColor("#111418"), Color.WHITE, Color.parseColor("#2563EB"))
    val TAG_NOMES = listOf("Roxo", "Preto", "Branco", "Azul")
    val PARADA_CORES = intArrayOf(Color.parseColor("#FF1F1F"), Color.parseColor("#FF7A00"), Color.parseColor("#FF2D95"), Color.parseColor("#FFD400"))
    val PARADA_NOMES = listOf("Vermelho", "Laranja", "Rosa", "Amarelo")

    /**
     * Semáforo compacto: um quadro só, moldura na cor do semáforo, barra de lucro em pé na lateral.
     * Mostra o app, km, valor, destino com ícone do piso e os avisos (mercado, condomínio, paradas).
     */
    private fun montarCompacto(ctx: Context, a: Analysis, p: Prefs): View {
        val ui = Ui(ctx)
        val verdict = when (a.verdict) { Verdict.GREEN -> C.GOOD; Verdict.YELLOW -> C.MID; Verdict.RED -> C.BAD }
        val top = p.isTop(a)
        val neon = p.topCorInt
        val ink = Color.parseColor("#06210A")
        val size = p.cardFont.toFloat()
        val br = java.util.Locale("pt", "BR")
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val frameBg = GradientDrawable().apply {
            setColor(Color.parseColor("#17142A")); cornerRadius = ui.dpf(24f)
            setStroke(ui.dp(4), if (top) neon else verdict)
        }
        val frame = ui.hbox().apply { background = frameBg; setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10)) }
        val esq = ui.vbox()

        // linha 1: etiqueta do app (+ paradas / super)
        val l1 = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        val tagCor = if (a.offer.platform == "Uber") p.tagCorUber else if (a.offer.platform == "99") p.tagCor99 else Color.GRAY
        val clara = (Color.red(tagCor) * 299 + Color.green(tagCor) * 587 + Color.blue(tagCor) * 114) / 1000 > 160
        val tagEsc = when (p.appTagSize) { 0 -> 0.55f; 1 -> 0.75f; else -> 0.95f }
        l1.addView(ui.tv(when (a.offer.platform) { "Uber" -> "UBER"; "99" -> "99"; else -> "TESTE" }, size * tagEsc,
            if (clara) Color.parseColor("#1B1F24") else Color.WHITE, 3).apply {
            background = ui.round(tagCor, 8); setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(2)); setSingleLine(true)
        })
        val stops = a.offer.stops
        if (stops > 0) l1.addView(ui.tv(if (stops == 1) "PARADA" else "$stops PARADAS", size * 0.55f, Color.WHITE, 3).apply {
            background = ui.round(Color.rgb(224, 16, 16), 6); setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(2))
        }, LinearLayout.LayoutParams(wrap, wrap).also { it.marginStart = ui.dp(8) })
        var superTag: View? = null
        if (top) {
            superTag = ui.tv("⚡ SUPER", size * 0.55f, neon, 3).apply { background = ui.round(ink, 6); setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(2)) }
            l1.addView(superTag, LinearLayout.LayoutParams(wrap, wrap).also { it.marginStart = ui.dp(8) })
        }
        esq.addView(l1)

        // linha 2: km e valor, sem rótulos
        val pills = ui.hbox()
        fun pill(value: String, fill: Int, txt: Int): View = ui.tv(value, size * 1.1f, txt, 3).apply {
            background = GradientDrawable().apply { setColor(fill); cornerRadius = ui.dpf(40f) }
            setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(5)); setSingleLine(true); maxLines = 1; ellipsize = null
        }
        pills.addView(pill(String.format(br, "%.1f km", a.totalKm), Color.parseColor("#000000"), Color.WHITE), LinearLayout.LayoutParams(wrap, wrap, 1f))
        val vFill = if (top) neon else verdict
        pills.addView(pill(String.format(br, "R$ %.2f", a.offer.value), vFill, if (top || a.verdict == Verdict.YELLOW) ink else Color.WHITE),
            LinearLayout.LayoutParams(wrap, wrap, 1f).also { it.marginStart = ui.dp(6) })
        esq.addView(pills, ui.lp(top = 8))

        // linha 3: destino com ícone do piso
        val dest = Destino.analisar(a.offer.destText, p, a.offer.pickupText)
        if (p.bairroOn && !dest.vazio) esq.addView(bairroCompacto(ctx, ui, dest, size), ui.lp(top = 8))

        frame.addView(esq, LinearLayout.LayoutParams(wrap, wrap))

        // R$ por km, grande, ao lado
        val kmCol = ui.vbox().apply { gravity = Gravity.CENTER }
        val corKm = if (top) neon else verdict
        kmCol.addView(ui.tv(String.format(br, "%.2f", a.perKm), size * 2.3f, corKm, 3, true).apply { setSingleLine(true); includeFontPadding = false })
        kmCol.addView(ui.tv("R$/km", size * 0.6f, Color.WHITE, 2, true).apply { alpha = 0.85f })
        frame.addView(kmCol, LinearLayout.LayoutParams(wrap, ViewGroup.LayoutParams.MATCH_PARENT).also { it.marginStart = ui.dp(12) })

        if (a.verdict == Verdict.GREEN && p.eqOn) {
            val lo = p.eqMinC / 100.0; val hi = maxOf(p.eqMaxC / 100.0, lo + 0.01)
            val frac = ((a.perKm - lo) / (hi - lo)).coerceIn(0.0, 1.0)
            frame.addView(Equalizador(ctx, frac, top, p.eqCor, p.eqMs.toLong()), LinearLayout.LayoutParams(ui.dp(30), ViewGroup.LayoutParams.MATCH_PARENT).also { it.marginStart = ui.dp(10) })
        }

        if (top) {
            val anim = android.animation.ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = p.topMs.toLong(); repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener {
                    val f = it.animatedValue as Float
                    frameBg.setStroke(ui.dp(5), Color.argb((255 * f).toInt(), Color.red(neon), Color.green(neon), Color.blue(neon)))
                    superTag?.alpha = 0.35f + 0.65f * f
                }
            }
            frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { if (!anim.isStarted) anim.start() }
                override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
            })
            anim.start()
        }

        val negra = when {
            dest.negra != null -> Destino.Negra(p.avisoLinha1, p.avisoLinha2)
            dest.mercado != null -> Destino.Negra(p.mercadoLinha1, p.avisoLinha2)
            p.paradasDemais(a.offer.stops) -> Destino.Negra("${a.offer.stops} PARADAS", p.avisoLinha2)
            else -> return frame
        }
        return if (p.avisoStyle == 1) cartazNegra(ctx, ui, frame, negra, size) else painelNegra(ctx, ui, frame, negra, size)
    }

    private fun montar(ctx: Context, a: Analysis, p: Prefs): View {
        if (p.compacto) return montarCompacto(ctx, a, p)
        Templates.ensure(p.template)
        val tpl = Templates.tpl(p.template)
        val ui = Ui(ctx)
        val verdict = when (a.verdict) {
            Verdict.GREEN -> C.GOOD
            Verdict.YELLOW -> C.MID
            Verdict.RED -> C.BAD
        }
        val theme = p.cardTheme
        val top = p.isTop(a)
        val neon = p.topCorInt
        val ink = Color.parseColor("#06210A")
        val bg = when {
            top -> neon
            theme == 0 -> Color.WHITE
            theme == 1 -> Color.parseColor("#17142A")
            else -> verdict
        }
        val fg = when {
            top -> ink
            theme == 0 -> C.TEXT
            theme == 1 -> Color.WHITE
            else -> if (a.verdict == Verdict.YELLOW) Color.parseColor("#1A1A1A") else Color.WHITE
        }
        val muted = if (theme == 0) C.MUTED else Color.argb(200, Color.red(fg), Color.green(fg), Color.blue(fg))
        val size = p.cardFont.toFloat()

        val outer = ui.vbox().apply {
            background = GradientDrawable().apply {
                if (top) {
                    orientation = GradientDrawable.Orientation.TL_BR
                    colors = intArrayOf(neon, darker(neon))
                } else setColor(bg)
                cornerRadius = ui.dpf(if (top) maxOf(22f, tpl.ovRadius.toFloat()) else tpl.ovRadius.toFloat())
                val w = if (theme == 2 && !top) 0 else maxOf(2, tpl.ovBorder + 1)
                if (w > 0) setStroke(ui.dp(w), if (top) Color.WHITE else verdict)
            }
            setPadding(ui.dp(14), ui.dp(8), ui.dp(16), ui.dp(8))
        }

        val compacto = p.compacto
        val row = ui.hbox()
        if (!compacto) p.metrics.forEach { key ->
            val col = ui.vbox()
            col.addView(ui.tv(Metrics.label(key), size * 0.55f, muted, 1))
            val vr = ui.hbox()
            val bar = View(ctx).apply {
                background = ui.round(if (theme == 2 || top) fg else Metrics.color(a, key, p), 3)
                layoutParams = LinearLayout.LayoutParams(ui.dp(4), ui.dp((size * 1.25f).toInt()))
            }
            vr.addView(bar)
            vr.addView(ui.tv(Metrics.value(a, key), size, fg, 3).apply { setPadding(ui.dp(6), 0, 0, 0) })
            col.addView(vr)
            row.addView(col, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.marginEnd = ui.dp(14)
            })
        }
        if (!compacto) outer.addView(row)

        val stops = a.offer.stops

        val totalRounded = Math.round(a.totalMin).toInt()
        val h = totalRounded / 60
        val m = totalRounded % 60
        val info = String.format(java.util.Locale("pt", "BR"), "%dh%02dm  •  %.1fkm", h, m, a.totalKm)
        val foot = ui.hbox()
        val badge = when (a.offer.platform) {
            "Uber" -> "UBER"
            "99" -> "99"
            else -> "TESTE"
        }
        val tagCor = if (a.offer.platform == "Uber") p.tagCorUber else if (a.offer.platform == "99") p.tagCor99 else Color.GRAY
        val clara = (Color.red(tagCor) * 299 + Color.green(tagCor) * 587 + Color.blue(tagCor) * 114) / 1000 > 160
        val tagTxt = if (clara) Color.parseColor("#1B1F24") else Color.WHITE
        val tagEsc = when (p.appTagSize) { 0 -> 0.55f; 1 -> 0.75f; else -> 0.95f }
        foot.addView(ui.tv(badge, size * tagEsc, tagTxt, 3).apply {
            background = ui.round(tagCor, 8, if (clara) 1 else 0, Color.parseColor("#1B1F24"))
            setPadding(ui.dp(10), ui.dp(2), ui.dp(10), ui.dp(2))
            setSingleLine(true)
        })
        if (!compacto) foot.addView(ui.tv(info, size * 0.8f, fg, 2).apply { setPadding(ui.dp(14), 0, 0, 0) })
        if (stops > 0) {
            val tag = ui.tv(if (stops == 1) "PARADA" else "$stops PARADAS", size * 0.5f, Color.WHITE, 3).apply {
                background = ui.round(Color.rgb(224, 16, 16), 4)
                setPadding(ui.dp(6), ui.dp(1), ui.dp(6), ui.dp(1))
            }
            foot.addView(tag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = ui.dp(8) })
        }
        var superTag: View? = null
        if (top) {
            superTag = ui.tv("⚡ SUPER", size * 0.5f, neon, 3).apply {
                background = ui.round(ink, 4)
                setPadding(ui.dp(6), ui.dp(1), ui.dp(6), ui.dp(1))
            }
            foot.addView(superTag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = ui.dp(8) })
        }
        outer.addView(foot, ui.lp(top = if (compacto) 0 else 4))

        // Equalizador: só no quadro verde. Quanto mais a corrida paga por km, mais alto sobe.
        if (a.verdict == Verdict.GREEN && p.eqOn) {
            val inner = ui.vbox()
            while (outer.childCount > 0) { val c = outer.getChildAt(0); outer.removeViewAt(0); inner.addView(c) }
            outer.orientation = LinearLayout.HORIZONTAL
            outer.addView(inner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lo = p.eqMinC / 100.0; val hi = maxOf(p.eqMaxC / 100.0, lo + 0.01)
            val frac = ((a.perKm - lo) / (hi - lo)).coerceIn(0.0, 1.0)
            outer.addView(Equalizador(ctx, frac, top, p.eqCor, p.eqMs.toLong()), LinearLayout.LayoutParams(ui.dp(34), ViewGroup.LayoutParams.MATCH_PARENT).also {
                it.marginStart = ui.dp(12); it.topMargin = ui.dp(2); it.bottomMargin = ui.dp(2)
            })
        }

        val dest = Destino.analisar(a.offer.destText, p, a.offer.pickupText)
        val comBairro = p.bairroOn && !dest.vazio
        if (!p.showTotals && !top && !comBairro && dest.negra == null && dest.mercado == null && !p.paradasDemais(a.offer.stops)) return outer

        // Envoltório: quadro + (km total e valor) em pílulas + brilho neon quando é corrida TOP
        val box = ui.vbox().apply { setPadding(ui.dp(p.espacoCards), ui.dp(p.espacoCards), ui.dp(p.espacoCards), ui.dp(p.espacoCards)) }
        box.addView(outer)
        if (p.showTotals) {
            val pills = ui.hbox()
            fun pill(label: String, value: String, fill: Int?, txt: Int, stroke: Int): View {
                val v = ui.vbox().apply {
                    background = GradientDrawable().apply {
                        setColor(fill ?: Color.argb(215, 255, 255, 255))
                        cornerRadius = ui.dpf(40f)
                        setStroke(ui.dp(2), stroke)
                    }
                    setPadding(ui.dp(14), ui.dp(2), ui.dp(14), ui.dp(3))
                }
                if (!compacto) v.addView(ui.tv(label, size * 0.45f, txt, 3).apply { alpha = 0.8f })
                else v.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(5))
                v.addView(ui.tv(value, size * 1.1f, txt, 3).apply { setSingleLine(true); maxLines = 1; ellipsize = null })
                return v
            }
            val br = java.util.Locale("pt", "BR")
            val preto = Color.parseColor("#111418")
            val km = pill("KM TOTAL", String.format(br, "%.1f km", a.totalKm), preto, Color.WHITE, preto)
            val valueFill = if (top) neon else if (theme == 2) verdict else verdict
            val valueTxt = if (top || a.verdict == Verdict.YELLOW) ink else Color.WHITE
            val vl = pill("VALOR", String.format(br, "R$ %.2f", a.offer.value), valueFill, valueTxt, valueFill)
            // largura natural primeiro (o valor nunca quebra em duas linhas); a sobra é dividida igual
            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
            pills.addView(km, LinearLayout.LayoutParams(wrap, wrap, 1f))
            pills.addView(vl, LinearLayout.LayoutParams(wrap, wrap, 1f).also { it.marginStart = ui.dp(4) })
            box.addView(pills, ui.lp(top = p.espacoCards))
        }

        if (comBairro) box.addView(if (compacto) bairroCompacto(ctx, ui, dest, size) else bairroCard(ctx, ui, dest, p.bairroStyle, size), ui.lp(top = p.espacoCards))

        if (top) {
            val period = p.topMs.toLong()
            val halo = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = ui.dpf(30f)
                setStroke(ui.dp(5), neon)
            }
            box.background = halo
            val anim = android.animation.ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = period
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener {
                    val f = it.animatedValue as Float
                    halo.setStroke(ui.dp(5), Color.argb((255 * f).toInt(), Color.red(neon), Color.green(neon), Color.blue(neon)))
                    superTag?.alpha = 0.35f + 0.65f * f
                }
            }
            box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { if (!anim.isStarted) anim.start() }
                override fun onViewDetachedFromWindow(v: View) { anim.cancel() }
            })
            anim.start()
        }
        val negra = when {
            dest.negra != null -> Destino.Negra(p.avisoLinha1, p.avisoLinha2)
            dest.mercado != null -> Destino.Negra(p.mercadoLinha1, p.avisoLinha2)
            p.paradasDemais(a.offer.stops) -> Destino.Negra("${a.offer.stops} PARADAS", p.avisoLinha2)
            else -> return box
        }
        return if (p.avisoStyle == 1) cartazNegra(ctx, ui, box, negra, size) else painelNegra(ctx, ui, box, negra, size)
    }

    private val AZUL = Color.parseColor("#1D4ED8")
    private val VERMELHO = Color.parseColor("#DC2626")
    private val AMBAR = Color.parseColor("#F59E0B")

    /** Pisca entre duas cores no fundo de [bg] (e na borda, se pedido) enquanto a view estiver na tela. */
    private fun piscar(v: View, bg: GradientDrawable, de: Int, para: Int, borda: Boolean, ui: Ui) {
        val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float
                val c = android.animation.ArgbEvaluator().evaluate(f, de, para) as Int
                bg.setColor(c)
                if (borda) bg.setStroke(ui.dp(3), Color.argb((255 * f).toInt(), 255, 90, 90))
            }
        }
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) { if (!anim.isStarted) anim.start() }
            override fun onViewDetachedFromWindow(x: View) { anim.cancel() }
        })
        anim.start()
    }

    private fun selo(ui: Ui, txt: String, fundo: Int, cor: Int, size: Float): TextView =
        ui.tv(txt, size * 0.68f, cor, 3).apply {
            background = ui.round(fundo, 20)
            setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4))
            maxLines = 2; maxWidth = ui.dp(240)
        }

    /** Card azul do bairro de destino nos 3 modelos (A faixa larga, B pílula fina, C placa com status). */
    private fun bairroCard(ctx: Context, ui: Ui, d: Destino.Info, estilo: Int, size: Float): View {
        val nome = d.condominio ?: d.bairro ?: d.rua ?: ""
        val condo = d.condominio != null
        val roxo = Color.parseColor("#7C3AED")
        // só a RUA do destino: aparece apenas quando ela é de terra (asfalto não precisa aviso)
        val terraTxt: String? = null
        val piso = iconePiso(ctx, ui, d.pavimento, size)
        val bg = GradientDrawable().apply { setColor(AZUL) }
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val nomeTv = ui.tv(nome, size * 0.95f, Color.WHITE, 3).apply { maxLines = 2; maxWidth = ui.dp(270) }
        when (estilo) {
            1 -> {
                bg.cornerRadius = ui.dpf(40f)
                val r = ui.hbox().apply { background = bg; setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6)); gravity = Gravity.CENTER_VERTICAL }
                r.addView(ui.tv(if (d.evitado) "⚠ EVITAR" else if (condo) "Condomínio" else if (d.bairro == null) "Destino" else "Bairro", size * 0.5f, Color.WHITE, 2).apply { alpha = 0.9f })
                r.addView(nomeTv, LinearLayout.LayoutParams(wrap, wrap).also { it.marginStart = ui.dp(8) })
                if (piso != null) r.addView(piso, LinearLayout.LayoutParams(ui.dp((size * 2.0f).toInt()), ui.dp((size * 2.0f).toInt())).also { it.marginStart = ui.dp(8) })
                if (d.evitado) piscar(r, bg, AZUL, VERMELHO, true, ui) else if (condo) bg.setStroke(ui.dp(3), roxo)
                return r
            }
            2 -> {
                bg.cornerRadius = ui.dpf(20f)
                val r = ui.hbox().apply { background = bg; clipToOutline = true }
                val esq = ui.vbox().apply { setPadding(ui.dp(14), ui.dp(6), ui.dp(12), ui.dp(6)) }
                esq.addView(ui.tv(if (condo) "CONDOMÍNIO FECHADO" else "BAIRRO DE DESTINO", size * 0.42f, Color.WHITE, 2).apply { alpha = 0.85f })
                esq.addView(nomeTv)
                val sub2 = if (condo) listOfNotNull(d.rua, d.bairro?.takeIf { it != d.condominio }).joinToString(" · ") else if (d.bairro != null) d.rua ?: "" else ""
                if (sub2.isNotBlank()) esq.addView(ui.tv(sub2, size * 0.48f, Color.WHITE, 0).apply { alpha = 0.9f; maxLines = 2; maxWidth = ui.dp(270) })
                r.addView(esq, LinearLayout.LayoutParams(wrap, wrap, 1f))
                if (piso != null) r.addView(piso, LinearLayout.LayoutParams(ui.dp((size * 2.0f).toInt()), ui.dp((size * 2.0f).toInt())).also { it.gravity = Gravity.CENTER_VERTICAL; it.marginEnd = ui.dp(8) })
                val (tag, cor) = when { d.evitado -> "EVITAR" to VERMELHO; condo -> "CONDOM." to roxo; terraTxt != null -> terraTxt to AMBAR; else -> "OK" to Color.parseColor("#60A5FA") }
                val ladoBg = GradientDrawable().apply { setColor(cor); cornerRadii = floatArrayOf(0f, 0f, ui.dpf(20f), ui.dpf(20f), ui.dpf(20f), ui.dpf(20f), 0f, 0f) }
                val lado = ui.tv(tag, size * 0.55f, if (cor == AMBAR) Color.parseColor("#2A1600") else Color.WHITE, 3, true).apply {
                    background = ladoBg; gravity = Gravity.CENTER; setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4)); maxLines = 3; maxWidth = ui.dp(120)
                }
                r.addView(lado, LinearLayout.LayoutParams(wrap, ViewGroup.LayoutParams.MATCH_PARENT))
                if (d.evitado) piscar(lado, ladoBg, VERMELHO, Color.parseColor("#7F1D1D"), false, ui)
                if (d.evitado) bg.setStroke(ui.dp(3), Color.parseColor("#FF5A5A"))
                return r
            }
            else -> {
                bg.cornerRadius = ui.dpf(22f)
                val r = ui.hbox().apply { background = bg; setPadding(ui.dp(14), ui.dp(8), ui.dp(16), ui.dp(8)); gravity = Gravity.CENTER_VERTICAL }
                r.addView(ui.tv(if (d.evitado) "⚠" else "📍", size * 0.8f, Color.WHITE, 2, true).apply {
                    background = ui.round(Color.argb(46, 255, 255, 255), 30)
                    layoutParams = LinearLayout.LayoutParams(ui.dp(38), ui.dp(38))
                })
                val col = ui.vbox().apply { setPadding(ui.dp(10), 0, 0, 0) }
                col.addView(ui.tv(if (d.evitado) "BAIRRO QUE VOCÊ EVITA" else if (condo) "CONDOMÍNIO FECHADO" else "DESTINO", size * 0.42f, Color.WHITE, 3).apply { alpha = 0.9f })
                col.addView(nomeTv)
                val linha = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
                val sub3 = if (condo) listOfNotNull(d.rua, d.bairro?.takeIf { it != d.condominio }).joinToString(" · ") else if (d.bairro != null) d.rua ?: "" else ""
                if (sub3.isNotBlank()) linha.addView(ui.tv(sub3, size * 0.48f, Color.WHITE, 0).apply { maxLines = 2; maxWidth = ui.dp(240) })

                if (condo && !d.evitado) bg.setStroke(ui.dp(3), roxo)
                if (linha.childCount > 0) col.addView(linha)
                r.addView(col, LinearLayout.LayoutParams(wrap, wrap, 1f))
                if (piso != null) r.addView(piso, LinearLayout.LayoutParams(ui.dp((size * 2.0f).toInt()), ui.dp((size * 2.0f).toInt())).also { it.marginStart = ui.dp(10) })
                if (d.evitado) piscar(r, bg, AZUL, VERMELHO, true, ui)
                return r
            }
        }
    }

    /** Ícone do piso da rua de destino: terra (também para trecho de terra) ou asfalto. */
    private fun iconePiso(ctx: Context, ui: Ui, pav: String?, size: Float): View? = when (pav) {
        "terra", "misto" -> IconePiso(ctx, true).apply { contentDescription = if (pav == "terra") "Rua de terra" else "Rua com trecho de terra" }
        "pavimentada" -> IconePiso(ctx, false).apply { contentDescription = "Rua asfaltada" }
        else -> null
    }

    /** Destino no modo compacto: só o nome e o ícone do piso, sem rótulo nem alfinete. */
    private fun bairroCompacto(ctx: Context, ui: Ui, d: Destino.Info, size: Float): View {
        val nome = d.condominio ?: d.bairro ?: d.rua ?: ""
        val condo = d.condominio != null
        val bg = GradientDrawable().apply { setColor(AZUL); cornerRadius = ui.dpf(22f) }
        val r = ui.hbox().apply { background = bg; setPadding(ui.dp(16), ui.dp(6), ui.dp(12), ui.dp(6)); gravity = Gravity.CENTER_VERTICAL }
        r.addView(ui.tv((if (d.evitado) "⚠ " else "") + nome, size * 0.95f, Color.WHITE, 3).apply { maxLines = 2; maxWidth = ui.dp(270) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        iconePiso(ctx, ui, d.pavimento, size)?.let { r.addView(it, LinearLayout.LayoutParams(ui.dp((size * 2.0f).toInt()), ui.dp((size * 2.0f).toInt())).also { lp -> lp.marginStart = ui.dp(10) }) }
        if (d.evitado) piscar(r, bg, AZUL, VERMELHO, true, ui) else if (condo) bg.setStroke(ui.dp(3), Color.parseColor("#7C3AED"))
        return r
    }

    private fun conteudoNegra(ui: Ui, n: Destino.Negra, size: Float, grande: Boolean): LinearLayout {
        val c = ui.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        c.addView(ui.tv("⛔", size * (if (grande) 2.0f else 1.4f), Color.WHITE, 3, true))
        if (n.nome.isNotBlank()) c.addView(ui.tv(n.nome, size * (if (grande) 1.05f else 0.7f), Color.WHITE, 3, true).apply { maxWidth = ui.dp(if (grande) 280 else 110) }, ui.lp(top = 2))
        if (n.motivo.isNotBlank()) c.addView(ui.tv(n.motivo, size * (if (grande) 1.3f else 0.9f), Color.WHITE, 3, true).apply { maxWidth = ui.dp(if (grande) 280 else 110) })
        return c
    }

    /** Opção 1: painel vermelho grande ao lado do quadro. */
    private fun painelNegra(ctx: Context, ui: Ui, box: View, n: Destino.Negra, size: Float): View {
        val livre = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        box.measure(livre, livre)
        val tela = ctx.resources.displayMetrics.widthPixels - ui.dp(24)
        val cabeAoLado = box.measuredWidth + ui.dp(126) <= tela
        if (!cabeAoLado) {
            // não cabe ao lado: o aviso vai logo abaixo do quadro, na largura dele
            val col = ui.vbox()
            col.addView(box)
            val bg2 = GradientDrawable().apply { setColor(Color.parseColor("#B91C1C")); cornerRadius = ui.dpf(22f) }
            val p2 = conteudoNegra(ui, n, size, false).apply { background = bg2; gravity = Gravity.CENTER; setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6)) }
            col.addView(p2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = ui.dp(3) })
            piscar(p2, bg2, Color.parseColor("#B91C1C"), Color.parseColor("#EF4444"), true, ui)
            return col
        }
        val row = ui.hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(box)
        val bg = GradientDrawable().apply { setColor(Color.parseColor("#B91C1C")); cornerRadius = ui.dpf(22f) }
        val painel = conteudoNegra(ui, n, size, false).apply {
            background = bg; gravity = Gravity.CENTER
            setPadding(ui.dp(10), ui.dp(14), ui.dp(10), ui.dp(14))
        }
        row.addView(painel, LinearLayout.LayoutParams(ui.dp(120), ViewGroup.LayoutParams.MATCH_PARENT).also { it.marginStart = ui.dp(3); it.topMargin = ui.dp(3); it.bottomMargin = ui.dp(3) })
        piscar(painel, bg, Color.parseColor("#B91C1C"), Color.parseColor("#EF4444"), true, ui)
        return row
    }

    /** Opção 2: cartaz vermelho por cima do quadro; some ao tocar em "Entendi" ou depois de 6 s. */
    private fun cartazNegra(ctx: Context, ui: Ui, box: View, n: Destino.Negra, size: Float): View {
        val fr = android.widget.FrameLayout(ctx)
        fr.addView(box)
        val bg = GradientDrawable().apply { setColor(Color.parseColor("#B91C1C")); cornerRadius = ui.dpf(28f) }
        val cartaz = conteudoNegra(ui, n, size, true).apply {
            background = bg; gravity = Gravity.CENTER
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        }
        val ok = ui.tv("Entendi", size * 0.6f, Color.parseColor("#B91C1C"), 3, true).apply {
            background = ui.round(Color.WHITE, 30)
            setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(8))
            setOnClickListener { cartaz.visibility = View.GONE }
        }
        cartaz.addView(ok, ui.lp(top = 10))
        cartaz.setOnClickListener { /* não fecha o quadro por baixo */ }
        fr.addView(cartaz, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        piscar(cartaz, bg, Color.parseColor("#B91C1C"), Color.parseColor("#EF4444"), true, ui)
        cartaz.postDelayed({ cartaz.visibility = View.GONE }, 6000)
        return fr
    }

    /** Verde neon, turquesa, lima, ciano. */
    val NEONS = intArrayOf(Color.parseColor("#39FF14"), Color.parseColor("#00F5D4"), Color.parseColor("#C6FF00"), Color.parseColor("#00E5FF"))
    val NEON_NAMES = listOf("Verde neon", "Turquesa", "Lima", "Ciano")

    private fun darker(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = hsv[2] * 0.78f
        return Color.HSVToColor(hsv)
    }

    /** Oferta de exemplo para a prévia e o botão de teste. */
    fun sample(p: Prefs): Analysis =
        Calc.analyze(Offer("Uber", 18.50, 1.2, 4.0, 6.8, 17.0), p)

    /** Oferta de exemplo "corrida TOP" para a prévia da configuração. */
    fun sampleTop(p: Prefs): Analysis =
        Calc.analyze(Offer("Uber", 36.0, 1.2, 4.0, 8.4, 22.0), p)
}

/** Barra vertical em blocos, como o equalizador de um rádio: acende de baixo para cima, em neon piscante. */
class Equalizador(ctx: Context, private val nivel: Double, private val noNeon: Boolean, private val neon: Int, private val ms: Long) : View(ctx) {
    private val n = 10
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val r = android.graphics.RectF()
    private val d = ctx.resources.displayMetrics.density
    private val acesos = maxOf(1, Math.ceil(nivel * n).toInt())
    private var brilho = 1f
    private val anim = android.animation.ValueAnimator.ofFloat(1f, 0.35f).apply {
        duration = ms
        repeatMode = android.animation.ValueAnimator.REVERSE
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener { brilho = it.animatedValue as Float; invalidate() }
    }

    init {
        minimumHeight = (80 * d).toInt()
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // necessário para o brilho (glow)
        contentDescription = "Lucro ${Math.round(nivel * 100)}% da faixa"
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (!anim.isStarted) anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val gap = 3f * d
        val pad = 4 * d
        // fundo bem escuro para o neon saltar
        p.clearShadowLayer(); p.style = android.graphics.Paint.Style.FILL
        p.color = Color.parseColor("#05140A")
        r.set(0f, 0f, w, h); c.drawRoundRect(r, 8 * d, 8 * d, p)
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Color.argb(160, 255, 255, 255)
        c.drawRoundRect(r, 8 * d, 8 * d, p)
        p.style = android.graphics.Paint.Style.FILL
        val seg = (h - 2 * pad - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bottom = h - pad - i * (seg + gap)
            r.set(pad, bottom - seg, w - pad, bottom)
            if (i < acesos) {
                // o bloco do topo do nível pisca mais forte; os de baixo pulsam junto, mais suave
                val topo = i == acesos - 1
                val a = if (topo) brilho else 0.65f + 0.35f * brilho
                val cor = Color.argb((255 * a).toInt(), Color.red(neon), Color.green(neon), Color.blue(neon))
                p.color = cor
                p.setShadowLayer((if (topo) 10f else 6f) * d * a, 0f, 0f, cor)
                c.drawRoundRect(r, 3 * d, 3 * d, p)
                p.clearShadowLayer()
            } else {
                p.color = Color.argb(40, 255, 255, 255)
                c.drawRoundRect(r, 3 * d, 3 * d, p)
            }
        }
    }
}

/**
 * Encaixa o quadro na tela: mede tudo no tamanho natural e, se não couber na largura
 * ou na altura disponível, reduz por igual (escala). Nada é cortado.
 */
class Encaixe(ctx: Context, private val maxW: Int, private val maxH: Int) : android.widget.FrameLayout(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val c = if (childCount > 0) getChildAt(0) else { setMeasuredDimension(0, 0); return }
        val livre = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        c.measure(livre, livre)
        val cw = c.measuredWidth.coerceAtLeast(1); val ch = c.measuredHeight.coerceAtLeast(1)
        val s = minOf(1f, maxW.toFloat() / cw, maxH.toFloat() / ch)
        c.pivotX = 0f; c.pivotY = 0f; c.scaleX = s; c.scaleY = s
        setMeasuredDimension(Math.ceil(cw * s.toDouble()).toInt(), Math.ceil(ch * s.toDouble()).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (childCount > 0) { val c = getChildAt(0); c.layout(0, 0, c.measuredWidth, c.measuredHeight) }
    }
}



/** Ícone do piso: uma estrada em perspectiva. Asfalto cinza com faixas; terra marrom com marcas de pneu. */
class IconePiso(ctx: Context, private val terra: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // fundo redondo claro para destacar sobre o azul
        p.style = Paint.Style.FILL
        p.color = if (terra) Color.parseColor("#FFF4E5") else Color.parseColor("#EEF2F7")
        c.drawRoundRect(RectF(0f, 0f, w, h), w * 0.26f, w * 0.26f, p)
        // estrada: trapézio do horizonte (fino) até embaixo (largo)
        val topoE = w * 0.42f; val topoD = w * 0.58f; val baseE = w * 0.1f; val baseD = w * 0.9f
        val yT = h * 0.16f; val yB = h * 0.9f
        val estrada = Path().apply { moveTo(topoE, yT); lineTo(topoD, yT); lineTo(baseD, yB); lineTo(baseE, yB); close() }
        p.color = if (terra) Color.parseColor("#B7793F") else Color.parseColor("#3B4250")
        c.drawPath(estrada, p)
        if (terra) {
            // duas marcas de pneu e pedrinhas
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.color = Color.parseColor("#7A4A1E"); p.strokeWidth = w * 0.055f
            c.drawLine(w * 0.47f, yT + h * 0.04f, w * 0.33f, yB - h * 0.03f, p)
            c.drawLine(w * 0.53f, yT + h * 0.04f, w * 0.67f, yB - h * 0.03f, p)
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#8C5A2B")
            listOf(0.5f to 0.55f, 0.42f to 0.78f, 0.6f to 0.72f, 0.5f to 0.35f).forEach { (x, y) -> c.drawCircle(w * x, h * y, w * 0.035f, p) }
            // tufos de mato nas beiras
            p.color = Color.parseColor("#4E9A3A")
            c.drawCircle(w * 0.12f, h * 0.62f, w * 0.06f, p); c.drawCircle(w * 0.88f, h * 0.55f, w * 0.06f, p)
        } else {
            // faixas brancas nas bordas e tracejado central amarelo, em perspectiva
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.color = Color.WHITE; p.strokeWidth = w * 0.04f
            c.drawLine(topoE + w * 0.02f, yT + h * 0.02f, baseE + w * 0.06f, yB - h * 0.02f, p)
            c.drawLine(topoD - w * 0.02f, yT + h * 0.02f, baseD - w * 0.06f, yB - h * 0.02f, p)
            p.color = Color.parseColor("#FACC15")
            listOf(0.2f to 0.32f, 0.42f to 0.56f, 0.66f to 0.86f).forEachIndexed { i, (a0, b0) ->
                p.strokeWidth = w * (0.035f + i * 0.02f)
                c.drawLine(w * 0.5f, h * a0, w * 0.5f, h * b0, p)
            }
        }
    }
}
