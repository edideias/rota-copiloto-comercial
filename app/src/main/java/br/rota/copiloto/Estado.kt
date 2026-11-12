package br.rota.copiloto

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estado da leitura ao vivo, para o motorista ver na hora se o app está olhando a tela. */
object Estado {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))
    @Volatile var loopVivo = 0L
    @Volatile var appFrente = ""
    @Volatile var ultimaLeitura = 0L
    @Volatile var ultimoMotivo = ""
    @Volatile var ultimoTexto = ""
    @Volatile var ultimoPainel = 0L
    @Volatile var ultimoPainelTxt = ""
    @Volatile var ultimoErro = ""
    private val leituras = ArrayDeque<Long>()

    @Synchronized fun leitura(motivo: String, texto: String) {
        val t = SystemClock.uptimeMillis()
        ultimaLeitura = t; ultimoMotivo = motivo; ultimoTexto = texto.take(300)
        leituras.addLast(t)
        while (leituras.isNotEmpty() && t - leituras.first() > 60000) leituras.removeFirst()
    }

    @Synchronized private fun porMinuto(): Int {
        val t = SystemClock.uptimeMillis()
        while (leituras.isNotEmpty() && t - leituras.first() > 60000) leituras.removeFirst()
        return leituras.size
    }

    private fun ha(t: Long): String {
        if (t == 0L) return "nunca"
        val s = (SystemClock.uptimeMillis() - t) / 1000
        val agora = System.currentTimeMillis() - (SystemClock.uptimeMillis() - t)
        return "${fmt.format(Date(agora))} (há ${s}s)"
    }

    fun resumo(conectado: Boolean, ligadoNoSistema: Boolean): String {
        val vivo = loopVivo != 0L && SystemClock.uptimeMillis() - loopVivo < 5000
        return buildString {
            append("Acessibilidade ligada no Android: ").append(if (ligadoNoSistema) "SIM" else "NÃO  ← ligue de novo").append('\n')
            append("Serviço rodando agora: ").append(if (conectado) "SIM" else "NÃO  ← desligue e ligue a acessibilidade").append('\n')
            append("Leitura viva: ").append(if (vivo) "SIM" else "NÃO").append('\n')
            append("App na frente: ").append(appFrente.ifEmpty { "?" }).append('\n')
            append("Leituras da tela no último minuto: ").append(porMinuto()).append('\n')
            append("Última leitura: ").append(ha(ultimaLeitura)).append(" → ").append(ultimoMotivo.ifEmpty { "-" }).append('\n')
            append("Último semáforo: ").append(ha(ultimoPainel)).append(' ').append(ultimoPainelTxt).append('\n')
            if (ultimoErro.isNotEmpty()) append("Último erro: ").append(ultimoErro).append('\n')
            if (ultimoTexto.isNotEmpty()) append("\nÚltimo texto lido: ").append(ultimoTexto)
        }
    }
}
