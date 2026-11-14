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
    val destText: String = "",  // endereço de destino como aparece na tela
    val pickupText: String = ""  // endereço de embarque como aparece na tela
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
        get() {
            // altura padrão desceu para não cobrir o X (recusar) do 99; quem estava no padrão antigo desce junto, uma vez
            if (!sp.getBoolean("alturaV2", false)) {
                if (d("overlayTopDp", 80.0) <= 80.0) putD("overlayTopDp", 135.0)
                sp.edit().putBoolean("alturaV2", true).apply()
            }
            // padrão sobe um pouco (135 -> 100), sem mexer em quem já escolheu outra altura
            if (!sp.getBoolean("alturaV3", false)) {
                if (d("overlayTopDp", 100.0) == 135.0) putD("overlayTopDp", 100.0)
                sp.edit().putBoolean("alturaV3", true).apply()
            }
            return d("overlayTopDp", 100.0)
        }
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
    // ---- Aparência do quadro (admin)
    /** 0 pequeno, 1 médio, 2 grande */
    var appTagSize: Int
        get() = sp.getInt("appTagSize", 2)
        set(v) { sp.edit().putInt("appTagSize", v).apply() }
    /** 0 roxo, 1 preto, 2 branco, 3 azul */
    var appTagColor: Int
        get() = sp.getInt("appTagColor", 0)
        set(v) { sp.edit().putInt("appTagColor", v).apply() }
    var paradaOn: Boolean
        get() = sp.getBoolean("paradaOn", true)
        set(v) { sp.edit().putBoolean("paradaOn", v).apply() }
    /** espessura da moldura da parada, em dp */
    var paradaEsp: Int
        get() = sp.getInt("paradaEsp", 12)
        set(v) { sp.edit().putInt("paradaEsp", v.coerceIn(4, 30)).apply() }
    /** 0 vermelho, 1 laranja, 2 rosa, 3 amarelo */
    var paradaCor: Int
        get() = sp.getInt("paradaCor", 0)
        set(v) { sp.edit().putInt("paradaCor", v).apply() }
    /** 0 lenta, 1 normal, 2 rápida */
    var paradaVel: Int
        get() = sp.getInt("paradaVel", 1)
        set(v) { sp.edit().putInt("paradaVel", v).apply() }
    /** some com o quadro assim que a oferta sai da tela */
    /** Semáforo compacto: só o essencial (etiqueta do app, barra de lucro, km, valor, destino e avisos). */
    var compacto: Boolean
        get() = sp.getBoolean("compacto", false)
        set(v) { sp.edit().putBoolean("compacto", v).apply() }

    // ---- modo compacto: cada peça liga/desliga e tem tamanho, cor e espaço próprios
    fun cpB(k: String, d: Boolean) = sp.getBoolean("cp_$k", d)
    fun setCpB(k: String, v: Boolean) { sp.edit().putBoolean("cp_$k", v).apply() }
    fun cpI(k: String, d: Int) = sp.getInt("cp_$k", d)
    fun setCpI(k: String, v: Int) { sp.edit().putInt("cp_$k", v).apply() }

    var sumirJunto: Boolean
        get() = sp.getBoolean("sumirJunto", true)
        set(v) { sp.edit().putBoolean("sumirJunto", v).apply() }

    // cores livres (escolhidas no espectro) e piscadas por barra deslizante
    var tagCorUber: Int
        get() = sp.getInt("tagCorUber", 0xFF7C3AED.toInt())
        set(v) { sp.edit().putInt("tagCorUber", v).apply() }
    var tagCor99: Int
        get() = sp.getInt("tagCor99", 0xFFEA580C.toInt())
        set(v) { sp.edit().putInt("tagCor99", v).apply() }
    var paradaCorInt: Int
        get() = sp.getInt("paradaCorInt", intArrayOf(0xFFFF1F1F.toInt(), 0xFFFF7A00.toInt(), 0xFFFF2D95.toInt(), 0xFFFFD400.toInt())[paradaCor.coerceIn(0, 3)])
        set(v) { sp.edit().putInt("paradaCorInt", v).apply() }
    var topCorInt: Int
        get() = sp.getInt("topCorInt", intArrayOf(0xFF39FF14.toInt(), 0xFF00F5D4.toInt(), 0xFFC6FF00.toInt(), 0xFF00E5FF.toInt())[topColor.coerceIn(0, 3)])
        set(v) { sp.edit().putInt("topCorInt", v).apply() }
    /** meia piscada em ms (menor = mais rápido); 70 ms é ultra rápido */
    var paradaMs: Int
        get() = sp.getInt("paradaMs", when (paradaVel) { 0 -> 900; 2 -> 250; else -> 450 })
        set(v) { sp.edit().putInt("paradaMs", v.coerceIn(70, 1500)).apply() }
    var topMs: Int
        get() = sp.getInt("topMs", when (topSpeed) { 0 -> 1500; 2 -> 480; else -> 950 })
        set(v) { sp.edit().putInt("topMs", v.coerceIn(70, 1500)).apply() }

    /** contorno na tela inteira quando a corrida é TOP */
    var telaTopOn: Boolean
        get() = sp.getBoolean("telaTopOn", true)
        set(v) { sp.edit().putBoolean("telaTopOn", v).apply() }
    var telaTopEsp: Int
        get() = sp.getInt("telaTopEsp", 4)
        set(v) { sp.edit().putInt("telaTopEsp", v.coerceIn(1, 24)).apply() }
    /** cantos do contorno, para acompanhar o formato da tela */
    var telaTopRaio: Int
        get() = sp.getInt("telaTopRaio", 28)
        set(v) { sp.edit().putInt("telaTopRaio", v.coerceIn(0, 60)).apply() }

    /** barra tipo equalizador no quadro verde: mostra o quanto a corrida paga dentro da faixa mínimo–máximo */
    var eqOn: Boolean
        get() = sp.getBoolean("eqOn", true)
        set(v) { sp.edit().putBoolean("eqOn", v).apply() }
    /** R$/km em centavos: começo e topo da barra */
    var eqMinC: Int
        get() = sp.getInt("eqMinC", Math.round(greenKm * 100).toInt())
        set(v) { sp.edit().putInt("eqMinC", v.coerceIn(10, 1990)).apply() }
    var eqMaxC: Int
        get() = sp.getInt("eqMaxC", maxOf(topMinC, eqMinC + 50))
        set(v) { sp.edit().putInt("eqMaxC", v.coerceIn(20, 2000)).apply() }

    var eqCor: Int
        get() = sp.getInt("eqCor", 0xFF39FF14.toInt())
        set(v) { sp.edit().putInt("eqCor", v).apply() }
    var eqMs: Int
        get() = sp.getInt("eqMs", 350)
        set(v) { sp.edit().putInt("eqMs", v.coerceIn(70, 1500)).apply() }

    // ---- Aviso "NÃO VOU" (lista negra e muitas paradas) e espaçamento
    var avisoLinha1: String
        get() = sp.getString("avisoLinha1", "CONDOMÍNIO") ?: "CONDOMÍNIO"
        set(v) { sp.edit().putString("avisoLinha1", v).apply() }
    var avisoLinha2: String
        get() = sp.getString("avisoLinha2", "NÃO VOU") ?: "NÃO VOU"
        set(v) { sp.edit().putString("avisoLinha2", v).apply() }
    var naoVouParadasOn: Boolean
        get() = sp.getBoolean("naoVouParadasOn", true)
        set(v) { sp.edit().putBoolean("naoVouParadasOn", v).apply() }
    var naoVouParadasMin: Int
        get() = sp.getInt("naoVouParadasMin", 2)
        set(v) { sp.edit().putInt("naoVouParadasMin", v.coerceIn(1, 6)).apply() }
    /** espaço entre o semáforo, as pílulas e o card do bairro (dp) */
    var espacoCards: Int
        get() = sp.getInt("espacoCards", 3)
        set(v) { sp.edit().putInt("espacoCards", v.coerceIn(0, 20)).apply() }

    var mercadoOn: Boolean
        get() = sp.getBoolean("mercadoOn", true)
        set(v) { sp.edit().putBoolean("mercadoOn", v).apply() }
    var mercadoLinha1: String
        get() = sp.getString("mercadoLinha1", "EMBARQUE NO MERCADO") ?: "EMBARQUE NO MERCADO"
        set(v) { sp.edit().putString("mercadoLinha1", v).apply() }

    fun paradasDemais(stops: Int) = naoVouParadasOn && stops >= naoVouParadasMin

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
        get() {
            val lida = try {
                val a = org.json.JSONArray(sp.getString("listaNegra", "[]"))
                List(a.length()) { val o = a.getJSONObject(it); Destino.Negra(o.getString("n"), o.optString("m")) }
            } catch (_: Exception) { emptyList() }
            // Lista padrão entra uma vez só; depois o que você apagar fica apagado.
            if (!sp.getBoolean("negraPadrao2", false)) {
                // atualiza os itens padrão (novos jeitos de escrever) sem mexer nos seus
                val chave = { n: Destino.Negra -> Destino.norm(n.nome.substringBefore("/")) }
                val padrao = Destino.PADRAO.associateBy(chave)
                val ja = sp.getBoolean("negraPadrao1", false)
                val atual = lida.map { n -> padrao[chave(n)]?.let { it.copy(motivo = n.motivo) } ?: n }
                val chaves = atual.map(chave).toSet()
                val junta = if (ja) atual else atual + Destino.PADRAO.filter { chave(it) !in chaves }
                sp.edit().putBoolean("negraPadrao1", true).putBoolean("negraPadrao2", true).apply()
                listaNegra = junta
                return junta
            }
            return lida
        }
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
    /** valor mínimo da corrida para ser TOP (centavos); abaixo disso nunca é TOP */
    var topMinValorC: Int
        get() = sp.getInt("topMinValorC", 1000)
        set(v) { sp.edit().putInt("topMinValorC", v.coerceIn(0, 20000)).apply() }

    fun isTop(a: Analysis): Boolean = topOn && a.verdict == Verdict.GREEN && a.perKm >= topMinC / 100.0 &&
        a.offer.value >= topMinValorC / 100.0

    /** Modelo visual do aplicativo (0 a 9). */
    var template: Int
        get() {
            // tela nova (modelos 6 + 3) vira o padrão uma vez; dá para voltar a outro modelo em Configurar
            if (!sp.getBoolean("homeV2", false)) sp.edit().putInt("template", 10).putBoolean("homeV2", true).apply()
            return sp.getInt("template", 10)
        }
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
