package br.rota.copiloto

import org.json.JSONObject

/**
 * Textos editáveis ao vivo. Cada texto fixo do app é identificado pelo próprio texto original
 * ("ver todas", "Configurar"...). Quando o usuário troca um texto, guardamos original -> novo
 * num único campo de preferências. Textos com valor (R$ 12,40, 3,75 km, "5 de 8") não entram:
 * são dados, não rótulos.
 */
object Textos {
    const val CHAVE = "txt_map"

    private val valor = Regex("\\d[.,]\\d|\\d+ de \\d+|%")

    /** Trocas aplicadas agora (do rascunho, se houver tela de configuração aberta). */
    @Volatile var mapa: Map<String, String> = emptyMap()

    /** Modo editar: ligado, cada texto fixo vira alvo de toque. Não é salvo: volta ao normal ao reabrir o app. */
    @Volatile var editando = false

    /** Chamado no modo editar: (texto original, texto mostrado agora). */
    var onEditar: ((String, String) -> Unit)? = null

    fun editavel(original: String): Boolean =
        original.isNotBlank() && original.length <= 200 && !valor.containsMatchIn(original)

    fun de(original: String): String =
        if (editavel(original)) mapa[original] ?: original else original

    fun carregar(json: String?) {
        mapa = parse(json)
    }

    fun parse(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.optString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun serializar(m: Map<String, String>): String = JSONObject(m).toString()
}
