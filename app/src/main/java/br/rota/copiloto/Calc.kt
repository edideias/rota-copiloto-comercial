package br.rota.copiloto

import android.content.Context

enum class Verdict { GREEN, YELLOW, RED }

data class Offer(
    val platform: String,
    val value: Double,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double,
    val stops: Int = 0,         // paradas no caminho (0 = nenhuma)
    val destText: String = ""  // endereço de destino como aparece na tela
)

data class Analysis(
    val offer: Offer,
    val totalKm: Double,
    val totalMin: Double,
    val perKm: Double,
    val perHour: Double,      // -1 quando o tempo não foi lido
    val profit: Double,
    val verdict: Verdict,
    val profitPerHour: Double = -1.0,
    val profitPct: Double = 0.0,
    val perMin: Double = -1.0
)

object Calc {
    fun analyze(o: Offer, p: Prefs): Analysis {
        val km = o.pickupKm + o.tripKm
        val min = o.pickupMin + o.tripMin
        val perKm = if (km > 0) o.value / km else 0.0
        val hourKnown = min > 0
        val perHour = if (hourKnown) o.value / (min / 60.0) else -1.0
        val profit = o.value - km * p.costPerKm

        val red = perKm < p.yellowKm || (hourKnown && perHour < p.yellowHour)
        val green = perKm >= p.greenKm && (!hourKnown || perHour >= p.greenHour)
        val verdict = when {
            red -> Verdict.RED
            green -> Verdict.GREEN
            else -> Verdict.YELLOW
        }
        val profitPerHour = if (hourKnown) profit / (min / 60.0) else -1.0
        val profitPct = if (o.value > 0) profit / o.value * 100.0 else 0.0
        val perMin = if (hourKnown) o.value / min else -1.0
        return Analysis(o, km, min, perKm, perHour, profit, verdict, profitPerHour, profitPct, perMin)
    }
}

/** Métricas que podem aparecer no cartão. */
object Metrics {
    val ALL = listOf("km", "hora", "lucrohora", "lucro", "lucropct", "min")

    fun label(key: String) = when (key) {
        "km" -> "\$/Km"
        "hora" -> "\$/Hora"
        "lucrohora" -> "Lucro/Hora"
        "lucro" -> "Lucro"
        "lucropct" -> "Lucro %"
        else -> "\$/Min"
    }

    private val br = java.util.Locale("pt", "BR")

    fun value(a: Analysis, key: String): String = when (key) {
        "km" -> String.format(br, "%.2f", a.perKm)
        "hora" -> if (a.perHour >= 0) String.format(br, "%.2f", a.perHour) else "--"
        "lucrohora" -> if (a.perHour >= 0) String.format(br, "%.2f", a.profitPerHour) else "--"
        "lucro" -> String.format(br, "%.2f", a.profit)
        "lucropct" -> String.format(br, "%.0f%%", a.profitPct)
        else -> if (a.perMin >= 0) String.format(br, "%.2f", a.perMin) else "--"
    }

    fun color(a: Analysis, key: String, p: Prefs): Int {
        fun byKm(v: Double) = if (v >= p.greenKm) C.GOOD else if (v < p.yellowKm) C.BAD else C.MID
        fun byHour(v: Double) = if (v >= p.greenHour) C.GOOD else if (v < p.yellowHour) C.BAD else C.MID
        return when (key) {
            "km" -> byKm(a.perKm)
            "hora" -> if (a.perHour >= 0) byHour(a.perHour) else C.MUTED
            "lucrohora" -> C.PURPLE
            "lucro" -> if (a.profit > 0) C.GOOD else C.BAD
            "lucropct" -> if (a.profitPct >= 50) C.GOOD else if (a.profitPct >= 30) C.MID else C.BAD
            else -> if (a.perMin >= 0) byHour(a.perMin * 60.0) else C.MUTED
        }
    }
}

/** Configurações salvas só no aparelho. */
class Prefs(ctx: Context, override_: android.content.SharedPreferences? = null) {
    val store: android.content.SharedPreferences =
        override_ ?: ctx.getSharedPreferences("copiloto", Context.MODE_PRIVATE)
    private val sp get() = store

    /** Grava na loja real tudo o que está num rascunho (MemPrefs). */
    fun commitFrom(draft: android.content.SharedPreferences) {
        val e = store.edit()
        for ((k, v) in draft.all) {
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
            }
        }
        e.apply()
    }

    private fun d(k: String, def: Double): Double = sp.getString(k, null)?.toDoubleOrNull() ?: def
    private fun putD(k: String, v: Double) { sp.edit().putString(k, v.toString()).apply() }

    var greenKm: Double
        get() = d("greenKm", 1.80)
        set(v) = putD("greenKm", v)
    var yellowKm: Double
        get() = d("yellowKm", 1.30)
        set(v) = putD("yellowKm", v)
    var greenHour: Double
        get() = d("greenHour", 35.0)
        set(v) = putD("greenHour", v)
    var yellowHour: Double
        get() = d("yellowHour", 25.0)
        set(v) = putD("yellowHour", v)
    var costPerKm: Double
        get() = d("costPerKm", 0.60)
        set(v) = putD("costPerKm", v)
    var overlayTopDp: Double
        get() = d("overlayTopDp", 80.0)
        set(v) = putD("overlayTopDp", v)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }
    var useUber: Boolean
        get() = sp.getBoolean("useUber", true)
        set(v) { sp.edit().putBoolean("useUber", v).apply() }
    var use99: Boolean
        get() = sp.getBoolean("use99", true)
        set(v) { sp.edit().putBoolean("use99", v).apply() }
    var useOcr: Boolean
        get() = sp.getBoolean("useOcr", true)
        set(v) { sp.edit().putBoolean("useOcr", v).apply() }
    var calcEnabled: Boolean
        get() = sp.getBoolean("calcEnabled", true)
        set(v) { sp.edit().putBoolean("calcEnabled", v).apply() }
    var saveShots: Boolean
        get() = sp.getBoolean("saveShots", false)
        set(v) { sp.edit().putBoolean("saveShots", v).apply() }
    var wizardDone: Boolean
        get() = sp.getBoolean("wizardDone", false)
        set(v) { sp.edit().putBoolean("wizardDone", v).apply() }

    /** Métricas do cartão, na ordem escolhida (1 a 4). */
    var metrics: List<String>
        get() = (sp.getString("metrics", null) ?: "km,hora").split(",").filter { it in Metrics.ALL }.ifEmpty { listOf("km", "hora") }
        set(v) { sp.edit().putString("metrics", v.joinToString(",")).apply() }
    /** 0 esquerda, 1 centro, 2 direita */
    var cardPos: Int
        get() = sp.getInt("cardPos", 1)
        set(v) { sp.edit().putInt("cardPos", v).apply() }
    // ---- Destino: card do bairro, rua de terra e lista negra
    var bairroOn: Boolean
        get() = sp.getBoolean("bairroOn", true)
        set(v) { sp.edit().putBoolean("bairroOn", v).apply() }
    var avisarTerra: Boolean
        get() = sp.getBoolean("avisarTerra", true)
        set(v) { sp.edit().putBoolean("avisarTerra", v).apply() }
    /** 0 faixa larga, 1 pílula fina, 2 placa com status */
    var bairroStyle: Int
        get() = sp.getInt("bairroStyle", 0)
        set(v) { sp.edit().putInt("bairroStyle", v).apply() }
    /** 0 painel ao lado, 1 cartaz por cima */
    var avisoStyle: Int
        get() = sp.getInt("avisoStyle", 0)
        set(v) { sp.edit().putInt("avisoStyle", v).apply() }
    var avisoVibrar: Boolean
        get() = sp.getBoolean("avisoVibrar", true)
        set(v) { sp.edit().putBoolean("avisoVibrar", v).apply() }
    var bairrosEvitados: List<String>
        get() = try {
            val a = org.json.JSONArray(sp.getString("bairrosEvitados", "[]")); List(a.length()) { a.getString(it) }
        } catch (_: Exception) { emptyList() }
        set(v) { sp.edit().putString("bairrosEvitados", org.json.JSONArray(v.distinct()).toString()).apply() }
    var listaNegra: List<Destino.Negra>
        get() = try {
            val a = org.json.JSONArray(sp.getString("listaNegra", "[]"))
            List(a.length()) { val o = a.getJSONObject(it); Destino.Negra(o.getString("n"), o.optString("m")) }
        } catch (_: Exception) { emptyList() }
        set(v) {
            val a = org.json.JSONArray()
            v.forEach { a.put(org.json.JSONObject().put("n", it.nome).put("m", it.motivo)) }
            sp.edit().putString("listaNegra", a.toString()).apply()
        }

    // ---- Destaque "corrida TOP" (verde neon piscando) + km total/valor abaixo do quadro
    var topOn: Boolean
        get() = sp.getBoolean("topOn", true)
        set(v) { sp.edit().putBoolean("topOn", v).apply() }
    /** Mínimo de R$ por km (centavos: 350 = R$ 3,50). */
    var topMinC: Int
        get() = sp.getInt("topMinC", 350)
        set(v) { sp.edit().putInt("topMinC", v.coerceIn(100, 2000)).apply() }
    /** 0 verde neon, 1 turquesa, 2 lima, 3 ciano */
    var topColor: Int
        get() = sp.getInt("topColor", 0)
        set(v) { sp.edit().putInt("topColor", v).apply() }
    /** 0 lenta, 1 normal, 2 rápida */
    var topSpeed: Int
        get() = sp.getInt("topSpeed", 1)
        set(v) { sp.edit().putInt("topSpeed", v).apply() }
    var topVibrate: Boolean
        get() = sp.getBoolean("topVibrate", false)
        set(v) { sp.edit().putBoolean("topVibrate", v).apply() }
    var showTotals: Boolean
        get() = sp.getBoolean("showTotals", true)
        set(v) { sp.edit().putBoolean("showTotals", v).apply() }

    /** A oferta é "TOP"? Só dentro do verde e a partir do mínimo por km. */
    fun isTop(a: Analysis): Boolean = topOn && a.verdict == Verdict.GREEN && a.perKm >= topMinC / 100.0

    /** Modelo visual do aplicativo (0 a 9). */
    var template: Int
        get() = sp.getInt("template", 0)
        set(v) { sp.edit().putInt("template", v).apply() }
    /** 0 claro, 1 escuro, 2 cheio */
    var cardTheme: Int
        get() = sp.getInt("cardTheme", 1)
        set(v) { sp.edit().putInt("cardTheme", v).apply() }
    var cardFont: Int
        get() = sp.getInt("cardFont", 18)
        set(v) { sp.edit().putInt("cardFont", v.coerceIn(12, 30)).apply() }

    /** Segundos que o cartão fica na tela (0 = até tocar nele). */
    var holdSec: Int
        get() = sp.getInt("holdSec", 10)
        set(v) { sp.edit().putInt("holdSec", v).apply() }

    var termsAccepted: Boolean
        get() = sp.getBoolean("termsAccepted", false)
        set(v) { sp.edit().putBoolean("termsAccepted", v).apply() }
}
