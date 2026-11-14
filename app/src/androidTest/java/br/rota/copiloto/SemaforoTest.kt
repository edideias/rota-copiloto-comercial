package br.rota.copiloto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Desenha o semáforo completo e o compacto lado a lado, com ofertas de exemplo. */
@RunWith(AndroidJUnit4::class)
class SemaforoTest {
    @Test
    fun desenhar() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        val prefs = Prefs(app)
        Destino.iniciar(app)
        Thread.sleep(4000) // banco de ruas carrega em segundo plano
        val ofertas = listOf(
            Offer("99", 31.70, 2.1, 6.0, 15.9, 29.0, destText = "Avenida Senador Antônio Mendes Canale, 1500, Condomínio Residencial Parque dos Poderes"),
            Offer("99", 23.40, 5.0, 11.0, 12.2, 22.0, pickupText = "Rua Pres. Artur Bernardes, 1096, Vila Santo Amaro", destText = "Rua Dos Cafezeiros, 64, Jardim São Conrado"),
            Offer("99", 19.90, 0.995, 4.0, 6.7, 16.0, pickupText = "Shopping Norte Sul Plaza, Avenida Pres. Ernesto Geisel, 2300 - Jardim Joquei Club", destText = "RUA_ASFALTO, 120 - Centro"),
            Offer("99", 12.40, 1.5, 5.0, 5.2, 14.0, pickupText = "Rua Bahia, 1491, Monte Castelo", destText = "RUA_TERRA, 55"),
            Offer("Uber", 8.10, 2.4, 6.0, 3.3, 9.0, pickupText = "Supermercado Comper, Avenida Afonso Pena, 4000", destText = "Rua 26 de Agosto, 2315 - Centro", stops = 1)
        )
        val terra = intent("TERRA"); val asf = intent("ASF")
        for (modo in listOf(false, true)) {
            prefs.compacto = modo
            inst.runOnMainSync {
                val ui = Ui(app)
                val col = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#2B3340")); setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)) }
                ofertas.forEach { o0 ->
                    val o = o0.copy(destText = o0.destText.replace("RUA_ASFALTO", asf).replace("RUA_TERRA", terra))
                    col.addView(OverlayCard.build(app, Calc.analyze(o, prefs), prefs), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = ui.dp(18) })
                }
                col.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                col.layout(0, 0, 1080, col.measuredHeight)
                val bmp = Bitmap.createBitmap(1080, col.measuredHeight, Bitmap.Config.ARGB_8888)
                col.draw(Canvas(bmp))
                File(app.filesDir, if (modo) "tela-compacto.png" else "tela-completo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        prefs.compacto = false
    }

    private fun intent(t: String) = if (t == "TERRA") NOME_TERRA else NOME_ASF
    companion object { var NOME_TERRA = "Rua 13 de Novembro"; var NOME_ASF = "Rua 11 de Outubro" }
}
