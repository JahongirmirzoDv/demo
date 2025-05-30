package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

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
    var certification: String = "",
    var designDoc: String = "",
    var srNum: String = ""
)

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

    const val DESIGN_DOC = "design_doc"

    const val SR_NUM = "sr_num"
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val message: String, val previewFilePath: String? = null) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

class MainViewModel : ViewModel() {
    private val logger: Logger = LogManager.getLogger(MainViewModel::class.java)

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState

    private val _documentPreviewText =
        MutableStateFlow<String>("Hujjat oldindan ko'rish uchun shu yerda paydo bo'ladi.\n\nAvval manba va chiqish papkalarini tanlang, so'ng ma'lumotlarni to'ldirib, \"Hujjatlarni To'ldirish\" tugmasini bosing.")
    val documentPreviewText: StateFlow<String> = _documentPreviewText

    private val _lastProcessedFileName = MutableStateFlow<String?>(null)
    val lastProcessedFileName: StateFlow<String?> = _lastProcessedFileName

    fun processDocuments(
        templateFolderPath: String,
        outputFolderPath: String,
        outputFileName: String,
        formData: FormData,
        globalStyleIsBold: Boolean,
        globalStyleIsItalic: Boolean,
        globalStyleFontFamily: String?
    ) {
        if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
            _processingState.value = ProcessingState.Error("Iltimos, manba va chiqish papkalarini tanlang.")
            return
        }

        _processingState.value = ProcessingState.Processing
        _documentPreviewText.value = "Hujjatlar qayta ishlanmoqda..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
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
                    TemplateKeys.CERTIFICATION to formData.certification,
                    TemplateKeys.DESIGN_DOC to formData.designDoc,
                    TemplateKeys.SR_NUM to formData.srNum
                )

                val rootTemplateDir = File(templateFolderPath)
                val rootOutputDir = File(outputFolderPath)
                var firstSuccessPathForPreview: String? = null
                val errorFileMessagesList = mutableListOf<String>()
                var filesProcessedCount = 0

                processDirectory(
                    rootTemplateDir,
                    rootOutputDir,
                    dataMap,
                    outputFileName,
                    globalStyleIsBold,
                    globalStyleIsItalic,
                    globalStyleFontFamily?.takeIf { it.isNotBlank() }
                ) { filePath, fileName, isSuccess, errorMessage ->
                    if (isSuccess) {
                        filesProcessedCount++
                        if (firstSuccessPathForPreview == null) {
                            firstSuccessPathForPreview = filePath
                            _lastProcessedFileName.value = fileName
                        }
                    } else if (errorMessage != null) {
                        errorFileMessagesList.add(errorMessage)
                    }
                }

                // Update state based on processing results
                if (filesProcessedCount > 0) {
                    val successMessage = "$filesProcessedCount ta hujjat muvaffaqiyatli to'ldirildi."
                    val errorMessage = if (errorFileMessagesList.isNotEmpty()) {
                        "\nQuyidagi fayllarda xatoliklar yuz berdi:\n - ${errorFileMessagesList.joinToString("\n - ")}"
                    } else ""

                    _processingState.value = ProcessingState.Success(
                        successMessage + errorMessage,
                        firstSuccessPathForPreview
                    )

                    if (firstSuccessPathForPreview != null) {
                        _documentPreviewText.value = extractTextFromDocx(firstSuccessPathForPreview)
                    }
                } else {
                    if (errorFileMessagesList.isEmpty()) {
                        _processingState.value = ProcessingState.Error("Manba papkasida DOCX fayllar topilmadi.")
                        _documentPreviewText.value = "Manba papkasida DOCX fayllar topilmadi."
                    } else {
                        _processingState.value = ProcessingState.Error("Hujjatlarni qayta ishlashda xatolik yuz berdi.")
                        _documentPreviewText.value =
                            "Hujjatlarni qayta ishlashda xatolik yuz berdi. Oldindan ko'rish uchun fayl yo'q."
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing documents", e)
                _processingState.value = ProcessingState.Error("Umumiy kutilmagan xatolik: ${e.message}")
                _documentPreviewText.value = "Xatolik yuz berdi: ${e.message}"
            }
        }
    }

    private fun processDirectory(
        currentTemplateDir: File,
        rootOutputDir: File,
        dataMap: Map<String, String>,
        outputFileName: String,
        globalIsBold: Boolean,
        globalIsItalic: Boolean,
        globalFontFamily: String?,
        callback: (String, String, Boolean, String?) -> Unit
    ) {
        if (!currentTemplateDir.exists() || !currentTemplateDir.isDirectory) {
            logger.error("Template directory does not exist or is not a directory: ${currentTemplateDir.absolutePath}")
            callback("", "", false, "Manba papkasi topilmadi yoki papka emas.")
            return
        }

        if (!rootOutputDir.exists() && !rootOutputDir.mkdirs()) {
            logger.error("Could not create output directory: ${rootOutputDir.absolutePath}")
            callback("", "", false, "Chiqish papkasini yaratib bo'lmadi.")
            return
        }

        if (!rootOutputDir.isDirectory) {
            logger.error("Output path is not a directory: ${rootOutputDir.absolutePath}")
            callback("", "", false, "Chiqish joyi papka emas.")
            return
        }

        currentTemplateDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                val relativePath = entry.toRelativeString(currentTemplateDir)
                val outputSubDir = File(rootOutputDir, relativePath)
                if (!outputSubDir.exists()) {
                    outputSubDir.mkdirs()
                }
                processDirectory(
                    entry,
                    outputSubDir,
                    dataMap,
                    outputFileName,
                    globalIsBold,
                    globalIsItalic,
                    globalFontFamily,
                    callback
                )
            } else if (entry.isFile && entry.extension.equals("docx", ignoreCase = true)) {
                try {
                    // Use a custom output file name if provided, else use original name
                    val finalOutputName = if (outputFileName.isNotBlank()) {
                        // Ensure it has .docx extension
                        if (outputFileName.endsWith(
                                ".docx",
                                ignoreCase = true
                            )
                        ) outputFileName else "$outputFileName.docx"
                    } else {
                        entry.name // Use the original name if outputFileName is blank
                    }

                    val finalOutputFile = File(rootOutputDir, finalOutputName)

                    fillTemplate(
                        entry.absolutePath,
                        finalOutputFile.absolutePath,
                        dataMap,
                        globalIsBold,
                        globalIsItalic,
                        globalFontFamily
                    )

                    logger.info("Successfully processed file: ${entry.name} -> ${finalOutputFile.absolutePath}")
                    callback(finalOutputFile.absolutePath, finalOutputFile.name, true, null)
                } catch (e: Exception) {
                    logger.error("Error processing file: ${entry.name}", e)
                    callback("", entry.name, false, "${entry.name}: ${e.message}")
                }
            }
        }
    }

    // --- Helper functions ---

    fun extractTextFromDocx(filePath: String): String {
        return try {
            FileInputStream(filePath).use { fis ->
                XWPFDocument(fis).use { document ->
                    XWPFWordExtractor(document).use { extractor ->
                        extractor.text ?: "Matn topilmadi."
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error extracting text from document", e)
            "Hujjat matnini oldindan ko'rishda xatolik: ${e.message}"
        }
    }

    private fun fillTemplate(
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
                logger.error("Error writing to output file: $outputPath", e)
                throw IOException("Error writing to '$outputPath': ${e.message}", e)
            } finally {
                try {
                    doc.close()
                } catch (e: IOException) {
                    logger.error("Error closing document: $inputPath", e)
                }
            }
        }
    }

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
                            paragraphText.substring(currentSegmentStartIndex, placeholderStart),
                            StyleProperties(),
                            false
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
                val originalRunStyle = extractStyle(run)
                if (!placeholderRegex.containsMatchIn(runText)) {
                    newRunsData.add(Triple(runText, originalRunStyle, false))
                } else {
                    var lastIndex = 0
                    placeholderRegex.findAll(runText).forEach { matchResult ->
                        val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
                        val placeholderStart = matchResult.range.first
                        if (placeholderStart > lastIndex) {
                            newRunsData.add(
                                Triple(
                                    runText.substring(lastIndex, placeholderStart),
                                    originalRunStyle,
                                    false
                                )
                            )
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
                    originalStyle?.let { applyStyleProperties(newRun, it) }
                }
            }
        }
    }

    private fun extractStyle(run: XWPFRun): StyleProperties {
        val sizeFromPOI: Double? = try {
            run.fontSizeAsDouble
        } catch (e: Exception) {
            logger.error("Error getting font size", e)
            null
        }
        val finalFontSize: Double? = if (sizeFromPOI != null && sizeFromPOI > 0) sizeFromPOI else null
        return StyleProperties(
            isBold = run.isBold,
            isItalic = run.isItalic,
            underline = run.underline ?: UnderlinePatterns.NONE,
            isStrikeThrough = run.isStrikeThrough,
            fontFamily = run.fontFamily,
            fontSize = finalFontSize,
            color = run.color
        )
    }

    private fun applyStyleProperties(run: XWPFRun, styleProps: StyleProperties) {
        run.isBold = styleProps.isBold
        run.isItalic = styleProps.isItalic
        run.underline = styleProps.underline
        run.isStrikeThrough = styleProps.isStrikeThrough
        styleProps.fontFamily?.let { run.fontFamily = it }
        styleProps.fontSize?.let { if (it > 0) run.setFontSize(it) }
        styleProps.color?.let { run.color = it }
    }
}
