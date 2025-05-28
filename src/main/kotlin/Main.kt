@file:OptIn(ExperimentalMaterialApi::class)

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save // Icon for output file name
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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

// --- Preference Keys and Node Path ---
private const val PREFS_NODE_PATH = "com.example.hujjatoldiruvchi.prefs"
private const val KEY_TEMPLATE_FOLDER = "templateFolderPath"
private const val KEY_OUTPUT_FOLDER = "outputFolderPath"
private const val KEY_OUTPUT_FILENAME = "outputFileName" // New key for output file name
private const val KEY_GLOBAL_BOLD = "globalStyleBold"
private const val KEY_GLOBAL_ITALIC = "globalStyleItalic"
private const val KEY_GLOBAL_FONT_FAMILY = "globalStyleFontFamily"

// --- Helper functions to save and load preferences ---
private fun saveStringPreference(key: String, value: String) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).put(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        println("Error saving string preference $key: ${e.message}")
        e.printStackTrace()
    }
}

private fun loadStringPreference(key: String, defaultValue: String = ""): String {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).get(key, defaultValue)
    } catch (e: Exception) {
        println("Error loading string preference $key: ${e.message}")
        e.printStackTrace()
        defaultValue
    }
}

private fun saveBooleanPreference(key: String, value: Boolean) {
    try {
        Preferences.userRoot().node(PREFS_NODE_PATH).putBoolean(key, value)
        Preferences.userRoot().node(PREFS_NODE_PATH).flush()
    } catch (e: Exception) {
        println("Error saving boolean preference $key: ${e.message}")
        e.printStackTrace()
    }
}

private fun loadBooleanPreference(key: String, defaultValue: Boolean = false): Boolean {
    return try {
        Preferences.userRoot().node(PREFS_NODE_PATH).getBoolean(key, defaultValue)
    } catch (e: Exception) {
        println("Error loading boolean preference $key: ${e.message}")
        e.printStackTrace()
        defaultValue
    }
}

// --- FormData ---
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

// --- StyleProperties for XWPFRun ---
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
        e.printStackTrace()
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

// --- fillTemplate Function ---
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
        val placeholderRegex = Regex("\\{([^}]+)}")

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
            } catch (e: IOException) {
                println("Error closing document for '$inputPath': ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

// --- processParagraphRuns Function ---
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
            if (run.text() != null && placeholderRegex.containsMatchIn(run.text())) {
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
            val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
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
            if (runText == null) {
                return@forEach
            }
            val originalRunStyle = run.extractStyle()
            if (!placeholderRegex.containsMatchIn(runText)) {
                newRunsData.add(Triple(runText, originalRunStyle, false))
            } else {
                var lastIndex = 0
                placeholderRegex.findAll(runText).forEach { matchResult ->
                    val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
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
        while (paragraph.runs.isNotEmpty()) {
            paragraph.removeRun(0)
        }
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
                originalStyle?.fontSize?.let { if (it > 0) newRun.setFontSize(it) }
                originalStyle?.color?.let { newRun.color = it }
                newRun.underline = originalStyle?.underline ?: UnderlinePatterns.NONE
                newRun.isStrikeThrough = originalStyle?.isStrikeThrough ?: false
            } else {
                originalStyle?.let { newRun.applyStyleProperties(it) }
            }
        }
    }
}

// --- TemplateKeys ---
object TemplateKeys {
    const val OBJECT_NAME = "object_name"
    const val OBJECT_DESC = "object_desc"
    const val SUB_CONTRACTOR = "sub_contractor"
    const val SUB_CONTRACTOR_NAME = "sub_contractor_name"
    const val CONTRACTOR = "contractor"
    const val CONTRACTOR_NAME = "contractor_name"
    const val DESIGN_ORG = "design_org"
    const val DESIGN_ORG_NAME = "design_org_name"
    const val CUSTOMER = "customer"
    const val CUSTOMER_NAME = "customer_name"
    const val CERTIFICATION = "certification"
}

// --- Function to extract PLAIN text for preview ---
fun extractTextFromDocx(filePath: String): String {
    return try {
        FileInputStream(filePath).use { fis ->
            XWPFDocument(fis).use { document ->
                XWPFWordExtractor(document).use { extractor -> extractor.text ?: "Matn topilmadi." }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "Hujjat matnini oldindan ko'rishda xatolik: ${e.message}"
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
        Text(if (selectedPath.isNotEmpty()) selectedPath else "$buttonText: Tanlanmagan", maxLines = 1)
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
@Preview
fun App() {
    var formData by remember { mutableStateOf(FormData()) }
    var resultMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var templateFolderPath by remember { mutableStateOf(loadStringPreference(KEY_TEMPLATE_FOLDER)) }
    var outputFolderPath by remember { mutableStateOf(loadStringPreference(KEY_OUTPUT_FOLDER)) }
    var outputFileName by remember { mutableStateOf(loadStringPreference(KEY_OUTPUT_FILENAME)) } // State for output file name
    var globalStyleIsBold by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_BOLD)) }
    var globalStyleIsItalic by remember { mutableStateOf(loadBooleanPreference(KEY_GLOBAL_ITALIC)) }
    var globalStyleFontFamily by remember { mutableStateOf(loadStringPreference(KEY_GLOBAL_FONT_FAMILY)) }

    var documentPreviewText by remember { mutableStateOf("Hujjat oldindan ko'rish uchun shu yerda paydo bo'ladi.\n\nAvval manba va chiqish papkalarini tanlang, so'ng ma'lumotlarni to'ldirib, \"Hujjatlarni To'ldirish\" tugmasini bosing.") }
    var lastProcessedFileNameForPreview by remember { mutableStateOf<String?>(null) }

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
            // Left Pane: Controls
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Hujjatlarni Avtomatik To'ldirish",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                                println("Error getting system fonts: ${e.message}. Using fallback list.")
                                e.printStackTrace()
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
                            label = { Text("Nomi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.objectDesc,
                            { formData = formData.copy(objectDesc = it) },
                            label = { Text("Tavsifi (объект)") },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp),
                        )
                        OutlinedTextField(
                            formData.certification,
                            { formData = formData.copy(certification = it) },
                            label = { Text("Yashirin ishlar nomi") },
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
                            label = { Text("Subpudratchi (lavozimi)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.subContractorName,
                            { formData = formData.copy(subContractorName = it) },
                            label = { Text("Subpudratchi (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.contractor,
                            { formData = formData.copy(contractor = it) },
                            label = { Text("Pudratchi (lavozimi)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.contractorName,
                            { formData = formData.copy(contractorName = it) },
                            label = { Text("Pudratchi (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.customer,
                            { formData = formData.copy(customer = it) },
                            label = { Text("Buyurtmachi (lavozimi)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.customerName,
                            { formData = formData.copy(customerName = it) },
                            label = { Text("Buyurtmachi (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Divider(Modifier.padding(vertical = 6.dp))
                        OutlinedTextField(
                            formData.designOrg,
                            { formData = formData.copy(designOrg = it) },
                            label = { Text("Loyiha tashkiloti (lavozimi)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            formData.designOrgName,
                            { formData = formData.copy(designOrgName = it) },
                            label = { Text("Loyiha tashkiloti (F.I.O)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }


                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
                            resultMessage = "Iltimos, manba va chiqish papkalarini tanlang."
                            return@Button
                        }
                        isProcessing = true
                        resultMessage = "Qayta ishlanmoqda..."
                        documentPreviewText = "Hujjatlar qayta ishlanmoqda..."
                        var tempLastProcessedFileNameForPreview: String? = null

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
                            val rootTemplateDir = File(templateFolderPath)
                            val rootOutputDir = File(outputFolderPath)
                            var currentMsg = ""
                            var firstSuccessPathForPreview: String? = null
                            val errorFileMessagesList = mutableListOf<String>()
                            var filesProcessedCount = 0

                            fun processDirectory(currentTemplateDir: File) {
                                currentTemplateDir.listFiles()?.forEach { entry ->
                                    if (entry.isDirectory) {
                                        val relativePath = entry.toRelativeString(rootTemplateDir)
                                        val outputSubDir = File(rootOutputDir, relativePath)
                                        if (!outputSubDir.exists()) {
                                            outputSubDir.mkdirs()
                                        }
                                        processDirectory(entry)
                                    } else if (entry.isFile && entry.extension.equals("docx", ignoreCase = true)) {
                                        try {
                                            val relativePath = entry.toRelativeString(rootTemplateDir)
                                            val relativeParent = File(relativePath).parent

                                            val outputDirWithSubfolders = if (relativeParent != null) {
                                                File(rootOutputDir, relativeParent)
                                            } else {
                                                rootOutputDir
                                            }
                                            outputDirWithSubfolders.mkdirs()

                                            // Use custom output file name if provided, else use original name with "filled_" prefix
                                            val finalOutputName = if (outputFileName.isNotBlank()) {
                                                // Ensure it has .docx extension
                                                if (outputFileName.endsWith(
                                                        ".docx",
                                                        ignoreCase = true
                                                    )
                                                ) outputFileName else "$outputFileName.docx"
                                            } else {
                                                entry.name // Use original name if outputFileName is blank
                                            }
                                            val finalOutputFile = File(outputDirWithSubfolders, finalOutputName)


                                            fillTemplate(
                                                entry.absolutePath,
                                                finalOutputFile.absolutePath,
                                                dataMap,
                                                globalStyleIsBold,
                                                globalStyleIsItalic,
                                                globalStyleFontFamily.ifBlank { null }
                                            )
                                            if (firstSuccessPathForPreview == null) {
                                                firstSuccessPathForPreview = finalOutputFile.absolutePath
                                                tempLastProcessedFileNameForPreview = finalOutputFile.name
                                            }
                                            filesProcessedCount++
                                        } catch (e: Exception) {
                                            val errorLocation =
                                                if (entry.parentFile != null && entry.parentFile != rootTemplateDir) {
                                                    entry.parentFile.toRelativeString(rootTemplateDir)
                                                } else "root"
                                            errorFileMessagesList.add("${entry.name} (in '$errorLocation'): ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }

                            try {
                                if (!rootTemplateDir.exists() || !rootTemplateDir.isDirectory) {
                                    currentMsg = "Xatolik: Manba papkasi topilmadi yoki papka emas."
                                } else if (!rootOutputDir.exists() && !rootOutputDir.mkdirs()) {
                                    currentMsg = "Xatolik: Chiqish papkasini yaratib bo'lmadi."
                                } else if (!rootOutputDir.isDirectory) {
                                    currentMsg = "Xatolik: Chiqish joyi papka emas."
                                }

                                if (currentMsg.isEmpty()) {
                                    processDirectory(rootTemplateDir)

                                    currentMsg = if (filesProcessedCount > 0) {
                                        "$filesProcessedCount ta hujjat muvaffaqiyatli to'ldirildi."
                                    } else {
                                        "Manba papkasida DOCX fayllar topilmadi."
                                    }
                                    if (errorFileMessagesList.isNotEmpty()) {
                                        currentMsg += "\nQuyidagi fayllarda xatoliklar yuz berdi:\n - ${
                                            errorFileMessagesList.joinToString(
                                                "\n - "
                                            )
                                        }"
                                    }
                                }
                            } catch (e: Exception) {
                                currentMsg = "Umumiy kutilmagan xatolik: ${e.message}"
                                e.printStackTrace()
                            } finally {
                                resultMessage = currentMsg
                                isProcessing = false
                                lastProcessedFileNameForPreview = tempLastProcessedFileNameForPreview

                                documentPreviewText = if (firstSuccessPathForPreview != null) {
                                    extractTextFromDocx(firstSuccessPathForPreview!!)
                                } else {
                                    if (filesProcessedCount == 0 && errorFileMessagesList.isEmpty()) {
                                        "Manba papkasida DOCX fayllar topilmadi."
                                    } else if (errorFileMessagesList.isNotEmpty() && filesProcessedCount == 0) {
                                        "Hujjatlarni qayta ishlashda xatolik yuz berdi. Oldindan ko'rish uchun fayl yo'q."
                                    } else {
                                        "Oldindan ko'rish uchun hujjat yaratilmadi yoki topilmadi."
                                    }
                                }
                            }
                        }
                    }, enabled = !isProcessing, modifier = Modifier.fillMaxWidth().height(56.dp) // Increased height
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp), color = MaterialTheme.colors.onPrimary, strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Qayta ishlanmoqda...")
                    } else {
                        Text("Hujjatlarni To'ldirish", fontSize = 16.sp) // Slightly larger font
                    }
                }
                if (resultMessage.isNotEmpty()) {
                    Text(
                        resultMessage,
                        Modifier.padding(top = 12.dp),
                        color = if (resultMessage.startsWith("Xatolik") || resultMessage.contains("xatoliklar yuz berdi")) MaterialTheme.colors.error else MaterialTheme.colors.onSurface
                    )
                }
            }

            Divider(Modifier.fillMaxHeight().width(1.dp).padding(vertical = 16.dp))

            // Right Pane: Preview
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colors.surface.copy(alpha = 0.5f)) // Softer background
                    .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    lastProcessedFileNameForPreview?.let { "Oldindan ko'rish: $it" } ?: "Hujjat Oldindan Ko'rish",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Box(
                    modifier = Modifier.weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(
                            MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                            shape = MaterialTheme.shapes.medium
                        ) // Added background and shape to viewport
                        .border(1.dp, Color.LightGray, shape = MaterialTheme.shapes.medium), // Border for viewport
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .width(previewBaseWidth)
                            .height(previewBaseHeight)
                            .scale(scaleX = previewScale, scaleY = previewScale)
                            .verticalScroll(rememberScrollState()),
                        color = Color.White, // Explicit white for paper
                        elevation = 4.dp,
                        border = BorderStroke(1.dp, Color.DarkGray) // Darker border for "paper"
                    ) {
                        Text(
                            documentPreviewText, Modifier.padding(16.dp),
                            fontSize = previewTextBaseFontSize,
                            lineHeight = previewTextBaseFontSize * 1.5 // Improved line height for readability
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
                        // Spacer(Modifier.width(4.dp))
                        // Text("Kichraytirish")
                    }
                    Text("Masshtab: ${(previewScale * 100).toInt()}%", modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = {
                        previewScale = (previewScale + scaleIncrement).coerceIn(minScale, maxScale)
                    }) {
                        Icon(Icons.Default.ZoomIn, "Kattalashtirish")
                        // Spacer(Modifier.width(4.dp))
                        // Text("Kattalashtirish")
                    }
                }
            }
        }
    }
}

fun main() = application {

    val icon = painterResource("resources/icons/png_icon.png")


    Window(
        onCloseRequest = ::exitApplication,
        title = "Hujjat (AKT) To'ldiruvchi",
        state = WindowState(placement = WindowPlacement.Maximized),
        icon = icon
    ) {
        App()
    }
}