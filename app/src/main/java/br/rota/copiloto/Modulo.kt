package br.rota.copiloto

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Telas do Diário de Ganhos, do Mapa de calor e da Agenda.
 * Botões grandes e poucos toques: é para usar dirigindo (parado).
 */
class Modulo(
    private val ctx: Context,
    private val ui: Ui,
    private val prefs: Prefs,
    private val refazer: () -> Unit,
    private val exportar: (formato: String) -> Unit = {},
    private val importar: () -> Unit = {},
    private val pedirLocalizacao: () -> Unit = {},
    private val pedirAlarmes: () -> Unit = {}
) {
    private val br = Locale("pt", "BR")
    private fun dp(v: Int) = ui.dp(v)
    private fun rs(v: Double) = String.format(br, "R$ %.2f", v)
    private fun n2(v: Double) = String.format(br, "%.2f", v)
    private fun hm(min: Int) = "${min / 60}h${String.format(br, "%02d", min % 60)}"
    private val fHora = SimpleDateFormat("HH:mm", br)
    private val fDia = SimpleDateFormat("EEE dd/MM", br)
    private val fDiaHora = SimpleDateFormat("EEE dd/MM HH:mm", br)

    // ------------------------------------------------------------------ peças

    private fun titulo(t: String, top: Int = 22): View = ui.tv(t.uppercase(br), 13f, C.MUTED, 2).apply {
        layoutParams = ui.lp(top = top); letterSpacing = 0.08f
    }

    private fun botaoGrande(texto: String, cheio: Boolean, cor: Int = C.PURPLE, f: () -> Unit): TextView =
        ui.tv(texto, 17f, if (cheio) C.BG else C.TEXT, 3, true).apply {
            background = if (cheio) ui.round(cor, 16) else ui.round(C.WHITE, 16, 2, cor)
            minHeight = dp(60)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { f() }
        }

    private fun numero(rotulo: String, valor: String, cor: Int = C.TEXT): View {
        val b = ui.vbox().apply { background = ui.round(C.BG, 14); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        b.addView(ui.tv(rotulo, 12f, C.MUTED, 1))
        b.addView(ui.tv(valor, 20f, cor, 3))
        return b
    }

    private fun grade2(vararg vs: View): View {
        val col = ui.vbox()
        vs.toList().chunked(2).forEachIndexed { i, par ->
            val r = ui.hbox()
            par.forEachIndexed { j, v -> r.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { if (j == 1) it.marginStart = dp(8) }) }
            if (par.size == 1) r.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).also { it.marginStart = dp(8) })
            col.addView(r, ui.lp(top = if (i == 0) 0 else 8))
        }
        return col
    }

    private fun barra(frac: Float, cor: Int): View {
        val trilho = FrameLayout(ctx).apply { background = ui.round(C.LINE, 6) }
        val l = LinearLayout(ctx).apply { weightSum = 1f }
        l.addView(View(ctx).apply { background = ui.round(cor, 6) }, LinearLayout.LayoutParams(0, dp(12), frac.coerceIn(0f, 1f)))
        trilho.addView(l, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(12)))
        return trilho
    }

    private fun corPorHora(v: Double, ref: Double): Int = when {
        v < 0 -> C.LINE
        v >= ref * 1.15 -> C.GOOD
        v >= ref * 0.85 -> C.MID
        else -> C.BAD
    }

    // ------------------------------------------------------------------ DIÁRIO

    fun diario(): View {
        Diario.carregar(ctx)
        val c = ui.vbox()
        val agora = System.currentTimeMillis()

        // online / pausa
        val est = Diario.estado
        val st = when (est) {
            "online" -> "Online há ${hm(((agora - Diario.estadoDesde) / 60000).toInt())}"
            "pausa" -> "Em pausa há ${hm(((agora - Diario.estadoDesde) / 60000).toInt())}"
            else -> "Offline. Toque ONLINE quando começar a rodar."
        }
        c.addView(ui.tv(st, 16f, C.TEXT, 2))
        val bts = ui.hbox()
        listOf(Triple("ONLINE", "online", C.GOOD), Triple("PAUSA", "pausa", C.MID), Triple("ENCERRAR", "offline", C.BAD)).forEachIndexed { i, (t, e, cor) ->
            bts.addView(botaoGrande(t, est == e, cor) { Diario.mudarEstado(ctx, e); refazer() },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { if (i > 0) it.marginStart = dp(8) })
        }
        c.addView(bts, ui.lp(top = 10))

        // resumo do dia
        val r = Diario.resumo(ctx, prefs)
        c.addView(titulo("Resumo de hoje"))
        val card = ui.card()
        card.addView(ui.tv("Total recebido", 13f, C.MUTED, 1))
        card.addView(ui.tv(rs(r.total), 38f, C.PURPLE, 3))
        card.addView(ui.tv("${r.corridas} corrida(s)" + if (r.pendentes > 0) " · ${r.pendentes} para confirmar" else "", 13f, C.MUTED, 1), ui.lp(top = 2))
        card.addView(grade2(
            numero("R$ por hora", if (r.minOnline > 0) rs(r.porHora) else "–"),
            numero("R$ por km", if (r.km > 0) rs(r.porKm) else "–"),
            numero("Tempo ocioso", hm(r.minOcioso), if (r.minOnline > 0 && r.minOcioso > r.minOnline / 2) C.BAD else C.TEXT),
            numero("Lucro líquido", rs(r.lucro), if (r.lucro >= 0) C.GOOD else C.BAD),
            numero("Online", hm(r.minOnline)),
            numero("Pausa", hm(r.minPausa))
        ), ui.lp(top = 12))
        card.addView(ui.tv("Lucro = recebido − ${String.format(br, "%.1f", r.km)} km × ${rs(prefs.costPerKm)}/km (seu custo em Ajustes).", 12f, C.MUTED, 0), ui.lp(top = 10))
        c.addView(card, ui.lp(top = 8))

        // localização
        val locOk = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!locOk) {
            val l = ui.card()
            l.addView(ui.tv("Permitir localização", 16f, C.TEXT, 2))
            l.addView(ui.tv("Quando a oferta não mostra o bairro do embarque, o app usa o GPS para descobrir.", 13f, C.MUTED, 0), ui.lp(top = 4))
            l.addView(ui.button("Permitir") { pedirLocalizacao() })
            c.addView(l, ui.lp(top = 12))
        }

        // pendentes
        val ini = Diario.inicioDoDia(agora)
        val todas = Diario.corridas.filter { it.ts >= ini - 2 * 86_400_000L }.sortedByDescending { it.ts }
        val pend = todas.filter { !it.confirmada }
        if (pend.isNotEmpty()) {
            c.addView(titulo("Confirme o valor final"))
            pend.forEach { co ->
                val k = ui.card()
                k.addView(ui.tv("${co.plataforma} · ${fDiaHora.format(Date(co.ts))}", 13f, C.MUTED, 1))
                k.addView(ui.tv("${co.embarque.ifEmpty { "?" }} → ${co.destino.ifEmpty { "?" }}", 16f, C.TEXT, 2), ui.lp(top = 2))
                k.addView(ui.tv("${String.format(br, "%.1f", co.km)} km · ${co.min} min · esperou ${co.esperaMin} min", 13f, C.MUTED, 0), ui.lp(top = 2))
                k.addView(botaoGrande("CONFIRMAR ${rs(co.valor)}", true) { Diario.confirmar(ctx, co.id); refazer() }, ui.lp(top = 10))
                val linha = ui.hbox()
                linha.addView(botaoGrande("Outro valor", false) { editarCorrida(co) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                linha.addView(botaoGrande("Não peguei", false, C.BAD) { Diario.apagarCorrida(ctx, co.id); refazer() },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) })
                k.addView(linha, ui.lp(top = 8))
                c.addView(k, ui.lp(top = 8))
            }
        }

        c.addView(botaoGrande("+ Registrar corrida à mão", false) { editarCorrida(null) }, ui.lp(top = 16))

        // corridas de hoje
        val hoje = todas.filter { it.confirmada && it.ts >= ini }
        c.addView(titulo("Corridas de hoje"))
        if (hoje.isEmpty()) c.addView(ui.tv("Toque \"Peguei\" no semáforo depois de aceitar uma corrida: ela aparece aqui.", 14f, C.MUTED, 0), ui.lp(top = 6))
        hoje.forEach { co ->
            val l = ui.hbox().apply { background = ui.round(C.WHITE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { editarCorrida(co) } }
            val m = ui.vbox()
            m.addView(ui.tv("${co.embarque.ifEmpty { "?" }} → ${co.destino.ifEmpty { "?" }}", 15f, C.TEXT, 2))
            m.addView(ui.tv("${fHora.format(Date(co.ts))} · ${co.plataforma} · ${String.format(br, "%.1f", co.km)} km · ${co.min} min", 12f, C.MUTED, 0))
            l.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l.addView(ui.tv(rs(co.valor), 17f, C.TEXT, 3))
            c.addView(l, ui.lp(top = 8))
        }

        // dias anteriores
        val dias = Diario.corridas.map { Diario.inicioDoDia(it.ts) }.distinct().filter { it < ini }.sortedDescending().take(7)
        if (dias.isNotEmpty()) {
            c.addView(titulo("Dias anteriores"))
            dias.forEach { d ->
                val rd = Diario.resumo(ctx, prefs, d, d + 86_399_999L)
                val l = ui.hbox().apply { background = ui.round(C.WHITE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) }
                l.addView(ui.tv(fDia.format(Date(d)), 15f, C.TEXT, 2), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                l.addView(ui.tv("${rd.corridas} · ${rs(rd.total)} · ${if (rd.minOnline > 0) rs(rd.porHora) + "/h" else "–"}", 14f, C.MUTED, 1))
                c.addView(l, ui.lp(top = 8))
            }
        }

        // backup
        c.addView(titulo("Backup (fica só no seu celular)"))
        val bk = ui.hbox()
        bk.addView(botaoGrande("Exportar CSV", false) { exportar("csv") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bk.addView(botaoGrande("Exportar JSON", false) { exportar("json") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) })
        c.addView(bk, ui.lp(top = 8))
        c.addView(botaoGrande("Importar backup (CSV ou JSON)", false) { importar() }, ui.lp(top = 8))
        c.addView(ui.tv("Importar junta com o que já existe: nada é apagado.", 12f, C.MUTED, 0), ui.lp(top = 6))
        return c
    }

    private fun campo(rotulo: String, valor: String, numerico: Boolean): Pair<View, EditText> {
        val b = ui.vbox()
        b.addView(ui.tv(rotulo, 13f, C.MUTED, 1))
        val e = EditText(ctx).apply {
            setText(valor); textSize = 18f; setTextColor(C.TEXT)
            inputType = if (numerico) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            minHeight = dp(52)
        }
        b.addView(e)
        return b to e
    }

    private fun campoBairro(rotulo: String, valor: String): Pair<View, EditText> {
        val b = ui.vbox()
        b.addView(ui.tv(rotulo, 13f, C.MUTED, 1))
        val e = AutoCompleteTextView(ctx).apply {
            setText(valor); textSize = 18f; setTextColor(C.TEXT); threshold = 2; minHeight = dp(52)
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, Destino.nomesBairros))
        }
        b.addView(e)
        return b to e
    }

    private fun num(e: EditText): Double? = e.text.toString().trim().replace("R$", "").trim().let {
        if (it.contains(",")) it.replace(".", "").replace(",", ".") else it
    }.toDoubleOrNull()

    private fun editarCorrida(co: Diario.Corrida?) {
        val f = ui.vbox().apply { setPadding(dp(20), dp(8), dp(20), dp(0)) }
        val (v1, valor) = campo("Valor recebido (R$)", co?.let { n2(it.valor) } ?: "", true)
        val (v2, km) = campo("Km (embarque + corrida)", co?.let { String.format(br, "%.1f", it.km) } ?: "", true)
        val (v3, min) = campo("Minutos da corrida", co?.min?.toString() ?: "", true)
        val (v4, emb) = campoBairro("Bairro de embarque", co?.embarque ?: (Alarmes.bairroAqui(ctx) ?: ""))
        val (v5, dst) = campoBairro("Bairro de destino", co?.destino ?: "")
        listOf(v1, v2, v3, v4, v5).forEach { f.addView(it, ui.lp(top = 8)) }
        val d = AlertDialog.Builder(ctx)
            .setTitle(if (co == null) "Registrar corrida" else "Corrida")
            .setView(android.widget.ScrollView(ctx).apply { addView(f) })
            .setPositiveButton("Salvar") { _, _ ->
                val agora = System.currentTimeMillis()
                val novo = Diario.Corrida(
                    id = co?.id ?: agora, ts = co?.ts ?: agora, plataforma = co?.plataforma ?: "99",
                    embarque = emb.text.toString().trim(), destino = dst.text.toString().trim(),
                    valor = num(valor) ?: co?.valor ?: 0.0, km = num(km) ?: co?.km ?: 0.0,
                    min = num(min)?.toInt() ?: co?.min ?: 0, esperaMin = co?.esperaMin ?: 0, confirmada = true
                )
                if (novo.valor > 0) { Diario.editar(ctx, novo); if (co == null) Alarmes.avisarMissoes(ctx) }
                refazer()
            }
            .setNegativeButton("Cancelar", null)
        if (co != null) d.setNeutralButton("Apagar") { _, _ ->
            AlertDialog.Builder(ctx).setMessage("Apagar esta corrida do Diário?")
                .setPositiveButton("Apagar") { _, _ -> Diario.apagarCorrida(ctx, co.id); refazer() }
                .setNegativeButton("Manter", null).show()
        }
        d.show()
    }

    // ------------------------------------------------------------------ MAPA DE CALOR

    fun mapa(): View {
        Diario.carregar(ctx)
        val c = ui.vbox()
        val dias = Diario.diasDeDados(ctx)
        val total = Diario.corridas.size

        val conf = ui.card()
        conf.addView(ui.tv("Confiança da previsão", 15f, C.TEXT, 2))
        conf.addView(barra(dias / 28f, if (dias >= 28) C.GOOD else C.MID), ui.lp(h = dp(12), top = 10))
        conf.addView(ui.tv(
            if (dias >= 28) "$dias dias e $total corridas: previsão boa."
            else "$dias de 28 dias · $total corridas. A previsão ainda é fraca: use como pista, não como regra.",
            13f, C.MUTED, 0), ui.lp(top = 8))
        c.addView(conf)

        val cel = Diario.celulas(ctx)
        val ref = cel.filter { it.minutos > 0 }.let { l -> if (l.isEmpty()) 0.0 else l.sumOf { it.total } / (l.sumOf { it.minutos } / 60.0) }
        if (cel.isEmpty()) {
            c.addView(ui.tv("Ainda sem corridas no Diário. Cada \"Peguei\" ensina o mapa onde e quando você ganha mais por hora.", 14f, C.MUTED, 0), ui.lp(top = 16))
            return c
        }
        c.addView(ui.tv("Métrica: R$ por hora, contando a espera até a corrida. Sua média geral: ${rs(ref)}/h.", 13f, C.MUTED, 0), ui.lp(top = 12))

        fun lista(tit: String, l: List<Diario.Celula>, cor: Int) {
            c.addView(titulo(tit))
            if (l.isEmpty()) { c.addView(ui.tv("Precisa de pelo menos 2 corridas no mesmo bairro e faixa de horário.", 13f, C.MUTED, 0), ui.lp(top = 6)); return }
            l.forEachIndexed { i, x ->
                val r = ui.hbox().apply { background = ui.round(C.WHITE, 14); setPadding(dp(14), dp(12), dp(14), dp(12)) }
                r.addView(ui.tv("${i + 1}", 18f, cor, 3), LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT))
                val m = ui.vbox()
                m.addView(ui.tv(x.bairro, 16f, C.TEXT, 2))
                m.addView(ui.tv("${x.quando()} · ${x.corridas} corridas", 13f, C.MUTED, 0))
                r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                r.addView(ui.tv("${rs(x.porHora)}/h", 16f, cor, 3))
                c.addView(r, ui.lp(top = 8))
            }
        }
        lista("5 melhores lugares e horários", Diario.melhores(ctx), C.GOOD)
        lista("Lugares para evitar", Diario.piores(ctx).filter { it.porHora < ref }, C.BAD)

        // grade dia × horário
        c.addView(titulo("Semana × horário (toque para ver os bairros)"))
        val g = Diario.grade(ctx)
        val tab = ui.vbox().apply { background = ui.round(C.WHITE, 16); setPadding(dp(8), dp(10), dp(8), dp(10)) }
        val cab = ui.hbox()
        cab.addView(View(ctx), LinearLayout.LayoutParams(dp(38), 1))
        Diario.FAIXAS.forEach { (a, _) -> cab.addView(ui.tv("${a}h", 11f, C.MUTED, 1, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        tab.addView(cab)
        for (d in 0 until 7) {
            val r = ui.hbox()
            r.addView(ui.tv(Diario.DIAS[d], 12f, C.TEXT, 2), LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.WRAP_CONTENT))
            for (fx in Diario.FAIXAS.indices) {
                val v = g[d][fx]
                val cor = corPorHora(v, ref)
                val t = ui.tv(if (v < 0) "" else String.format(br, "%.0f", v), 11f, if (v < 0) C.MUTED else Color.parseColor("#0B0F0D"), 2, true).apply {
                    background = ui.round(cor, 6); minHeight = dp(34)
                    gravity = Gravity.CENTER
                    if (v >= 0) setOnClickListener { verCelula(d, fx) }
                }
                r.addView(t, LinearLayout.LayoutParams(0, dp(34), 1f).also { it.marginStart = dp(3) })
            }
            tab.addView(r, ui.lp(top = 4))
        }
        c.addView(tab, ui.lp(top = 8))
        c.addView(ui.tv("Números = R$/hora. Verde acima da sua média, amarelo perto, vermelho abaixo, cinza sem dados.", 12f, C.MUTED, 0), ui.lp(top = 6))
        return c
    }

    private fun verCelula(d: Int, fx: Int) {
        val l = Diario.celulas(ctx).filter { it.dia == d && it.faixa == fx }.sortedByDescending { it.porHora }
        val txt = l.joinToString("\n") { "${it.bairro.ifEmpty { "Bairro ?" }}: ${rs(it.porHora)}/h · ${it.corridas} corrida(s)" }
        AlertDialog.Builder(ctx).setTitle("${Diario.DIAS[d]} ${Diario.nomeFaixa(fx)}").setMessage(txt).setPositiveButton("OK", null).show()
    }

    // ------------------------------------------------------------------ AGENDA

    fun agenda(): View {
        Diario.carregar(ctx)
        val c = ui.vbox()
        val agora = System.currentTimeMillis()

        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            val k = ui.card()
            k.addView(ui.tv("Avisos no horário certo", 16f, C.TEXT, 2))
            k.addView(ui.tv("O Android pede uma permissão para o aviso chegar na hora exata (sem ela pode atrasar alguns minutos).", 13f, C.MUTED, 0), ui.lp(top = 4))
            k.addView(ui.button("Permitir") { pedirAlarmes() })
            c.addView(k, ui.lp(top = 0))
        }

        // eventos
        c.addView(titulo("Eventos em Campo Grande"))
        c.addView(botaoGrande("+ Evento (show, jogo, feira, formatura, voo)", true) { editarEvento(null) }, ui.lp(top = 8))
        val evs = Diario.eventos.filter { it.fim > agora - 3 * 3600_000L }.sortedBy { it.ini }
        if (evs.isEmpty()) c.addView(ui.tv("Você recebe um aviso 30 minutos antes do fim, para se posicionar perto da saída.", 13f, C.MUTED, 0), ui.lp(top = 8))
        evs.forEach { e ->
            val k = ui.card().apply { setOnClickListener { editarEvento(e) } }
            k.addView(ui.tv(e.tipo.uppercase(br), 12f, C.PURPLE, 2))
            k.addView(ui.tv(e.nome, 17f, C.TEXT, 2), ui.lp(top = 2))
            k.addView(ui.tv("${e.local.ifEmpty { "Local ?" }} · ${fDiaHora.format(Date(e.ini))} até ${fHora.format(Date(e.fim))}", 13f, C.MUTED, 0), ui.lp(top = 2))
            k.addView(ui.tv(if (e.fim < agora) "Já terminou" else "Aviso às ${fHora.format(Date(e.fim - 30 * 60_000L))}", 13f, if (e.fim < agora) C.MUTED else C.GOOD, 2), ui.lp(top = 6))
            c.addView(k, ui.lp(top = 8))
        }

        // incentivos
        c.addView(titulo("Incentivos da 99"))
        val ib = ui.hbox()
        ib.addView(botaoGrande("+ Taxa zero", true) { editarIncentivo(null, "taxa_zero") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        ib.addView(botaoGrande("+ Missão", true) { editarIncentivo(null, "missao") }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) })
        c.addView(ib, ui.lp(top = 8))
        val inc = Diario.incentivos.filter { it.fim > agora - 86_400_000L }.sortedBy { it.ini }
        if (inc.isEmpty()) c.addView(ui.tv("Cadastre as janelas de taxa zero e as missões do Multiplica: o app avisa quando começam e conta as corridas do Diário.", 13f, C.MUTED, 0), ui.lp(top = 8))
        inc.forEach { i ->
            val k = ui.card().apply { setOnClickListener { editarIncentivo(i, i.tipo) } }
            val ativo = agora in i.ini..i.fim
            k.addView(ui.tv((if (i.tipo == "taxa_zero") "TAXA ZERO" else "MISSÃO") + when {
                ativo -> " · AGORA"; agora < i.ini -> " · começa ${fDiaHora.format(Date(i.ini))}"; else -> " · encerrada" }, 12f, if (ativo) C.GOOD else C.PURPLE, 2))
            k.addView(ui.tv(i.nome, 17f, C.TEXT, 2), ui.lp(top = 2))
            k.addView(ui.tv("${fDiaHora.format(Date(i.ini))} até ${fDiaHora.format(Date(i.fim))}", 13f, C.MUTED, 0), ui.lp(top = 2))
            if (i.tipo == "missao" && i.meta > 0) {
                val feitas = Diario.feitasNaMissao(i)
                k.addView(barra(feitas.toFloat() / i.meta, if (feitas >= i.meta) C.GOOD else C.PURPLE), ui.lp(h = dp(12), top = 10))
                val faltam = (i.meta - feitas).coerceAtLeast(0)
                k.addView(ui.tv("$feitas de ${i.meta} corridas" + (if (faltam > 0) " · faltam $faltam" else " · meta batida") +
                    (if (i.bonus > 0) " · bônus ${rs(i.bonus)}" else ""), 15f, C.TEXT, 2), ui.lp(top = 6))
            }
            c.addView(k, ui.lp(top = 8))
        }
        return c
    }

    private fun escolherDataHora(base: Long, f: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = base }
        DatePickerDialog(ctx, { _, y, m, d ->
            TimePickerDialog(ctx, { _, h, mi ->
                f(Calendar.getInstance().apply { set(y, m, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun botaoHora(rotulo: String, valor: Long, f: (Long) -> Unit): TextView {
        var v = valor
        val b = botaoGrande("$rotulo: ${fDiaHora.format(Date(v))}", false) {}
        b.setOnClickListener { escolherDataHora(v) { n -> v = n; b.text = "$rotulo: ${fDiaHora.format(Date(n))}"; f(n) } }
        return b
    }

    private fun proximaHoraCheia(mais: Int): Long = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, mais); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun editarEvento(e: Diario.Evento?) {
        val tipos = listOf("Show", "Jogo", "Feira", "Formatura", "Voo", "Outro")
        var tipo = e?.tipo ?: "Show"
        var ini = e?.ini ?: proximaHoraCheia(2)
        var fim = e?.fim ?: (ini + 3 * 3600_000L)
        val f = ui.vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val linhaTipos = ui.hbox()
        val chips = mutableListOf<TextView>()
        fun pintar() = chips.forEachIndexed { i, t -> val on = tipos[i] == tipo
            t.background = ui.round(if (on) C.PURPLE else C.WHITE, 12, 1, C.PURPLE); t.setTextColor(if (on) C.BG else C.TEXT) }
        val sc = android.widget.HorizontalScrollView(ctx)
        tipos.forEach { t -> val ch = ui.tv(t, 15f, C.TEXT, 2, true).apply { setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { tipo = t; pintar() } }
            chips.add(ch); linhaTipos.addView(ch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(6) }) }
        pintar(); sc.addView(linhaTipos); f.addView(sc)
        val (v1, nome) = campo("Nome do evento", e?.nome ?: "", false)
        val (v2, local) = campoBairro("Local (ex.: Bosque Expo, Morenão, Aeroporto)", e?.local ?: "")
        f.addView(v1, ui.lp(top = 8)); f.addView(v2, ui.lp(top = 8))
        f.addView(botaoHora("Começa", ini) { ini = it; if (fim <= it) fim = it + 3 * 3600_000L }, ui.lp(top = 10))
        f.addView(botaoHora("Termina", fim) { fim = it }, ui.lp(top = 8))
        val d = AlertDialog.Builder(ctx).setTitle(if (e == null) "Novo evento" else "Evento")
            .setView(android.widget.ScrollView(ctx).apply { addView(f) })
            .setPositiveButton("Salvar") { _, _ ->
                val n = nome.text.toString().trim().ifEmpty { tipo }
                Diario.salvarEvento(ctx, Diario.Evento(e?.id ?: System.currentTimeMillis(), n, tipo, local.text.toString().trim(), ini, maxOf(fim, ini + 600_000L)))
                Alarmes.reagendar(ctx); refazer()
            }.setNegativeButton("Cancelar", null)
        if (e != null) d.setNeutralButton("Apagar") { _, _ -> Diario.apagarEvento(ctx, e.id); Alarmes.reagendar(ctx); refazer() }
        d.show()
    }

    private fun editarIncentivo(i: Diario.Incentivo?, tipo: String) {
        var ini = i?.ini ?: proximaHoraCheia(1)
        var fim = i?.fim ?: (ini + 3 * 3600_000L)
        val f = ui.vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val (v1, nome) = campo(if (tipo == "taxa_zero") "Nome (ex.: Taxa zero da noite)" else "Nome da missão (ex.: Multiplica 99 sexta)",
            i?.nome ?: if (tipo == "taxa_zero") "Taxa zero" else "Multiplica 99", false)
        f.addView(v1)
        f.addView(botaoHora("Começa", ini) { ini = it; if (fim <= it) fim = it + 3 * 3600_000L }, ui.lp(top = 10))
        f.addView(botaoHora("Termina", fim) { fim = it }, ui.lp(top = 8))
        var meta: EditText? = null; var bonus: EditText? = null
        if (tipo == "missao") {
            val (v2, m) = campo("Meta de corridas", i?.meta?.toString() ?: "", true)
            val (v3, b) = campo("Bônus (R$)", i?.bonus?.let { n2(it) } ?: "", true)
            f.addView(v2, ui.lp(top = 8)); f.addView(v3, ui.lp(top = 8)); meta = m; bonus = b
        }
        val d = AlertDialog.Builder(ctx).setTitle(if (tipo == "taxa_zero") "Janela de taxa zero" else "Missão Multiplica 99")
            .setView(android.widget.ScrollView(ctx).apply { addView(f) })
            .setPositiveButton("Salvar") { _, _ ->
                Diario.salvarIncentivo(ctx, Diario.Incentivo(i?.id ?: System.currentTimeMillis(), tipo, nome.text.toString().trim().ifEmpty { "Incentivo" },
                    ini, maxOf(fim, ini + 600_000L), meta?.let { num(it)?.toInt() } ?: 0, bonus?.let { num(it) } ?: 0.0))
                Alarmes.reagendar(ctx); refazer()
            }.setNegativeButton("Cancelar", null)
        if (i != null) d.setNeutralButton("Apagar") { _, _ -> Diario.apagarIncentivo(ctx, i.id); Alarmes.reagendar(ctx); refazer() }
        d.show()
    }
}
