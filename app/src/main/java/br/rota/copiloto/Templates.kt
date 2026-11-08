package br.rota.copiloto

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Um modelo (template) visual do aplicativo: cores, fonte e formato dos cantos. */
class Tpl(
    val name: String,
    val tagline: String,
    val bg: Int,
    val surface: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val soft: Int,
    val line: Int,
    val bar: Int,
    val darkBars: Boolean,
    val rs: Float,
    val family: Int,
    val ovRadius: Int,
    val ovBorder: Int
)

object Templates {
    private fun h(s: String) = Color.parseColor(s)

    var current = -1

    val all: List<Tpl> by lazy {
        listOf(
            Tpl("Cockpit", "Medidor escuro com arco", h("#0B1215"), h("#121D21"), h("#E8F1F2"), h("#8FA3A8"), h("#2DD4BF"), h("#1B2A2F"), h("#22343A"), h("#0B1215"), true, 1f, 4, 16, 2),
            Tpl("Bento pastel", "Blocos coloridos e arredondados", h("#F6F1EA"), h("#FFFFFF"), h("#2B2622"), h("#6E655C"), h("#2F5D50"), h("#E3EBE5"), h("#E4DCCF"), h("#F6F1EA"), false, 1.4f, 0, 28, 0),
            Tpl("Recibo", "Estilo nota fiscal", h("#E9E3D0"), h("#F7F3E8"), h("#1E1E1C"), h("#5F5B50"), h("#1E1E1C"), h("#E4DFCF"), h("#B9B29C"), h("#E9E3D0"), false, 0.1f, 2, 2, 2),
            Tpl("Neo-brutalista", "Bordas grossas e cores fortes", h("#FFE14D"), h("#FFFFFF"), h("#111111"), h("#444444"), h("#111111"), h("#FF9EB9"), h("#111111"), h("#FFE14D"), false, 0f, 3, 0, 4),
            Tpl("Tipográfico", "Só letras grandes e linhas", h("#FAFAF7"), h("#FFFFFF"), h("#141414"), h("#5E5E58"), h("#C2331B"), h("#F3E3DF"), h("#D8D8D0"), h("#FAFAF7"), false, 0.2f, 1, 4, 1),
            Tpl("Semáforo gigante", "A tela muda com a oferta", h("#0F1410"), h("#1A221C"), h("#F2F5F0"), h("#9FB2A4"), h("#2BD16B"), h("#24302A"), h("#34423A"), h("#0F1410"), true, 1.1f, 0, 20, 3),
            Tpl("Trilho lateral", "Menu vertical à esquerda", h("#EEF1F6"), h("#FFFFFF"), h("#14213D"), h("#5C6B8A"), h("#14213D"), h("#DDE3F0"), h("#D5DBE8"), h("#EEF1F6"), false, 1f, 0, 18, 2),
            Tpl("Cartas deslizantes", "Cartões que você desliza", h("#0A2A31"), h("#123C45"), h("#F6EFE2"), h("#9CC9CF"), h("#7FE0C1"), h("#1B5560"), h("#2B5C66"), h("#0A2A31"), true, 1.5f, 0, 28, 0),
            Tpl("Painel do turno", "Faixas de meta e números", h("#17181C"), h("#22242A"), h("#F2F3EE"), h("#9A9DA6"), h("#C6F432"), h("#2E3036"), h("#34363D"), h("#17181C"), true, 1.2f, 0, 22, 2),
            Tpl("Mapa e gaveta", "Mapa ao fundo, controles embaixo", h("#D9E4DD"), h("#FFFFFF"), h("#1B2A24"), h("#5A6C63"), h("#E4572E"), h("#EEF3EF"), h("#CBD5CF"), h("#D9E4DD"), false, 1f, 0, 18, 2)
        )
    }

    fun tpl(i: Int): Tpl = all[i.coerceIn(0, all.size - 1)]

    /** Aplica as cores e a fonte do modelo em todo o app. */
    fun apply(i: Int) {
        val idx = i.coerceIn(0, all.size - 1)
        val t = all[idx]
        C.BG = t.bg
        C.WHITE = t.surface
        C.TEXT = t.text
        C.MUTED = t.muted
        C.PURPLE = t.accent
        C.PURPLE_DARK = t.accent
        C.PURPLE_LIGHT = t.soft
        C.LINE = t.line
        C.RS = t.rs
        Fonts.setFamily(t.family)
        current = idx
    }

    fun ensure(i: Int) {
        if (current != i.coerceIn(0, all.size - 1)) apply(i)
    }
}

/** Tudo o que a tela inicial de qualquer modelo precisa para funcionar. */
class HomeActions(
    val prefs: Prefs,
    val reading: Boolean,
    val a: Analysis,
    val footer: String,
    val setCopilot: (Boolean) -> Unit,
    val setCalc: (Boolean) -> Unit,
    val setShots: (Boolean) -> Unit,
    val activate: () -> Unit,
    val release: () -> Unit,
    val config: () -> Unit,
    val apps: () -> Unit,
    val shots: () -> Unit,
    val tutorial: () -> Unit,
    val xiaomi: () -> Unit,
    val diag: () -> Unit,
    val help: () -> Unit
)

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private fun <T : View> T.tap(f: () -> Unit): T {
    setOnClickListener { f() }
    return this
}

private class B(val ctx: Context) {
    val ui = Ui(ctx)
    fun dp(v: Int) = ui.dp(v)
    fun v(): LinearLayout = ui.vbox()
    fun h(): LinearLayout = ui.hbox()
    fun t(s: CharSequence, sp: Float, color: Int, w: Int = 0, center: Boolean = false): TextView = ui.tv(s, sp, color, w, center)

    fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, weight: Float = 0f, start: Int = 0, end: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).also { it.setMargins(dp(start), dp(top), dp(end), dp(bottom)) }

    fun rr(color: Int, r: Int, stroke: Int = 0, sc: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(r).toFloat()
            if (stroke > 0) setStroke(dp(stroke), sc)
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun btn(label: String, fg: Int, bgc: Int, r: Int, sp: Float = 15f, w: Int = 2, stroke: Int = 0, sc: Int = 0, f: () -> Unit): TextView =
        t(label, sp, fg, w, true).apply {
            background = rr(bgc, r, stroke, sc)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { f() }
        }

    fun sw(on: Boolean, track: Int, off: Int, thumb: Int, f: () -> Unit): View {
        val fl = FrameLayout(ctx)
        fl.background = rr(if (on) track else off, 16)
        val th = View(ctx)
        th.background = oval(thumb)
        fl.addView(
            th,
            FrameLayout.LayoutParams(dp(24), dp(24)).also {
                it.gravity = (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
                it.leftMargin = dp(4)
                it.rightMargin = dp(4)
            }
        )
        fl.layoutParams = LinearLayout.LayoutParams(dp(54), dp(32))
        fl.setOnClickListener { f() }
        return fl
    }

    fun icon(res: Int, tint: Int, size: Int = 22): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    fun dot(color: Int, size: Int = 8): View = View(ctx).apply {
        background = oval(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).also { it.marginEnd = dp(8) }
    }
}

private fun vcol(a: Analysis): Int = when (a.verdict) {
    Verdict.GREEN -> C.GOOD
    Verdict.YELLOW -> C.MID
    Verdict.RED -> C.BAD
}

private fun vname(a: Analysis): String = when (a.verdict) {
    Verdict.GREEN -> "BOA"
    Verdict.YELLOW -> "MÉDIA"
    Verdict.RED -> "RUIM"
}

private fun onVerdict(a: Analysis): Int = if (a.verdict == Verdict.YELLOW) Color.parseColor("#1A1A1A") else Color.WHITE

private fun scaffold(b: B, bg: Int, content: View, bottom: View? = null): View {
    val root = b.v().apply { setBackgroundColor(bg) }
    val sv = ScrollView(b.ctx).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
    }
    sv.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
    root.addView(sv, b.lp(h = 0, weight = 1f))
    if (bottom != null) root.addView(bottom)
    return root
}

private fun spacer(b: B): View = View(b.ctx).apply { layoutParams = b.lp(h = 0, weight = 1f) }

private fun readingChip(b: B, a: HomeActions, fg: Int, okc: Int): View {
    val r = b.h()
    if (a.reading) {
        r.addView(b.dot(okc))
        r.addView(b.t("Leitura ativa", 12f, fg, 1))
    } else {
        r.addView(b.dot(C.BAD))
        r.addView(b.t("Leitura desligada · ativar", 12f, fg, 2))
        r.setOnClickListener { a.activate() }
        r.setOnLongClickListener { a.release(); true }
    }
    return r
}

private fun footerView(b: B, a: HomeActions): View = b.t(a.footer, 11f, C.MUTED, 0, true).apply {
    layoutParams = b.lp(top = 18)
}

/** Linha de atalhos: Configurar, Tutorial, Ajuda Xiaomi e Diagnóstico. */
private fun dock(b: B, a: HomeActions, bg: Int, fg: Int, act: Int): View {
    val d = b.h().apply {
        setBackgroundColor(bg)
        setPadding(0, b.dp(10), 0, b.dp(12))
    }
    val items = listOf(
        Triple("Configurar", android.R.drawable.ic_menu_preferences, a.config),
        Triple("Tutorial", android.R.drawable.ic_menu_help, a.tutorial),
        Triple("Xiaomi", android.R.drawable.ic_menu_info_details, a.xiaomi),
        Triple("Diagnóstico", android.R.drawable.ic_menu_manage, a.diag)
    )
    items.forEach { (label, res, f) ->
        val col = b.v().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, b.dp(4), 0, b.dp(4))
        }
        col.addView(b.icon(res, act))
        col.addView(b.t(label, 11f, fg, 1, true), b.lp(top = 4))
        col.setOnClickListener { f() }
        d.addView(col, b.lp(0, weight = 1f))
    }
    return d
}

// ------------------------------------------------------------------ views desenhadas

private class GaugeView(
    ctx: Context,
    private val frac: Float,
    private val col: Int,
    private val trk: Int,
    private val big: String,
    private val cap: String,
    private val top: String,
    private val fg: Int,
    private val mu: Int
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val u = Ui(ctx)

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val sw = u.dpf(16f)
        val m = sw / 2 + u.dpf(4f)
        val rect = RectF(m, m, s - m, s - m)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.color = trk
        c.drawArc(rect, 135f, 270f, false, p)
        p.color = col
        c.drawArc(rect, 135f, 270f * frac.coerceIn(0.03f, 1f), false, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = u.font(1)
        p.textSize = u.dpf(12f)
        p.color = mu
        c.drawText(top, s / 2, s / 2 - u.dpf(42f), p)
        p.typeface = u.font(3)
        p.textSize = u.dpf(54f)
        p.color = fg
        c.drawText(big, s / 2, s / 2 + u.dpf(16f), p)
        p.typeface = u.font(2)
        p.textSize = u.dpf(14f)
        p.color = col
        c.drawText(cap, s / 2, s / 2 + u.dpf(46f), p)
    }
}

/** Barra com as três faixas (ruim, média, boa) e um marcador onde a oferta caiu. */
private class ZoneBar(
    ctx: Context,
    private val value: Double,
    private val yellow: Double,
    private val green: Double,
    private val max: Double,
    private val fg: Int
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val u = Ui(ctx)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), u.dp(64))
    }

    private fun x(v: Double): Float = (v.coerceIn(0.0, max) / max).toFloat() * width

    override fun onDraw(c: Canvas) {
        val y = u.dpf(34f)
        val th = u.dpf(14f)
        val r = th / 2
        p.style = Paint.Style.FILL
        p.color = C.BAD
        c.drawRoundRect(RectF(0f, y - th / 2, x(yellow) + r, y + th / 2), r, r, p)
        p.color = C.MID
        c.drawRect(x(yellow), y - th / 2, x(green), y + th / 2, p)
        p.color = C.GOOD
        c.drawRoundRect(RectF(x(green) - r, y - th / 2, width.toFloat(), y + th / 2), r, r, p)
        val mx = x(value).coerceIn(u.dpf(8f), width - u.dpf(8f))
        p.color = fg
        val tri = Path()
        tri.moveTo(mx - u.dpf(8f), u.dpf(2f))
        tri.lineTo(mx + u.dpf(8f), u.dpf(2f))
        tri.lineTo(mx, u.dpf(16f))
        tri.close()
        c.drawPath(tri, p)
        p.textAlign = Paint.Align.CENTER
        p.typeface = u.font(1)
        p.textSize = u.dpf(11f)
        p.color = C.MUTED
        val br = java.util.Locale("pt", "BR")
        c.drawText(String.format(br, "%.2f", yellow), x(yellow), y + u.dpf(26f), p)
        c.drawText(String.format(br, "%.2f", green), x(green), y + u.dpf(26f), p)
    }
}

private class MapView(ctx: Context, private val bg: Int, private val road: Int, private val route: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(bg)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = road
        p.strokeWidth = w * 0.035f
        for (i in 0..3) c.drawLine(-10f, h * (0.12f + 0.25f * i), w + 10f, h * (0.05f + 0.25f * i), p)
        for (i in 0..3) c.drawLine(w * (0.08f + 0.28f * i), -10f, w * (0.14f + 0.28f * i), h + 10f, p)
        p.color = route
        p.strokeWidth = w * 0.016f
        p.strokeJoin = Paint.Join.ROUND
        val path = Path()
        path.moveTo(w * 0.18f, h * 0.55f)
        path.lineTo(w * 0.28f, h * 0.42f)
        path.lineTo(w * 0.52f, h * 0.36f)
        path.lineTo(w * 0.64f, h * 0.22f)
        path.lineTo(w * 0.86f, h * 0.12f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#1B2A24")
        c.drawCircle(w * 0.18f, h * 0.55f, w * 0.022f, p)
        p.color = route
        c.drawCircle(w * 0.86f, h * 0.12f, w * 0.022f, p)
    }
}

// ------------------------------------------------------------------ as 10 telas iniciais

object HomeTemplates {
    fun build(ctx: Context, idx: Int, a: HomeActions): View {
        val b = B(ctx)
        return when (idx) {
            1 -> bento(b, a)
            2 -> recibo(b, a)
            3 -> brutal(b, a)
            4 -> tipografico(b, a)
            5 -> semaforo(b, a)
            6 -> trilho(b, a)
            7 -> cartas(b, a)
            8 -> turno(b, a)
            9 -> mapa(b, a)
            else -> cockpit(b, a)
        }
    }

    // 1 · Cockpit --------------------------------------------------------
    private fun cockpit(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val c = b.v().apply { setPadding(b.dp(22), b.dp(26), b.dp(22), b.dp(16)) }
        val top = b.h()
        top.addView(b.t("ROTA·COPILOTO", 14f, C.TEXT, 3), b.lp(0, weight = 1f))
        top.addView(readingChip(b, a, C.MUTED, C.PURPLE))
        c.addView(top)

        val frac = (an.perKm / (p.greenKm * 1.6)).toFloat()
        val g = GaugeView(b.ctx, frac, vcol(an), C.LINE, Metrics.value(an, "km"), "R$ por km · " + vname(an).lowercase(), "ÚLTIMA OFERTA", C.TEXT, C.MUTED)
        c.addView(g, LinearLayout.LayoutParams(b.dp(260), b.dp(260)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL
            it.topMargin = b.dp(22)
        })

        val row = b.h()
        fun stat(label: String, value: String, col: Int, last: Boolean) {
            val s = b.v().apply {
                background = b.rr(C.WHITE, 16)
                setPadding(b.dp(16), b.dp(12), b.dp(16), b.dp(12))
            }
            s.addView(b.t(label, 11f, C.MUTED, 1))
            s.addView(b.t(value, 22f, col, 3))
            row.addView(s, b.lp(0, weight = 1f, end = if (last) 0 else 12))
        }
        stat("R$/HORA", Metrics.value(an, "hora"), C.TEXT, false)
        stat("LUCRO", "R$ " + Metrics.value(an, "lucro"), Metrics.color(an, "lucro", p), true)
        c.addView(row, b.lp(top = 6))

        val on = p.enabled
        val big = b.h().apply {
            background = b.rr(if (on) C.PURPLE else C.LINE, 30)
            setPadding(b.dp(26), 0, b.dp(8), 0)
        }
        big.addView(b.t(if (on) "Copiloto ligado" else "Copiloto desligado", 17f, if (on) C.WHITE else C.TEXT, 3), b.lp(0, weight = 1f))
        big.addView(View(b.ctx).apply { background = b.oval(if (on) C.WHITE else C.MUTED) }, LinearLayout.LayoutParams(b.dp(44), b.dp(44)))
        big.setOnClickListener { a.setCopilot(!on) }
        c.addView(big, b.lp(h = b.dp(60), top = 16))

        val chips = b.h()
        fun chip(label: String, isOn: Boolean, wgt: Float, last: Boolean, f: () -> Unit) {
            val t = b.t(label, 14f, if (isOn) C.TEXT else C.MUTED, if (isOn) 2 else 1, true).apply {
                background = b.rr(Color.TRANSPARENT, 14, 1, if (isOn) C.PURPLE else C.LINE)
                setPadding(0, b.dp(14), 0, b.dp(14))
                setOnClickListener { f() }
            }
            chips.addView(t, b.lp(0, weight = wgt, end = if (last) 0 else 10))
        }
        chip("Uber", p.useUber, 1f, false) { a.apps() }
        chip("99", p.use99, 1f, false) { a.apps() }
        chip("Captura", p.saveShots, 1.4f, true) { a.setShots(!p.saveShots) }
        c.addView(chips, b.lp(top = 14))
        c.addView(spacer(b))
        c.addView(footerView(b, a))
        return scaffold(b, C.BG, c, dock(b, a, C.BG, C.MUTED, C.PURPLE))
    }

    // 2 · Bento pastel ---------------------------------------------------
    private fun bento(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val dark = Color.parseColor("#2B2622")
        val c = b.v().apply { setPadding(b.dp(16), b.dp(30), b.dp(16), b.dp(20)) }
        val hd = b.v().apply { setPadding(b.dp(6), 0, b.dp(6), b.dp(14)) }
        hd.addView(b.t("Bom turno.", 30f, C.TEXT, 3))
        hd.addView(readingChip(b, a, C.MUTED, C.PURPLE), b.lp(top = 6))
        c.addView(hd)

        fun row(vararg vs: View, h: Int) {
            val r = b.h()
            vs.forEachIndexed { i, v -> r.addView(v, b.lp(0, b.dp(h), weight = 1f, end = if (i < vs.size - 1) 10 else 0)) }
            c.addView(r, b.lp(top = 10))
        }

        fun tile(bgc: Int, fg: Int, label: String, value: String, f: () -> Unit): View {
            val t = b.v().apply {
                background = b.rr(bgc, 28)
                setPadding(b.dp(18), b.dp(16), b.dp(18), b.dp(16))
            }
            t.addView(b.t(label, 12f, fg, 1))
            t.addView(spacer(b))
            t.addView(b.t(value, 22f, fg, 3))
            t.setOnClickListener { f() }
            return t
        }

        val on = p.enabled
        val wide = b.h().apply {
            background = b.rr(C.PURPLE, 28)
            setPadding(b.dp(24), 0, b.dp(20), 0)
        }
        val wl = b.v()
        wl.addView(b.t("COPILOTO", 12f, C.WHITE, 1))
        wl.addView(b.t(if (on) "Ligado" else "Desligado", 22f, C.WHITE, 3))
        wide.addView(wl, b.lp(0, weight = 1f))
        wide.addView(b.sw(on, C.WHITE, C.LINE, C.PURPLE) { a.setCopilot(!on) })
        wide.setOnClickListener { a.setCopilot(!on) }
        c.addView(wide, b.lp(h = b.dp(92)))

        val q = b.h().apply {
            background = b.rr(Color.parseColor("#F4D58D"), 28)
            setPadding(b.dp(22), b.dp(16), b.dp(22), b.dp(16))
        }
        val ql = b.v()
        ql.addView(b.t("QUADRO NA TELA · EXEMPLO", 11f, Color.parseColor("#5A4A1B"), 1))
        ql.addView(b.t("R$ " + Metrics.value(an, "km") + "/km", 30f, dark, 3))
        ql.addView(b.t("R$ " + Metrics.value(an, "hora") + "/h · lucro R$ " + Metrics.value(an, "lucro"), 13f, Color.parseColor("#5A4A1B"), 1))
        q.addView(ql, b.lp(0, weight = 1f))
        q.addView(b.t(vname(an), 13f, onVerdict(an), 3, true).apply {
            background = b.oval(vcol(an))
            layoutParams = LinearLayout.LayoutParams(b.dp(62), b.dp(62))
        })
        c.addView(q, b.lp(top = 10))

        row(
            tile(if (p.useUber) Color.parseColor("#1F1F1F") else C.LINE, if (p.useUber) Color.parseColor("#F6EFE2") else C.MUTED, "APP", "Uber") { a.apps() },
            tile(if (p.use99) Color.parseColor("#FF8A5B") else C.LINE, if (p.use99) dark else C.MUTED, "APP", "99") { a.apps() },
            h = 112
        )
        row(
            tile(Color.parseColor("#CFE3F1"), dark, "Captura de tela", if (p.saveShots) "Ligada" else "Desligada") { a.setShots(!p.saveShots) },
            tile(Color.parseColor("#E8D5F2"), dark, "Configurar", "Metas e quadro") { a.config() },
            h = 100
        )
        row(
            tile(C.WHITE, C.TEXT, "Passo a passo", "Tutorial") { a.tutorial() },
            tile(C.WHITE, C.TEXT, "Para o celular", "Xiaomi") { a.xiaomi() },
            h = 84
        )
        c.addView(
            b.btn("Diagnóstico e log", C.MUTED, Color.TRANSPARENT, 28, 14f, 1, 2, C.MUTED) { a.diag() },
            b.lp(top = 10)
        )
        c.addView(spacer(b))
        c.addView(footerView(b, a))
        return scaffold(b, C.BG, c)
    }

    // 3 · Recibo ---------------------------------------------------------
    private fun recibo(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val outer = b.v().apply { setPadding(b.dp(16), b.dp(22), b.dp(16), b.dp(16)) }
        val paper = b.v().apply {
            background = b.rr(C.WHITE, 2)
            setPadding(b.dp(20), b.dp(24), b.dp(20), b.dp(20))
        }

        fun dashed(top: Int): View = View(b.ctx).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(b.dp(1), C.TEXT, b.dp(6).toFloat(), b.dp(4).toFloat())
            }
            layoutParams = b.lp(h = b.dp(2), top = top)
        }

        fun leader(label: String, value: View) {
            val r = b.h()
            r.addView(b.t(label, 14f, C.TEXT, 1))
            r.addView(View(b.ctx).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(b.dp(1), C.MUTED, b.dp(2).toFloat(), b.dp(3).toFloat())
                }
            }, b.lp(0, b.dp(2), weight = 1f, start = 6, end = 6, top = 10))
            r.addView(value)
            paper.addView(r, b.lp(top = 12))
        }

        paper.addView(b.t("ROTA COPILOTO", 20f, C.TEXT, 3, true))
        paper.addView(b.t("CALCULADORA DE CORRIDA · SÓ LÊ E CALCULA", 10f, C.MUTED, 0, true), b.lp(top = 4))
        paper.addView(dashed(14))

        leader("LEITURA", b.t(if (a.reading) "ATIVA" else "[ ATIVAR ]", 14f, C.TEXT, 3).tap { if (!a.reading) a.activate() })
        leader("COPILOTO", b.t(if (p.enabled) "[ LIGADO ]" else "[ DESLIGADO ]", 14f, C.TEXT, 3).tap { a.setCopilot(!p.enabled) })
        leader("UBER", b.t(if (p.useUber) "[X]" else "[ ]", 14f, C.TEXT, 3).tap { a.apps() })
        leader("99", b.t(if (p.use99) "[X]" else "[ ]", 14f, C.TEXT, 3).tap { a.apps() })
        leader("CAPTURA DE TELA", b.t(if (p.saveShots) "[X]" else "[ ]", 14f, C.TEXT, 3).tap { a.setShots(!p.saveShots) })
        paper.addView(dashed(16))

        paper.addView(b.t("ÚLTIMA OFERTA LIDA", 11f, C.MUTED, 1), b.lp(top = 14))
        val kmRow = b.h()
        kmRow.addView(b.t("R$ / KM", 15f, C.TEXT, 1), b.lp(0, weight = 1f))
        kmRow.addView(b.t(Metrics.value(an, "km"), 28f, C.TEXT, 3))
        paper.addView(kmRow, b.lp(top = 8))
        val hRow = b.h()
        hRow.addView(b.t("R$ / HORA", 15f, C.TEXT, 1), b.lp(0, weight = 1f))
        hRow.addView(b.t(Metrics.value(an, "hora"), 15f, C.TEXT, 3))
        paper.addView(hRow, b.lp(top = 6))
        val lRow = b.h()
        lRow.addView(b.t("LUCRO ESTIMADO", 15f, C.TEXT, 1), b.lp(0, weight = 1f))
        lRow.addView(b.t(Metrics.value(an, "lucro"), 15f, C.TEXT, 3))
        paper.addView(lRow, b.lp(top = 6))
        paper.addView(
            b.t("CLASSIFICAÇÃO: " + vname(an), 13f, onVerdict(an), 3).apply {
                background = b.rr(vcol(an), 2)
                setPadding(b.dp(10), b.dp(3), b.dp(10), b.dp(3))
            },
            LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = b.dp(10) }
        )
        paper.addView(dashed(16))

        paper.addView(b.btn("> CONFIGURAR", C.TEXT, Color.TRANSPARENT, 0, 14f, 3, 2, C.TEXT) { a.config() }, b.lp(top = 14))
        val two = b.h()
        two.addView(b.btn("TUTORIAL", C.TEXT, Color.TRANSPARENT, 0, 12f, 2, 2, C.TEXT) { a.tutorial() }, b.lp(0, weight = 1f, end = 8))
        two.addView(b.btn("AJUDA XIAOMI", C.TEXT, Color.TRANSPARENT, 0, 12f, 2, 2, C.TEXT) { a.xiaomi() }, b.lp(0, weight = 1f))
        paper.addView(two, b.lp(top = 8))
        paper.addView(b.btn("DIAGNÓSTICO / LOG", C.TEXT, Color.TRANSPARENT, 0, 12f, 2, 2, C.TEXT) { a.diag() }, b.lp(top = 8))
        outer.addView(paper)
        outer.addView(footerView(b, a))
        return scaffold(b, C.BG, outer)
    }

    // 4 · Neo-brutalista -------------------------------------------------
    private fun hard(b: B, inner: View, fill: Int, lp: LinearLayout.LayoutParams, off: Int = 5): View {
        val ink = Color.parseColor("#111111")
        val f = FrameLayout(b.ctx)
        f.addView(View(b.ctx).apply { setBackgroundColor(ink) }, FrameLayout.LayoutParams(MATCH, MATCH).also {
            it.setMargins(b.dp(off), b.dp(off), 0, 0)
        })
        inner.background = b.rr(fill, 0, 3, ink)
        f.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(0, 0, b.dp(off), b.dp(off)) })
        f.layoutParams = lp
        return f
    }

    private fun brutal(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val ink = Color.parseColor("#111111")
        val c = b.v().apply { setPadding(b.dp(18), b.dp(30), b.dp(18), b.dp(18)) }
        val head = b.h()
        head.addView(b.t("ROTA\nCOPILOTO", 28f, ink, 3), b.lp(0, weight = 1f))
        head.addView(
            b.t(if (a.reading) "LEITURA ON" else "ATIVAR", 12f, Color.parseColor("#FFE14D"), 3, true).apply {
                background = b.rr(ink, 0)
                setPadding(b.dp(12), b.dp(8), b.dp(12), b.dp(8))
                setOnClickListener { if (!a.reading) a.activate() }
            }
        )
        c.addView(head)

        val card = b.h().apply { setPadding(b.dp(16), b.dp(14), b.dp(16), b.dp(14)) }
        val cl = b.v()
        cl.addView(b.t("R$ POR KM", 12f, ink, 3))
        cl.addView(b.t(Metrics.value(an, "km"), 50f, ink, 3))
        card.addView(cl, b.lp(0, weight = 1f))
        val cr = b.v().apply { gravity = Gravity.END }
        cr.addView(b.t("R$ " + Metrics.value(an, "hora") + " / HORA", 14f, ink, 3))
        cr.addView(b.t("LUCRO R$ " + Metrics.value(an, "lucro"), 14f, ink, 3), b.lp(top = 4))
        cr.addView(
            b.t(vname(an), 13f, onVerdict(an), 3, true).apply {
                background = b.rr(vcol(an), 0, 3, ink)
                setPadding(b.dp(10), b.dp(2), b.dp(10), b.dp(2))
            },
            LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = b.dp(6) }
        )
        card.addView(cr)
        c.addView(hard(b, card, Color.WHITE, b.lp(top = 18), 7))

        val on = p.enabled
        val big = b.h().apply { setPadding(b.dp(18), 0, b.dp(18), 0) }
        big.addView(b.t("COPILOTO", 24f, ink, 3), b.lp(0, weight = 1f))
        big.addView(
            b.t(if (on) "LIGADO" else "DESLIGADO", 15f, Color.parseColor("#FF5C8A"), 3).apply {
                background = b.rr(ink, 0)
                setPadding(b.dp(10), b.dp(4), b.dp(10), b.dp(4))
            }
        )
        big.setOnClickListener { a.setCopilot(!on) }
        c.addView(hard(b, big.apply { minimumHeight = b.dp(72) }, Color.parseColor("#FF5C8A"), b.lp(top = 16), 7))

        fun small(label: String, fill: Int, fg: Int, last: Boolean, f: () -> Unit): View {
            val t = b.t(label, 14f, fg, 3, true).apply {
                setPadding(0, b.dp(15), 0, b.dp(15))
                setOnClickListener { f() }
            }
            return hard(b, t, fill, b.lp(0, weight = 1f, end = if (last) 0 else 8), 5)
        }
        val r1 = b.h()
        r1.addView(small(if (p.useUber) "UBER ✓" else "UBER", ink, Color.WHITE, false) { a.apps() })
        r1.addView(small(if (p.use99) "99 ✓" else "99", Color.parseColor("#FF8A3D"), ink, false) { a.apps() })
        r1.addView(small(if (p.saveShots) "CAPTURA ✓" else "CAPTURA", Color.WHITE, ink, true) { a.setShots(!p.saveShots) })
        c.addView(r1, b.lp(top = 16))

        c.addView(spacer(b))
        val cfg = b.t("CONFIGURAR →", 22f, Color.WHITE, 3).apply {
            setPadding(b.dp(18), b.dp(18), b.dp(18), b.dp(18))
            setOnClickListener { a.config() }
        }
        c.addView(hard(b, cfg, Color.parseColor("#4D7CFF"), b.lp(top = 18), 7))
        val r2 = b.h()
        r2.addView(small("TUTORIAL", Color.WHITE, ink, false) { a.tutorial() })
        r2.addView(small("XIAOMI", Color.WHITE, ink, false) { a.xiaomi() })
        r2.addView(small("DIAGNÓSTICO", Color.WHITE, ink, true) { a.diag() })
        c.addView(r2, b.lp(top = 16))
        c.addView(footerView(b, a))
        return scaffold(b, C.BG, c)
    }

    // 5 · Tipográfico ----------------------------------------------------
    private fun tipografico(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val c = b.v().apply { setPadding(b.dp(26), b.dp(34), b.dp(26), b.dp(22)) }
        val top = b.h()
        top.addView(b.t("Rota Copiloto", 12f, C.MUTED, 1), b.lp(0, weight = 1f))
        top.addView(readingChip(b, a, C.MUTED, C.PURPLE))
        c.addView(top)
        c.addView(b.t(Metrics.value(an, "km"), 88f, C.TEXT, 0), b.lp(top = 28))
        val v = b.h()
        v.addView(b.t("reais por km — ", 15f, C.TEXT, 1))
        v.addView(b.t(vname(an).lowercase(), 15f, C.PURPLE, 3))
        c.addView(v, b.lp(top = 6))
        c.addView(b.t("R$ " + Metrics.value(an, "hora") + " por hora · lucro estimado R$ " + Metrics.value(an, "lucro"), 13f, C.MUTED, 0), b.lp(top = 4))
        c.addView(View(b.ctx).apply { setBackgroundColor(C.TEXT) }, b.lp(h = b.dp(1), top = 26))

        fun row(title: String, value: String, vcolr: Int, f: () -> Unit) {
            val r = b.h().apply { setPadding(0, b.dp(14), 0, b.dp(14)) }
            r.addView(b.t(title, 26f, C.TEXT, 0), b.lp(0, weight = 1f))
            r.addView(b.t(value, 14f, vcolr, 2))
            r.setOnClickListener { f() }
            c.addView(r)
            c.addView(View(b.ctx).apply { setBackgroundColor(C.LINE) }, b.lp(h = b.dp(1)))
        }
        row("Copiloto", if (p.enabled) "Ligado" else "Desligado", if (p.enabled) C.PURPLE else C.MUTED) { a.setCopilot(!p.enabled) }
        val apps = listOfNotNull(if (p.useUber) "Uber" else null, if (p.use99) "99" else null).joinToString(" · ").ifEmpty { "Nenhum" }
        row("Apps", apps, C.MUTED) { a.apps() }
        row("Captura de tela", if (p.saveShots) "Ligada" else "Desligada", if (p.saveShots) C.PURPLE else C.MUTED) { a.setShots(!p.saveShots) }
        row("Configurar", "→", C.TEXT) { a.config() }
        c.addView(spacer(b))
        c.addView(View(b.ctx).apply { setBackgroundColor(C.TEXT) }, b.lp(h = b.dp(1), top = 24))
        val links = b.h().apply { setPadding(0, b.dp(14), 0, 0) }
        links.addView(b.t("Tutorial", 13f, C.TEXT, 2).tap { a.tutorial() }, b.lp(end = 22, w = WRAP))
        links.addView(b.t("Ajuda Xiaomi", 13f, C.TEXT, 2).tap { a.xiaomi() }, b.lp(end = 22, w = WRAP))
        links.addView(b.t("Diagnóstico", 13f, C.TEXT, 2).tap { a.diag() }, b.lp(w = WRAP))
        c.addView(links)
        c.addView(b.t("Só lê e calcula. Nunca aceita corrida.", 11f, C.MUTED, 0), b.lp(top = 12))
        c.addView(footerView(b, a))
        return scaffold(b, C.BG, c)
    }

    // 6 · Semáforo gigante -----------------------------------------------
    private fun semaforo(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val fg = onVerdict(an)
        val c = b.v()
        val r = b.dp(40).toFloat()
        val topBlock = b.v().apply {
            background = GradientDrawable().apply {
                setColor(vcol(an))
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
            setPadding(b.dp(24), b.dp(34), b.dp(24), b.dp(26))
        }
        val hd = b.h()
        hd.addView(b.t("ROTA COPILOTO", 12f, fg, 3), b.lp(0, weight = 1f))
        hd.addView(b.t(if (a.reading) "LEITURA ATIVA" else "ATIVAR LEITURA", 12f, fg, 3).tap { if (!a.reading) a.activate() })
        topBlock.addView(hd)
        topBlock.addView(b.t("Última oferta", 15f, fg, 2), b.lp(top = 28))
        topBlock.addView(b.t(Metrics.value(an, "km"), 88f, fg, 3))
        topBlock.addView(b.t("R$ por km · " + vname(an), 18f, fg, 2))
        topBlock.addView(b.t("R$ " + Metrics.value(an, "hora") + "/h   ·   Lucro R$ " + Metrics.value(an, "lucro"), 14f, fg, 2), b.lp(top = 14))
        c.addView(topBlock)

        val body = b.v().apply { setPadding(b.dp(22), b.dp(20), b.dp(22), b.dp(18)) }
        val on = p.enabled
        val cp = b.h().apply {
            background = b.rr(C.WHITE, 20)
            setPadding(b.dp(18), b.dp(16), b.dp(18), b.dp(16))
        }
        val cl = b.v()
        cl.addView(b.t("Copiloto", 16f, C.TEXT, 3))
        cl.addView(b.t("Mostra o quadro nas ofertas", 12f, C.MUTED, 0))
        cp.addView(cl, b.lp(0, weight = 1f))
        cp.addView(b.sw(on, C.PURPLE, C.LINE, C.WHITE) { a.setCopilot(!on) })
        body.addView(cp)

        val chips = b.h()
        fun chip(label: String, isOn: Boolean, wgt: Float, last: Boolean, f: () -> Unit) {
            chips.addView(
                b.btn(label, if (isOn) C.BG else C.MUTED, if (isOn) C.TEXT else Color.TRANSPARENT, 16, 14f, 3, if (isOn) 0 else 2, C.LINE) { f() },
                b.lp(0, weight = wgt, end = if (last) 0 else 10)
            )
        }
        chip("Uber", p.useUber, 1f, false) { a.apps() }
        chip("99", p.use99, 1f, false) { a.apps() }
        chip("Captura", p.saveShots, 1.3f, true) { a.setShots(!p.saveShots) }
        body.addView(chips, b.lp(top = 12))

        val cfg = b.h().apply {
            background = b.rr(C.WHITE, 18)
            setPadding(b.dp(18), b.dp(18), b.dp(18), b.dp(18))
        }
        cfg.addView(b.t("Configurar", 15f, C.TEXT, 3), b.lp(0, weight = 1f))
        cfg.addView(b.t("→", 18f, C.PURPLE, 3))
        cfg.setOnClickListener { a.config() }
        body.addView(cfg, b.lp(top = 12))
        body.addView(spacer(b))
        val three = b.h()
        three.addView(b.btn("Tutorial", C.MUTED, Color.TRANSPARENT, 14, 12f, 2, 2, C.LINE) { a.tutorial() }, b.lp(0, weight = 1f, end = 10))
        three.addView(b.btn("Ajuda Xiaomi", C.MUTED, Color.TRANSPARENT, 14, 12f, 2, 2, C.LINE) { a.xiaomi() }, b.lp(0, weight = 1f, end = 10))
        three.addView(b.btn("Diagnóstico", C.MUTED, Color.TRANSPARENT, 14, 12f, 2, 2, C.LINE) { a.diag() }, b.lp(0, weight = 1f))
        body.addView(three, b.lp(top = 18))
        body.addView(footerView(b, a))
        c.addView(body, b.lp(h = 0, weight = 1f))
        return scaffold(b, C.BG, c)
    }

    // 7 · Trilho lateral -------------------------------------------------
    private fun trilho(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val amber = Color.parseColor("#FCA311")
        val rail = b.v().apply {
            setBackgroundColor(C.PURPLE)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, b.dp(26), 0, b.dp(18))
        }
        rail.addView(
            b.t("R", 20f, C.PURPLE, 3, true).apply { background = b.rr(amber, 12) },
            LinearLayout.LayoutParams(b.dp(40), b.dp(40)).also { it.bottomMargin = b.dp(14) }
        )
        fun item(label: String, res: Int, active: Boolean, f: () -> Unit) {
            val col = b.v().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, b.dp(8), 0, b.dp(8))
                if (active) background = b.rr(Color.parseColor("#22345E"), 16)
                setOnClickListener { f() }
            }
            val tint = if (active) amber else Color.parseColor("#9FB0CF")
            col.addView(b.icon(res, tint))
            col.addView(b.t(label, 10f, tint, 2, true), b.lp(top = 3))
            rail.addView(col, b.lp(h = WRAP, top = 4, start = 8, end = 8))
        }
        item("Início", android.R.drawable.ic_menu_myplaces, true) {}
        item("Apps", android.R.drawable.ic_menu_agenda, false) { a.apps() }
        item("Metas", android.R.drawable.ic_menu_compass, false) { a.config() }
        item("Quadro", android.R.drawable.ic_menu_gallery, false) { a.config() }
        item("Tempo", android.R.drawable.ic_menu_recent_history, false) { a.config() }
        item("Captura", android.R.drawable.ic_menu_camera, false) { a.shots() }
        rail.addView(spacer(b))
        item("Ajuda", android.R.drawable.ic_menu_help, false) { a.help() }

        val c = b.v().apply { setPadding(b.dp(16), b.dp(28), b.dp(16), b.dp(18)) }
        c.addView(b.t("ROTA COPILOTO", 12f, C.MUTED, 2))
        c.addView(b.t("Início", 26f, C.TEXT, 3))
        c.addView(readingChip(b, a, C.MUTED, C.GOOD), b.lp(top = 8))

        val dark = b.v().apply {
            background = b.rr(C.PURPLE, 22)
            setPadding(b.dp(18), b.dp(16), b.dp(18), b.dp(16))
        }
        dark.addView(b.t("ÚLTIMA OFERTA", 11f, Color.parseColor("#9FB0CF"), 1))
        val big = b.h()
        big.addView(b.t(Metrics.value(an, "km"), 42f, Color.WHITE, 3))
        big.addView(b.t(" R$/km", 13f, Color.parseColor("#9FB0CF"), 2), b.lp(w = WRAP, start = 4))
        dark.addView(big)
        val mid = b.h()
        mid.addView(b.t("R$ " + Metrics.value(an, "hora") + "/h", 13f, Color.WHITE, 2), b.lp(0, weight = 1f))
        mid.addView(b.t("Lucro " + Metrics.value(an, "lucro"), 13f, amber, 3))
        dark.addView(mid, b.lp(top = 8))
        dark.addView(View(b.ctx).apply { background = b.rr(vcol(an), 4) }, b.lp(h = b.dp(8), top = 12))
        c.addView(dark, b.lp(top = 16))

        fun card(title: String, sub: String, on: Boolean?, f: () -> Unit) {
            val r = b.h().apply {
                background = b.rr(C.WHITE, 18)
                setPadding(b.dp(16), b.dp(14), b.dp(16), b.dp(14))
                setOnClickListener { f() }
            }
            val l = b.v()
            l.addView(b.t(title, 15f, C.TEXT, 3))
            l.addView(b.t(sub, 12f, C.MUTED, 0))
            r.addView(l, b.lp(0, weight = 1f))
            if (on != null) r.addView(b.sw(on, amber, C.LINE, C.WHITE) { f() })
            c.addView(r, b.lp(top = 12))
        }
        card("Copiloto", if (p.enabled) "Ligado" else "Desligado", p.enabled) { a.setCopilot(!p.enabled) }
        card("Apps lidos", listOfNotNull(if (p.useUber) "Uber" else null, if (p.use99) "99" else null).joinToString(" · ").ifEmpty { "Nenhum" }, null) { a.apps() }
        card("Captura de tela", if (p.saveShots) "Ligada" else "Desligada", p.saveShots) { a.setShots(!p.saveShots) }
        c.addView(spacer(b))
        val links = b.h().apply { setPadding(0, b.dp(16), 0, 0) }
        links.addView(b.t("Tutorial", 12f, C.PURPLE, 3).tap { a.tutorial() }, b.lp(0, weight = 1f))
        links.addView(b.t("Ajuda Xiaomi", 12f, C.PURPLE, 3).tap { a.xiaomi() }, b.lp(0, weight = 1f))
        links.addView(b.t("Diagnóstico", 12f, C.PURPLE, 3).tap { a.diag() }, b.lp(0, weight = 1f))
        c.addView(links)
        c.addView(footerView(b, a))

        val root = b.h().apply { setBackgroundColor(C.BG) }
        root.addView(rail, b.lp(b.dp(76), MATCH))
        val sv = ScrollView(b.ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        sv.addView(c, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(sv, b.lp(0, MATCH, weight = 1f))
        return root
    }

    // 8 · Cartas deslizantes ---------------------------------------------
    private fun cartas(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val cream = Color.parseColor("#F6EFE2")
        val ink = Color.parseColor("#10282D")
        val c = b.v().apply { setPadding(0, b.dp(30), 0, b.dp(18)) }
        val hd = b.h().apply { setPadding(b.dp(24), 0, b.dp(24), 0) }
        val hl = b.v()
        hl.addView(b.t("ROTA COPILOTO", 13f, C.MUTED, 2))
        hl.addView(readingChip(b, a, C.PURPLE, C.PURPLE), b.lp(top = 4))
        hd.addView(hl, b.lp(0, weight = 1f))
        hd.addView(
            b.icon(android.R.drawable.ic_menu_preferences, C.TEXT, 24).apply {
                setPadding(b.dp(10), b.dp(10), b.dp(10), b.dp(10))
                background = b.oval(C.LINE)
                setOnClickListener { a.config() }
            }.also { it.layoutParams = LinearLayout.LayoutParams(b.dp(46), b.dp(46)) }
        )
        c.addView(hd)

        val track = b.h().apply { setPadding(b.dp(24), 0, b.dp(10), 0) }
        fun card(bgc: Int): LinearLayout {
            val k = b.v().apply {
                background = b.rr(bgc, 32)
                setPadding(b.dp(24), b.dp(24), b.dp(24), b.dp(22))
            }
            track.addView(k, b.lp(b.dp(300), b.dp(380), end = 14))
            return k
        }

        val k1 = card(cream)
        k1.addView(b.t("CÁLCULO DE GANHOS", 12f, Color.parseColor("#587077"), 2))
        k1.addView(b.t(Metrics.value(an, "km"), 66f, ink, 3), b.lp(top = 12))
        k1.addView(b.t("reais por km", 16f, Color.parseColor("#587077"), 1))
        val mini = b.h()
        fun m(label: String, value: String, last: Boolean) {
            val t = b.v().apply {
                background = b.rr(Color.parseColor("#E4D9C3"), 18)
                setPadding(b.dp(12), b.dp(10), b.dp(12), b.dp(10))
            }
            t.addView(b.t(label, 11f, Color.parseColor("#587077"), 1))
            t.addView(b.t(value, 20f, ink, 3))
            mini.addView(t, b.lp(0, weight = 1f, end = if (last) 0 else 10))
        }
        m("POR HORA", "R$ " + Metrics.value(an, "hora"), false)
        m("LUCRO", "R$ " + Metrics.value(an, "lucro"), true)
        k1.addView(mini, b.lp(top = 20))
        k1.addView(spacer(b))
        val chips = b.h()
        chips.addView(b.t(if (p.useUber) "Uber" else "Uber (off)", 13f, cream, 3, true).apply {
            background = b.rr(ink, 16)
            setPadding(b.dp(14), b.dp(7), b.dp(14), b.dp(7))
            setOnClickListener { a.apps() }
        }, b.lp(w = WRAP, end = 8))
        chips.addView(b.t(if (p.use99) "99" else "99 (off)", 13f, cream, 3, true).apply {
            background = b.rr(ink, 16)
            setPadding(b.dp(14), b.dp(7), b.dp(14), b.dp(7))
            setOnClickListener { a.apps() }
        }, b.lp(w = WRAP))
        chips.addView(View(b.ctx), b.lp(0, 1, weight = 1f))
        chips.addView(b.t(vname(an).lowercase().replaceFirstChar { it.uppercase() }, 13f, onVerdict(an), 3, true).apply {
            background = b.rr(vcol(an), 16)
            setPadding(b.dp(14), b.dp(7), b.dp(14), b.dp(7))
        })
        k1.addView(chips)

        val k2 = card(C.PURPLE_LIGHT)
        k2.addView(b.t("CAPTURA DE TELA", 12f, C.MUTED, 2))
        k2.addView(b.t("Salva cada oferta na galeria", 24f, C.TEXT, 3), b.lp(top = 14))
        k2.addView(spacer(b))
        val cr = b.h()
        cr.addView(b.t(if (p.saveShots) "Ligada" else "Desligada", 16f, C.TEXT, 2), b.lp(0, weight = 1f))
        cr.addView(b.sw(p.saveShots, C.PURPLE, C.LINE, C.WHITE) { a.setShots(!p.saveShots) })
        k2.addView(cr)
        k2.addView(b.btn("Ajustes da captura", C.PURPLE, Color.TRANSPARENT, 16, 14f, 2, 2, C.PURPLE) { a.shots() }, b.lp(top = 14))

        val k3 = card(C.WHITE)
        k3.addView(b.t("FERRAMENTAS", 12f, C.MUTED, 2))
        k3.addView(b.btn("Configurar", C.WHITE, C.PURPLE, 18) { a.config() }, b.lp(top = 14))
        k3.addView(b.btn("Tutorial guiado", C.PURPLE, C.PURPLE_LIGHT, 18) { a.tutorial() }, b.lp(top = 10))
        k3.addView(b.btn("Ajuda Xiaomi", C.PURPLE, C.PURPLE_LIGHT, 18) { a.xiaomi() }, b.lp(top = 10))
        k3.addView(b.btn("Diagnóstico", C.PURPLE, C.PURPLE_LIGHT, 18) { a.diag() }, b.lp(top = 10))

        val hs = HorizontalScrollView(b.ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(track)
        }
        c.addView(hs, b.lp(top = 26))

        val dots = b.h().apply { gravity = Gravity.CENTER }
        val dotViews = (0..2).map { View(b.ctx) }
        fun paintDots(sel: Int) {
            dotViews.forEachIndexed { i, d ->
                d.background = b.rr(if (i == sel) C.TEXT else C.LINE, 4)
                d.layoutParams = LinearLayout.LayoutParams(b.dp(if (i == sel) 26 else 8), b.dp(8)).also { it.marginEnd = b.dp(8) }
            }
        }
        paintDots(0)
        dotViews.forEach { dots.addView(it) }
        hs.setOnScrollChangeListener { _, sx, _, _, _ -> paintDots(((sx + b.dp(150)) / b.dp(314)).coerceIn(0, 2)) }
        c.addView(dots, b.lp(top = 16))
        c.addView(spacer(b))

        val on = p.enabled
        val big = b.h().apply {
            background = b.rr(if (on) C.PURPLE else C.LINE, 32)
            setPadding(b.dp(28), 0, b.dp(8), 0)
        }
        big.addView(b.t(if (on) "Copiloto ligado" else "Copiloto desligado", 18f, if (on) C.WHITE else C.TEXT, 3), b.lp(0, weight = 1f))
        big.addView(View(b.ctx).apply { background = b.oval(if (on) C.WHITE else C.MUTED) }, LinearLayout.LayoutParams(b.dp(48), b.dp(48)))
        big.setOnClickListener { a.setCopilot(!on) }
        c.addView(big, b.lp(h = b.dp(64), top = 18, start = 24, end = 24))
        c.addView(footerView(b, a))
        return scaffold(b, C.BG, c)
    }

    // 9 · Painel do turno ------------------------------------------------
    private fun turno(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val c = b.v().apply { setPadding(b.dp(20), b.dp(30), b.dp(20), b.dp(16)) }
        val hd = b.h()
        val tl = b.h()
        tl.addView(b.t("Rota", 18f, C.TEXT, 3))
        tl.addView(b.t(" Copiloto", 18f, C.PURPLE, 3))
        hd.addView(tl, b.lp(0, weight = 1f))
        fun app(label: String, on: Boolean) = b.t(label, 12f, if (on) C.BG else C.MUTED, 3, true).apply {
            background = b.rr(if (on) C.PURPLE else C.LINE, 14)
            setPadding(b.dp(12), b.dp(6), b.dp(12), b.dp(6))
            setOnClickListener { a.apps() }
        }
        hd.addView(app("Uber", p.useUber), b.lp(w = WRAP, end = 6))
        hd.addView(app("99", p.use99), b.lp(w = WRAP))
        c.addView(hd)
        c.addView(readingChip(b, a, C.MUTED, C.PURPLE), b.lp(top = 8))

        val card = b.v().apply {
            background = b.rr(C.WHITE, 24)
            setPadding(b.dp(18), b.dp(16), b.dp(18), b.dp(12))
        }
        val tr = b.h().apply { gravity = Gravity.TOP }
        val tl2 = b.v()
        tl2.addView(b.t("Última oferta · R$/km", 12f, C.MUTED, 1))
        tl2.addView(b.t(Metrics.value(an, "km"), 44f, C.TEXT, 3))
        tr.addView(tl2, b.lp(0, weight = 1f))
        val rr = b.v().apply { gravity = Gravity.END }
        rr.addView(b.t("R$ " + Metrics.value(an, "hora") + " / hora", 13f, C.TEXT, 2))
        rr.addView(b.t("Lucro R$ " + Metrics.value(an, "lucro"), 13f, C.PURPLE, 3), b.lp(top = 4))
        rr.addView(b.t(vname(an), 12f, onVerdict(an), 3, true).apply {
            background = b.rr(vcol(an), 10)
            setPadding(b.dp(10), b.dp(3), b.dp(10), b.dp(3))
        }, LinearLayout.LayoutParams(WRAP, WRAP).also { it.topMargin = b.dp(6) })
        tr.addView(rr)
        card.addView(tr)
        card.addView(ZoneBar(b.ctx, an.perKm, p.yellowKm, p.greenKm, p.greenKm * 1.8, C.TEXT), b.lp(top = 14))
        card.addView(b.t("Suas metas de R$/km: abaixo é ruim, acima é bom.", 11f, C.MUTED, 0))
        c.addView(card, b.lp(top = 16))

        val on = p.enabled
        val r = b.h()
        fun tile(label: String, value: String, hot: Boolean, last: Boolean, f: () -> Unit) {
            val t = b.v().apply {
                background = b.rr(if (hot) C.PURPLE else C.WHITE, 22)
                setPadding(b.dp(16), b.dp(14), b.dp(16), b.dp(14))
                setOnClickListener { f() }
            }
            t.addView(b.t(label, 12f, if (hot) C.BG else C.MUTED, 1))
            t.addView(spacer(b))
            t.addView(b.t(value, 20f, if (hot) C.BG else C.TEXT, 3))
            r.addView(t, b.lp(0, b.dp(84), weight = 1f, end = if (last) 0 else 10))
        }
        tile("Copiloto", if (on) "Ligado" else "Desligado", on, false) { a.setCopilot(!on) }
        tile("Captura de tela", if (p.saveShots) "Ligada" else "Desligada", p.saveShots, true) { a.setShots(!p.saveShots) }
        c.addView(r, b.lp(top = 12))
        val cfg = b.h().apply {
            background = b.rr(C.WHITE, 20)
            setPadding(b.dp(20), b.dp(18), b.dp(20), b.dp(18))
            setOnClickListener { a.config() }
        }
        cfg.addView(b.t("Metas, custo e quadro", 15f, C.TEXT, 3), b.lp(0, weight = 1f))
        cfg.addView(b.t("Configurar →", 14f, C.PURPLE, 3))
        c.addView(cfg, b.lp(top = 12))
        c.addView(spacer(b))
        c.addView(footerView(b, a))

        val nav = b.h().apply {
            background = GradientDrawable().apply {
                setColor(C.WHITE)
                val rd = b.dp(26).toFloat()
                cornerRadii = floatArrayOf(rd, rd, rd, rd, 0f, 0f, 0f, 0f)
            }
            setPadding(b.dp(24), b.dp(16), b.dp(24), b.dp(18))
        }
        nav.addView(b.t("Painel", 12f, C.PURPLE, 3, true), b.lp(0, weight = 1f))
        nav.addView(b.t("Tutorial", 12f, C.MUTED, 2, true).tap { a.tutorial() }, b.lp(0, weight = 1f))
        nav.addView(b.t("Ajuda Xiaomi", 12f, C.MUTED, 2, true).tap { a.xiaomi() }, b.lp(0, weight = 1f))
        nav.addView(b.t("Diagnóstico", 12f, C.MUTED, 2, true).tap { a.diag() }, b.lp(0, weight = 1f))
        return scaffold(b, C.BG, c, nav)
    }

    // 10 · Mapa e gaveta -------------------------------------------------
    private fun mapa(b: B, a: HomeActions): View {
        val an = a.a
        val p = a.prefs
        val ink = Color.parseColor("#1B2A24")
        val root = FrameLayout(b.ctx).apply { setBackgroundColor(C.BG) }
        root.addView(MapView(b.ctx, C.BG, Color.WHITE, C.PURPLE), FrameLayout.LayoutParams(MATCH, MATCH))

        val top = b.h().apply { setPadding(b.dp(16), b.dp(30), b.dp(16), 0) }
        top.addView(
            b.t("Rota Copiloto", 14f, ink, 3).apply {
                background = b.rr(Color.WHITE, 22)
                setPadding(b.dp(16), b.dp(10), b.dp(16), b.dp(10))
            }
        )
        top.addView(View(b.ctx), b.lp(0, 1, weight = 1f))
        top.addView(
            b.t(if (a.reading) "● Leitura ativa" else "● Ativar leitura", 12f, Color.WHITE, 2).apply {
                background = b.rr(ink, 22)
                setPadding(b.dp(16), b.dp(10), b.dp(16), b.dp(10))
                setOnClickListener { if (!a.reading) a.activate() }
            }
        )
        val topWrap = b.v()
        topWrap.addView(top)
        val q = b.h().apply {
            background = b.rr(Color.WHITE, 22)
            elevation = b.ui.dpf(8f)
            setPadding(b.dp(18), b.dp(14), b.dp(18), b.dp(14))
        }
        val ql = b.v()
        ql.addView(b.t("QUADRO NA OFERTA", 11f, Color.parseColor("#5A6C63"), 2))
        ql.addView(b.t("R$ " + Metrics.value(an, "km") + "/km", 32f, ink, 3))
        ql.addView(b.t("R$ " + Metrics.value(an, "hora") + "/h · lucro R$ " + Metrics.value(an, "lucro"), 12f, Color.parseColor("#5A6C63"), 2))
        q.addView(ql, b.lp(0, weight = 1f))
        q.addView(b.t(vname(an), 14f, onVerdict(an), 3, true).apply {
            background = b.rr(vcol(an), 16)
            setPadding(b.dp(14), b.dp(10), b.dp(14), b.dp(10))
        })
        topWrap.addView(q, b.lp(top = 14, start = 34, end = 34))
        root.addView(topWrap, FrameLayout.LayoutParams(MATCH, WRAP))

        val sheet = b.v().apply {
            background = GradientDrawable().apply {
                setColor(C.WHITE)
                val rd = b.dp(32).toFloat()
                cornerRadii = floatArrayOf(rd, rd, rd, rd, 0f, 0f, 0f, 0f)
            }
            elevation = b.ui.dpf(12f)
            setPadding(b.dp(22), b.dp(12), b.dp(22), b.dp(18))
        }
        sheet.addView(View(b.ctx).apply { background = b.rr(C.LINE, 3) }, LinearLayout.LayoutParams(b.dp(44), b.dp(5)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val on = p.enabled
        val cp = b.h()
        val cl = b.v()
        cl.addView(b.t("Copiloto", 19f, C.TEXT, 3))
        cl.addView(b.t("Lê Uber e 99 e calcula", 13f, C.MUTED, 0))
        cp.addView(cl, b.lp(0, weight = 1f))
        cp.addView(b.sw(on, C.PURPLE, C.LINE, C.WHITE) { a.setCopilot(!on) })
        sheet.addView(cp, b.lp(top = 14))
        val chips = b.h()
        fun chip(label: String, isOn: Boolean, wgt: Float, last: Boolean, f: () -> Unit) {
            chips.addView(
                b.btn(label, if (isOn) C.WHITE else C.MUTED, if (isOn) ink else Color.TRANSPARENT, 14, 14f, 3, if (isOn) 0 else 2, C.LINE) { f() },
                b.lp(0, weight = wgt, end = if (last) 0 else 10)
            )
        }
        chip("Uber", p.useUber, 1f, false) { a.apps() }
        chip("99", p.use99, 1f, false) { a.apps() }
        chip("Captura", p.saveShots, 1.4f, true) { a.setShots(!p.saveShots) }
        sheet.addView(chips, b.lp(top = 14))
        sheet.addView(b.btn("Configurar  →", C.TEXT, C.PURPLE_LIGHT, 16, 15f, 3) { a.config() }, b.lp(top = 12))
        val three = b.h()
        three.addView(b.btn("Tutorial", C.TEXT, C.PURPLE_LIGHT, 14, 12f, 2) { a.tutorial() }, b.lp(0, weight = 1f, end = 10))
        three.addView(b.btn("Ajuda Xiaomi", C.TEXT, C.PURPLE_LIGHT, 14, 12f, 2) { a.xiaomi() }, b.lp(0, weight = 1f, end = 10))
        three.addView(b.btn("Diagnóstico", C.TEXT, C.PURPLE_LIGHT, 14, 12f, 2) { a.diag() }, b.lp(0, weight = 1f))
        sheet.addView(three, b.lp(top = 12))
        sheet.addView(footerView(b, a))
        root.addView(sheet, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        return root
    }
}
