package br.rota.copiloto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Desenha a tela inicial num bitmap (com ofertas de exemplo) para conferir o visual. */
@RunWith(AndroidJUnit4::class)
class TelaInicialTest {
    @Test
    fun desenhar() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        val prefs = Prefs(app)
        prefs.template
        Templates.apply(10)
        Destino.iniciar(app)
        listOf(
            Offer("99", 25.10, 0.9, 4.0, 14.8, 29.0),
            Offer("Uber", 22.08, 6.0, 14.0, 8.2, 31.0),
            Offer("99", 14.49, 3.8, 9.0, 10.2, 23.0),
            Offer("99", 8.80, 1.2, 3.0, 3.9, 8.0),
            Offer("99", 10.94, 1.5, 5.0, 8.5, 17.0)
        ).forEachIndexed { i, o -> Historico.add(app, Calc.analyze(o, prefs), listOf("Universitário", "Centro", "Mata do Jacinto", "Jardim Leblon", "Nova Lima")[i]) }
        Historico.marcarOnline(app)
        val out = app.filesDir
        for ((nome, ligado) in listOf("tela-ligada" to true, "tela-desligada" to false)) {
            prefs.enabled = ligado
            inst.runOnMainSync {
                val acts = HomeActions(prefs, ligado, OverlayCard.sample(prefs), "v3.17.0 · Android 13 · 7GB RAM\nXiaomi 23122PCD1G",
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                val v = HomeTemplates.build(app, 10, acts)
                val w = 1080; val h = 2400
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                v.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                File(out, "$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
