package br.rota.copiloto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ofertas lidas hoje e início do turno. Fica no próprio aparelho e zera a cada dia. */
object Historico {
    data class Item(val t: Long, val p: String, val v: Double, val km: Double, val pk: Double, val ver: Verdict, val bairro: String)

    private val dia = SimpleDateFormat("yyyyMMdd", Locale.US)
    private fun hojeKey() = dia.format(Date())
    private fun sp(ctx: Context) = ctx.getSharedPreferences("historico", Context.MODE_PRIVATE)

    @Synchronized
    fun add(ctx: Context, a: Analysis, bairro: String) {
        try {
            val s = sp(ctx)
            val arr = if (s.getString("dia", "") == hojeKey()) JSONArray(s.getString("itens", "[]")) else JSONArray()
            arr.put(JSONObject().apply {
                put("t", System.currentTimeMillis()); put("p", a.offer.platform); put("v", a.offer.value)
                put("km", a.totalKm); put("pk", a.perKm); put("ver", a.verdict.name); put("b", bairro)
            })
            val keep = JSONArray()
            for (i in maxOf(0, arr.length() - 200) until arr.length()) keep.put(arr.get(i))
            s.edit().putString("dia", hojeKey()).putString("itens", keep.toString()).apply()
        } catch (_: Exception) {}
    }

    fun hoje(ctx: Context): List<Item> {
        val s = sp(ctx)
        if (s.getString("dia", "") != hojeKey()) return emptyList()
        return try {
            val arr = JSONArray(s.getString("itens", "[]"))
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Item(it.optLong("t"), it.optString("p"), it.optDouble("v"), it.optDouble("km"), it.optDouble("pk"),
                    runCatching { Verdict.valueOf(it.optString("ver")) }.getOrDefault(Verdict.YELLOW), it.optString("b"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private var ultimaMarca = 0L

    /** Chamado enquanto o app de corrida está na frente: guarda a hora em que o turno começou hoje. */
    fun marcarOnline(ctx: Context) {
        val agora = System.currentTimeMillis()
        if (agora - ultimaMarca < 60000) return
        ultimaMarca = agora
        val s = sp(ctx)
        val e = s.edit()
        // turno novo: outro dia, ou mais de 2 h parado
        if (s.getString("turnoDia", "") != hojeKey() || agora - s.getLong("turnoVisto", 0L) > 2 * 3600_000L) {
            e.putString("turnoDia", hojeKey()).putLong("turnoIni", agora)
        }
        e.putLong("turnoVisto", agora).apply()
    }

    /** Início do turno de hoje (0 = ainda não rodou hoje). */
    fun inicioTurno(ctx: Context): Long {
        val s = sp(ctx)
        return if (s.getString("turnoDia", "") == hojeKey()) s.getLong("turnoIni", 0L) else 0L
    }

    fun turnoAtivo(ctx: Context): Boolean = System.currentTimeMillis() - sp(ctx).getLong("turnoVisto", 0L) < 10 * 60_000L
}
