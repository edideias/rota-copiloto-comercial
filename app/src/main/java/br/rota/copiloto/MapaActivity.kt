package br.rota.copiloto

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout

/** Mapa do bairro, aberto pela notificação da escuta por voz (toque quando estiver parado). */
class MapaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val nome = i.getStringExtra("nome") ?: "Bairro"
        val l = Destino.Local(nome, i.getDoubleExtra("lat", 0.0), i.getDoubleExtra("lon", 0.0), 0.0, "")
        val origem = if (i.hasExtra("ox")) Destino.Local("Você", i.getDoubleExtra("ox", 0.0), i.getDoubleExtra("oy", 0.0), 0.0, "") else null
        val ui = Ui(this)
        val col = ui.vbox().apply { setBackgroundColor(C.BG); setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(12)) }
        col.addView(ui.tv(nome, 20f, C.TEXT, 3))
        col.addView(ui.tv(i.getStringExtra("frase") ?: "", 14f, C.MUTED, 0), ui.lp(top = 4))
        val mapa = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            loadDataWithBaseURL("https://localhost/", Mapa.html(origem, l), "text/html", "UTF-8", null)
        }
        col.addView(mapa, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).also { it.topMargin = ui.dp(10) })
        col.addView(ui.button("Fechar", false) { finish() }, ui.lp(top = 10))
        setContentView(col)
    }
}
