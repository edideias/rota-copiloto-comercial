package br.rota.copiloto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/** Desenha Diário, Mapa e Agenda com três semanas de corridas de exemplo e confere as contas. */
@RunWith(AndroidJUnit4::class)
class ModuloTelasTest {
    @Test
    fun desenharEConferir() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        File(app.filesDir, "diario.json").delete()
        Diario.limparMemoria()
        val prefs = Prefs(app)
        prefs.template
        Templates.apply(10)
        val bairros = listOf("Centro", "Universitário", "Jardim dos Estados", "Tiradentes", "Coronel Antonino", "Aero Rancho")
        val rnd = java.util.Random(7)
        val agora = System.currentTimeMillis()
        for (d in 20 downTo 1) {
            for (k in 0 until 6) {
                val cal = Calendar.getInstance().apply { timeInMillis = agora - d * 86_400_000L; set(Calendar.HOUR_OF_DAY, 7 + k * 2 + rnd.nextInt(2)); set(Calendar.MINUTE, rnd.nextInt(60)) }
                val b = bairros[(d + k) % bairros.size]
                val bom = b == "Centro" || b == "Jardim dos Estados"
                val km = 4.0 + rnd.nextInt(12)
                Diario.editar(app, Diario.Corrida(cal.timeInMillis, cal.timeInMillis, "99", b, bairros[rnd.nextInt(bairros.size)],
                    km * (if (bom) 2.1 else 1.3), km, (km * 2.2).toInt(), if (bom) 4 + rnd.nextInt(5) else 12 + rnd.nextInt(15), true))
            }
        }
        // hoje: online, 2 corridas confirmadas e 1 pendente vinda do "Peguei"
        Diario.mudarEstado(app, "online", agora - 150 * 60_000L)
        Diario.editar(app, Diario.Corrida(agora - 120 * 60_000L, agora - 120 * 60_000L, "99", "Centro", "Universitário", 25.10, 15.7, 29, 8, true))
        Diario.editar(app, Diario.Corrida(agora - 70 * 60_000L, agora - 70 * 60_000L, "99", "Universitário", "Tiradentes", 14.49, 14.0, 23, 12, true))
        val o = Offer("99", 22.08, 2.0, 5.0, 8.2, 21.0, pickupText = "Rua Bahia, 1491, Monte Castelo", destText = "Rua 26 de Agosto, 2315 - Centro")
        val pend = Diario.registrarDaOferta(app, Calc.analyze(o, prefs), "Monte Castelo", "Centro", agora - 10 * 60_000L)
        check(!pend.confirmada && pend.valor == 22.08) { "pendente errada" }
        // anterior: começou há 70 min e durou 23 → terminou há 47; Peguei há 10 → esperou 37
        check(pend.esperaMin == 37) { "espera ${pend.esperaMin}" }
        Diario.salvarEvento(app, Diario.Evento(1, "Show no Bosque Expo", "Show", "Bosque Expo", agora + 3600_000L, agora + 4 * 3600_000L))
        Diario.salvarIncentivo(app, Diario.Incentivo(2, "missao", "Multiplica 99 sexta", agora - 3 * 3600_000L, agora + 5 * 3600_000L, 10, 30.0))
        Diario.salvarIncentivo(app, Diario.Incentivo(3, "taxa_zero", "Taxa zero da noite", agora + 6 * 3600_000L, agora + 9 * 3600_000L, 0, 0.0))

        val r = Diario.resumo(app, prefs)
        check(r.corridas == 3) { "corridas ${r.corridas}" }
        check(Math.abs(r.total - (25.10 + 14.49 + 22.08)) < 0.01) { "total ${r.total}" }
        check(r.minOnline >= 149) { "online ${r.minOnline}" }
        val melhores = Diario.melhores(app)
        check(melhores.isNotEmpty() && (melhores[0].bairro == "Centro" || melhores[0].bairro == "Jardim dos Estados")) { "melhor ${melhores.firstOrNull()}" }
        // backup: CSV de volta não duplica
        val antes = Diario.corridas.size
        Diario.importarCsv(app, Diario.paraCsv())
        check(Diario.corridas.size == antes) { "csv duplicou: ${Diario.corridas.size} vs $antes" }
        Diario.limparMemoria(); File(app.filesDir, "diario.json").delete()
        Diario.importarJson(app, "{}")

        // recria do zero para desenhar
        Diario.limparMemoria()
        android.util.Log.i("LEITOR", "### contas do Diário OK (pendente espera ${pend.esperaMin} min, melhor ${melhores[0].rotulo()} ${melhores[0].porHora})")
    }

    @Test
    fun desenhar() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        val prefs = Prefs(app)
        Templates.apply(10)
        File(app.filesDir, "diario.json").delete()
        Diario.limparMemoria()
        val bairros = listOf("Centro", "Universitário", "Jardim dos Estados", "Tiradentes", "Coronel Antonino", "Aero Rancho")
        val rnd = java.util.Random(3)
        val agora = System.currentTimeMillis()
        for (d in 13 downTo 1) for (k in 0 until 6) {
            val cal = Calendar.getInstance().apply { timeInMillis = agora - d * 86_400_000L; set(Calendar.HOUR_OF_DAY, 6 + k * 3); set(Calendar.MINUTE, rnd.nextInt(60)) }
            val b = bairros[(d * 2 + k) % bairros.size]; val bom = b == "Centro" || b == "Jardim dos Estados"
            val km = 4.0 + rnd.nextInt(12)
            Diario.editar(app, Diario.Corrida(cal.timeInMillis, cal.timeInMillis, "99", b, bairros[rnd.nextInt(6)], km * (if (bom) 2.1 else 1.3), km, (km * 2.2).toInt(), if (bom) 5 else 18, true))
        }
        Diario.mudarEstado(app, "online", agora - 150 * 60_000L)
        Diario.editar(app, Diario.Corrida(agora - 120 * 60_000L, agora - 120 * 60_000L, "99", "Centro", "Universitário", 25.10, 15.7, 29, 8, true))
        Diario.editar(app, Diario.Corrida(agora - 70 * 60_000L, agora - 70 * 60_000L, "99", "Universitário", "Tiradentes", 14.49, 14.0, 23, 12, true))
        val o = Offer("99", 22.08, 2.0, 5.0, 8.2, 21.0)
        Diario.registrarDaOferta(app, Calc.analyze(o, prefs), "Monte Castelo", "Centro", agora - 10 * 60_000L)
        Diario.salvarEvento(app, Diario.Evento(1, "Show no Bosque Expo", "Show", "Bosque Expo", agora + 3600_000L, agora + 4 * 3600_000L))
        Diario.salvarIncentivo(app, Diario.Incentivo(2, "missao", "Multiplica 99 sexta", agora - 3 * 3600_000L, agora + 5 * 3600_000L, 10, 30.0))
        Diario.salvarIncentivo(app, Diario.Incentivo(3, "taxa_zero", "Taxa zero da noite", agora + 6 * 3600_000L, agora + 9 * 3600_000L, 0, 0.0))

        inst.runOnMainSync {
            val ui = Ui(app)
            val m = Modulo(app, ui, prefs, {})
            for ((nome, v) in listOf("diario" to m.diario(), "mapa" to m.mapa(), "agenda" to m.agenda())) {
                val wrap = LinearLayout(app).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(C.BG); setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16)); addView(v) }
                val w = 1080
                wrap.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val h = wrap.measuredHeight.coerceIn(400, 6000)
                wrap.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                wrap.draw(Canvas(bmp))
                File(app.filesDir, "tela-$nome.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val acts = HomeActions(prefs, true, OverlayCard.sample(prefs), "v3.18.0", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            val v = HomeTemplates.build(app, 10, acts)
            v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, 1080, 2400)
            val bmp = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(app.filesDir, "tela-inicio.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
