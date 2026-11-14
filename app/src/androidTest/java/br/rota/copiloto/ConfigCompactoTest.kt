package br.rota.copiloto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Abre Configurar → Modo compacto e fotografa a página inteira. */
@RunWith(AndroidJUnit4::class)
class ConfigCompactoTest {
    private fun achar(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) achar(v.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun fotografar() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        Prefs(app).setCpB("bairro", false) // uma peça desligada, para ver o estado apagado
        val i = Intent(app, MainActivity::class.java).putExtra("abrir", "compacto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val act = inst.startActivitySync(i)
        Thread.sleep(2500)
        inst.runOnMainSync {
            val sv = achar(act.window.decorView) ?: return@runOnMainSync
            val c = sv.getChildAt(0)
            val w = sv.width
            c.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            c.layout(0, 0, w, c.measuredHeight)
            val h = c.measuredHeight.coerceAtMost(9000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bmp); cv.drawColor(C.BG); c.draw(cv)
            File(app.filesDir, "tela-config.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        Prefs(app).setCpB("bairro", true)
        act.finish()
    }
}
