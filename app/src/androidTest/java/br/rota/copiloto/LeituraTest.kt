package br.rota.copiloto

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Test
import org.junit.runner.RunWith

/** Passa prints reais do motorista pelo mesmo caminho da leitura ao vivo e registra o resultado de cada um. */
@RunWith(AndroidJUnit4::class)
class LeituraTest {
    @Test
    fun lerPrints() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val testCtx = inst.context
        val app = inst.targetContext
        Destino.iniciar(app)
        val prefs = Prefs(app)
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val nomes = testCtx.assets.list("prints")!!.sorted()
        for (n in nomes) {
            val bmp = testCtx.assets.open("prints/$n").use { BitmapFactory.decodeStream(it) }
            val (small, _) = Leitor.recortar(bmp)
            val t = Tasks.await(rec.process(InputImage.fromBitmap(small, 0)))
            val joined = Leitor.limpar(Leitor.linhas(t, null))
            val (o, motivo) = Leitor.avaliar(joined, "99")
            var painel = ""
            if (o != null) {
                inst.runOnMainSync {
                    painel = try { OverlayCard.build(app, Calc.analyze(o, prefs), prefs); "painel ok" } catch (e: Throwable) { "PAINEL ERRO: $e" }
                }
            }
            val res = if (o == null) "NAO LEU ($motivo)" else "LEU ${o.platform} R$${o.value} emb ${"%.2f".format(o.pickupKm)}km corrida ${"%.2f".format(o.tripKm)}km paradas ${o.stops} | $painel"
            Log.i("LEITOR", "### $n => $res")
            Log.i("LEITOR", "    texto: ${joined.take(700)}")
        }
    }
}
