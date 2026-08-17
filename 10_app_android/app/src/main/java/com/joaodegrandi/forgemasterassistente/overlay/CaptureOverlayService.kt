package com.joaodegrandi.forgemasterassistente.overlay

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.joaodegrandi.forgemasterassistente.R
import com.joaodegrandi.forgemasterassistente.model.CalibrationDraft
import com.joaodegrandi.forgemasterassistente.model.ComparisonResult
import com.joaodegrandi.forgemasterassistente.model.FieldExpectation
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.OcrReadResult
import com.joaodegrandi.forgemasterassistente.model.OcrSourceDraft
import com.joaodegrandi.forgemasterassistente.model.PanelType
import com.joaodegrandi.forgemasterassistente.model.PetComparison
import com.joaodegrandi.forgemasterassistente.model.Recommendation
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import com.joaodegrandi.forgemasterassistente.model.mainStatExpectation
import com.joaodegrandi.forgemasterassistente.ocr.MlKitOcrEngine
import com.joaodegrandi.forgemasterassistente.parser.StatParser
import com.joaodegrandi.forgemasterassistente.scoring.ReplacementEvaluator
import com.joaodegrandi.forgemasterassistente.storage.AppStorage
import com.joaodegrandi.forgemasterassistente.ui.CropPreviewStore
import com.joaodegrandi.forgemasterassistente.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class CaptureOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var storage: AppStorage
    private lateinit var ocrEngine: MlKitOcrEngine
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var imageThread: HandlerThread? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var cardView: View? = null
    private var calibrationTarget: SourceId? = null
    private val pendingCapture = AtomicBoolean(false)
    private val pendingCropPreview = AtomicBoolean(false)
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        storage = AppStorage(applicationContext)
        ocrEngine = MlKitOcrEngine()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || resultData == null || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        if (projection == null) {
            startProjection(resultCode, resultData)
            showBubble()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            stopSelf()
            return
        }
        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (!stopping) stopSelf()
            }
        }, mainHandler)

        val bounds = windowManager.maximumWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val density = resources.displayMetrics.densityDpi
        imageThread = HandlerThread("ForgeMasterCapture").also { it.start() }
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ available ->
                val image = available.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!pendingCapture.compareAndSet(true, false)) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                val bitmap = try {
                    image.toBitmap(width, height)
                } finally {
                    image.close()
                }
                if (pendingCropPreview.compareAndSet(true, false)) {
                    mainHandler.post {
                        bubbleView?.visibility = View.VISIBLE
                        CropPreviewStore.replace(bitmap)
                        runCatching { startActivity(MainActivity.cropPreviewIntent(this)) }
                            .onFailure { CropPreviewStore.clear() }
                    }
                } else {
                    mainHandler.post { bubbleView?.visibility = View.VISIBLE }
                    processCapturedBitmap(bitmap)
                }
            }, Handler(imageThread!!.looper))
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "ForgeMasterSingleFrame",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            Handler(imageThread!!.looper),
        )
    }

    private fun requestSingleCapture() {
        if (pendingCapture.get()) return
        hideCard()
        bubbleView?.visibility = View.INVISIBLE
        mainHandler.postDelayed({
            pendingCapture.set(true)
        }, 140L)
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        serviceScope.launch {
            val draft = storage.currentCalibration()
            val target = calibrationTarget ?: draft.currentSource
            val crops = storage.currentCropProfiles()
            val result = try {
                ocrEngine.read(bitmap, target, crops)
            } catch (error: Throwable) {
                null
            } finally {
                bitmap.recycle()
            }
            withContext(Dispatchers.Main) {
                if (result == null) {
                    showTemporaryCard("INCONCLUSIVO\nFalha ao executar o OCR. Tente novamente.")
                } else {
                    presentReadResult(result, target)
                }
            }
        }
    }

    private fun presentReadResult(result: OcrReadResult, target: SourceId?) {
        if (result.sources.isEmpty()) {
            showTemporaryCard(
                "${if (result.requiresReview) "INCONCLUSIVO" else "LEITURA"}\n${result.message}\n" +
                    result.rawLines.take(8).joinToString("\n") { it.text },
            )
            return
        }
        if (target != null) {
            showSourceEditor(result.sources.first(), target, EditorPurpose.CALIBRATION, result)
            return
        }
        when (result.panelType) {
            PanelType.FORGE_COMPARISON -> {
                val equipped = result.sources.getOrNull(0)
                val candidate = result.sources.getOrNull(1)
                if (equipped == null || candidate == null) {
                    showTemporaryCard("INCONCLUSIVO\nNão foi possível separar equipado e candidato.")
                    return
                }
                serviceScope.launch {
                    val build = storage.currentBuild()
                    val matches = build.sources.filter { source ->
                        source.id in EQUIPMENT_IDS &&
                            source.name.normalized() == equipped.name.normalized()
                    }
                    withContext(Dispatchers.Main) {
                        showSourceEditor(
                            candidate,
                            matches.singleOrNull()?.id,
                            EditorPurpose.COMPARISON,
                            result,
                            selectableIds = EQUIPMENT_IDS,
                        )
                    }
                }
            }
            PanelType.MOUNT_DETAIL -> showSourceEditor(
                result.sources.first(),
                SourceId.MOUNT,
                EditorPurpose.COMPARISON,
                result,
            )
            PanelType.PET_DETAIL -> showSourceEditor(
                result.sources.first(),
                SourceId.PET_1,
                EditorPurpose.PET_COMPARISON,
                result,
                selectableIds = PET_IDS,
            )
            PanelType.EQUIPMENT_DETAIL,
            PanelType.SKILLS,
            -> serviceScope.launch {
                val build = storage.currentBuild()
                val recognized = result.sources.first()
                val inferred = if (result.panelType == PanelType.SKILLS) SourceId.SKILLS else {
                    build.sources.filter { it.name.normalized() == recognized.name.normalized() }
                        .singleOrNull()?.id
                }
                withContext(Dispatchers.Main) {
                    if (inferred == null) {
                        showTemporaryCard("INCONCLUSIVO\nSelecione a fonte pelo toque longo e capture novamente.")
                    } else {
                        showSourceEditor(recognized, inferred, EditorPurpose.CALIBRATION, result)
                    }
                }
            }
            else -> showTemporaryCard("INCONCLUSIVO\n${result.message}")
        }
    }

    private fun showSourceEditor(
        draft: OcrSourceDraft,
        initialSourceId: SourceId?,
        purpose: EditorPurpose,
        readResult: OcrReadResult,
        selectableIds: List<SourceId> = emptyList(),
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val title = content.textView(
            when (purpose) {
                EditorPurpose.CALIBRATION -> "Revisar calibração"
                EditorPurpose.COMPARISON -> "Comparar candidato"
                EditorPurpose.PET_COMPARISON -> "Comparar com os 3 pets"
            },
            18f,
            true,
        )
        val summary = content.textView("Recalculando…", 15f, true)
        content.textView("OCR: ${(readResult.confidence * 100).toInt()}%", 12f)

        val sourceSpinner = if (selectableIds.isNotEmpty()) {
            Spinner(this).also { spinner ->
                val labels = listOf("Selecione o slot") + selectableIds.map { it.displayName() }
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
                val initialIndex = initialSourceId?.let(selectableIds::indexOf)?.takeIf { it >= 0 }?.plus(1) ?: 0
                spinner.setSelection(initialIndex)
                content.addView(spinner)
            }
        } else null

        val name = content.editField("Nome", draft.name)
        val rarity = content.editField("Raridade", draft.rarity)
        val level = content.editField("Nível", draft.level?.toString().orEmpty())
        val damage = content.editField(
            "Base Damage (aceita k/m/b)",
            draft.baseDamage?.decimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty(),
        )
        val health = content.editField(
            "Base Health (aceita k/m/b)",
            draft.baseHealth?.decimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty(),
        )
        val sub1 = content.editField("Substat 1", draft.subStats.getOrNull(0)?.rawText.orEmpty())
        val sub2 = content.editField("Substat 2", draft.subStats.getOrNull(1)?.rawText.orEmpty())
        var valuesConfirmed = readResult.confidence >= MIN_CONFIDENCE && !readResult.requiresReview
        var latestRecord: SourceRecord? = null
        var latestSourceId: SourceId? = initialSourceId
        var recomputeJob: Job? = null

        fun selectedId(): SourceId? {
            if (selectableIds.isEmpty()) return initialSourceId
            val position = sourceSpinner?.selectedItemPosition ?: 0
            return selectableIds.getOrNull(position - 1)
        }

        fun recompute() {
            recomputeJob?.cancel()
            recomputeJob = serviceScope.launch {
                val sourceId = selectedId()
                val activeMode = storage.currentMode()
                latestSourceId = sourceId
                val record = sourceId?.let { id ->
                    buildRecord(
                        id, name.text.toString(), rarity.text.toString(), level.text.toString(),
                        damage.text.toString(), health.text.toString(),
                        sub1.text.toString(), sub2.text.toString(),
                    )
                }
                latestRecord = record
                val message = when {
                    sourceId == null -> "INCONCLUSIVO\nSelecione o slot correto."
                    record == null || !record.isComplete() ->
                        "INCONCLUSIVO\nRevise os campos obrigatórios."
                    !valuesConfirmed ->
                        "INCONCLUSIVO\nOCR inseguro: confirme os valores abaixo."
                    purpose == EditorPurpose.CALIBRATION ->
                        "Fonte pronta para salvar no rascunho."
                    purpose == EditorPurpose.PET_COMPARISON -> {
                        val petComparison = ReplacementEvaluator.comparePet(
                            storage.currentBuild(),
                            record,
                            activeMode,
                        )
                        formatPetComparison(petComparison)
                    }
                    else -> formatComparison(
                        ReplacementEvaluator.compare(
                            storage.currentBuild(),
                            sourceId,
                            record,
                            activeMode,
                        ),
                    )
                }
                withContext(Dispatchers.Main) {
                    summary.text = "Modo ativo: ${activeMode.name}\n$message"
                }
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                valuesConfirmed = true
                recompute()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        listOf(name, rarity, level, damage, health, sub1, sub2).forEach { it.addTextChangedListener(watcher) }
        sourceSpinner?.setOnItemSelectedListener(SimpleItemSelectedListener { recompute() })

        if (!valuesConfirmed) {
            content.actionButton("Confirmar valores reconhecidos") {
                valuesConfirmed = true
                recompute()
            }
        }
        content.actionButton(
            when (purpose) {
                EditorPurpose.CALIBRATION -> "Salvar no rascunho"
                else -> "Equipei no jogo"
            },
        ) {
            val record = latestRecord
            val sourceId = latestSourceId
            if (!valuesConfirmed || record == null || sourceId == null || !record.isComplete()) {
                summary.text = "INCONCLUSIVO\nConfirme e corrija os valores antes de salvar."
                return@actionButton
            }
            serviceScope.launch {
                if (purpose == EditorPurpose.CALIBRATION) {
                    val calibration = storage.currentCalibration()
                    val selected = calibration.selectedSources
                        .takeIf { it.contains(sourceId) }
                        ?: listOf(sourceId)
                    storage.saveCalibrationSource(record, selected)
                    calibrationTarget = null
                } else {
                    storage.confirmReplacement(sourceId, record)
                }
                withContext(Dispatchers.Main) {
                    showTemporaryCard(
                        if (purpose == EditorPurpose.CALIBRATION) {
                            "Fonte salva no rascunho. Confirme a calibração quando terminar."
                        } else {
                            "Troca registrada. A build salva já é a nova base."
                        },
                    )
                }
            }
        }
        content.actionButton("Alternar modo Melee/Ranged") {
            serviceScope.launch {
                val next = if (storage.currentMode() == WeaponMode.MELEE) {
                    WeaponMode.RANGED
                } else {
                    WeaponMode.MELEE
                }
                storage.saveWeaponMode(next)
                recompute()
            }
        }
        content.actionButton("Desfazer última troca") {
            serviceScope.launch {
                val undone = storage.undoLastReplacement()
                withContext(Dispatchers.Main) {
                    summary.text = if (undone) {
                        "Última troca desfeita."
                    } else {
                        "Não há troca para desfazer."
                    }
                }
            }
        }
        content.actionButton("Descartar leitura") { hideCard() }

        showCard(content)
        recompute()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val bubble = TextView(this).apply {
            text = "FM"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(11, 110, 222))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val params = WindowManager.LayoutParams(
            dp(62),
            dp(62),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(180)
        }
        attachBubbleGestures(bubble, params)
        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubbleParams = params
    }

    private fun attachBubbleGestures(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var longPressed = false
        val longPress = Runnable {
            if (!moved) {
                longPressed = true
                showControlMenu()
            }
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    longPressed = false
                    mainHandler.postDelayed(longPress, 550L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        mainHandler.removeCallbacks(longPress)
                    }
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    mainHandler.removeCallbacks(longPress)
                    if (!moved && !longPressed && event.actionMasked == MotionEvent.ACTION_UP) {
                        requestSingleCapture()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showControlMenu() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.textView("Atalhos da bolha", 18f, true)
        val spinner = Spinner(this).also {
            it.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                SourceId.entries.map { it.displayName() },
            )
            content.addView(it)
        }
        content.actionButton("Calibrar/recalibrar fonte selecionada") {
            val id = SourceId.entries[spinner.selectedItemPosition]
            calibrationTarget = id
            serviceScope.launch {
                val current = storage.currentCalibration()
                val hasActiveDraft = current.currentSource != null || current.build.sources.isNotEmpty()
                val selected = if (hasActiveDraft && current.selectedSources.contains(id)) {
                    current.selectedSources
                } else {
                    listOf(id)
                }
                storage.saveCalibrationDraft(
                    current.copy(selectedSources = selected, currentSource = id),
                )
                withContext(Dispatchers.Main) {
                    showTemporaryCard("Fonte selecionada: ${id.displayName()}\nAbra o detalhe no jogo e toque na bolha.")
                }
            }
        }
        content.actionButton("Abrir calibração no app") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hideCard()
        }
        content.actionButton("Ajustar recortes sobre prévia") {
            if (!pendingCapture.get()) {
                pendingCropPreview.set(true)
                requestSingleCapture()
            }
        }
        content.actionButton("Alternar modo") {
            serviceScope.launch {
                val next = if (storage.currentMode() == WeaponMode.MELEE) WeaponMode.RANGED else WeaponMode.MELEE
                storage.saveWeaponMode(next)
                withContext(Dispatchers.Main) { showTemporaryCard("Modo ativo: ${next.name}") }
            }
        }
        content.actionButton("Desfazer última troca") {
            serviceScope.launch {
                val undone = storage.undoLastReplacement()
                withContext(Dispatchers.Main) {
                    showTemporaryCard(if (undone) "Última troca desfeita." else "Não há troca para desfazer.")
                }
            }
        }
        content.actionButton("Encerrar sessão") { stopSelf() }
        content.actionButton("Fechar menu") { hideCard() }
        showCard(content)
    }

    private fun showTemporaryCard(message: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            textView(message, 15f, true)
            actionButton("Fechar") { hideCard() }
        }
        showCard(content)
    }

    private fun showCard(content: View) {
        hideCard()
        val scroll = ScrollView(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(2), Color.rgb(11, 110, 222))
            }
            elevation = dp(10).toFloat()
            addView(content)
        }
        val params = WindowManager.LayoutParams(
            dp(360),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(84)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager.addView(scroll, params)
        cardView = scroll
    }

    private fun hideCard() {
        cardView?.let { runCatching { windowManager.removeView(it) } }
        cardView = null
    }

    private fun buildRecord(
        id: SourceId,
        name: String,
        rarity: String,
        level: String,
        damage: String,
        health: String,
        sub1: String,
        sub2: String,
    ): SourceRecord? {
        val expectation = id.mainStatExpectation()
        fun field(raw: String, expected: FieldExpectation): NumericField? = when (expected) {
            FieldExpectation.ABSENT -> NumericField.expectedAbsent()
            FieldExpectation.OPTIONAL -> if (raw.isBlank()) NumericField.expectedAbsent() else {
                StatParser.parseAbbreviatedNumber(raw)?.let { NumericField.recognized(it, raw, 1f) }
            }
            FieldExpectation.REQUIRED ->
                StatParser.parseAbbreviatedNumber(raw)?.let { NumericField.recognized(it, raw, 1f) }
        }
        val baseDamage = field(damage, expectation.damage) ?: return null
        val baseHealth = field(health, expectation.health) ?: return null
        val subStats = listOf(sub1, sub2).filter(String::isNotBlank).map { raw ->
            StatParser.parseSubStat(raw, 1f) ?: return null
        }
        return runCatching {
            SourceRecord(
                id = id,
                name = if (id == SourceId.SKILLS) "Skills" else name.trim(),
                rarity = rarity.trim(),
                level = level.toIntOrNull(),
                baseDamage = baseDamage,
                baseHealth = baseHealth,
                subStats = subStats,
                ocrConfidence = 1f,
                readAtEpochMillis = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    private fun formatComparison(result: ComparisonResult): String {
        val delta = result.delta?.multiply(BigDecimal("100"))
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.toPlainString()
        val header = when (result.recommendation) {
            Recommendation.EQUIPAR -> "EQUIPAR  +${delta}%"
            Recommendation.VENDER -> "VENDER  ${delta}%"
            Recommendation.INCONCLUSIVO -> "INCONCLUSIVO"
        }
        val changes = result.changes.joinToString("\n") { change ->
            val ignored = if (change.affectsDecision) "" else " (ignorado na decisão)"
            "${change.label}: ${change.before.strip()} → ${change.after.strip()}$ignored"
        }
        return buildString {
            appendLine(header)
            appendLine(result.reason)
            appendLine("Dano: ${result.damageBefore.short()} → ${result.damageAfter.short()}")
            if (changes.isNotBlank()) append(changes)
        }.trim()
    }

    private fun formatPetComparison(result: PetComparison): String = buildString {
        appendLine("Cenários do pet candidato")
        result.scenarios.forEach { scenario ->
            appendLine("${scenario.sourceId?.displayName()}: ${formatComparison(scenario).lineSequence().first()}")
        }
        append("Melhor substituição: ${result.best?.sourceId?.displayName() ?: "inconclusiva"}")
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_forgemaster)
        .setContentTitle(getString(R.string.capture_notification_title))
        .setContentText(getString(R.string.capture_notification_text))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(
            0,
            "Encerrar",
            PendingIntent.getService(
                this,
                1,
                Intent(this, CaptureOverlayService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    override fun onDestroy() {
        stopping = true
        hideCard()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        imageThread?.quitSafely()
        imageThread = null
        pendingCropPreview.set(false)
        projection?.stop()
        projection = null
        ocrEngine.close()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun Image.toBitmap(targetWidth: Int, targetHeight: Int): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        padded.copyPixelsFromBuffer(buffer)
        if (padded.width == targetWidth && padded.height == targetHeight) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, targetWidth, targetHeight)
        padded.recycle()
        return cropped
    }

    private fun LinearLayout.textView(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this@CaptureOverlayService).also { textView ->
            textView.text = value
            textView.textSize = size
            textView.setTextColor(Color.rgb(25, 28, 34))
            if (bold) textView.setTypeface(textView.typeface, android.graphics.Typeface.BOLD)
            textView.setPadding(0, dp(3), 0, dp(5))
            addView(textView)
        }

    private fun LinearLayout.editField(hint: String, value: String): EditText =
        EditText(this@CaptureOverlayService).also { editText ->
            editText.hint = hint
            editText.setText(value)
            editText.setSingleLine(true)
            editText.textSize = 14f
            addView(editText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }

    private fun LinearLayout.actionButton(label: String, action: () -> Unit): Button =
        Button(this@CaptureOverlayService).also { button ->
            button.text = label
            button.setOnClickListener { action() }
            addView(button, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6) })
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun String.normalized(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun BigDecimal.strip(): String = stripTrailingZeros().toPlainString()

    private fun BigDecimal.short(): String {
        val absolute = abs(toDouble())
        val (divisor, suffix) = when {
            absolute >= 1_000_000_000 -> BigDecimal("1000000000") to "b"
            absolute >= 1_000_000 -> BigDecimal("1000000") to "m"
            absolute >= 1_000 -> BigDecimal("1000") to "k"
            else -> BigDecimal.ONE to ""
        }
        return divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix
    }

    private fun SourceId.displayName(): String = when (this) {
        SourceId.HEAD -> "Cabeça"
        SourceId.TORSO -> "Torso"
        SourceId.GLOVE -> "Luva"
        SourceId.NECKLACE -> "Colar"
        SourceId.RING -> "Anel"
        SourceId.WEAPON -> "Arma"
        SourceId.BOOT -> "Bota"
        SourceId.BELT -> "Cinto"
        SourceId.MOUNT -> "Montaria"
        SourceId.PET_1 -> "Pet 1"
        SourceId.PET_2 -> "Pet 2"
        SourceId.PET_3 -> "Pet 3"
        SourceId.SKILLS -> "Skills"
    }

    private enum class EditorPurpose { CALIBRATION, COMPARISON, PET_COMPARISON }

    companion object {
        private const val CHANNEL_ID = "forgemaster_capture"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_START = "forgemaster.action.START"
        private const val ACTION_STOP = "forgemaster.action.STOP"
        private const val EXTRA_RESULT_CODE = "projection_result_code"
        private const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val MIN_CONFIDENCE = 0.85f
        private val EQUIPMENT_IDS = listOf(
            SourceId.HEAD,
            SourceId.TORSO,
            SourceId.GLOVE,
            SourceId.NECKLACE,
            SourceId.RING,
            SourceId.WEAPON,
            SourceId.BOOT,
            SourceId.BELT,
        )
        private val PET_IDS = listOf(SourceId.PET_1, SourceId.PET_2, SourceId.PET_3)

        fun startIntent(context: Context, resultCode: Int, resultData: Intent): Intent =
            Intent(context, CaptureOverlayService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
    }
}

private class SimpleItemSelectedListener(
    private val onSelected: () -> Unit,
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
        onSelected()
    }

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
