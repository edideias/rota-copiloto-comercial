package br.rota.copiloto

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.MediaStore
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

/**
 * Serviço SOMENTE LEITURA.
 * - Lê os textos da tela do Uber Driver / 99 Motorista.
 * - Calcula e desenha um painel por cima.
 * - NUNCA chama performAction, nunca faz gesto, nunca aceita ou recusa corrida.
 */
class CopilotService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CopilotService? = null

        /**
         * Só considera tela de oferta se tiver o botão da oferta (evita painel em telas de histórico).
         * 99: "Aceitar (1s)".  Uber: "Tenho interesse" (ou "Aceitar", em outras versões).
         */
        private const val REQUIRE_OFFER_BUTTON = true
        private val LISTA = Regex("""(?i)solicita[cç][oõ]es|buscando|radar de viagens""")
        /** Página de lista de corridas (dentro do app): várias ofertas, etiqueta "Nova", aba Entregas, botão Escolher. */
        private val PAGINA_LISTA = Regex("""(?i)\bentregas\b|\bordenar\b""")
        /** Etiqueta "Pop Nova", "Comfort Nova"... Uma só = tela de detalhe (pode mostrar). Duas ou mais = lista. */
        private val ETIQUETA_NOVA = Regex("""(?i)\b(negocia\w*|pop|comfort|top|moto|plus|entrega|corrida|econ[oô]mico)\s+nova\b""")
        private val PAR_TEMPO_KM = Regex("""(?i)\d+\s*min[^\d]{0,12}?\d+(?:[.,]\d+)?\s*k?m(?![a-z])""")
        private val BOTAO = Regex("""(?i)aceitar por|tenho interesse|\bescolh""")
        /** Só a janela de oferta conta: lista de solicitações, abas do app e prints não. */
        private fun naoEhJanelaDeOferta(t: String): Boolean =
            PAGINA_LISTA.containsMatchIn(t) || ETIQUETA_NOVA.findAll(t).count() >= 2 || BOTAO.findAll(t).count() >= 2 || PAR_TEMPO_KM.findAll(t).count() >= 3
        private val OFFER_BUTTON_WORDS = listOf("aceitar", "tenho interesse", "selecionar", "escolher")

        private fun hasOfferButton(text: String): Boolean =
            OFFER_BUTTON_WORDS.any { text.contains(it, ignoreCase = true) }

    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { dismiss() }

    private var lastRun = 0L
    private var lastSnapshot = ""
    private var lastSnapshotLog = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        DebugLog.add("Serviço conectado.")
        Destino.iniciar(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        DebugLog.seen(pkg)
        // Qual app está na frente: o 99 fica "mudo" enquanto espera corrida, então a leitura
        // por imagem continua enquanto ele estiver aberto, e para quando você troca de app.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName &&
            !pkg.startsWith("com.android.systemui") && !pkg.contains("inputmethod", true) && !pkg.contains("keyboard", true)) {
            fgPkg = pkg
            val pf = platformOf(pkg)
            // saiu do Uber/99 (galeria, prints, outro app): nada de leitura e o quadro some
            if (pf == null && attached && !shownSimulated) hide()
            if (pf != null && prefs.useOcr && ((pf == "99" && prefs.use99) || (pf == "Uber" && prefs.useUber))) { lastTargetEvent = SystemClock.uptimeMillis(); ensureOcrLoop() }
        }
        val platform = platformOf(pkg) ?: return
        if (!prefs.enabled || !prefs.calcEnabled) return
        if (platform == "Uber" && !prefs.useUber) return
        if (platform == "99" && !prefs.use99) return

        val now = SystemClock.uptimeMillis()
        if (platform == "99") {
            lastTargetEvent = now
            if (prefs.useOcr) ensureOcrLoop()
        }
        if (now - lastRun < 250) return
        lastRun = now

        // 1) Texto de todas as janelas do app (a oferta pode ficar numa janela separada).
        val texts = gatherTexts(pkg, event)
        val joined = texts.joinToString(" | ")
        if (texts.isEmpty()) {
            diag("[$platform] pkg=$pkg: nenhum texto legível nas janelas do app")
        } else {
            logSnapshot(platform, joined)
            if ((!REQUIRE_OFFER_BUTTON || hasOfferButton(joined)) && !(LISTA.containsMatchIn(joined) && !hasOfferButton(joined)) && !naoEhJanelaDeOferta(joined)) {
                val offer = OfferParser.parse(OfferParser.platformOf(joined, platform), joined)
                if (offer != null) {
                    show(Calc.analyze(offer, prefs), -1L)
                    return
                }
            }
            // a oferta deste app saiu da tela: o quadro some na hora
            if (attached && !shownSimulated && prefs.sumirJunto && shownKey.startsWith("$platform|") && !hasOfferButton(joined)) {
                hide(); return
            }
        }

        // 2) Plano B: lê a oferta por imagem (OCR no próprio aparelho).
        if (prefs.useOcr) tryOcr(platform)
    }

    // ---------- leitura por imagem (OCR) ----------

    // O 99 não avisa a acessibilidade quando a oferta aparece. Por isso, enquanto o 99
    // estiver ativo, a leitura por imagem roda por tempo (a cada ~1,5 s), não por evento.
    private var lastTargetEvent = 0L
    @Volatile private var meuQuadro: android.graphics.Rect? = null
    private var fgPkg = ""
    private var ocrLoopRunning = false
    private val ocrLoop = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val fg = platformOf(fgPkg)
            val ligado = (fg == "99" && prefs.use99) || (fg == "Uber" && prefs.useUber)
            if (prefs.enabled && prefs.calcEnabled && prefs.useOcr && (ligado || (fgPkg.isEmpty() && now - lastTargetEvent < 20000))) {
                tryOcr(fg ?: "99")
                handler.postDelayed(this, 450)
            } else {
                ocrLoopRunning = false
            }
        }
    }

    private fun ensureOcrLoop() {
        if (ocrLoopRunning) return
        ocrLoopRunning = true
        handler.post(ocrLoop)
    }

    private var lastOcrNote = 0L
    private var lastBrightness = ""

    /** Mensagens do OCR com limite próprio (não somem atrás de outros avisos). */
    private fun ocrNote(msg: String) {
        val t = SystemClock.uptimeMillis()
        if (t - lastOcrNote < 2000) return
        lastOcrNote = t
        DebugLog.add(msg)
    }

    private fun brightnessInfo(b: Bitmap): String {
        var sum = 0L
        var n = 0
        val sx = maxOf(1, b.width / 20)
        val sy = maxOf(1, b.height / 20)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val p = b.getPixel(x, y)
                sum += ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
                n++
                x += sx
            }
            y += sy
        }
        return "imagem ${b.width}x${b.height}, brilho ${if (n > 0) sum / n else 0}"
    }

    private fun screenshotError(code: Int): String = when (code) {
        1 -> "erro interno"
        2 -> "sem permissão de acessibilidade"
        3 -> "intervalo curto"
        4 -> "tela inválida"
        5 -> "janela inválida"
        6 -> "janela protegida (o app bloqueia captura)"
        else -> "código $code"
    }

    private var lastOcr = 0L
    private var lastOcrLog = 0L
    private var ocrBusy = false
    private var ocrAnnounced = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun tryOcr(platform: String) {
        if (Build.VERSION.SDK_INT < 30) {
            diag("Leitura por imagem exige Android 11 ou mais novo.")
            return
        }
        val now = SystemClock.uptimeMillis()
        if (ocrBusy && now - lastOcr > 6000) ocrBusy = false
        if (ocrBusy || now - lastOcr < 400) return
        lastOcr = now
        ocrBusy = true
        if (!ocrAnnounced) {
            ocrAnnounced = true
            DebugLog.add("Leitura por imagem (OCR) ligada.")
        }
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                            if (hw == null) {
                                hb.close()
                                ocrBusy = false
                                ocrNote("OCR: não consegui montar a imagem da tela")
                                return
                            }
                            val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            hb.close()
                            if (bmp == null) {
                                ocrBusy = false
                                return
                            }
                            lastBrightness = brightnessInfo(bmp)
                            // So a parte de baixo (onde fica o painel da oferta) e em escala menor: OCR bem mais rapido.
                            val y0 = (bmp.height * 0.25f).toInt()
                            val crop = Bitmap.createBitmap(bmp, 0, y0, bmp.width, bmp.height - y0)
                            val small = Bitmap.createScaledBitmap(crop, (crop.width * 0.6f).toInt(), (crop.height * 0.6f).toInt(), true)
                            // onde está o nosso quadro, nas coordenadas da imagem reduzida (para não lermos a nós mesmos)
                            meuQuadro = overlay?.takeIf { attached && it.isShown }?.let { v ->
                                val xy = IntArray(2); v.getLocationOnScreen(xy)
                                android.graphics.Rect(((xy[0]) * 0.6f).toInt(), ((xy[1] - y0) * 0.6f).toInt(),
                                    ((xy[0] + v.width) * 0.6f).toInt(), ((xy[1] - y0 + v.height) * 0.6f).toInt())
                            }
                            if (crop !== bmp) bmp.recycle()
                            recognizer.process(InputImage.fromBitmap(small, 0))
                                .addOnSuccessListener { t ->
                                    ocrBusy = false
                                    // linhas na ordem da tela (de cima para baixo), sem o que o nosso quadro desenhou
                                    val linhas = t.textBlocks.flatMap { it.lines }
                                        .filter { l -> val b = l.boundingBox; b == null || meuQuadro == null || !android.graphics.Rect.intersects(b, meuQuadro!!) }
                                        .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / 12 }, { it.boundingBox?.left ?: 0 }))
                                    onOcrText(platform, linhas.joinToString("\n") { it.text })
                                }
                                .addOnFailureListener { e ->
                                    ocrBusy = false
                                    ocrNote("OCR falhou: ${e.message}")
                                }
                        } catch (e: Exception) {
                            ocrBusy = false
                            ocrNote("OCR erro: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        ocrBusy = false
                        if (errorCode != 3) ocrNote("Screenshot recusado: ${screenshotError(errorCode)}")
                    }
                }
            )
        } catch (e: Exception) {
            ocrBusy = false
            ocrNote("OCR não iniciou: ${e.message}")
        }
    }

    private fun onOcrText(platform: String, raw: String) {
        // Tira o que o próprio painel desenhou, para não ler a si mesmo.
        val hm = Regex("""\d+\s*h\s*\d{1,2}\s*m""")
        val lines = raw.split("\n").filter { l ->
            val s = l.lowercase().trim()
            !(s.contains("lucro") || s.startsWith("$/") || s.startsWith("\$/") ||
                hm.containsMatchIn(s) ||
                (s.contains("/km") && s.contains("/h")) ||
                s.contains("vale a pena") || s.contains("no limite") || s.contains("não compensa"))
        }
        val joined = lines.joinToString(" | ")

        val t = SystemClock.uptimeMillis()
        if (t - lastOcrLog > 2500) {
            lastOcrLog = t
            val size = if (joined.contains("km", ignoreCase = true)) 600 else 120
            DebugLog.add("[$platform] OCR ($lastBrightness): ${joined.take(size)}")
        }

        val plat = OfferParser.platformOf(joined, platform)
        val lida = OfferParser.parse(plat, joined)
        // parada lida em qualquer quadro da mesma oferta fica valendo
        val offer = lida?.let { o ->
            val k = "${o.value}|${o.pickupKm}|${o.tripKm}"
            if (k != paradaKey) { paradaKey = k; paradaMax = 0 }
            paradaMax = maxOf(paradaMax, o.stops)
            if (paradaMax > o.stops) o.copy(stops = paradaMax) else o
        }
        // Oferta só conta quando está aberta (com botão Aceitar / Tenho interesse).
        // Na lista "Solicitações" do 99 (ou no "Radar de Viagens" do Uber) ela ainda não abriu.
        val naLista = (LISTA.containsMatchIn(joined) && !hasOfferButton(joined)) || naoEhJanelaDeOferta(joined)
        // O próprio semáforo cobre parte do cartão e o texto embaixo dele não é lido.
        // Enquanto o botão da oferta continua na tela, a oferta continua: leitura pela metade não esconde
        // o quadro nem troca os números. Só uma leitura completa (com o embarque) pode atualizar.
        if (attached && !shownSimulated && !naLista && hasOfferButton(joined) && (offer == null || offer.pickupKm <= 0.0)) {
            noOfferFrames = 0
            return
        }
        if (offer == null || naLista || !(hasOfferButton(joined) || offer.pickupKm > 0.0)) {
            // Sem oferta na tela: depois de alguns quadros seguidos, some com o painel antigo.
            pendingKey = ""; pendingCount = 0
            // sem nenhum "x min / y km" na tela a oferta já saiu: some na hora; se ainda há sinais dela, confirma com mais uma leitura
            val restos = Regex("""(?i)\d+\s*min""").containsMatchIn(joined) && Regex("""(?i)\d\s*k?m\b""").containsMatchIn(joined)
            if (++noOfferFrames >= (if (restos) 2 else 1) && attached && !shownSimulated && prefs.sumirJunto) hide()
            return
        }
        noOfferFrames = 0
        // So mostra quando a mesma leitura se repete em quadros seguidos (evita ler a oferta
        // no meio da animacao) e, se faltar a busca ate o passageiro, espera ainda mais.
        val key = "${offer.value}|${offer.pickupKm}|${offer.tripKm}"
        if (key == pendingKey) pendingCount++ else { pendingKey = key; pendingCount = 1 }
        val need = if (offer.pickupKm > 0.0) 2 else 4
        if (pendingCount < need) return
        show(Calc.analyze(offer, prefs), -1L)
    }

    private var pendingKey = ""
    private var pendingCount = 0
    private var noOfferFrames = 0
    private var paradaKey = ""
    private var paradaMax = 0
    private var shownSimulated = false

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        handler.removeCallbacks(ocrLoop)
        ocrLoopRunning = false
        hide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hide()
        instance = null
        super.onDestroy()
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 40) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) out.add(t.trim())
        val d = node.contentDescription?.toString()
        if (!d.isNullOrBlank() && d != t) out.add(d.trim())
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    /** Aceita variações do nome do pacote (Uber Driver e 99 Motorista). */
    private fun platformOf(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            p.contains("uber") && p.contains("driver") -> "Uber"
            p.contains("99") -> "99"
            else -> null
        }
    }

    /** Junta os textos de TODAS as janelas do app (a oferta pode estar numa janela separada). */
    private fun gatherTexts(pkg: String, event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        val seenWindows = HashSet<Int>()
        fun addRoot(r: AccessibilityNodeInfo?) {
            if (r == null || r.packageName?.toString() != pkg) return
            if (!seenWindows.add(r.windowId)) return
            collect(r, out, 0)
        }
        addRoot(rootInActiveWindow)
        try {
            for (w in windows) addRoot(w.root)
        } catch (_: Exception) {
        }
        if (out.isEmpty()) event.source?.let { collect(it, out, 0) }
        return out
    }

    private var lastDiag = 0L
    private var lastPlainLog = 0L

    private fun diag(msg: String) {
        val t = SystemClock.uptimeMillis()
        if (t - lastDiag < 3000) return
        lastDiag = t
        DebugLog.add(msg)
    }

    private fun logSnapshot(platform: String, joined: String) {
        if (!joined.contains("R$")) {
            val t = SystemClock.uptimeMillis()
            if (t - lastPlainLog > 4000) {
                lastPlainLog = t
                DebugLog.add("[$platform] tela sem R\$ (${joined.length} caracteres): ${joined.take(200)}")
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        if (joined == lastSnapshot || now - lastSnapshotLog < 2000) return
        lastSnapshot = joined
        lastSnapshotLog = now
        val parsed = OfferParser.parse(platform, joined)
        val tag = if (parsed != null) {
            "OK valor=${fmt(parsed.value)} busca=${parsed.pickupKm}km/${parsed.pickupMin.toInt()}min " +
                "viagem=${parsed.tripKm}km/${parsed.tripMin.toInt()}min"
        } else "SEM LEITURA"
        DebugLog.add("[$platform] $tag\n$joined")
    }

    /** Mostra uma oferta de teste (botão "Testar painel"). */
    fun showSimulation() {
        val p = Prefs(this)
        val offer = Offer("Simulação", 18.50, 1.2, 4.0, 6.8, 17.0)
        show(Calc.analyze(offer, p), 6000L)
    }

    private var overlay: FrameLayout? = null
    private var overlayLp: WindowManager.LayoutParams? = null
    private var attached = false
    private var lastOfferKey = ""
    private var lastOfferAt = 0L

    private var shownKey = ""
    private var dismissedKey = ""
    private var dismissedAt = 0L

    /** Fecha o quadro (por tempo ou por toque) e não deixa a mesma oferta reaparecer logo em seguida. */
    private fun dismiss() {
        dismissedKey = shownKey
        dismissedAt = SystemClock.uptimeMillis()
        hide()
    }

    /** holdMs < 0 usa o tempo configurado (0 = até tocar, no máximo 60 s). */
    private fun show(a: Analysis, holdMs: Long) {
        try {
            val o = a.offer
            val key = "${o.platform}|${o.value}|${o.pickupKm}|${o.tripKm}|${o.stops}|${Destino.analisar(o.destText, prefs, o.pickupText).let { it.bairro ?: it.rua ?: "" }}"
            val now = SystemClock.uptimeMillis()
            val simulated = holdMs >= 0
            if (simulated) {
                dismissedKey = ""
            } else {
                if (key == dismissedKey && now - dismissedAt < 30000) return
                if (attached && key == shownKey) return // mesma oferta, já na tela
            }
            shownKey = key
            shownSimulated = simulated

            val box = overlay ?: FrameLayout(this).also { overlay = it }
            box.removeAllViews()
            // nunca cortado: se não couber entre a posição escolhida e a borda da tela, o quadro encolhe por igual
            val dm = resources.displayMetrics
            val maxW = dm.widthPixels - dp(24)
            val maxH = dm.heightPixels - dp(prefs.overlayTopDp.toInt()) - dp(24)
            box.addView(Encaixe(this, maxW, maxH).apply { addView(OverlayCard.build(this@CopilotService, a, prefs)) })
            box.setOnClickListener { dismiss() }

            var lp = overlayLp
            if (lp == null) {
                lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                overlayLp = lp
            }
            lp.gravity = Gravity.TOP or when (prefs.cardPos) {
                0 -> Gravity.START
                2 -> Gravity.END
                else -> Gravity.CENTER_HORIZONTAL
            }
            lp.x = dp(12)
            lp.y = dp(prefs.overlayTopDp.toInt())
            if (prefs.telaTopOn && prefs.isTop(a)) mostrarContorno() else tirarContorno()
            if (!attached) {
                wm.addView(box, lp)
                attached = true
            } else {
                wm.updateViewLayout(box, lp)
            }
            val hold = if (simulated) holdMs else (if (prefs.holdSec <= 0) 60000L else prefs.holdSec * 1000L)
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, hold)
            onOffer(a)
        } catch (e: Exception) {
            DebugLog.add("Erro ao mostrar painel: ${e.message}")
        }
    }

    // ---------- contorno na tela inteira (corrida TOP) ----------
    private var contorno: View? = null
    private var contornoAnim: android.animation.ValueAnimator? = null

    private fun mostrarContorno() {
        tirarContorno()
        try {
            val cor = prefs.topCorInt
            val esp = dp(prefs.telaTopEsp)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.TRANSPARENT); cornerRadius = dp(prefs.telaTopRaio).toFloat(); setStroke(esp, cor)
            }
            val v = View(this).apply { background = bg }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            wm.addView(v, lp)
            contorno = v
            contornoAnim = android.animation.ValueAnimator.ofFloat(1f, 0.2f).apply {
                duration = prefs.topMs.toLong()
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                addUpdateListener { bg.setStroke(esp, Color.argb((255 * (it.animatedValue as Float)).toInt(), Color.red(cor), Color.green(cor), Color.blue(cor))) }
                start()
            }
        } catch (e: Exception) {
            DebugLog.add("Contorno da tela: ${e.message}")
        }
    }

    private fun tirarContorno() {
        contornoAnim?.cancel(); contornoAnim = null
        contorno?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        contorno = null
    }

    private fun hide() {
        tirarContorno()
        handler.removeCallbacks(hideRunnable)
        val box = overlay ?: return
        if (attached) {
            try { wm.removeView(box) } catch (_: Exception) {}
            attached = false
        }
    }

    /** Uma oferta nova (não a mesma lida de novo): notificação e captura de tela, se ligadas. */
    private fun onOffer(a: Analysis) {
        val o = a.offer
        val key = "${o.platform}|${o.value}|${o.pickupKm}|${o.tripKm}"
        val now = SystemClock.uptimeMillis()
        if (key == lastOfferKey && now - lastOfferAt < 20000) return
        lastOfferKey = key
        lastOfferAt = now
        notifyOffer(a)
        if (prefs.topVibrate && prefs.isTop(a)) vibrateTop()
        if (prefs.avisoVibrar) {
            val d = Destino.analisar(o.destText, prefs, o.pickupText)
            if (d.negra != null || d.mercado != null || d.evitado || prefs.paradasDemais(o.stops)) vibrateAlert()
        }
        if (o.platform != "Simulação") saveShot(o.platform)
    }

    /** Três toques curtos: corrida TOP detectada. */
    private fun vibrateTop() {
        try {
            val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120, 90, 120), -1))
        } catch (_: Exception) {}
    }

    /** Vibração longa: destino na lista negra ou bairro evitado. */
    private fun vibrateAlert() {
        try {
            val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 450, 150, 450), -1))
        } catch (_: Exception) {}
    }

    private fun notifyOffer(a: Analysis) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("ofertas", "Última oferta", NotificationManager.IMPORTANCE_LOW)
            )
            val br = Locale("pt", "BR")
            val hour = if (a.perHour >= 0) String.format(br, "R\$ %.0f/h", a.perHour) else "—/h"
            val n = Notification.Builder(this, "ofertas")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("${a.offer.platform} • " + fmt(a.offer.value))
                .setContentText(String.format(br, "R\$ %.2f/km • ", a.perKm) + hour + String.format(br, " • lucro R\$ %.2f", a.profit))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        } catch (_: Exception) {
        }
    }

    private var lastShot = 0L

    private fun saveShot(tag: String) {
        if (!prefs.saveShots || Build.VERSION.SDK_INT < 30) return
        val now = SystemClock.uptimeMillis()
        if (now - lastShot < 8000) return
        lastShot = now
        handler.postDelayed({
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            try {
                                val hb = result.hardwareBuffer
                                val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                hw?.recycle()
                                hb.close()
                                if (bmp != null) Thread { writeShot(bmp, tag) }.start()
                            } catch (e: Exception) {
                                DebugLog.add("Captura: ${e.message}")
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            DebugLog.add("Captura recusada: ${screenshotError(errorCode)}")
                        }
                    }
                )
            } catch (e: Exception) {
                DebugLog.add("Captura não iniciou: ${e.message}")
            }
        }, 500)
    }

    private fun writeShot(bmp: Bitmap, tag: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "rota_${tag}_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RotaCopiloto")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            DebugLog.add("Captura não salva: ${e.message}")
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", v)
}
