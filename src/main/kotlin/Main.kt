@file:OptIn(ExperimentalMaterialApi::class)

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds // Added for better viewport control
import androidx.compose.ui.draw.scale // For the scale modifier
import androidx.compose.ui.graphics.Color
// Removed TransformOrigin and ScaleFactor imports as Modifier.scale defaults to Center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.prefs.Preferences
import javax.swing.JFileChooser

// --- Preference Keys and Node Path (remains the same) ---
private const val PREFS_NODE_PATH = "com.example.hujjatoldiruvchi.prefs"
private const val KEY_TEMPLATE_FOLDER = "templateFolderPath"
private const val KEY_OUTPUT_FOLDER = "outputFolderPath"
private const val KEY_GLOBAL_BOLD = "globalStyleBold"
private const val KEY_GLOBAL_ITALIC = "globalStyleItalic"
private const val KEY_GLOBAL_FONT_FAMILY = "globalStyleFontFamily"

// --- Helper functions to save and load preferences (remains the same) ---
private fun saveStringPreference(key: String, value: String) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).put(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        println("Error saving string preference $key: ${e.message}")
    }
}

private fun loadStringPreference(key: String, defaultValue: String = ""): String {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).get(key, defaultValue)
    } catch (e: Exception) {
        println("Error loading string preference $key: ${e.message}")
        defaultValue
    }
}

private fun saveBooleanPreference(key: String, value: Boolean) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).putBoolean(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        println("Error saving boolean preference $key: ${e.message}")
    }
}

private fun loadBooleanPreference(key: String, defaultValue: Boolean = false): Boolean {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).getBoolean(key, defaultValue)
    } catch (e: Exception) {
        println("Error loading boolean preference $key: ${e.message}")
        defaultValue
    }
}

// --- FormData reverted to simple Strings (remains the same) ---
data class FormData(
    var objectName: String = "",
    var objectDesc: String = "",
    var subContractor: String = "",
    var subContractorName: String = "",
    var contractor: String = "",
    var contractorName: String = "",
    var designOrg: String = "",
    var designOrgName: String = "",
    var customer: String = "",
    var customerName: String = "",
    var certification: String = ""
)

// --- StyleProperties for XWPFRun (remains the same) ---
data class StyleProperties(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val underline: UnderlinePatterns = UnderlinePatterns.NONE,
    val isStrikeThrough: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val color: String? = null
)

fun XWPFRun.extractStyle(): StyleProperties {
    val sizeFromPOI: Double? = try {
        this.fontSizeAsDouble
    } catch (e: Exception) {
        null
    }
    val finalFontSize: Double? = if (sizeFromPOI != null && sizeFromPOI > 0) sizeFromPOI else null
    return StyleProperties(
        isBold = this.isBold,
        isItalic = this.isItalic,
        underline = this.underline ?: UnderlinePatterns.NONE,
        isStrikeThrough = this.isStrikeThrough,
        fontFamily = this.fontFamily,
        fontSize = finalFontSize,
        color = this.color
    )
}

fun XWPFRun.applyStyleProperties(styleProps: StyleProperties) {
    this.isBold = styleProps.isBold
    this.isItalic = styleProps.isItalic
    this.underline = styleProps.underline
    this.isStrikeThrough = styleProps.isStrikeThrough
    styleProps.fontFamily?.let { this.fontFamily = it }
    styleProps.fontSize?.let { if (it > 0) this.setFontSize(it) }
    styleProps.color?.let { this.color = it }
}

// --- fillTemplate Function (remains the same) ---
fun fillTemplate(
    inputPath: String,
    outputPath: String,
    data: Map<String, String>,
    globalIsBold: Boolean,
    globalIsItalic: Boolean,
    globalFontFamily: String?
) {
    FileInputStream(inputPath).use { fis ->
        val doc = XWPFDocument(fis)
        val placeholderRegex = Regex("\\{([^}]+)\\}")

        doc.paragraphs.forEach { paragraph ->
            processParagraphRuns(paragraph, data, placeholderRegex, globalIsBold, globalIsItalic, globalFontFamily)
        }
        for (table in doc.tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    cell.paragraphs.forEach { paragraphInCell ->
                        processParagraphRuns(
                            paragraphInCell, data, placeholderRegex, globalIsBold, globalIsItalic, globalFontFamily
                        )
                    }
                }
            }
        }
        try {
            FileOutputStream(outputPath).use { fos -> doc.write(fos) }
        } catch (e: IOException) {
            throw IOException("Error writing to '$outputPath': ${e.message}", e)
        } finally {
            try {
                doc.close()
            } catch (e: IOException) { /* Log */
            }
        }
    }
}

// --- processParagraphRuns Function (remains the same) ---
private fun processParagraphRuns(
    paragraph: XWPFParagraph,
    data: Map<String, String>,
    placeholderRegex: Regex,
    globalIsBold: Boolean,
    globalIsItalic: Boolean,
    globalFontFamily: String?
) {
    val originalRuns = ArrayList(paragraph.runs)
    var needsRebuilding = false
    if (originalRuns.isEmpty() && placeholderRegex.containsMatchIn(paragraph.text)) {
        needsRebuilding = true
    } else {
        for (run in originalRuns) {
            if (placeholderRegex.containsMatchIn(run.text())) {
                needsRebuilding = true
                break
            }
        }
    }

    if (!needsRebuilding) return

    val newRunsData = mutableListOf<Triple<String, StyleProperties?, Boolean>>()

    if (originalRuns.isEmpty()) {
        val paragraphText = paragraph.text
        var currentSegmentStartIndex = 0
        placeholderRegex.findAll(paragraphText).forEach { matchResult ->
            val key = matchResult.groupValues.getOrNull(1) ?: ""
            val placeholderStart = matchResult.range.first
            val placeholderEnd = matchResult.range.last + 1

            if (placeholderStart > currentSegmentStartIndex) {
                newRunsData.add(
                    Triple(
                        paragraphText.substring(currentSegmentStartIndex, placeholderStart), StyleProperties(), false
                    )
                )
            }
            val replacementText = data[key]
            if (replacementText != null) {
                newRunsData.add(Triple(replacementText, null, true))
            } else {
                newRunsData.add(Triple(matchResult.value, StyleProperties(), false))
            }
            currentSegmentStartIndex = placeholderEnd
        }
        if (currentSegmentStartIndex < paragraphText.length) {
            newRunsData.add(Triple(paragraphText.substring(currentSegmentStartIndex), StyleProperties(), false))
        }
    } else {
        originalRuns.forEach { run ->
            val runText = run.text()
            val originalRunStyle = run.extractStyle()
            if (!placeholderRegex.containsMatchIn(runText)) {
                newRunsData.add(Triple(runText, originalRunStyle, false))
            } else {
                var lastIndex = 0
                placeholderRegex.findAll(runText).forEach { matchResult ->
                    val key = matchResult.groupValues.getOrNull(1) ?: ""
                    val placeholderStart = matchResult.range.first
                    if (placeholderStart > lastIndex) {
                        newRunsData.add(Triple(runText.substring(lastIndex, placeholderStart), originalRunStyle, false))
                    }
                    val replacementText = data[key]
                    if (replacementText != null) {
                        newRunsData.add(Triple(replacementText, originalRunStyle, true))
                    } else {
                        newRunsData.add(Triple(matchResult.value, originalRunStyle, false))
                    }
                    lastIndex = matchResult.range.last + 1
                }
                if (lastIndex < runText.length) {
                    newRunsData.add(Triple(runText.substring(lastIndex), originalRunStyle, false))
                }
            }
        }
    }

    if (newRunsData.isNotEmpty()) {
        while (paragraph.runs.isNotEmpty()) paragraph.removeRun(0)
        newRunsData.forEach { (text, originalStyle, isReplacement) ->
            val newRun = paragraph.createRun()
            newRun.setText(text)
            if (isReplacement) {
                newRun.isBold = globalIsBold
                newRun.isItalic = globalIsItalic
                if (!globalFontFamily.isNullOrBlank()) {
                    newRun.fontFamily = globalFontFamily
                } else {
                    originalStyle?.fontFamily?.let { newRun.fontFamily = it }
                }
                originalStyle?.fontSize?.let { newRun.setFontSize(it) }
                originalStyle?.color?.let { newRun.color = it }
                newRun.underline = originalStyle?.underline ?: UnderlinePatterns.NONE
                newRun.isStrikeThrough = originalStyle?.isStrikeThrough ?: false
            } else {
                originalStyle?.let { newRun.applyStyleProperties(it) }
            }
        }
    }
}


// --- TemplateKeys (remains the same) ---
object TemplateKeys {
    const val OBJECT_NAME = "object_name";
    const val OBJECT_DESC = "object_desc"
    const val SUB_CONTRACTOR = "sub_contractor";
    const val SUB_CONTRACTOR_NAME = "sub_contractor_name"
    const val CONTRACTOR = "contractor";
    const val CONTRACTOR_NAME = "contractor_name"
    const val DESIGN_ORG = "design_org";
    const val DESIGN_ORG_NAME = "design_org_name"
    const val CUSTOMER = "customer";
    const val CUSTOMER_NAME = "customer_name"
    const val CERTIFICATION = "certification"
}

// --- Function to extract PLAIN text for preview (remains the same) ---
fun extractTextFromDocx(filePath: String): String {
    return try {
        FileInputStream(filePath).use { fis ->
            XWPFDocument(fis).use { document ->
                XWPFWordExtractor(document).use { extractor -> extractor.text ?: "Matn topilmadi." }
            }
        }
    } catch (e: Exception) {
        "Hujjat matnini oldindan ko'rishda xatolik: ${e.message}"
    }
}

// --- FolderPickerButton Composable (remains the same) ---
@Composable
fun FolderPickerButton(buttonText: String, selectedPath: String, onPathSelected: (String) -> Unit) {
    var isDialogVisible by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { isDialogVisible = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, "Folder", Modifier.padding(end = 8.dp))
        Text(if (selectedPath.isNotEmpty()) "$buttonText: $selectedPath" else "$buttonText: Tanlanmagan")
    }
    if (isDialogVisible) {
        DisposableEffect(Unit) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Papka Tanlash"; selectedPath.takeIf { it.isNotEmpty() }
                ?.let { currentDirectory = File(it) }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath?.let(onPathSelected)
            }
            isDialogVisible = false; onDispose {}
        }
    }
}

@Composable
@Preview
fun App() {
    var formData by remember { mutableStateOf(FormData()) }
    var resultMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var templateFolderPath by remember { mutableStateOf(loadStringPreference(KEY_TEMPLATE_FOLDER)) }
    var outputFolderPath by remember { mutableStateOf(loadStringPreference(KEY_OUTPUT_FOLDER)) }

    var globalStyleIsBold by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_BOLD)) }
    var globalStyleIsItalic by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_ITALIC)) }
    var globalStyleFontFamily by remember { mutableStateOf(loadStringPreference(KEY_GLOBAL_FONT_FAMILY)) }

    // State for font dropdown
    var fontDropdownExpanded by remember { mutableStateOf(false) }
    val systemFontFamilies = remember {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
        } catch (e: Exception) {
            println("Error getting system fonts: ${e.message}. Using fallback list.")
            // Provide a basic fallback list if system fonts can't be loaded
            listOf(
                "Arial",
                "Calibri",
                "Times New Roman",
                "Courier New",
                "Verdana",
                "Georgia",
                "Impact",
                "Comic Sans MS"
            )
        }
    }

    var documentPreviewText by remember { mutableStateOf("Hujjat oldindan ko'rish uchun shu yerda paydo bo'ladi.\n\nAvval manba va chiqish papkalarini tanlang, so'ng ma'lumotlarni to'ldirib, \"Hujjatlarni To'ldirish\" tugmasini bosing.") }
    var lastProcessedFileName by remember { mutableStateOf<String?>(null) }

    var previewScale by remember { mutableStateOf(1f) }
    val minScale = 0.3f
    val maxScale = 3.0f
    val scaleIncrement = 0.1f

    val a4AspectRatio = 1.414f
    val previewBaseWidth = 350.dp
    val previewBaseHeight = previewBaseWidth * a4AspectRatio
    val previewTextBaseFontSize = 10.sp

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            Column( // Left Pane
                modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Hujjat Ma'lumotlari", style = MaterialTheme.typography.h5)
                FolderPickerButton("Manba papkasi", templateFolderPath) {
                    templateFolderPath = it; saveStringPreference(KEY_TEMPLATE_FOLDER, it)
                }
                FolderPickerButton("Chiqish papkasi", outputFolderPath) {
                    outputFolderPath = it; saveStringPreference(KEY_OUTPUT_FOLDER, it)
                }
                Divider(Modifier.padding(vertical = 8.dp))

                Text("Font style", style = MaterialTheme.typography.subtitle1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bold:")
                    Switch(
                        checked = globalStyleIsBold,
                        onCheckedChange = { globalStyleIsBold = it; saveBooleanPreference(KEY_GLOBAL_BOLD, it) })
                    Text("Italic:")
                    Switch(
                        checked = globalStyleIsItalic,
                        onCheckedChange = { globalStyleIsItalic = it; saveBooleanPreference(KEY_GLOBAL_ITALIC, it) })
                }

                // Font Family Dropdown
                ExposedDropdownMenuBox(
                    expanded = fontDropdownExpanded,
                    onExpandedChange = { fontDropdownExpanded = !fontDropdownExpanded },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp) // Added some top padding
                ) {
                    OutlinedTextField(
                        value = globalStyleFontFamily.ifEmpty { "Font Family" }, // Display current or placeholder
                        onValueChange = { /* This TextField is read-only for selection purposes */ },
                        label = { Text("Font Family") },
                        readOnly = true, // Important: selection is via dropdown
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth() // This modifier is for the TextField itself within the Box
                    )
                    ExposedDropdownMenu(
                        expanded = fontDropdownExpanded,
                        onDismissRequest = { fontDropdownExpanded = false }
                    ) {
                        if (systemFontFamilies.isNotEmpty()) {
                            systemFontFamilies.forEach { selectionOption ->
                                DropdownMenuItem(
                                    onClick = {
                                        globalStyleFontFamily = selectionOption
                                        saveStringPreference(KEY_GLOBAL_FONT_FAMILY, selectionOption)
                                        fontDropdownExpanded = false
                                    }
                                ) {
                                    Text(text = selectionOption)
                                }
                            }
                        } else {
                            DropdownMenuItem(onClick = {}, enabled = false) {
                                Text("No system fonts found")
                            }
                        }
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))

                // Input Fields (OutlinedTextFields for formData remain the same)
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
                    singleLine = false
                )
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
                OutlinedTextField(
                    formData.certification,
                    { formData = formData.copy(certification = it) },
                    label = { Text("наименование скрытых работ") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp),
                    singleLine = false
                )


                Spacer(Modifier.height(12.dp))
                Button( // Process Button (logic remains the same)
                    onClick = {
                        if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
                            resultMessage = "Iltimos, manba va chiqish papkalarini tanlang."
                            return@Button
                        }
                        isProcessing = true; resultMessage = "Qayta ishlanmoqda..."; documentPreviewText =
                        "Hujjatlar qayta ishlanmoqda..."; lastProcessedFileName = null
                        coroutineScope.launch(Dispatchers.IO) {
                            val dataMap = mapOf(
                                TemplateKeys.OBJECT_NAME to formData.objectName,
                                TemplateKeys.OBJECT_DESC to formData.objectDesc,
                                TemplateKeys.SUB_CONTRACTOR to formData.subContractor,
                                TemplateKeys.SUB_CONTRACTOR_NAME to formData.subContractorName,
                                TemplateKeys.CONTRACTOR to formData.contractor,
                                TemplateKeys.CONTRACTOR_NAME to formData.contractorName,
                                TemplateKeys.CUSTOMER to formData.customer,
                                TemplateKeys.CUSTOMER_NAME to formData.customerName,
                                TemplateKeys.DESIGN_ORG to formData.designOrg,
                                TemplateKeys.DESIGN_ORG_NAME to formData.designOrgName,
                                TemplateKeys.CERTIFICATION to formData.certification
                            )
                            var currentMsg = "";
                            var firstSuccessPath: String? = null
                            try {
                                val tDir = File(templateFolderPath);
                                val oDir = File(outputFolderPath)
                                if (!tDir.exists() || !tDir.isDirectory) currentMsg =
                                    "Xatolik: Manba papkasi topilmadi."
                                else if (!oDir.exists()) oDir.mkdirs()
                                else if (!oDir.isDirectory) currentMsg = "Xatolik: Chiqish joyi papka emas."

                                if (currentMsg.isEmpty()) {
                                    var count = 0;
                                    val processed = mutableListOf<String>();
                                    val errors = mutableListOf<String>()
                                    tDir.listFiles()?.filter { it.isFile && it.extension.equals("docx", true) }
                                        ?.forEach { file ->
                                            val outFile = File(oDir, "filled_${file.name}")
                                            try {
                                                fillTemplate(
                                                    file.absolutePath,
                                                    outFile.absolutePath,
                                                    dataMap,
                                                    globalStyleIsBold,
                                                    globalStyleIsItalic,
                                                    globalStyleFontFamily.ifBlank { null })
                                                processed.add(file.name); if (firstSuccessPath == null) {
                                                    firstSuccessPath = outFile.absolutePath; lastProcessedFileName =
                                                        outFile.name
                                                }; count++
                                            } catch (e: Exception) {
                                                errors.add("${file.name} (${e.message})"); e.printStackTrace()
                                            }
                                        }
                                    currentMsg =
                                        if (count > 0) "$count ta hujjat to'ldirildi: ${processed.joinToString()}." else "Manba papkasida DOCX fayllar topilmadi."
                                    if (errors.isNotEmpty()) currentMsg += "\nXatoliklar: ${errors.joinToString()}"
                                    firstSuccessPath?.let { documentPreviewText = extractTextFromDocx(it) } ?: run {
                                        documentPreviewText =
                                            if (count == 0 && errors.isEmpty()) "Manba papkasida DOCX fayllar topilmadi."
                                            else if (errors.isNotEmpty() && count == 0) "Hujjatlarni qayta ishlashda xatolik." else "Oldindan ko'rish uchun hujjat yaratilmadi."
                                    }
                                }
                            } catch (e: Exception) {
                                currentMsg = "Umumiy xatolik: ${e.message}"; documentPreviewText =
                                    "Xatolik: ${e.message}"; e.printStackTrace()
                            } finally {
                                resultMessage = currentMsg; isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing, modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(Modifier.size(24.dp), MaterialTheme.colors.onPrimary, 3.dp); Spacer(
                            Modifier.width(12.dp)
                        ); Text("To'ldirilmoqda...")
                    } else Text("Hujjatlarni To'ldirish")
                }
                if (resultMessage.isNotEmpty()) Text(resultMessage, Modifier.padding(top = 12.dp))
            }
            Divider(Modifier.fillMaxHeight().width(1.dp).padding(vertical = 16.dp))

            // Right Pane - Preview Area (contents remain the same as previous version v3.10)
            Column(
                Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    lastProcessedFileName?.let { "Oldindan ko'rish: $it" } ?: "Hujjat Oldindan Ko'rish",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
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
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Text(
                            documentPreviewText,
                            Modifier.padding(16.dp),
                            fontSize = previewTextBaseFontSize
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { previewScale = (previewScale + scaleIncrement).coerceIn(minScale, maxScale) }) {
                        Icon(Icons.Default.ZoomIn, "Zoom In")
                    }
                    Text("Zoom: ${(previewScale * 100).toInt()}%")
                    Button(onClick = { previewScale = (previewScale - scaleIncrement).coerceIn(minScale, maxScale) }) {
                        Icon(Icons.Default.ZoomOut, "Zoom Out")
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hujjat(AKT) To'ldiruvchi", // Updated title
        state = WindowState(placement = WindowPlacement.Maximized)
    ) {
        App()
    }
}