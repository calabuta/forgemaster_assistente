package com.joaodegrandi.forgemasterassistente.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joaodegrandi.forgemasterassistente.model.CropProfile
import com.joaodegrandi.forgemasterassistente.model.FieldExpectation
import com.joaodegrandi.forgemasterassistente.model.NormalizedRect
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.PanelType
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.ValueState
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import com.joaodegrandi.forgemasterassistente.model.mainStatExpectation
import com.joaodegrandi.forgemasterassistente.overlay.CaptureOverlayService
import com.joaodegrandi.forgemasterassistente.parser.StatParser
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingSessionStart = false
    private var cropPreview: Bitmap? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { continueStartingSession() }

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { continueStartingSession() }

    private val projectionPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pendingSessionStart = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(
                this,
                CaptureOverlayService.startIntent(this, result.resultCode, result.data!!),
            )
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cropPreview = CropPreviewStore.take()
        val openCropSettings = intent.getBooleanExtra(EXTRA_OPEN_CROP_SETTINGS, false)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0B6EDE),
                    secondary = Color(0xFF415F91),
                ),
            ) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onStartSession = ::startOverlaySession,
                    onSetMode = viewModel::setMode,
                    onBeginFullCalibration = viewModel::beginFullCalibration,
                    onBeginPartialCalibration = viewModel::beginPartialCalibration,
                    onSelectSource = viewModel::selectCalibrationSource,
                    onSaveSource = viewModel::saveManualSource,
                    onConfirmCalibration = viewModel::confirmCalibration,
                    onDiscardCalibration = viewModel::discardCalibration,
                    onUndo = viewModel::undo,
                    onSaveCrops = viewModel::saveCropProfiles,
                    onResetCrops = viewModel::resetCrops,
                    cropPreview = cropPreview,
                    initialShowCrops = openCropSettings,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cropPreview?.takeUnless(Bitmap::isRecycled)?.recycle()
        cropPreview = null
    }

    private fun startOverlaySession() {
        pendingSessionStart = true
        continueStartingSession()
    }

    private fun continueStartingSession() {
        if (!pendingSessionStart) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionPermission.launch(
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()),
        )
    }

    companion object {
        private const val EXTRA_OPEN_CROP_SETTINGS = "open_crop_settings"

        fun cropPreviewIntent(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OPEN_CROP_SETTINGS, true)
    }
}

@Composable
private fun MainScreen(
    state: MainUiState,
    onStartSession: () -> Unit,
    onSetMode: (WeaponMode) -> Unit,
    onBeginFullCalibration: () -> Unit,
    onBeginPartialCalibration: (SourceId) -> Unit,
    onSelectSource: (SourceId) -> Unit,
    onSaveSource: (SourceRecord) -> Unit,
    onConfirmCalibration: () -> Unit,
    onDiscardCalibration: () -> Unit,
    onUndo: () -> Unit,
    onSaveCrops: (List<CropProfile>) -> Unit,
    onResetCrops: () -> Unit,
    cropPreview: Bitmap?,
    initialShowCrops: Boolean,
) {
    var editedSource by remember { mutableStateOf<SourceId?>(null) }
    var showCrops by remember { mutableStateOf(initialShowCrops) }
    var preview by remember { mutableStateOf(cropPreview) }
    val calibrated = state.build.sources.count { it.isComplete() }
    val draftDone = state.calibration.selectedSources.count { id ->
        state.calibration.build.source(id)?.isComplete() == true
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("ForgeMaster Assistente", style = MaterialTheme.typography.headlineMedium)
                Text("Build calibrada: $calibrated/13 fontes")
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Modo de cálculo", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeButton(WeaponMode.MELEE, state.mode, onSetMode)
                            ModeButton(WeaponMode.RANGED, state.mode, onSetMode)
                        }
                        Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) {
                            Text("Iniciar bolha sobre o jogo")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Calibração", style = MaterialTheme.typography.titleMedium)
                        Text("Rascunho: $draftDone/${state.calibration.selectedSources.size} selecionadas")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onBeginFullCalibration) { Text("Completa") }
                            OutlinedButton(onClick = { showCrops = true }) { Text("Recortes") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConfirmCalibration) { Text("Confirmar") }
                            TextButton(onClick = onDiscardCalibration) { Text("Descartar rascunho") }
                        }
                    }
                }
            }
            items(SourceId.entries) { id ->
                val active = state.build.source(id)
                val draft = state.calibration.build.source(id)
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(id.displayName(), style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    draft?.isComplete() == true -> "Rascunho pronto: ${draft.name.ifBlank { id.displayName() }}"
                                    active?.isComplete() == true -> "Ativo: ${active.name.ifBlank { id.displayName() }}"
                                    else -> "Pendente"
                                },
                            )
                        }
                        OutlinedButton(onClick = {
                            if (state.build.source(id) != null) onBeginPartialCalibration(id)
                            else onSelectSource(id)
                            editedSource = id
                        }) {
                            Text(if (active == null) "Preencher" else "Recalibrar")
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.undo != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Desfazer última troca")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editedSource?.let { id ->
        val initial = state.calibration.build.source(id) ?: state.build.source(id)
        SourceEditorDialog(
            id = id,
            initial = initial,
            onDismiss = { editedSource = null },
            onSave = {
                onSaveSource(it)
                editedSource = null
            },
        )
    }
    if (showCrops) {
        CropSettingsDialog(
            initial = state.crops,
            preview = preview,
            onDismiss = {
                showCrops = false
                preview?.takeUnless(Bitmap::isRecycled)?.recycle()
                preview = null
            },
            onSave = {
                onSaveCrops(it)
                showCrops = false
                preview?.takeUnless(Bitmap::isRecycled)?.recycle()
                preview = null
            },
            onReset = {
                onResetCrops()
                showCrops = false
                preview?.takeUnless(Bitmap::isRecycled)?.recycle()
                preview = null
            },
        )
    }
}

@Composable
private fun ModeButton(
    mode: WeaponMode,
    selected: WeaponMode,
    onSetMode: (WeaponMode) -> Unit,
) {
    if (mode == selected) {
        Button(onClick = { onSetMode(mode) }) { Text(mode.name) }
    } else {
        OutlinedButton(onClick = { onSetMode(mode) }) { Text(mode.name) }
    }
}

@Composable
private fun SourceEditorDialog(
    id: SourceId,
    initial: SourceRecord?,
    onDismiss: () -> Unit,
    onSave: (SourceRecord) -> Unit,
) {
    var name by remember(id, initial) { mutableStateOf(initial?.name.orEmpty()) }
    var rarity by remember(id, initial) { mutableStateOf(initial?.rarity.orEmpty()) }
    var level by remember(id, initial) { mutableStateOf(initial?.level?.toString().orEmpty()) }
    var damage by remember(id, initial) {
        mutableStateOf(initial?.baseDamage?.decimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var health by remember(id, initial) {
        mutableStateOf(initial?.baseHealth?.decimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var sub1 by remember(id, initial) { mutableStateOf(initial?.subStats?.getOrNull(0)?.rawText.orEmpty()) }
    var sub2 by remember(id, initial) { mutableStateOf(initial?.subStats?.getOrNull(1)?.rawText.orEmpty()) }
    var error by remember { mutableStateOf("") }
    val expectation = id.mainStatExpectation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(id.displayName()) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Nome") }) }
                if (id != SourceId.SKILLS) {
                    item { OutlinedTextField(rarity, { rarity = it }, label = { Text("Raridade") }) }
                    item { OutlinedTextField(level, { level = it }, label = { Text("Nível") }) }
                }
                if (expectation.damage != FieldExpectation.ABSENT) {
                    item { OutlinedTextField(damage, { damage = it }, label = { Text("Base Damage (aceita k/m/b)") }) }
                }
                if (expectation.health != FieldExpectation.ABSENT) {
                    item { OutlinedTextField(health, { health = it }, label = { Text("Base Health (aceita k/m/b)") }) }
                }
                if (id != SourceId.SKILLS) {
                    item { OutlinedTextField(sub1, { sub1 = it }, label = { Text("Substat 1, ex.: +12% Damage") }) }
                    item { OutlinedTextField(sub2, { sub2 = it }, label = { Text("Substat 2") }) }
                }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val record = buildManualRecord(id, name, rarity, level, damage, health, sub1, sub2)
                if (record == null || !record.isComplete()) {
                    error = "Revise os campos obrigatórios e o formato dos substats."
                } else {
                    onSave(record)
                }
            }) { Text("Salvar no rascunho") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun buildManualRecord(
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
    val subStats = listOf(sub1, sub2).filter { it.isNotBlank() }.map { raw ->
        StatParser.parseSubStat(raw, 1f) ?: return null
    }
    return SourceRecord(
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropSettingsDialog(
    initial: List<CropProfile>,
    preview: Bitmap?,
    onDismiss: () -> Unit,
    onSave: (List<CropProfile>) -> Unit,
    onReset: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial.firstOrNull()?.panelType ?: PanelType.FORGE_COMPARISON) }
    val current = initial.firstOrNull { it.panelType == selected }
        ?: CropProfile(selected, NormalizedRect(0.05f, 0.2f, 0.95f, 0.8f))
    var left by remember(selected) { mutableFloatStateOf(current.content.left) }
    var top by remember(selected) { mutableFloatStateOf(current.content.top) }
    var width by remember(selected) { mutableFloatStateOf(current.content.right - current.content.left) }
    var height by remember(selected) { mutableFloatStateOf(current.content.bottom - current.content.top) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajuste simples dos recortes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (preview == null) {
                    Text("A prévia real fica disponível ao abrir este ajuste pelo toque longo na bolha.")
                } else {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(preview.width.toFloat() / preview.height.toFloat())
                            .align(Alignment.CenterHorizontally),
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Prévia do recorte",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(left * size.width, top * size.height),
                                size = Size(width * size.width, height * size.height),
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    }
                }
                Text("Painel: ${selected.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val all = PanelType.entries.filter { it != PanelType.UNKNOWN }
                        selected = all[(all.indexOf(selected) + all.size - 1) % all.size]
                    }) { Text("Anterior") }
                    TextButton(onClick = {
                        val all = PanelType.entries.filter { it != PanelType.UNKNOWN }
                        selected = all[(all.indexOf(selected) + 1) % all.size]
                    }) { Text("Próximo") }
                }
                Text("X: ${(left * 100).toInt()}%")
                Slider(left, { left = it.coerceAtMost(1f - width) }, valueRange = 0f..0.9f)
                Text("Y: ${(top * 100).toInt()}%")
                Slider(top, { top = it.coerceAtMost(1f - height) }, valueRange = 0f..0.9f)
                Text("Largura: ${(width * 100).toInt()}%")
                Slider(width, { width = it.coerceIn(0.1f, 1f - left) }, valueRange = 0.1f..1f)
                Text("Altura: ${(height * 100).toInt()}%")
                Slider(height, { height = it.coerceIn(0.1f, 1f - top) }, valueRange = 0.1f..1f)
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = initial.filterNot { it.panelType == selected } + CropProfile(
                    selected,
                    NormalizedRect(left, top, left + width, top + height),
                )
                onSave(updated)
            }) { Text("Salvar painel") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onReset) { Text("Restaurar padrões") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
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
