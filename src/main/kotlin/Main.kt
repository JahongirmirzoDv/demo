import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// --- StyleProperties Data Class and XWPFRun Extensions (from previous response) ---
// Data class to store style properties
data class StyleProperties(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val underline: UnderlinePatterns = UnderlinePatterns.NONE,
    val isStrikeThrough: Boolean = false,
    val isCapitalized: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Double? = null, // Explicitly nullable
    val color: String? = null
)

// Extension function to extract style from an XWPFRun
fun XWPFRun.extractStyle(): StyleProperties {
    val sizeFromPOI: Double? = try {
        this.fontSizeAsDouble
    } catch (npe: NullPointerException) {
        println("Debug: NullPointerException directly from fontSizeAsDouble() for run: '${this.text().take(30)}...'")
        null
    } catch (e: Exception) {
        println("Debug: Exception during fontSizeAsDouble() for run: '${this.text().take(30)}...'. Error: ${e.javaClass.simpleName} - ${e.message}")
        null
    }
    val finalFontSize: Double? = if (sizeFromPOI != null && sizeFromPOI > 0) sizeFromPOI else null

    return StyleProperties(
        isBold = this.isBold,
        isItalic = this.isItalic,
        underline = this.underline ?: UnderlinePatterns.NONE,
        isStrikeThrough = this.isStrikeThrough,
        isCapitalized = this.isCapitalized,
        fontFamily = this.fontFamily,
        fontSize = finalFontSize,
        color = this.color
    )
}

// Extension function to apply stored style to an XWPFRun
fun XWPFRun.applyStyle(style: StyleProperties) {
    this.isBold = style.isBold
    this.isItalic = style.isItalic
    if (style.underline != UnderlinePatterns.NONE) this.underline = style.underline
    this.isStrikeThrough = style.isStrikeThrough
    this.isCapitalized = style.isCapitalized
    style.fontFamily?.let { this.fontFamily = it }
    style.fontSize?.let { if (it > 0) this.setFontSize(it) }
    style.color?.let { this.color = it }
}

// --- fillTemplate Function (from previous response, ensure it's correct) ---
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
                    println("Warning: Could not extract style from first run. Para: '${paragraph.text.take(30)}...' Error: ${e.message}")
                }
            }

            var replacedText = paragraphText
            for ((key, value) in dataMap) {
                replacedText = replacedText.replace("{$key}", value ?: "")
            }

            if (replacedText != paragraphText) {
                while (paragraph.runs.isNotEmpty()) {
                    paragraph.removeRun(0)
                }
                val newRun = paragraph.createRun()
                newRun.setText(replacedText)
                firstRunStyle?.let { newRun.applyStyle(it) }
            }
        }

        doc.paragraphs.forEach { replaceInParagraph(it, data) }
        for (table in doc.tables) {
            for (row in table.rows) {
                for (cell in row.tableCells) {
                    cell.paragraphs.forEach { paraInCell ->
                        replaceInParagraph(paraInCell, data)
                    }
                }
            }
        }
        try {
            FileOutputStream(outputPath).use { fos -> doc.write(fos) }
        } catch (e: IOException) {
            throw IOException("Error writing to output file '$outputPath': ${e.message}", e)
        } finally {
            try { doc.close() } catch (e: IOException) { /* Log or handle */ }
        }
    }
}

// --- FormData and TemplateKeys (from previous response) ---
data class FormData(
    val objectName: String = "",
    val objectDesc: String = "", // This field could be multi-line
    val subContractor: String = "",
    val subContractorName: String = "",
    val contractor: String = "",
    val contractorName: String = "",
    val designOrg: String = "",
    val designOrgName: String = "",
    val customer: String = "",
    val customerName: String = "",
    val certification: String = "" // This could also be multi-line
)

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

// --- Composable for Folder Picker Button ---
@Composable
fun FolderPickerButton(
    buttonText: String,
    selectedPath: String,
    onPathSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder", modifier = Modifier.padding(end = 8.dp))
        Text(if (selectedPath.isNotEmpty()) "$buttonText: $selectedPath" else "$buttonText: Tanlanmagan")
    }

    if (showDialog) {
        AwtWindow(
            create = {
                // Using FileDialog for a native feel; JFileChooser is another option
                object : FileDialog(null as Frame?, "Select Folder", LOAD) {
                    init {
                        // For selecting directories with FileDialog (tricky, often needs system property or specific mode)
                        // For robust directory selection, JFileChooser is often preferred.
                        // However, FileDialog is simpler if it works for directory mode on the target OS.
                        // Let's try JFileChooser for better directory selection control.
                        // This AwtWindow will be invisible and just host JFileChooser.
                    }
                    override fun setVisible(visible: Boolean) {
                        if (visible) { // About to be shown
                            val chooser = javax.swing.JFileChooser().apply {
                                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Select Folder"
                                selectedPath.takeIf { it.isNotEmpty() }?.let { currentDirectory = File(it) }
                            }
                            val result = chooser.showOpenDialog(null)
                            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                chooser.selectedFile?.absolutePath?.let { onPathSelected(it) }
                            }
                        }
                        showDialog = false // Close our dialog state var
                        super.setVisible(false) // Keep AWT FileDialog invisible or dispose
                        dispose() // Dispose the FileDialog immediately
                    }
                }.apply { isVisible = true } // Trigger the overridden setVisible
            },
            dispose = { /* Nothing to do here as we dispose in setVisible */ }
        )
    }
}


@Composable
@Preview
fun App() {
    var formData by remember { mutableStateOf(FormData()) }
    var resultMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var templateFolderPath by remember { mutableStateOf("") }
    var outputFolderPath by remember { mutableStateOf("") }

    // For TextFields that might need more space
    val multilineFieldModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 70.dp) // Default smaller height, expands if content is larger

    val singlelineFieldModifier = Modifier.fillMaxWidth()


    MaterialTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp) // Increased spacing slightly
        ) {
            Text("Hujjat ma'lumotlari", style = MaterialTheme.typography.h5, modifier = Modifier.padding(bottom = 8.dp))

            // Folder Pickers
            FolderPickerButton("Manba papkasi", templateFolderPath, { templateFolderPath = it })
            FolderPickerButton("Chiqish papkasi", outputFolderPath, { outputFolderPath = it })

            Divider(modifier = Modifier.padding(vertical = 8.dp))


            // TextFields with varying height needs
            OutlinedTextField(formData.objectName, { formData = formData.copy(objectName = it) }, label = { Text("Nomi (наименование работ)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.objectDesc, { formData = formData.copy(objectDesc = it) }, label = { Text("Tavsifi (объект)") }, modifier = multilineFieldModifier) // Can be multi-line
            OutlinedTextField(formData.subContractor, { formData = formData.copy(subContractor = it) }, label = { Text("Subpudratchi (lavozimi)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.subContractorName, { formData = formData.copy(subContractorName = it) }, label = { Text("Subpudratchi (F.I.O)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.contractor, { formData = formData.copy(contractor = it) }, label = { Text("Pudratchi (lavozimi)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.contractorName, { formData = formData.copy(contractorName = it) }, label = { Text("Pudratchi (F.I.O)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.customer, { formData = formData.copy(customer = it) }, label = { Text("Buyurtmachi (lavozimi)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.customerName, { formData = formData.copy(customerName = it) }, label = { Text("Buyurtmachi (F.I.O)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.designOrg, { formData = formData.copy(designOrg = it) }, label = { Text("Loyiha tashkiloti (lavozimi)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.designOrgName, { formData = formData.copy(designOrgName = it) }, label = { Text("Loyiha tashkiloti (F.I.O)") }, modifier = singlelineFieldModifier, singleLine = true)
            OutlinedTextField(formData.certification, { formData = formData.copy(certification = it) }, label = { Text("Yashirin ishlar nomi") }, modifier = multilineFieldModifier) // Can be multi-line

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
                        resultMessage = "Iltimos, manba va chiqish papkalarini tanlang."
                        return@Button
                    }
                    isProcessing = true
                    resultMessage = "Qayta ishlanmoqda..."
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

                        val templateDir = File(templateFolderPath)
                        val outputDir = File(outputFolderPath)
                        var currentResultMessage: String = ""

                        try {
                            if (!templateDir.exists() || !templateDir.isDirectory) {
                                currentResultMessage = "Xatolik: Manba papkasi '$templateFolderPath' topilmadi yoki papka emas."
                                resultMessage = currentResultMessage
                                isProcessing = false
                                return@launch
                            }
                            if (!outputDir.exists()) {
                                outputDir.mkdirs()
                            } else if (!outputDir.isDirectory) {
                                currentResultMessage = "Xatolik: Chiqish joyi '$outputFolderPath' papka emas."
                                resultMessage = currentResultMessage
                                isProcessing = false
                                return@launch
                            }

                            var count = 0
                            val processedFiles = mutableListOf<String>()
                            val errorFiles = mutableListOf<String>()

                            templateDir.listFiles()?.forEach { file ->
                                if (file.isFile && file.extension.equals("docx", ignoreCase = true)) {
                                    val outFile = File(outputDir, "filled_${file.name}")
                                    try {
                                        fillTemplate(file.absolutePath, outFile.absolutePath, dataMap)
                                        processedFiles.add(file.name)
                                        count++
                                    } catch (e: Exception) {
                                        errorFiles.add("${file.name} (Xatolik: ${e.message})")
                                        e.printStackTrace() // For more detailed error logging in console
                                    }
                                }
                            }
                            currentResultMessage = if (count > 0) {
                                "$count ta hujjat muvaffaqiyatli to'ldirildi: ${processedFiles.joinToString()}. "
                            } else {
                                "Manba papkasida ('$templateFolderPath') to'ldirish uchun DOCX fayllar topilmadi. "
                            }
                            if (errorFiles.isNotEmpty()) {
                                currentResultMessage += "\nXatolik yuz bergan fayllar: ${errorFiles.joinToString()}"
                            }

                        } catch (e: Exception) {
                            currentResultMessage = "Umumiy xatolik yuz berdi: ${e.message}"
                            e.printStackTrace() // For more detailed error logging in console
                        } finally {
                            // Ensure UI updates are on the main thread if needed, though state updates are usually fine
                            resultMessage = currentResultMessage
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth().height(48.dp) // Made button a bit taller
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colors.onPrimary, // Color for the spinner
                        strokeWidth = 3.dp // Make spinner a bit thicker
                    )
                    Spacer(Modifier.width(12.dp)) // More space
                    Text("To'ldirilmoqda...", style = MaterialTheme.typography.button)
                } else {
                    Text("Hujjatlarni To'ldirish", style = MaterialTheme.typography.button)
                }
            }

            if (resultMessage.isNotEmpty()) {
                Text(
                    resultMessage,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp) // Added padding
                )
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hujjat To'ldiruvchi v2.0" // Updated title
    ) {
        App()
    }
}