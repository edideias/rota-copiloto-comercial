package br.rota.copiloto

/**
 * Regiões urbanas de Campo Grande, com as cores do mapa de referência.
 * Cada bairro rotulado no mapa entra na sua região. Os demais bairros recebem a região
 * do rotulado mais próximo, o que aproxima a divisão sem precisar de polígonos.
 */
object Regioes {
    /** Cores fortes, na ordem das regiões abaixo. */
    val CORES = intArrayOf(
        0xFFFDE047.toInt(), // Segredo: amarelo
        0xFFF472B6.toInt(), // Prosa: rosa
        0xFFEF4444.toInt(), // Centro: vermelho
        0xFFA855F7.toInt(), // Imbirussu: roxo
        0xFFF59E0B.toInt(), // Lagoa: laranja
        0xFF22C55E.toInt(), // Anhanduizinho: verde
        0xFF38BDF8.toInt()  // Bandeira: azul
    )

    private val ROTULADOS: List<Pair<Int, List<String>>> = listOf(
        0 to listOf("Nova Lima", "Mata do Segredo", "Nasser", "Seminário", "Coronel Antonino", "Monte Castelo", "José Abrão", "Nascente do Segredo"),
        1 to listOf("Novos Estados", "Estrela Dalva", "Chácara dos Poderes", "Carandá", "Veraneio", "Santa Fé", "Chácara Cachoeira", "Noroeste", "Margarida"),
        2 to listOf("Cruzeiro", "Centro", "Planalto", "Amambaí", "Bela Vista", "São Bento"),
        3 to listOf("Santo Amaro", "Panamá", "Popular", "Sobrinho", "Santo Antônio", "Nova Campo Grande", "Núcleo Industrial"),
        4 to listOf("Taveirópolis", "União", "São Conrado", "Leblon", "Tijuca", "Aero Rancho", "Batistão", "Caiobá", "Coophavila II", "Tarumã", "Bandeirantes", "Taquarussu"),
        5 to listOf("Piratininga", "Parati", "Pioneiros", "Centenário", "Aseado", "Los Angeles", "Centro-Oeste", "Jardim Paulista", "Dr Albuquerque", "Jacy"),
        6 to listOf("São Lourenço", "Tiradentes", "Vilas Boas", "Carlota", "Rita Vieira", "Universitário", "Moreninha", "Maria Aparecida Pedrossian", "Alves Pereira")
    )

    /** Região (índice em CORES) de cada ponto, na mesma ordem da lista recebida. */
    fun regiaoDos(pontos: List<Destino.Ponto>): IntArray {
        val rotulados = ArrayList<Triple<Double, Double, Int>>()
        val porNome = HashMap<String, Int>()
        for ((regiao, nomes) in ROTULADOS) for (n in nomes) porNome[Destino.norm(n)] = regiao
        for (p in pontos) porNome[Destino.norm(p.nome)]?.let { rotulados.add(Triple(p.lat, p.lon, it)) }
        return IntArray(pontos.size) { i ->
            val p = pontos[i]
            porNome[Destino.norm(p.nome)] ?: run {
                var melhor = 0
                var melhorD = Double.MAX_VALUE
                for ((la, lo, r) in rotulados) {
                    val d = (la - p.lat) * (la - p.lat) + (lo - p.lon) * (lo - p.lon)
                    if (d < melhorD) { melhorD = d; melhor = r }
                }
                melhor
            }
        }
    }
}
