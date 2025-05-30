package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.koin.compose.koinInject
import viewmodel.FormData
import viewmodel.MainViewModel
import viewmodel.ProcessingState
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser

// --- Preference Keys and Node Path ---
private const val PREFS_NODE_PATH = "uz.mobiledv.hujjatoldiruvchi.prefs"
private const val KEY_TEMPLATE_FOLDER = "templateFolderPath"
private const val KEY_OUTPUT_FOLDER = "outputFolderPath"
private const val KEY_OUTPUT_FILENAME = "outputFileName"
private const val KEY_GLOBAL_BOLD = "globalStyleBold"
private const val KEY_GLOBAL_ITALIC = "globalStyleItalic"
private const val KEY_GLOBAL_FONT_FAMILY = "globalStyleFontFamily"

private val logger: Logger = LogManager.getLogger("App")

// --- Helper functions to save and load preferences ---
private fun saveStringPreference(key: String, value: String) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).put(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        logger.error("Error saving string preference $key", e)
    }
}

private fun loadStringPreference(key: String, defaultValue: String = ""): String {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).get(key, defaultValue)
    } catch (e: Exception) {
        logger.error("Error loading string preference $key", e)
        defaultValue
    }
}

private fun saveBooleanPreference(key: String, value: Boolean) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).putBoolean(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        logger.error("Error saving boolean preference $key", e)
    }
}

private fun loadBooleanPreference(key: String, defaultValue: Boolean = false): Boolean {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).getBoolean(key, defaultValue)
    } catch (e: Exception) {
        logger.error("Error loading boolean preference $key", e)
        defaultValue
    }
}

// --- FolderPickerButton Composable ---
@Composable
fun FolderPickerButton(buttonText: String, selectedPath: String, onPathSelected: (String) -> Unit) {
    var isDialogVisible by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { isDialogVisible = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Default.FolderOpen, "Folder", Modifier.padding(end = 8.dp))
        Text(selectedPath.ifEmpty { "$buttonText: Tanlanmagan" }, maxLines = 1)
    }
    if (isDialogVisible) {
        DisposableEffect(Unit) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Papka Tanlash"; selectedPath.takeIf { it.isNotEmpty() }
                ?.let { currentDirectory = File(it) }
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath?.let(onPathSelected)
            }
            isDialogVisible = false
            onDispose {}
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun App(viewModel: MainViewModel = koinInject()) {
    var formData by remember { mutableStateOf(FormData()) }
    val coroutineScope = rememberCoroutineScope()

    var templateFolderPath by remember { mutableStateOf(loadStringPreference(KEY_TEMPLATE_FOLDER)) }
    var outputFolderPath by remember { mutableStateOf(loadStringPreference(KEY_OUTPUT_FOLDER)) }
    var outputFileName by remember { mutableStateOf(loadStringPreference(KEY_OUTPUT_FILENAME)) }
    var globalStyleIsBold by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_BOLD)) }
    var globalStyleIsItalic by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_ITALIC)) }
    var globalStyleFontFamily by remember { mutableStateOf(loadStringPreference(KEY_GLOBAL_FONT_FAMILY)) }

    val processingState by viewModel.processingState.collectAsState()
    val documentPreviewText by viewModel.documentPreviewText.collectAsState()
    val lastProcessedFileName by viewModel.lastProcessedFileName.collectAsState()

    var previewScale by remember { mutableStateOf(1.2f) }
    val minScale = 0.3f
    val maxScale = 3.0f
    val scaleIncrement = 0.1f
    val a4AspectRatio = 1.414f
    val previewBaseWidth = 350.dp
    val previewBaseHeight = previewBaseWidth * a4AspectRatio
    val previewTextBaseFontSize = 8.sp

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane: Controls
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
//                Text(
//                    "Hujjatlarni Avtomatik To'ldirish",
//                    style = MaterialTheme.typography.h5,
//                    modifier = Modifier.padding(bottom = 8.dp)
//                )

                Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Papkalar", style = MaterialTheme.typography.h6)
                        FolderPickerButton("Manba papkasi", templateFolderPath) {
                            templateFolderPath = it; saveStringPreference(KEY_TEMPLATE_FOLDER, it)
                        }
                        FolderPickerButton("Chiqish papkasi", outputFolderPath) {
                            outputFolderPath = it; saveStringPreference(KEY_OUTPUT_FOLDER, it)
                        }
                        OutlinedTextField(
                            value = outputFileName,
                            onValueChange = { outputFileName = it; saveStringPreference(KEY_OUTPUT_FILENAME, it) },
                            label = { Text("Chiqish fayl nomi (bo'sh qoldirilsa asl nomi)") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Save, "File Name") },
                            singleLine = true,
                            placeholder = { Text("Masalan: Yakuniy_akt.docx") }
                        )
                    }
                }

                Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Kiritilgan matn uchun global stil", style = MaterialTheme.typography.h6)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Qalin (Bold):")
                                Switch(
                                    checked = globalStyleIsBold,
                                    onCheckedChange = {
                                        globalStyleIsBold = it; saveBooleanPreference(
                                        KEY_GLOBAL_BOLD,
                                        it
                                    )
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Yotiq (Italic):")
                                Switch(
                                    checked = globalStyleIsItalic,
                                    onCheckedChange = {
                                        globalStyleIsItalic = it; saveBooleanPreference(
                                        KEY_GLOBAL_ITALIC,
                                        it
                                    )
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        var fontDropdownExpanded by remember { mutableStateOf(false) }
                        val systemFontFamilies = remember {
                            try {
                                GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
                            } catch (e: Exception) {
                                logger.error("Error getting system fonts", e)
                                listOf("Arial", "Calibri", "Times New Roman", "Courier New", "Verdana", "Georgia")
                            }
                        }
                        ExposedDropdownMenuBox(
                            expanded = fontDropdownExpanded,
                            onExpandedChange = { fontDropdownExpanded = !fontDropdownExpanded },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = globalStyleFontFamily.ifEmpty { "Shriftni tanlang yoki aslini qoldiring" },
                                onValueChange = { /* Read-only */ },
                                label = { Text("Shrift oilasi") },
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = fontDropdownExpanded, onDismissRequest = { fontDropdownExpanded = false }) {
                                DropdownMenuItem(onClick = {
                                    globalStyleFontFamily = ""
                                    saveStringPreference(KEY_GLOBAL_FONT_FAMILY, "")
                                    fontDropdownExpanded = false
                                }) { Text(text = "Aslini qoldirish / Standart") }

                                systemFontFamilies.forEach { selectionOption ->
                                    DropdownMenuItem(onClick = {
                                        globalStyleFontFamily = selectionOption
                                        saveStringPreference(KEY_GLOBAL_FONT_FAMILY, selectionOption)
                                        fontDropdownExpanded = false
                                    }) { Text(text = selectionOption) }
                                }
                            }
                        }
                    }
                }


                Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Asosiy Ma'lumotlar", style = MaterialTheme.typography.h6)
                        OutlinedTextField(
                            formData.objectName,
                            { formData = formData.copy(objectName = it) },
                            label = { Text("Nomi (наименование работ)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.objectDesc,
                            { formData = formData.copy(objectDesc = it) },
                            label = { Text("Tavsifi (наименование и место расположения объекта)") },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp),
                        )
                        OutlinedTextField(
                            formData.certification,
                            { formData = formData.copy(certification = it) },
                            label = { Text("наименование скрытых работ") },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp),
                        )
                    }
                }


                Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Tashkilotlar va Shaxslar", style = MaterialTheme.typography.h6)
                        OutlinedTextField(
                            formData.subContractor,
                            { formData = formData.copy(subContractor = it) },
                            label = { Text("представителя субподрядчика (должность)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.subContractorName,
                            { formData = formData.copy(subContractorName = it) },
                            label = { Text("представителя субподрядчика (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.contractor,
                            { formData = formData.copy(contractor = it) },
                            label = { Text("представителя подрядчика (должность)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.contractorName,
                            { formData = formData.copy(contractorName = it) },
                            label = { Text("представителя подрядчика (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.customer,
                            { formData = formData.copy(customer = it) },
                            label = { Text("Представитель Заказчика  (должность)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.customerName,
                            { formData = formData.copy(customerName = it) },
                            label = { Text("Представитель Заказчика (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.designOrg,
                            { formData = formData.copy(designOrg = it) },
                            label = { Text("представителя проектной организации (должность)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.designOrgName,
                            { formData = formData.copy(designOrgName = it) },
                            label = { Text("представителя проектной организации (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.designDoc,
                            { formData = formData.copy(designDoc = it) },
                            label = { Text("Работы выполнены по проектной документации") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.srNum,
                            { formData = formData.copy(srNum = it) },
                            label = { Text("номер проекта") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }


                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        logger.info("Processing documents with template folder: $templateFolderPath, output folder: $outputFolderPath")
                        coroutineScope.launch {
                            viewModel.processDocuments(
                                templateFolderPath,
                                outputFolderPath,
                                outputFileName,
                                formData,
                                globalStyleIsBold,
                                globalStyleIsItalic,
                                globalStyleFontFamily
                            )
                        }
                    },
                    enabled = processingState !is ProcessingState.Processing,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (processingState is ProcessingState.Processing) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp), color = MaterialTheme.colors.onPrimary, strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Qayta ishlanmoqda...")
                    } else {
                        Text("Hujjatlarni To'ldirish", fontSize = 16.sp)
                    }
                }

                when (processingState) {
                    is ProcessingState.Success -> {
                        val message = (processingState as ProcessingState.Success).message
                        Text(
                            message,
                            Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colors.onSurface
                        )
                    }

                    is ProcessingState.Error -> {
                        val message = (processingState as ProcessingState.Error).message
                        Text(
                            message,
                            Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colors.error
                        )
                    }

                    else -> {}
                }
            }

            Divider(Modifier.fillMaxHeight().width(1.dp).padding(vertical = 16.dp))

            // Right Pane: Preview
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.5f))
                    .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                Text(
//                    lastProcessedFileName?.let { "Oldindan ko'rish: $it" } ?: "Hujjat Oldindan Ko'rish",
//                    style = MaterialTheme.typography.h6,
//                    modifier = Modifier.padding(bottom = 12.dp)
//                )
                Box(
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(
                            MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                            shape = MaterialTheme.shapes.medium
                        )
                        .border(1.dp, Color.LightGray, shape = MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(previewBaseWidth)
                            .height(previewBaseHeight)
                            .scale(scaleX = previewScale, scaleY = previewScale)
                            .verticalScroll(rememberScrollState()),
                        color = Color.White,
                        elevation = 4.dp,
                        border = BorderStroke(1.dp, Color.DarkGray)
                    ) {
                        Text(
                            documentPreviewText, Modifier.padding(16.dp),
                            fontSize = previewTextBaseFontSize,
                            lineHeight = previewTextBaseFontSize * 1.5
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        previewScale = (previewScale - scaleIncrement).coerceIn(minScale, maxScale)
                    }) {
                        Icon(Icons.Default.ZoomOut, "Kichraytirish")
                    }
                    Text("Masshtab: ${(previewScale * 100).toInt()}%", modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = {
                        previewScale = (previewScale + scaleIncrement).coerceIn(minScale, maxScale)
                    }) {
                        Icon(Icons.Default.ZoomIn, "Kattalashtirish")
                    }
                }
            }
        }
    }
}