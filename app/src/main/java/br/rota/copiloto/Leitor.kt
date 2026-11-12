package br.rota.copiloto

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Regras de leitura da tela num lugar só: o serviço usa estas funções ao vivo e o
 * teste automático usa as mesmas, com prints reais, no reconhecimento de texto de verdade.
 */
object Leitor {
    val LISTA = Regex("""(?i)solicita[cç][oõ]es|buscando|radar de viagens""")
    /** Página de lista de corridas (dentro do app): aba Entregas, botão Ordenar. */
    private val PAGINA_LISTA = Regex("""(?i)\bentregas\b|\bordenar\b""")
    /** Etiqueta "Pop Nova", "Comfort Nova"... Uma só = tela de detalhe (pode mostrar). Duas ou mais = lista. */
    private val ETIQUETA_NOVA = Regex("""(?i)\b(negocia\w*|pop|comfort|top|moto|plus|entrega|corrida|econ[oô]mico)\s+nova\b""")
    private val PAR_TEMPO_KM = Regex("""(?i)\d+\s*min[^\d]{0,12}?\d+(?:[.,]\d+)?\s*k?m(?![a-z])""")
    private val BOTAO = Regex("""(?i)aceitar por|tenho interesse|\bescolh""")
    private val OFFER_BUTTON_WORDS = listOf("aceitar", "tenho interesse", "selecionar", "escolher", "tentando encontrar")

    fun hasOfferButton(text: String): Boolean = OFFER_BUTTON_WORDS.any { text.contains(it, ignoreCase = true) }

    /** Só a janela de oferta conta: lista de solicitações, abas do app e prints não. */
    fun naoEhJanelaDeOferta(t: String): Boolean =
        PAGINA_LISTA.containsMatchIn(t) || ETIQUETA_NOVA.findAll(t).count() >= 2 ||
            BOTAO.findAll(t).count() >= 2 || PAR_TEMPO_KM.findAll(t).count() >= 3

    /** Recorte usado ao vivo: tira o quarto de cima (mapa) e reduz para 60%. */
    fun recortar(bmp: Bitmap): Pair<Bitmap, Int> {
        val y0 = (bmp.height * 0.25f).toInt()
        val crop = Bitmap.createBitmap(bmp, 0, y0, bmp.width, bmp.height - y0)
        return Bitmap.createScaledBitmap(crop, (crop.width * 0.6f).toInt(), (crop.height * 0.6f).toInt(), true) to y0
    }

    /** Linhas na ordem da tela (de cima para baixo), sem o que o nosso quadro desenhou. */
    fun linhas(t: Text, meuQuadro: Rect?): String =
        t.textBlocks.flatMap { it.lines }
            .filter { l -> val b = l.boundingBox; b == null || meuQuadro == null || !Rect.intersects(b, meuQuadro) }
            .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
            .joinToString("\n") { it.text }

    /** Tira o que o próprio painel desenhou, para não ler a si mesmo. */
    fun limpar(raw: String): String {
        val hm = Regex("""\d+\s*h\s*\d{1,2}\s*m""")
        return raw.split("\n").filter { l ->
            val s = l.lowercase().trim()
            !(s.contains("lucro") || s.startsWith("$/") || s.startsWith("\$/") ||
                hm.containsMatchIn(s) ||
                (s.contains("/km") && s.contains("/h")) ||
                s.contains("vale a pena") || s.contains("no limite") || s.contains("não compensa"))
        }.joinToString(" | ")
    }

    /** Decide se o texto lido é uma oferta aberta. Devolve a oferta ou o motivo de não ser. */
    fun avaliar(joined: String, platform: String): Pair<Offer?, String> {
        if (LISTA.containsMatchIn(joined) && !hasOfferButton(joined)) return null to "lista sem botão"
        if (naoEhJanelaDeOferta(joined)) return null to "parece lista de várias ofertas"
        if (!hasOfferButton(joined)) return null to "sem botão Aceitar/Escolher"
        val o = OfferParser.parse(OfferParser.platformOf(joined, platform), joined) ?: return null to "não achei valor/distâncias"
        return o to "ok"
    }
}
