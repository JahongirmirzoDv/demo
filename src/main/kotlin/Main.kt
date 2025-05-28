import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
// import androidx.compose.foundation.border // Not used directly in the latest version, Surface has border
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
import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.text.TextStyle // Not used directly
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.swing.JFileChooser
import java.util.prefs.Preferences // Added for saving preferences

// --- Preference Keys and Node Path ---
private const val PREFS_NODE_PATH = "com.example.hujjatoldiruvchi.prefs"
private const val KEY_TEMPLATE_FOLDER = "templateFolderPath"
private const val KEY_OUTPUT_FOLDER = "outputFolderPath"

// --- Helper functions to save and load preferences ---
private fun savePathPreference(key: String, path: String) {
    try {
        val prefs = Preferences.userRoot().node(PREFS_NODE_PATH)
        prefs.put(key, path)
        prefs.flush() // Ensure changes are written
    } catch (e: SecurityException) {
        println("SecurityException while saving preference $key: ${e.message}")
        // Handle appropriately, e.g., inform user or log
    } catch (e: Exception) {
        println("Exception while saving preference $key: ${e.message}")
    }
}

private fun loadPathPreference(key: String): String {
    return try {
        val prefs = Preferences.userRoot().node(PREFS_NODE_PATH)
        prefs.get(key, "") // Return empty string if not found
    } catch (e: SecurityException) {
        println("SecurityException while loading preference $key: ${e.message}")
        "" // Return default on error
    } catch (e: Exception) {
        println("Exception while loading preference $key: ${e.message}")
        "" // Return default on error
    }
}


// --- StyleProperties Data Class and XWPFRun Extensions ---
data class StyleProperties(
    val isBold: Boolean = false, val isItalic: Boolean = false,
    val underline: UnderlinePatterns = UnderlinePatterns.NONE,
    val isStrikeThrough: Boolean = false, val isCapitalized: Boolean = false,
    val fontFamily: String? = null, val fontSize: Double? = null, val color: String? = null
)

fun XWPFRun.extractStyle(): StyleProperties {
    val sizeFromPOI: Double? = try {
        this.fontSizeAsDouble
    } catch (e: Exception) {
        null
    }
    val finalFontSize: Double? = if (sizeFromPOI != null && sizeFromPOI > 0) sizeFromPOI else null
    return StyleProperties(
        isBold = this.isBold, isItalic = this.isItalic,
        underline = this.underline ?: UnderlinePatterns.NONE,
        isStrikeThrough = this.isStrikeThrough, isCapitalized = this.isCapitalized,
        fontFamily = this.fontFamily, fontSize = finalFontSize, color = this.color
    )
}

fun XWPFRun.applyStyle(style: StyleProperties) {
    this.isBold = style.isBold; this.isItalic = style.isItalic
    if (style.underline != UnderlinePatterns.NONE) this.underline = style.underline
    this.isStrikeThrough = style.isStrikeThrough; this.isCapitalized = style.isCapitalized
    style.fontFamily?.let { this.fontFamily = it }
    style.fontSize?.let { if (it > 0) this.setFontSize(it) }
    style.color?.let { this.color = it }
}

// --- fillTemplate Function ---
fun fillTemplate(inputPath: String, outputPath: String, data: Map<String, String>) {
    FileInputStream(inputPath).use { fis ->
        val doc = XWPFDocument(fis)
        fun replaceInParagraph(paragraph: XWPFParagraph, dataMap: Map<String, String>) {
            val paragraphText = paragraph.text
            var needsReplacement = false
            for (key in dataMap.keys) {
                if (paragraphText.contains("{$key}")) {
                    needsReplacement = true
                    break
                }
            }
            if (!needsReplacement) return
            var firstRunStyle: StyleProperties? = null
            if (paragraph.runs.isNotEmpty()) {
                try {
                    firstRunStyle = paragraph.runs[0].extractStyle()
                } catch (e: Exception) {
                    println("Warning: Could not extract style. Para: '${paragraph.text.take(30)}...' Error: ${e.message}")
                }
            }
            var replacedText = paragraphText
            for ((key, value) in dataMap) {
                replacedText = replacedText.replace("{$key}", value ?: "")
            }
            if (replacedText != paragraphText) {
                while (paragraph.runs.isNotEmpty()) paragraph.removeRun(0)
                val newRun = paragraph.createRun()
                newRun.setText(replacedText)
                firstRunStyle?.let { newRun.applyStyle(it) }
            }
        }
        doc.paragraphs.forEach { replaceInParagraph(it, data) }
        for (table in doc.tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    cell.paragraphs.forEach { paraInCell -> replaceInParagraph(paraInCell, data) }
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

// --- FormData and TemplateKeys ---
data class FormData(
    val objectName: String = "", val objectDesc: String = "",
    val subContractor: String = "", val subContractorName: String = "",
    val contractor: String = "", val contractorName: String = "",
    val designOrg: String = "", val designOrgName: String = "",
    val customer: String = "", val customerName: String = "",
    val certification: String = ""
)

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

// --- Function to extract PLAIN text for preview ---
fun extractTextFromDocx(filePath: String): String {
    return try {
        FileInputStream(filePath).use { fis ->
            XWPFDocument(fis).use { document ->
                XWPFWordExtractor(document).use { extractor ->
                    extractor.text ?: "Matn topilmadi (null qaytardi)."
                }
            }
        }
    } catch (e: Exception) {
        println("Oldindan ko'rish uchun matn chiqarishda xatolik ($filePath): ${e.message}")
        e.printStackTrace()
        "Hujjat matnini oldindan ko'rishda xatolik yuz berdi: ${e.message}"
    }
}


// --- FolderPickerButton Composable ---
@Composable
fun FolderPickerButton(
    buttonText: String, selectedPath: String,
    onPathSelected: (String) -> Unit, modifier: Modifier = Modifier
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { isDialogVisible = true }, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Default.FolderOpen, "Open Folder", Modifier.padding(end = 8.dp))
        Text(if (selectedPath.isNotEmpty()) "$buttonText: $selectedPath" else "$buttonText: Tanlanmagan")
    }

    if (isDialogVisible) {
        DisposableEffect(Unit) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Papka Tanlash"
                selectedPath.takeIf { it.isNotEmpty() }?.let { currentDirectory = File(it) }
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

@Composable
@Preview
fun App() {
    var formData by remember { mutableStateOf(FormData()) }
    var resultMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize folder paths from preferences
    var templateFolderPath by remember { mutableStateOf(loadPathPreference(KEY_TEMPLATE_FOLDER)) }
    var outputFolderPath by remember { mutableStateOf(loadPathPreference(KEY_OUTPUT_FOLDER)) }

    var documentPreviewText by remember { mutableStateOf("Hujjat oldindan ko'rish uchun shu yerda paydo bo'ladi.\n\nAvval manba va chiqish papkalarini tanlang, so'ng ma'lumotlarni to'ldirib, \"Hujjatlarni To'ldirish\" tugmasini bosing.") }
    var lastProcessedFileName by remember { mutableStateOf<String?>(null) }

    var previewFontSize by remember { mutableStateOf(14.sp) }
    var previewFontWeight by remember { mutableStateOf(FontWeight.Normal) }
    var previewFontStyle by remember { mutableStateOf(FontStyle.Normal) }
    var previewFontFamily by remember { mutableStateOf(FontFamily.Default) }
    var fontMenuExpanded by remember { mutableStateOf(false) }
    val fontFamilies = listOf(FontFamily.Default, FontFamily.Serif, FontFamily.SansSerif, FontFamily.Monospace, FontFamily.Cursive)
    val fontFamilyNames = listOf("Default", "Serif", "SansSerif", "Monospace", "Cursive")


    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane: Controls and Inputs
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Hujjat Ma'lumotlari",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FolderPickerButton(
                    "Manba papkasi",
                    templateFolderPath,
                    { newPath ->
                        templateFolderPath = newPath
                        savePathPreference(KEY_TEMPLATE_FOLDER, newPath)
                    }
                )
                FolderPickerButton(
                    "Chiqish papkasi",
                    outputFolderPath,
                    { newPath ->
                        outputFolderPath = newPath
                        savePathPreference(KEY_OUTPUT_FOLDER, newPath)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val singleLineModifier = Modifier.fillMaxWidth()
                val multiLineModifier = Modifier.fillMaxWidth()

                OutlinedTextField(
                    formData.objectName,
                    { formData = formData.copy(objectName = it) },
                    label = { Text("Nomi") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.objectDesc,
                    { formData = formData.copy(objectDesc = it) },
                    label = { Text("Tavsifi (объект)") },
                    modifier = multiLineModifier,
                    singleLine = false
                )
                // ... (rest of the OutlinedTextFields remain the same)
                OutlinedTextField(
                    formData.subContractor,
                    { formData = formData.copy(subContractor = it) },
                    label = { Text("Subpudratchi (lavozimi)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.subContractorName,
                    { formData = formData.copy(subContractorName = it) },
                    label = { Text("Subpudratchi (F.I.O)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.contractor,
                    { formData = formData.copy(contractor = it) },
                    label = { Text("Pudratchi (lavozimi)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.contractorName,
                    { formData = formData.copy(contractorName = it) },
                    label = { Text("Pudratchi (F.I.O)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.customer,
                    { formData = formData.copy(customer = it) },
                    label = { Text("Buyurtmachi (lavozimi)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.customerName,
                    { formData = formData.copy(customerName = it) },
                    label = { Text("Buyurtmachi (F.I.O)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.designOrg,
                    { formData = formData.copy(designOrg = it) },
                    label = { Text("Loyiha tashkiloti (lavozimi)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.designOrgName,
                    { formData = formData.copy(designOrgName = it) },
                    label = { Text("Loyiha tashkiloti (F.I.O)") },
                    modifier = singleLineModifier,
                    singleLine = true
                )
                OutlinedTextField(
                    formData.certification,
                    { formData = formData.copy(certification = it) },
                    label = { Text("Yashirin ishlar nomi") },
                    modifier = multiLineModifier,
                    singleLine = false
                )


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
                        lastProcessedFileName = null

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
                            val templateDir = File(templateFolderPath);
                            val outputDir = File(outputFolderPath)
                            var currentResultMessage: String = ""
                            var firstSuccessPath: String? = null

                            try {
                                if (!templateDir.exists() || !templateDir.isDirectory) {
                                    currentResultMessage = "Xatolik: Manba papkasi topilmadi."
                                    // Removed: resultMessage = currentResultMessage; isProcessing = false; return@launch
                                    // This should be set in finally block or outside
                                } else if (!outputDir.exists()) {
                                    outputDir.mkdirs()
                                } else if (!outputDir.isDirectory) {
                                    currentResultMessage = "Xatolik: Chiqish joyi papka emas."
                                    // Removed: resultMessage = currentResultMessage; isProcessing = false; return@launch
                                }

                                if (currentResultMessage.isEmpty()) { // Proceed only if no initial folder errors
                                    var count = 0;
                                    val processedFiles = mutableListOf<String>();
                                    val errorFiles = mutableListOf<String>()
                                    templateDir.listFiles()?.filter { it.isFile && it.extension.equals("docx", true) }
                                        ?.forEach { file ->
                                            val outFile = File(outputDir, "filled_${file.name}")
                                            try {
                                                fillTemplate(file.absolutePath, outFile.absolutePath, dataMap)
                                                processedFiles.add(file.name)
                                                if (firstSuccessPath == null) {
                                                    firstSuccessPath = outFile.absolutePath
                                                    lastProcessedFileName = outFile.name
                                                }
                                                count++
                                            } catch (e: Exception) {
                                                errorFiles.add("${file.name} (Xato: ${e.message})"); e.printStackTrace()
                                            }
                                        }
                                    currentResultMessage =
                                        if (count > 0) "$count ta hujjat to'ldirildi: ${processedFiles.joinToString()}."
                                        else "Manba papkasida DOCX fayllar topilmadi."
                                    if (errorFiles.isNotEmpty()) currentResultMessage += "\nXatoliklar: ${errorFiles.joinToString()}"

                                    firstSuccessPath?.let {
                                        documentPreviewText = extractTextFromDocx(it)
                                    } ?: run {
                                        documentPreviewText = if (count == 0 && errorFiles.isEmpty()) {
                                            "Manba papkasida DOCX fayllar topilmadi."
                                        } else if (errorFiles.isNotEmpty() && count == 0) {
                                            "Hujjatlarni qayta ishlashda xatolik yuz berdi. Xatoliklarni tekshiring."
                                        } else if (count == 0) { // This case might be covered by the first one
                                            "Oldindan ko'rish uchun hujjat yaratilmadi."
                                        } else { // Should ideally not be reached if firstSuccessPath is null and count > 0
                                            "Oldindan ko'rish uchun fayl tanlanmadi."
                                        }
                                    }
                                } // end of if (currentResultMessage.isEmpty())

                            } catch (e: Exception) {
                                currentResultMessage = "Umumiy xatolik: ${e.message}"; e.printStackTrace()
                                documentPreviewText = "Oldindan ko'rishda xatolik: ${e.message}"
                            } finally {
                                resultMessage = currentResultMessage; isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            color = MaterialTheme.colors.onPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("To'ldirilmoqda...", style = MaterialTheme.typography.button)
                    } else {
                        Text("Hujjatlarni To'ldirish", style = MaterialTheme.typography.button)
                    }
                }
                if (resultMessage.isNotEmpty()) {
                    Text(
                        resultMessage,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // Vertical Divider
            Divider(modifier = Modifier.fillMaxHeight().width(1.dp).padding(vertical = 16.dp))

            // Right Pane: Document Preview
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
                    .padding(16.dp)
            ) {
                Text(
                    text = lastProcessedFileName?.let { "Oldindan ko'rish: $it" } ?: "Hujjat Oldindan Ko'rish",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Style:")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = previewFontWeight == FontWeight.Bold,
                            onCheckedChange = { checked ->
                                previewFontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                            }
                        )
                        Text("Bold", modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = previewFontStyle == FontStyle.Italic,
                            onCheckedChange = { checked ->
                                previewFontStyle = if (checked) FontStyle.Italic else FontStyle.Normal
                            }
                        )
                        Text("Italic", modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                    }
                    Box {
                        OutlinedButton(onClick = { fontMenuExpanded = true }) {
                            Text(fontFamilyNames[fontFamilies.indexOf(previewFontFamily)])
                        }
                        DropdownMenu(
                            expanded = fontMenuExpanded,
                            onDismissRequest = { fontMenuExpanded = false }
                        ) {
                            fontFamilies.forEachIndexed { index, fontFamily ->
                                DropdownMenuItem(onClick = {
                                    previewFontFamily = fontFamily
                                    fontMenuExpanded = false
                                }) {
                                    Text(fontFamilyNames[index])
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = Color.White,
                    elevation = 4.dp,
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text(
                        text = documentPreviewText,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        color = MaterialTheme.colors.onSurface,
                        fontSize = previewFontSize,
                        fontWeight = previewFontWeight,
                        fontStyle = previewFontStyle,
                        fontFamily = previewFontFamily
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        previewFontSize = (previewFontSize.value + 1).sp
                    }) {
                        Icon(Icons.Default.ZoomIn, "Zoom In")
                        Text(" Zoom In", modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = {
                        if (previewFontSize.value > 1) {
                            previewFontSize = (previewFontSize.value - 1).sp
                        }
                    }) {
                        Icon(Icons.Default.ZoomOut, "Zoom Out")
                        Text(" Zoom Out", modifier = Modifier.padding(start = 4.dp))
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