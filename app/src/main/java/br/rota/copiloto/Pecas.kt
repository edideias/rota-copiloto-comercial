package br.rota.copiloto

import android.graphics.Color

/**
 * Peças do semáforo visual. Cada uma tem interruptor, tamanhos e cores que o editor ajusta ao vivo.
 *
 * Os valores ficam em Prefs (chaves cp_*). Os padrões são exatamente o visual que já existia,
 * então quem não mexeu continua vendo o mesmo semáforo.
 */
object Pecas {
    /** Cor "automática": a peça usa a cor que o semáforo escolhe na hora. Não é uma cor de verdade. */
    const val AUTO = Int.MIN_VALUE

    /** Ajuste numérico: tamanho em %, espessura em dp, largura etc. */
    class Numero(
        val chave: String,
        val rotulo: String,
        val padrao: Int,
        val min: Int,
        val max: Int,
        val passo: Int = 10,
        val sufixo: String = "%",
        /** sem ajuste próprio, herda o valor de outra chave (ex.: km e valor herdam o tamanho dos números) */
        val herda: String? = null,
        /** texto mostrado no zero (ex.: "auto") */
        val zero: String? = null,
        /** falso = ajuste interno, usado só como herança; não aparece no editor */
        val visivel: Boolean = true
    ) {
        fun formata(v: Int): String = if (zero != null && v == 0) zero else "$v$sufixo"
    }

    /** Cor ajustável. [padrao] é AUTO quando a cor vem do semáforo; [auto] diz o que isso quer dizer. */
    class Cor(val chave: String, val rotulo: String, val padrao: Int, val auto: String? = null)

    class Peca(
        val id: String,
        val nome: String,
        val dica: String = "",
        /** chave do interruptor "Mostrar"; null = a peça não tem liga/desliga */
        val liga: String? = null,
        val numeros: List<Numero> = emptyList(),
        val cores: List<Cor> = emptyList()
    )

    private const val VERDE_AUTO = "verde, amarelo ou vermelho, pelo semáforo"

    val TODAS: List<Peca> = listOf(
        Peca("quadro", "Quadro", "Moldura e fundo que envolvem o semáforo. Com SUPER, a moldura pisca em neon.",
            numeros = listOf(
                Numero("margem", "Margem nas laterais", 8, 0, 40, 2, " dp"),
                Numero("respiro", "Respiro dentro do quadro", 10, 2, 30, 2, " dp"),
                Numero("espaco", "Espaço entre as linhas", 6, 0, 30, 2, " dp"),
                Numero("moldura", "Espessura da moldura", 4, 0, 14, 1, " dp"),
                Numero("raio", "Arredondamento", 24, 0, 48, 4, " dp")
            ),
            cores = listOf(
                Cor("fundo", "Fundo", Color.parseColor("#17142A")),
                Cor("cor_moldura", "Moldura", AUTO, VERDE_AUTO)
            )),
        Peca("tag", "Nome do app", "Uber, 99 e inDRIVE, cada um na sua cor.", liga = "tag",
            numeros = listOf(Numero("tamTag", "Tamanho", 100, 50, 200)),
            cores = listOf(
                Cor("cor_tag_Uber", "Uber", AUTO, "a cor padrão do app"),
                Cor("cor_tag_99", "99", AUTO, "a cor padrão do app"),
                Cor("cor_tag_inDriver", "inDRIVE", AUTO, "a cor padrão do app")
            )),
        Peca("parada", "Aviso de parada", "PARADA ou N PARADAS. Aparece quando a corrida tem parada.",
            numeros = listOf(Numero("tamParada", "Tamanho", 100, 50, 200)),
            cores = listOf(
                Cor("cor_parada_fundo", "Fundo", 0xFFE01010.toInt()),
                Cor("cor_parada_txt", "Texto", Color.WHITE)
            )),
        Peca("moldaParada", "Moldura piscante da parada", "O contorno que pisca quando há parada.",
            numeros = listOf(Numero("paradaEsp", "Espessura", 12, 4, 30, 2, " dp")),
            cores = listOf(Cor("cor_parada_moldura", "Cor", AUTO, "a cor escolhida em Parada"))),
        Peca("super", "Etiqueta SUPER", "O destaque da melhor corrida.",
            numeros = listOf(Numero("tamSuper", "Tamanho", 100, 50, 200)),
            cores = listOf(
                Cor("cor_super_fundo", "Fundo", 0xFF06210A.toInt()),
                Cor("cor_super_txt", "Texto", AUTO, "o neon do destaque")
            )),
        Peca("km", "Km total", "Pílula com os quilômetros da corrida.", liga = "km",
            numeros = listOf(Numero("tamKm", "Tamanho", 100, 50, 200, herda = "tamNumeros")),
            cores = listOf(
                Cor("corKm", "Fundo", Color.BLACK),
                Cor("cor_km_txt", "Texto", Color.WHITE)
            )),
        Peca("valor", "Valor", "Pílula com o valor da corrida.", liga = "valor",
            numeros = listOf(Numero("tamValor", "Tamanho", 100, 50, 200, herda = "tamNumeros")),
            cores = listOf(
                Cor("cor_valor", "Fundo", AUTO, VERDE_AUTO),
                Cor("cor_valor_txt", "Texto", AUTO, "preto ou branco, o que ler melhor")
            )),
        Peca("rskm", "R$ por km", "O número grande do lucro por quilômetro.", liga = "rsKm",
            numeros = listOf(Numero("tamRsKm", "Tamanho", 100, 50, 250)),
            cores = listOf(Cor("cor_rskm", "Número", AUTO, VERDE_AUTO))),
        Peca("rotulo", "Texto R$/km", "A palavra R$/km embaixo do número.", liga = "rsKmRotulo",
            numeros = listOf(Numero("tamRotulo", "Tamanho", 100, 50, 250)),
            cores = listOf(Cor("cor_rotulo", "Texto", Color.WHITE))),
        Peca("barra", "Barra de lucro", "Sobe e desce com o lucro. Aparece só em corrida verde.", liga = "eq",
            numeros = listOf(Numero("largEq", "Largura", 30, 14, 70, 4, "")),
            cores = listOf(Cor("cor_barra", "Cor", AUTO, "a cor escolhida em Barra de lucro"))),
        Peca("rua", "Caixa da rua", "A caixa com o nome da rua de destino.", liga = "rua",
            numeros = listOf(Numero("tamRua", "Tamanho do nome", 100, 50, 200)),
            cores = listOf(
                Cor("cor_rua_fundo", "Caixa", AUTO, VERDE_AUTO),
                Cor("cor_rua_txt", "Texto", AUTO, "branco ou preto, o que ler melhor")
            )),
        Peca("bairro", "Bairro", "A linha menor com o bairro, dentro da caixa da rua.", liga = "bairro",
            numeros = listOf(Numero("tamBairro", "Tamanho", 100, 50, 200))),
        Peca("icone", "Ícone do piso", "Terra ou asfalto. As cores do ícone são fixas, para não confundir.", liga = "icone",
            numeros = listOf(Numero("tamIcone", "Tamanho", 100, 50, 250))),
        Peca("saida", "Rua de embarque", "A linha de saída com o aviso SAÍDA.", liga = "bolaSaida",
            numeros = listOf(Numero("tamBolaSaida", "Tamanho", 100, 50, 250)),
            cores = listOf(
                Cor("cor_saida_fundo", "Fundo", Color.argb(205, 17, 20, 24)),
                Cor("cor_saida_txt", "Texto", Color.WHITE)
            )),
        Peca("faixa", "Faixa de mercado", "A faixa vermelha no embarque quando é mercado.", liga = "mercadoFaixa",
            numeros = listOf(Numero("tamFaixa", "Tamanho", 100, 50, 200)),
            cores = listOf(
                Cor("cor_faixa_fundo", "Fundo", 0xFFB91C1C.toInt()),
                Cor("cor_faixa_txt", "Texto", Color.WHITE)
            )),
        Peca("aviso", "Aviso grande (NÃO VOU)", "O aviso grande de destino bloqueado.",
            numeros = listOf(Numero("tamAviso", "Tamanho", 100, 50, 200)),
            cores = listOf(
                Cor("cor_aviso_fundo", "Fundo", 0xFFB91C1C.toInt()),
                Cor("cor_aviso_txt", "Texto", Color.WHITE)
            ))
    )

    /** Chaves internas: não aparecem no editor, mas servem de herança para outras peças. */
    private val OCULTOS = listOf(Numero("tamNumeros", "", 100, 50, 200, visivel = false))

    private val numeros: Map<String, Numero> = (TODAS.flatMap { it.numeros } + OCULTOS).associateBy { it.chave }
    private val cores: Map<String, Cor> = TODAS.flatMap { it.cores }.associateBy { it.chave }

    fun peca(id: String): Peca? = TODAS.firstOrNull { it.id == id }

    /** Valor atual de um ajuste numérico, com herança e com a espessura da parada, que mora em Prefs. */
    fun num(p: Prefs, chave: String): Int {
        val n = numeros[chave] ?: return p.cpI(chave, 100)
        val base = n.herda?.let { num(p, it) } ?: n.padrao
        return if (chave == "paradaEsp") p.paradaEsp else p.cpI(chave, base)
    }

    /** Grava um ajuste numérico (a espessura da parada vai para a configuração que já existia). */
    fun gravaNum(p: Prefs, chave: String, v: Int) {
        if (chave == "paradaEsp") p.paradaEsp = v else p.setCpI(chave, v)
    }

    /** Cor atual; null quando está em automática (a peça escolhe na hora, pelo semáforo). */
    fun cor(p: Prefs, chave: String): Int? {
        val v = p.cpI(chave, cores[chave]?.padrao ?: AUTO)
        return if (v == AUTO) null else v
    }

    /** Volta a peça ao visual original: aparece, tamanhos e cores no padrão. */
    fun restaurar(p: Prefs, pc: Peca) {
        pc.liga?.let { p.setCpB(it, true) }
        pc.numeros.forEach { n -> gravaNum(p, n.chave, n.herda?.let { h -> num(p, h) } ?: n.padrao) }
        pc.cores.forEach { p.setCpI(it.chave, it.padrao) }
    }
}
