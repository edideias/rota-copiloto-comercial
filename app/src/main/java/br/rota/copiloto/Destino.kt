package br.rota.copiloto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Entende o endereço de destino da oferta: bairro, pavimentação da rua e lista negra.
 *
 * O banco de ruas (dados/campo-grande-ruas.json no repositório) vem dentro do app e é
 * atualizado uma vez por mês pela internet, sem precisar de APK novo.
 * Pavimentação: Agetran/Sisgran (Prefeitura de Campo Grande). Bairros: OpenStreetMap.
 */
object Destino {

    data class Rua(val nome: String, val pavimento: String, val bairro: String?, val condominio: String?)
    data class Negra(val nome: String, val motivo: String)
    data class Info(
        val bairro: String?,
        val rua: String?,
        val pavimento: String?,      // terra, misto (parte terra) ou null quando é asfalto/sem aviso
        val evitado: Boolean,
        val negra: Negra?,
        val condominio: String? = null,  // destino dentro de condomínio fechado
        val mercado: String? = null      // destino é mercado / supermercado
    ) {
        val vazio get() = bairro == null && rua == null
    }

    /** Locais que já vêm na lista negra (dá para apagar na configuração). "/" separa outros jeitos de escrever. */
    val PADRAO = listOf(
        Negra("Condomínio Dama / Damha / Villas Damha", "Não vou de jeito nenhum"),
        Negra("Dama 1 / Damha 1 / Damha I", "Não vou de jeito nenhum"),
        Negra("Dama 2 / Damha 2 / Damha II", "Não vou de jeito nenhum"),
        Negra("Dama 3 / Damha 3 / Damha III", "Não vou de jeito nenhum"),
        Negra("Village Paraty / Village Parati / Vilage Paraty", "Não vou de jeito nenhum"),
        Negra("Alphaville / Alphavile", "Não vou de jeito nenhum")
    )

    private val GENERICO = Regex("""^(condominio|cond|residencial|resid|loteamento|parque|jardim|vila|conjunto)\s+""")

    /** O local da lista aparece no destino? Compara palavra inteira, ignorando "Condomínio", "Residencial" etc. */
    private fun bate(nomeLista: String, destNorm: String): Boolean {
        val alvo = " $destNorm "
        return nomeLista.split("/").any { alt ->
            val k = norm(alt.substringBefore(",")).replace(GENERICO, "").trim()
            k.length >= 4 && alvo.contains(" $k ")
        }
    }

    private const val URL_BANCO =
        "https://raw.githubusercontent.com/edideias/rota-copiloto-comercial/main/dados/campo-grande-ruas.json"

    @Volatile private var ruas: Map<String, Rua> = emptyMap()
    @Volatile private var bairros: Map<String, String> = emptyMap()   // normalizado -> nome
    @Volatile private var condominios: Map<String, String> = emptyMap()   // normalizado -> nome
    data class Mercado(val nome: String, val rua: String, val num: Int?)
    @Volatile private var mercados: List<Mercado> = emptyList()
    @Volatile var dataBanco: String = ""
        private set
    @Volatile private var carregando = false

    val nomesBairros: List<String> get() = bairros.values.sorted()
    val pronto: Boolean get() = ruas.isNotEmpty()

    /** Carrega o banco em segundo plano (arquivo baixado, senão o que veio no app) e atualiza se tiver mais de 30 dias. */
    fun iniciar(ctx: Context) {
        if (carregando || pronto) return
        carregando = true
        Thread {
            try {
                val arq = File(ctx.filesDir, "ruas.json")
                val txt = if (arq.exists() && arq.length() > 1000) arq.readText()
                else ctx.assets.open("ruas.json").bufferedReader().use { it.readText() }
                ler(txt)
                val idadeDias = (System.currentTimeMillis() - (if (arq.exists()) arq.lastModified() else 0L)) / 86_400_000L
                if (idadeDias > 30) atualizar(ctx)
            } catch (e: Exception) {
                DebugLog.add("Banco de ruas: ${e.message}")
            } finally {
                carregando = false
            }
        }.start()
    }

    /** Baixa a versão mais nova do banco. Retorna true se conseguiu. */
    fun atualizar(ctx: Context): Boolean = try {
        val c = URL(URL_BANCO).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        val txt = c.inputStream.bufferedReader().use { it.readText() }
        if (txt.length > 1000 && txt.trimStart().startsWith("{")) {
            ler(txt)
            File(ctx.filesDir, "ruas.json").writeText(txt)
            DebugLog.add("Banco de ruas atualizado ($dataBanco).")
            true
        } else false
    } catch (e: Exception) {
        DebugLog.add("Banco de ruas sem atualização: ${e.message}"); false
    }

    private fun ler(txt: String) {
        val o = JSONObject(txt)
        val arr = o.getJSONArray("ruas")
        val m = HashMap<String, Rua>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val bs = r.optJSONArray("bairros")
            val nome = r.getString("nome")
            val cd = if (r.isNull("condominio")) null else r.optString("condominio").takeIf { it.isNotBlank() }
            m[norm(nome)] = Rua(nome, r.optString("pavimento"), if (bs != null && bs.length() > 0) bs.getString(0) else null, cd)
        }
        val bm = HashMap<String, String>()
        val ba = o.optJSONArray("bairros") ?: JSONArray()
        for (i in 0 until ba.length()) ba.getString(i).let { bm[norm(it)] = it }
        val cs = HashMap<String, String>()
        val ca = o.optJSONArray("condominios") ?: JSONArray()
        for (i in 0 until ca.length()) ca.getString(i).let { cs[norm(it)] = it }
        val ml = ArrayList<Mercado>()
        val ma = o.optJSONArray("mercados") ?: JSONArray()
        for (i in 0 until ma.length()) ma.getJSONObject(i).let { x ->
            ml.add(Mercado(x.optString("nome"), x.optString("rua"), if (x.isNull("num")) null else x.optInt("num")))
        }
        ruas = m; bairros = bm; condominios = cs; mercados = ml
        dataBanco = o.optString("gerado_em").take(10)
    }

    // ------------------------------------------------------------------ texto

    private val NUM = mapOf(
        "um" to "1", "primeiro" to "1", "dois" to "2", "tres" to "3", "quatro" to "4", "cinco" to "5", "seis" to "6",
        "sete" to "7", "oito" to "8", "nove" to "9", "dez" to "10", "onze" to "11", "doze" to "12", "treze" to "13",
        "quatorze" to "14", "catorze" to "14", "quinze" to "15", "dezesseis" to "16", "dezessete" to "17",
        "dezoito" to "18", "dezenove" to "19", "vinte" to "20", "trinta" to "30"
    )

    fun norm(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        t = t.replace(Regex("[^a-z0-9 ]"), " ")
        t = t.replace(Regex("\\b(r|rua)\\b"), "rua").replace(Regex("\\b(av|avenida)\\b"), "avenida")
            .replace(Regex("\\b(tv|trav|travessa)\\b"), "travessa").replace(Regex("\\b(al|alameda)\\b"), "alameda")
        t = t.split(Regex("\\s+")).joinToString(" ") { NUM[it] ?: it }
        // "vinte e cinco" -> "20 5" -> "25"
        t = t.replace(Regex("\\b([2-3])0 e ([1-9])\\b")) { "${it.groupValues[1]}${it.groupValues[2]}" }
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private val ruaRx = Regex("""(?i)\b(rua|r\.|avenida|av\.?|travessa|tv\.?|alameda|al\.|estrada|rodovia|via)\s+[^,|\-]{2,60}""")
    private val soNumero = Regex("""^\s*(n[º°o.]*\s*)?\d+[a-zA-Z]?\s*$""")

    /** Palavras de botão e de tela que nunca são bairro. */
    private val NAO_BAIRRO = Regex("""(?i)interesse|aceitar|recusar|verificad|corrida|passageiro|selecionar|confirmar|minuto|\bmin\b|\bkm\b|uberx|comfort|black|flash|negocia|pgto|dinheiro|cart[aã]o|pix|parada|campo grande|\bms\b|brasil|cep""")

    /** Bairro escrito no próprio destino: "Rua Igarapé, 92 , Guanandi", "..., 4747, Vila Planalto, Campo Grande" ou "..., 5089 - Centro". */
    private fun bairroDoTexto(dest: String): String? {
        val limpo = (Regex("""\s+-\s+""").find(dest)?.let { m -> dest.substring(0, m.range.first) + ", " + dest.substring(m.range.last + 1) } ?: dest)
        val partes = limpo.split(",").map { it.trim().trimEnd('.', '…').trim().replace(Regex("(?i)^bairro\\s+"), "") }
        // 1) qualquer parte que seja um bairro conhecido no banco (de trás para frente)
        for (p in partes.reversed()) {
            if (p.length < 3 || NAO_BAIRRO.containsMatchIn(p) || ruaRx.containsMatchIn(p)) continue
            val n = norm(p)
            bairros[n]?.let { return it }
            bairros.entries.firstOrNull { it.key.length >= 4 && (n == it.key || n.startsWith(it.key + " ")) }?.let { return it.value }
        }
        // 2) não está no banco: só confia no texto que vem logo depois do número da casa
        for (i in 1 until partes.size) {
            val p = partes[i]
            if (!soNumero.matches(partes[i - 1])) continue
            if (p.length < 3 || NAO_BAIRRO.containsMatchIn(p) || ruaRx.containsMatchIn(p) || soNumero.matches(p)) continue
            if (p.split(" ").size <= 4) return p
        }
        return null
    }

    /** Palavras que já dizem que o destino é mercado, mesmo sem estar no mapa. */
    private val CARA_DE_MERCADO = Regex("""(?i)\b(super\s?mercado|supermerc|mercado|mercadinho|mercearia|minimercado|atacad[aã]o|atacadista|assa[ií]|comper|carrefour|p[aã]o de a[cç][uú]car|hipermercado|hiper)\b""")
    private val MARCA_GENERICA = Regex("""(?i)^(mercado|supermercado|mercadinho|mercearia)$""")

    /** O destino é um mercado? Pelo nome escrito, por um mercado conhecido do mapa, ou pelo endereço (mesma rua, número perto). */
    private fun mercadoDe(dest: String, nd: String, ruaNorm: String?): String? {
        CARA_DE_MERCADO.find(dest)?.let { return dest.substringBefore(",").trim().ifBlank { it.value } }
        val alvo = " $nd "
        mercados.firstOrNull { mk ->
            val n = norm(mk.nome)
            n.length >= 5 && !MARCA_GENERICA.matches(mk.nome) && alvo.contains(" $n ")
        }?.let { return it.nome }
        if (ruaNorm != null) {
            val num = Regex("""(?:,|\s)\s*(\d{1,5})\b""").find(dest.substringAfter(ruaNorm.split(" ").last(), dest))?.groupValues?.get(1)?.toIntOrNull()
            if (num != null) mercados.firstOrNull { it.rua == ruaNorm && it.num != null && Math.abs(it.num - num) <= 30 }?.let { return it.nome }
        }
        return null
    }

    private val CARA_DE_CONDO = Regex("""(?i)condom|damha|alphavil|terras alpha|village|vilage|resid""")

    fun analisar(dest: String, p: Prefs, embarque: String = ""): Info {
        if (dest.isBlank()) return Info(null, null, null, false, null)
        val ruaTxt = ruaRx.find(dest)?.value?.trim()
        val rua = ruaTxt?.let { ruas[norm(it)] }
        val bairro = bairroDoTexto(dest) ?: rua?.bairro
        val nd = norm(dest)
        val nb = bairro?.let { norm(it) } ?: ""
        val evitado = p.bairrosEvitados.any { e ->
            val ne = norm(e)
            ne.isNotEmpty() && (nb == ne || (nb.isNotEmpty() && nb.startsWith("$ne ")) || (nb.isEmpty() && nd.contains(ne)))
        }
        // condomínio: pela rua (banco), pelo bairro com nome de condomínio, ou por um condomínio citado no texto
        val condominio = rua?.condominio
            ?: bairro?.takeIf { b -> condominios.containsKey(norm(b)) || CARA_DE_CONDO.containsMatchIn(b) }
            ?: condominios.entries.firstOrNull { it.key.length >= 6 && " $nd ".contains(" ${it.key} ") }?.value
        // lista negra olha o texto do destino, o bairro e o condomínio onde a rua fica
        val tudo = norm(listOfNotNull(dest, bairro, condominio, rua?.bairro).joinToString(" "))
        val negra = p.listaNegra.firstOrNull { bate(it.nome, tudo) }
        val pav = if (p.avisarTerra && (rua?.pavimento == "terra" || rua?.pavimento == "misto")) rua.pavimento else null
        // mercado só importa no EMBARQUE (destino em mercado não tem problema)
        val mercado = if (p.mercadoOn && embarque.isNotBlank())
            mercadoDe(embarque, norm(embarque), ruaRx.find(embarque)?.value?.trim()?.let { norm(it) }) else null
        return Info(bairro, rua?.nome ?: ruaTxt, pav, evitado, negra, condominio, mercado)
    }
}
