package viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

// --- Data Classes ---
data class InstanceData(
    var instanceObjectName: String = "",
    var instanceSrNum: String = ""
)

data class StyleProperties(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val underline: UnderlinePatterns = UnderlinePatterns.NONE,
    val isStrikeThrough: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val color: String? = null
)

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
    var srNum: String = "",
    var subContractorCo: String = "",
    var contractorCo: String = "",
    var designCo: String = "",
    var customerCo: String = "",
    var instances: List<InstanceData> = List(9) { InstanceData() }
)

// Helper class to track if outputFileNameFromUI has been applied for a root file
data class ProcessingStateHolder(var outputFileNameApplied: Boolean = false)

// --- TemplateKeys Object ---
object TemplateKeys {
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
    const val SUB_CONTRACTOR_CO = "sub_contractor_co"
    const val CONTRACTOR_CO = "contractor_co"
    const val DESIGN_CO = "design_co"
    const val CUSTOMER_CO = "customer_co"

    fun objectNameKey(index: Int): String = "object_name_${index + 1}"
    fun srNumKey(index: Int): String = "sr_num_${index + 1}"
}

// --- ProcessingState Sealed Class ---
sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val message: String, val previewFilePath: String? = null) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

// --- MainViewModel Class ---
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
        outputFileNameFromUI: String, // Original name from UI
        formData: FormData,
        globalStyleIsBold: Boolean,
        globalStyleIsItalic: Boolean,
        globalStyleFontFamily: String?
    ) {
        val cleanOutputFileNameFromUI = outputFileNameFromUI.trim() // Trim whitespace

        logger.debug(
            "processDocuments called with: templateFolderPath='{}', outputFolderPath='{}', cleanOutputFileNameFromUI='{}', globalIsBold={}, globalIsItalic={}, globalFontFamily='{}'",
            templateFolderPath,
            outputFolderPath,
            cleanOutputFileNameFromUI,
            globalStyleIsBold,
            globalStyleIsItalic,
            globalStyleFontFamily ?: "null"
        )


        if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
            _processingState.value = ProcessingState.Error("Iltimos, manba va chiqish papkalarini tanlang.")
            return
        }

        if (formData.instances.any { it.instanceObjectName.isBlank() || it.instanceSrNum.isBlank() }) {
            _processingState.value =
                ProcessingState.Error("Iltimos, barcha ${formData.instances.size} obyekt uchun 'Nomi' va 'Proyekt Raqami'ni to'ldiring.")
            return
        }

        _processingState.value = ProcessingState.Processing
        _documentPreviewText.value = "Hujjat qayta ishlanmoqda..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dataMap = mutableMapOf<String, String>()
                dataMap[TemplateKeys.OBJECT_DESC] = formData.objectDesc
                dataMap[TemplateKeys.SUB_CONTRACTOR] = formData.subContractor
                dataMap[TemplateKeys.SUB_CONTRACTOR_NAME] = formData.subContractorName
                dataMap[TemplateKeys.CONTRACTOR] = formData.contractor
                dataMap[TemplateKeys.CONTRACTOR_NAME] = formData.contractorName
                dataMap[TemplateKeys.CUSTOMER] = formData.customer
                dataMap[TemplateKeys.CUSTOMER_NAME] = formData.customerName
                dataMap[TemplateKeys.DESIGN_ORG] = formData.designOrg
                dataMap[TemplateKeys.DESIGN_ORG_NAME] = formData.designOrgName
                dataMap[TemplateKeys.CERTIFICATION] = formData.certification
                dataMap[TemplateKeys.DESIGN_DOC] = formData.designDoc
                dataMap[TemplateKeys.SUB_CONTRACTOR_CO] = formData.subContractorCo
                dataMap[TemplateKeys.CONTRACTOR_CO] = formData.contractorCo
                dataMap[TemplateKeys.DESIGN_CO] = formData.designCo
                dataMap[TemplateKeys.CUSTOMER_CO] = formData.customerCo

                formData.instances.forEachIndexed { index, instanceData ->
                    dataMap[TemplateKeys.objectNameKey(index)] = instanceData.instanceObjectName
                    dataMap[TemplateKeys.srNumKey(index)] = instanceData.instanceSrNum
                }

                val rootTemplateDir = File(templateFolderPath)
                val rootOutputDir = File(outputFolderPath)
                val processingStateHolder = ProcessingStateHolder()

                var firstSuccessPathForPreview: String? = null
                val errorFileMessagesList = mutableListOf<String>()
                var filesProcessedCount = 0

                processDirectory(
                    currentTemplateDir = rootTemplateDir,
                    initialRootTemplateDir = rootTemplateDir,
                    targetDir = rootOutputDir,
                    dataMap = dataMap,
                    outputFileNameFromUI = cleanOutputFileNameFromUI, // Pass trimmed version
                    globalIsBold = globalStyleIsBold,
                    globalIsItalic = globalStyleIsItalic,
                    globalFontFamily = globalStyleFontFamily?.takeIf { it.isNotBlank() },
                    stateHolder = processingStateHolder,
                    callback = { filePath, fileName, isSuccess, errorMessage ->
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
                )

                if (filesProcessedCount > 0) {
                    val successMessage = "$filesProcessedCount ta hujjat shabloni muvaffaqiyatli to'ldirildi."
                    val errorMessageText = if (errorFileMessagesList.isNotEmpty()) {
                        "\nQuyidagi fayllarda xatoliklar yuz berdi:\n - ${errorFileMessagesList.joinToString("\n - ")}"
                    } else ""
                    _processingState.value = ProcessingState.Success(
                        successMessage + errorMessageText,
                        firstSuccessPathForPreview
                    )
                    if (firstSuccessPathForPreview != null) {
                        _documentPreviewText.value = extractTextFromDocx(firstSuccessPathForPreview)
                    } else {
                        _documentPreviewText.value = "Hujjat to'ldirildi, lekin oldindan ko'rish uchun fayl topilmadi."
                    }
                } else {
                    if (errorFileMessagesList.isEmpty()) {
                        _processingState.value = ProcessingState.Error("Manba papkasida DOCX fayllar topilmadi.")
                        _documentPreviewText.value = "Manba papkasida DOCX fayllar topilmadi."
                    } else {
                        val combinedErrors =
                            "Hujjatlarni qayta ishlashda xatolik yuz berdi:\n - ${errorFileMessagesList.joinToString("\n - ")}"
                        _processingState.value = ProcessingState.Error(combinedErrors)
                        _documentPreviewText.value = combinedErrors
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
        initialRootTemplateDir: File,
        targetDir: File,
        dataMap: Map<String, String>,
        outputFileNameFromUI: String, // This is the already trimmed version
        globalIsBold: Boolean,
        globalIsItalic: Boolean,
        globalFontFamily: String?,
        stateHolder: ProcessingStateHolder,
        callback: (filePath: String, fileName: String, isSuccess: Boolean, errorMessage: String?) -> Unit
    ) {
        if (!currentTemplateDir.exists() || !currentTemplateDir.isDirectory) {
            logger.error("Template directory does not exist or is not a directory: ${currentTemplateDir.absolutePath}")
            return
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            logger.error("Could not create output directory: ${targetDir.absolutePath}")
            callback("", "", false, "Chiqish papkasini yaratib bo'lmadi.")
            return
        }
        if (!targetDir.isDirectory) {
            logger.error("Output path is not a directory: ${targetDir.absolutePath}")
            callback("", "", false, "Chiqish joyi papka emas.")
            return
        }

        currentTemplateDir.listFiles()?.forEach { entry ->
            if (entry.name.startsWith("~$")) {
                logger.info("Skipping temporary Office file: ${entry.name}")
                return@forEach
            }

            logger.debug(
                "Processing entry: '{}' in currentTemplateDir: '{}'",
                entry.name,
                currentTemplateDir.absolutePath
            )

            if (entry.isDirectory) {
                val outputSubDir = File(targetDir, entry.name)
                processDirectory(
                    currentTemplateDir = entry,
                    initialRootTemplateDir = initialRootTemplateDir,
                    targetDir = outputSubDir,
                    dataMap = dataMap,
                    outputFileNameFromUI = outputFileNameFromUI,
                    globalIsBold = globalIsBold,
                    globalIsItalic = globalIsItalic,
                    globalFontFamily = globalFontFamily,
                    stateHolder = stateHolder,
                    callback = callback
                )
            } else if (entry.isFile && entry.extension.equals("docx", ignoreCase = true)) {
                try {
                    logger.debug(
                        "Checking file '{}': outputFileNameFromUI='{}', stateHolder.applied={}",
                        entry.name, outputFileNameFromUI, stateHolder.outputFileNameApplied
                    )

                    val isFileInInitialRoot = entry.parentFile.absolutePath == initialRootTemplateDir.absolutePath
                    logger.debug(
                        "Path check for '{}': entry.parentFile.abs='{}', initialRootTemplateDir.abs='{}', isFileInInitialRoot={}",
                        entry.name,
                        entry.parentFile.absolutePath,
                        initialRootTemplateDir.absolutePath,
                        isFileInInitialRoot
                    )

                    val resolvedOutputName: String
                    if (outputFileNameFromUI.isNotBlank() && isFileInInitialRoot && !stateHolder.outputFileNameApplied) {
                        logger.debug(
                            "Applying outputFileNameFromUI ('{}') to file '{}'",
                            outputFileNameFromUI,
                            entry.name
                        )
                        resolvedOutputName = if (outputFileNameFromUI.endsWith(".docx", ignoreCase = true)) {
                            outputFileNameFromUI
                        } else {
                            "$outputFileNameFromUI.docx"
                        }
                        stateHolder.outputFileNameApplied = true
                    } else {
                        logger.debug(
                            "NOT applying outputFileNameFromUI to file '{}'. Reason: outputNameBlank={}, notInRoot={}, alreadyApplied={}. Using entry.name: '{}'",
                            entry.name,
                            !outputFileNameFromUI.isNotBlank(),
                            !isFileInInitialRoot,
                            stateHolder.outputFileNameApplied,
                            entry.name
                        )
                        resolvedOutputName = entry.name
                    }

                    val finalOutputFile = File(targetDir, resolvedOutputName)

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
                    callback("", entry.name, false, "Fayl ${entry.name}: ${e.message}")
                }
            }
        }
    }

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

            doc.tables.forEach { table ->
                table.rows.forEach { row ->
                    row.tableCells.forEach { cell ->
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
        // Omitting detailed logging from this function for brevity in this response,
        // but the original detailed logging you had here for runs can be kept.
        // The key is that this function itself doesn't decide the output file name.
        logger.debug("Processing Paragraph. Full Text: \"{}\"", paragraph.text) // Simplified log for this example
        val originalRuns = ArrayList(paragraph.runs)
        var needsRebuilding = false

        if (placeholderRegex.containsMatchIn(paragraph.text)) {
            needsRebuilding = true
        } else {
            for (run in originalRuns) {
                val runText = run.text()
                if (runText != null && placeholderRegex.containsMatchIn(runText)) {
                    needsRebuilding = true
                    break
                }
            }
        }

        if (!needsRebuilding) {
            return
        }

        val newRunsData = mutableListOf<Triple<String, StyleProperties, Boolean>>()

        if (originalRuns.isEmpty() && placeholderRegex.containsMatchIn(paragraph.text)) {
            val paragraphText = paragraph.text
            var currentSegmentStartIndex = 0
            placeholderRegex.findAll(paragraphText).forEach { matchResult ->
                val placeholderFound = matchResult.value
                val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
                val replacementText = data[key]
                val placeholderStart = matchResult.range.first
                if (placeholderStart > currentSegmentStartIndex) {
                    newRunsData.add(
                        Triple(
                            paragraphText.substring(currentSegmentStartIndex, placeholderStart),
                            StyleProperties(),
                            false
                        )
                    )
                }
                if (replacementText != null) {
                    newRunsData.add(Triple(replacementText, StyleProperties(), true))
                } else {
                    newRunsData.add(Triple(placeholderFound, StyleProperties(), false))
                }
                currentSegmentStartIndex = matchResult.range.last + 1
            }
            if (currentSegmentStartIndex < paragraphText.length) {
                newRunsData.add(Triple(paragraphText.substring(currentSegmentStartIndex), StyleProperties(), false))
            }
        } else {
            originalRuns.forEach { run ->
                val runText = run.text()
                if (runText == null) return@forEach
                val originalRunStyle = extractStyle(run)
                if (!placeholderRegex.containsMatchIn(runText)) {
                    newRunsData.add(Triple(runText, originalRunStyle, false))
                } else {
                    var lastIndex = 0
                    placeholderRegex.findAll(runText).forEach { matchResult ->
                        val placeholderFound = matchResult.value
                        val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
                        val replacementText = data[key]
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
                        if (replacementText != null) {
                            newRunsData.add(Triple(replacementText, originalRunStyle, true))
                        } else {
                            newRunsData.add(Triple(placeholderFound, originalRunStyle, false))
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
                applyStyleProperties(newRun, originalStyle)
                if (isReplacement) {
                    if (globalIsBold) newRun.isBold = true
                    if (globalIsItalic) newRun.isItalic = true
                    globalFontFamily?.let { if (it.isNotBlank()) newRun.fontFamily = it }
                }
            }
        }
    }

    private fun extractStyle(run: XWPFRun): StyleProperties {
        return StyleProperties(
            isBold = run.isBold,
            isItalic = run.isItalic,
            underline = run.underline ?: UnderlinePatterns.NONE,
            isStrikeThrough = run.isStrikeThrough,
            fontFamily = run.fontFamily,
            fontSize = run.fontSizeAsDouble.takeIf { it != null && it > 0 },
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