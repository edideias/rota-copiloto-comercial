package br.rota.copiloto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Diário de Ganhos: corridas, online/pausa, eventos e incentivos.
 * Fica num arquivo próprio (diario.json), separado de tudo que o app já guardava.
 */
object Diario {

    data class Corrida(
        val id: Long, val ts: Long, val plataforma: String,
        val embarque: String, val destino: String,
        val valor: Double, val km: Double, val min: Int, val esperaMin: Int,
        val confirmada: Boolean
    ) {
        fun fim() = ts + min * 60_000L
    }

    data class Trecho(val ini: Long, val fim: Long, val tipo: String) // tipo: online | pausa

    data class Evento(val id: Long, val nome: String, val tipo: String, val local: String, val ini: Long, val fim: Long)

    data class Incentivo(
        val id: Long, val tipo: String, // taxa_zero | missao
        val nome: String, val ini: Long, val fim: Long, val meta: Int, val bonus: Double
    )

    // ------------------------------------------------------------------ estado em memória

    private var carregado = false
    val corridas = mutableListOf<Corrida>()
    val trechos = mutableListOf<Trecho>()
    val eventos = mutableListOf<Evento>()
    val incentivos = mutableListOf<Incentivo>()
    /** "offline", "online" ou "pausa"; e desde quando. */
    var estado = "offline"; private set
    var estadoDesde = 0L; private set

    private fun arq(ctx: Context) = File(ctx.filesDir, "diario.json")

    @Synchronized
    fun carregar(ctx: Context) {
        if (carregado) return
        carregado = true
        try {
            val f = arq(ctx)
            if (f.exists()) deJson(JSONObject(f.readText()), substituir = true)
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun salvar(ctx: Context) {
        try {
            val f = arq(ctx)
            val tmp = File(f.parentFile, "diario.tmp")
            tmp.writeText(paraJson().toString())
            tmp.renameTo(f)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ online / pausa

    @Synchronized
    fun mudarEstado(ctx: Context, novo: String, agora: Long = System.currentTimeMillis()) {
        carregar(ctx)
        if (novo == estado) return
        if (estado != "offline" && estadoDesde > 0) trechos.add(Trecho(estadoDesde, agora, estado))
        estado = novo
        estadoDesde = if (novo == "offline") 0L else agora
        salvar(ctx)
    }

    /** Fica online sozinho quando você registra uma corrida estando offline. */
    private fun garantirOnline(ctx: Context, agora: Long) {
        if (estado == "offline") { estado = "online"; estadoDesde = agora }
    }

    // ------------------------------------------------------------------ corridas

    /** Botão "Peguei" do semáforo: cria a corrida já preenchida com o que a oferta mostrou. */
    @Synchronized
    fun registrarDaOferta(ctx: Context, a: Analysis, embarque: String, destino: String, agora: Long = System.currentTimeMillis()): Corrida {
        carregar(ctx)
        garantirOnline(ctx, agora)
        val o = a.offer
        val c = Corrida(
            id = agora, ts = agora, plataforma = o.platform,
            embarque = embarque, destino = destino,
            valor = o.value, km = a.totalKm,
            min = (o.pickupMin + o.tripMin).toInt().coerceAtLeast(0),
            esperaMin = esperaAte(agora), confirmada = false
        )
        corridas.add(c)
        salvar(ctx)
        return c
    }

    /** Minutos desde o fim da corrida anterior (ou desde que ficou online), só no mesmo dia. */
    private fun esperaAte(t: Long): Int {
        val ini = inicioDoDia(t)
        val anterior = corridas.filter { it.ts in ini until t }.maxOfOrNull { it.fim() }
        val online = if (estado != "offline" && estadoDesde in ini until t) estadoDesde else null
        val base = listOfNotNull(anterior, online, trechos.filter { it.tipo == "online" && it.ini in ini until t }.minOfOrNull { it.ini }).maxOrNull()
            ?: return 0
        return ((t - base) / 60_000L).toInt().coerceIn(0, 600)
    }

    @Synchronized
    fun confirmar(ctx: Context, id: Long, valor: Double? = null, km: Double? = null, min: Int? = null) {
        carregar(ctx)
        val i = corridas.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = corridas[i]
        corridas[i] = c.copy(valor = valor ?: c.valor, km = km ?: c.km, min = min ?: c.min, confirmada = true)
        salvar(ctx)
    }

    @Synchronized
    fun editar(ctx: Context, c: Corrida) {
        carregar(ctx)
        val i = corridas.indexOfFirst { it.id == c.id }
        if (i >= 0) corridas[i] = c else corridas.add(c)
        salvar(ctx)
    }

    @Synchronized
    fun apagarCorrida(ctx: Context, id: Long) {
        carregar(ctx); corridas.removeAll { it.id == id }; salvar(ctx)
    }

    // ------------------------------------------------------------------ eventos e incentivos

    @Synchronized fun salvarEvento(ctx: Context, e: Evento) { carregar(ctx); eventos.removeAll { it.id == e.id }; eventos.add(e); salvar(ctx) }
    @Synchronized fun apagarEvento(ctx: Context, id: Long) { carregar(ctx); eventos.removeAll { it.id == id }; salvar(ctx) }
    @Synchronized fun salvarIncentivo(ctx: Context, i: Incentivo) { carregar(ctx); incentivos.removeAll { it.id == i.id }; incentivos.add(i); salvar(ctx) }
    @Synchronized fun apagarIncentivo(ctx: Context, id: Long) { carregar(ctx); incentivos.removeAll { it.id == id }; salvar(ctx) }

    /** Corridas da 99 dentro da janela da missão. */
    fun feitasNaMissao(i: Incentivo): Int = corridas.count { it.plataforma == "99" && it.ts in i.ini..i.fim }

    // ------------------------------------------------------------------ resumo do dia

    data class Resumo(
        val corridas: Int, val pendentes: Int, val total: Double, val km: Double,
        val minOnline: Int, val minCorrida: Int, val minOcioso: Int, val minPausa: Int,
        val porHora: Double, val porKm: Double, val lucro: Double
    )

    fun inicioDoDia(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun minutosDe(tipo: String, ini: Long, fim: Long, agora: Long): Int {
        var ms = 0L
        trechos.filter { it.tipo == tipo }.forEach { ms += (minOf(it.fim, fim) - maxOf(it.ini, ini)).coerceAtLeast(0) }
        if (estado == tipo && estadoDesde > 0) ms += (minOf(agora, fim) - maxOf(estadoDesde, ini)).coerceAtLeast(0)
        return (ms / 60_000L).toInt()
    }

    fun resumo(ctx: Context, p: Prefs, dia: Long = System.currentTimeMillis(), agora: Long = System.currentTimeMillis()): Resumo {
        carregar(ctx)
        val ini = inicioDoDia(dia); val fim = ini + 86_400_000L
        val hoje = corridas.filter { it.ts in ini until fim }
        val total = hoje.sumOf { it.valor }
        val km = hoje.sumOf { it.km }
        val minCorrida = hoje.sumOf { it.min }
        val minOnline = maxOf(minutosDe("online", ini, fim, agora), minCorrida)
        val minPausa = minutosDe("pausa", ini, fim, agora)
        val ocioso = (minOnline - minCorrida).coerceAtLeast(0)
        return Resumo(
            corridas = hoje.size, pendentes = hoje.count { !it.confirmada }, total = total, km = km,
            minOnline = minOnline, minCorrida = minCorrida, minOcioso = ocioso, minPausa = minPausa,
            porHora = if (minOnline > 0) total / (minOnline / 60.0) else 0.0,
            porKm = if (km > 0) total / km else 0.0,
            lucro = total - km * p.costPerKm
        )
    }

    // ------------------------------------------------------------------ mapa de calor

    val FAIXAS = listOf(0 to 6, 6 to 9, 9 to 12, 12 to 15, 15 to 18, 18 to 21, 21 to 24)
    val DIAS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    fun nomeFaixa(i: Int) = "${FAIXAS[i].first}h–${FAIXAS[i].second}h"

    fun faixaDe(t: Long): Int {
        val h = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
        return FAIXAS.indexOfFirst { h >= it.first && h < it.second }.coerceAtLeast(0)
    }
    fun diaDe(t: Long): Int = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK) - 1

    data class Celula(val bairro: String, val dia: Int, val faixa: Int, val corridas: Int, val total: Double, val minutos: Int) {
        /** R$ por hora contando a espera até a corrida, não só o tempo rodando. */
        val porHora: Double get() = if (minutos > 0) total / (minutos / 60.0) else 0.0
        fun rotulo() = "${bairro.ifEmpty { "Bairro ?" }} · ${DIAS[dia]} ${nomeFaixa(faixa)}"
    }

    fun celulas(ctx: Context): List<Celula> {
        carregar(ctx)
        return corridas.groupBy { Triple(it.embarque.ifEmpty { "" }, diaDe(it.ts), faixaDe(it.ts)) }
            .map { (k, l) -> Celula(k.first, k.second, k.third, l.size, l.sumOf { it.valor }, l.sumOf { it.min + it.esperaMin }) }
    }

    /** Grade dia × faixa somando todos os bairros. */
    fun grade(ctx: Context): Array<DoubleArray> {
        val g = Array(7) { DoubleArray(FAIXAS.size) { -1.0 } }
        celulas(ctx).groupBy { it.dia to it.faixa }.forEach { (k, l) ->
            val min = l.sumOf { it.minutos }
            if (min > 0) g[k.first][k.second] = l.sumOf { it.total } / (min / 60.0)
        }
        return g
    }

    fun melhores(ctx: Context, n: Int = 5, minimo: Int = 2) =
        celulas(ctx).filter { it.corridas >= minimo && it.bairro.isNotEmpty() }.sortedByDescending { it.porHora }.take(n)

    fun piores(ctx: Context, n: Int = 5, minimo: Int = 2) =
        celulas(ctx).filter { it.corridas >= minimo && it.bairro.isNotEmpty() }.sortedBy { it.porHora }.take(n)

    /** Dias de histórico (do primeiro registro até hoje), para a barra de confiança. */
    fun diasDeDados(ctx: Context): Int {
        carregar(ctx)
        val primeiro = corridas.minOfOrNull { it.ts } ?: return 0
        return ((System.currentTimeMillis() - primeiro) / 86_400_000L).toInt() + 1
    }

    // ------------------------------------------------------------------ backup

    fun paraJson(): JSONObject = JSONObject().apply {
        put("versao", 1)
        put("estado", estado); put("estadoDesde", estadoDesde)
        put("corridas", JSONArray().apply {
            corridas.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("ts", c.ts); put("plataforma", c.plataforma); put("embarque", c.embarque)
                put("destino", c.destino); put("valor", c.valor); put("km", c.km); put("min", c.min)
                put("espera", c.esperaMin); put("confirmada", c.confirmada)
            }) }
        })
        put("trechos", JSONArray().apply { trechos.forEach { put(JSONObject().put("ini", it.ini).put("fim", it.fim).put("tipo", it.tipo)) } })
        put("eventos", JSONArray().apply { eventos.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("nome", e.nome); put("tipo", e.tipo); put("local", e.local); put("ini", e.ini); put("fim", e.fim)
        }) } })
        put("incentivos", JSONArray().apply { incentivos.forEach { i -> put(JSONObject().apply {
            put("id", i.id); put("tipo", i.tipo); put("nome", i.nome); put("ini", i.ini); put("fim", i.fim); put("meta", i.meta); put("bonus", i.bonus)
        }) } })
    }

    /** Lê um backup. Sem substituir, junta ao que já existe (mesmo id não duplica). */
    @Synchronized
    fun deJson(j: JSONObject, substituir: Boolean) {
        if (substituir) { corridas.clear(); trechos.clear(); eventos.clear(); incentivos.clear()
            estado = j.optString("estado", "offline"); estadoDesde = j.optLong("estadoDesde", 0L) }
        val ids = corridas.map { it.id }.toMutableSet()
        j.optJSONArray("corridas")?.let { a -> for (k in 0 until a.length()) { val o = a.getJSONObject(k)
            val c = Corrida(o.optLong("id"), o.optLong("ts"), o.optString("plataforma"), o.optString("embarque"), o.optString("destino"),
                o.optDouble("valor", 0.0), o.optDouble("km", 0.0), o.optInt("min"), o.optInt("espera"), o.optBoolean("confirmada", true))
            if (ids.add(c.id)) corridas.add(c) } }
        val tset = trechos.map { it.ini to it.tipo }.toMutableSet()
        j.optJSONArray("trechos")?.let { a -> for (k in 0 until a.length()) { val o = a.getJSONObject(k)
            val t = Trecho(o.optLong("ini"), o.optLong("fim"), o.optString("tipo"))
            if (tset.add(t.ini to t.tipo)) trechos.add(t) } }
        val eids = eventos.map { it.id }.toMutableSet()
        j.optJSONArray("eventos")?.let { a -> for (k in 0 until a.length()) { val o = a.getJSONObject(k)
            val e = Evento(o.optLong("id"), o.optString("nome"), o.optString("tipo"), o.optString("local"), o.optLong("ini"), o.optLong("fim"))
            if (eids.add(e.id)) eventos.add(e) } }
        val iids = incentivos.map { it.id }.toMutableSet()
        j.optJSONArray("incentivos")?.let { a -> for (k in 0 until a.length()) { val o = a.getJSONObject(k)
            val i = Incentivo(o.optLong("id"), o.optString("tipo"), o.optString("nome"), o.optLong("ini"), o.optLong("fim"), o.optInt("meta"), o.optDouble("bonus", 0.0))
            if (iids.add(i.id)) incentivos.add(i) } }
        corridas.sortBy { it.ts }
    }

    @Synchronized
    fun importarJson(ctx: Context, texto: String) { carregar(ctx); deJson(JSONObject(texto), substituir = false); salvar(ctx) }

    private val fmtCsv = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("pt", "BR"))

    fun paraCsv(): String = buildString {
        append("data_hora;plataforma;embarque;destino;valor;km;minutos;espera_min;confirmada\n")
        corridas.sortedBy { it.ts }.forEach { c ->
            append(fmtCsv.format(Date(c.ts))).append(';').append(c.plataforma).append(';')
            append(c.embarque.replace(";", ",")).append(';').append(c.destino.replace(";", ",")).append(';')
            append(String.format(Locale("pt", "BR"), "%.2f", c.valor)).append(';')
            append(String.format(Locale("pt", "BR"), "%.1f", c.km)).append(';')
            append(c.min).append(';').append(c.esperaMin).append(';').append(if (c.confirmada) "sim" else "nao").append('\n')
        }
    }

    /** Lê o CSV que o próprio app exporta (ou uma planilha no mesmo formato). */
    @Synchronized
    fun importarCsv(ctx: Context, texto: String): Int {
        carregar(ctx)
        val ids = corridas.map { it.id }.toMutableSet()
        var n = 0
        texto.lines().drop(1).filter { it.isNotBlank() }.forEach { l ->
            val c = l.split(';')
            if (c.size < 8) return@forEach
            val ts = try { fmtCsv.parse(c[0].trim())?.time } catch (_: Exception) { null } ?: return@forEach
            val num = { s: String -> s.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 }
            // mesma corrida (mesmo minuto e mesmo valor) não entra duas vezes
            if (corridas.any { it.ts / 60_000L == ts / 60_000L && Math.abs(it.valor - num(c[4])) < 0.005 }) return@forEach
            var id = ts
            while (!ids.add(id)) id++
            corridas.add(Corrida(id, ts, c[1].trim(), c[2].trim(), c[3].trim(), num(c[4]), num(c[5]),
                c[6].trim().toIntOrNull() ?: 0, c[7].trim().toIntOrNull() ?: 0, c.getOrNull(8)?.trim() != "nao"))
            n++
        }
        corridas.sortBy { it.ts }
        salvar(ctx)
        return n
    }

    /** Só para testes: zera o que está na memória. */
    @Synchronized
    fun limparMemoria() { carregado = true; corridas.clear(); trechos.clear(); eventos.clear(); incentivos.clear(); estado = "offline"; estadoDesde = 0L }
}
