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

data class InstanceData(
    var instanceObjectName: String = "",
    var instanceSrNum: String = ""
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
    var srNum: String = "",
    var subContractorCo: String = "",
    var contractorCo: String = "",
    var designCo: String = "",
    var customerCo: String = "",

    var instances: List<InstanceData> = List(9) { InstanceData() }
)

// --- TemplateKeys ---
object TemplateKeys {
    //    const val OBJECT_NAME = "object_name"
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

//    const val SR_NUM = "sr_num"

    fun objectNameKey(index: Int): String = "object_name_${index + 1}"
    fun srNumKey(index: Int): String = "sr_num_${index + 1}"
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val message: String, val previewFilePath: String? = null) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

// In viewmodel/MainViewModel.kt

// ... (imports, StyleProperties, TemplateKeys, ProcessingState, InstanceData if defined here) ...
// Ensure InstanceData is imported or defined here:
// import viewmodel.InstanceData // If in a separate file

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
        outputFileNameFromUI: String, // The single output file name from UI
        formData: FormData,
        globalStyleIsBold: Boolean,
        globalStyleIsItalic: Boolean,
        globalStyleFontFamily: String?
    ) {
        if (templateFolderPath.isBlank() || outputFolderPath.isBlank()) {
            _processingState.value = ProcessingState.Error("Iltimos, manba va chiqish papkalarini tanlang.")
            return
        }

        // Optional: Validate that all 8 instance data fields are filled if required
        if (formData.instances.any { it.instanceObjectName.isBlank() || it.instanceSrNum.isBlank() }) {
            _processingState.value =
                ProcessingState.Error("Iltimos, barcha 8 obyekt uchun 'Nomi' va 'Proyekt Raqami'ni to'ldiring.")
            return
        }

        _processingState.value = ProcessingState.Processing
        _documentPreviewText.value = "Hujjat qayta ishlanmoqda..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // --- Construct the single, comprehensive dataMap ---
                val dataMap = mutableMapOf<String, String>()

                // Add common data from FormData
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
                // Note: Original formData.objectName and formData.srNum are not used here
                // unless you have corresponding non-indexed placeholders in your template.

                // Add data for the 8 instances using indexed keys
                formData.instances.forEachIndexed { index, instanceData ->
                    dataMap[TemplateKeys.objectNameKey(index)] = instanceData.instanceObjectName
                    dataMap[TemplateKeys.srNumKey(index)] = instanceData.instanceSrNum
                }
                // --- End of dataMap construction ---


                val rootTemplateDir = File(templateFolderPath)
                val rootOutputDir = File(outputFolderPath)
                var firstSuccessPathForPreview: String? = null
                val errorFileMessagesList = mutableListOf<String>()
                var filesProcessedCount = 0

                // Process each template file in the directory once
                processDirectory(
                    currentTemplateDir = rootTemplateDir,
                    rootOutputDir = rootOutputDir, // Files will be placed here, mirroring template structure
                    dataMap = dataMap, // Pass the comprehensive dataMap
                    outputFileNameFromUI = outputFileNameFromUI, // For naming the output file
                    globalIsBold = globalStyleIsBold,
                    globalIsItalic = globalStyleIsItalic,
                    globalFontFamily = globalStyleFontFamily?.takeIf { it.isNotBlank() }
                ) { filePath, fileName, isSuccess, errorMessage ->
                    if (isSuccess) {
                        filesProcessedCount++
                        if (firstSuccessPathForPreview == null) {
                            firstSuccessPathForPreview = filePath
                            _lastProcessedFileName.value =
                                fileName // Show the name of the (potentially renamed) filled document
                        }
                    } else if (errorMessage != null) {
                        errorFileMessagesList.add(errorMessage) // Error message now includes file name
                    }
                }

                // Update state based on processing results
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
        rootOutputDir: File, // Base output directory
        dataMap: Map<String, String>, // The single comprehensive dataMap
        outputFileNameFromUI: String, // Name for the output file if provided
        globalIsBold: Boolean,
        globalIsItalic: Boolean,
        globalFontFamily: String?,
        callback: (filePath: String, fileName: String, isSuccess: Boolean, errorMessage: String?) -> Unit
    ) {
        if (!currentTemplateDir.exists() || !currentTemplateDir.isDirectory) {
            logger.error("Template directory does not exist or is not a directory: ${currentTemplateDir.absolutePath}")
            // No files processed from here, so don't call callback with an error for this specific path.
            // The caller will determine if no files were processed overall.
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


        currentTemplateDir.listFiles()?.forEachIndexed { fileIndex, entry ->
            val relativePathFromRootTemplateDir = entry.relativeTo(
                currentTemplateDir.parentFile ?: currentTemplateDir
            ) // This needs to be relative to the initial root for structure replication

            if (entry.isDirectory) {
                // Replicate the directory structure in the output folder
                val outputSubDir = File(rootOutputDir, entry.name)
                if (!outputSubDir.exists() && !outputSubDir.mkdirs()) {
                    logger.error("Could not create output subdirectory: ${outputSubDir.absolutePath}")
                    // Potentially call callback with an error for this path, or collect errors.
                    // For now, we'll let it try processing other files/dirs.
                    return@forEachIndexed
                }
                processDirectory(
                    entry, // Process the subdirectory in the template
                    outputSubDir, // Output to the corresponding replicated subdirectory
                    dataMap,
                    outputFileNameFromUI, // Pass this along
                    globalIsBold,
                    globalIsItalic,
                    globalFontFamily,
                    callback
                )
            } else if (entry.isFile && entry.extension.equals("docx", ignoreCase = true)) {
                try {
                    // Determine the final output name for this single document.
                    // If multiple .docx files are in the root of templateFolderPath,
                    // and outputFileNameFromUI is given, only the *first one processed* effectively uses it.
                    // Subsequent ones will use their original name.
                    // This behavior might need refinement if multiple templates are common.
                    // For a single template in the root, this is fine.
                    val finalOutputName =
                        if (outputFileNameFromUI.isNotBlank() && currentTemplateDir == File(dataMap["_initialTemplateFolderPath_"] /* you'd need to pass this*/)) { // Check if we are processing a file directly in the root
                            if (outputFileNameFromUI.endsWith(
                                    ".docx",
                                    ignoreCase = true
                                )
                            ) outputFileNameFromUI else "$outputFileNameFromUI.docx"
                        } else {
                            entry.name // Use the original template name
                        }
                    // If outputFileNameFromUI is provided, it generally applies to the document(s) generated from templates in the root of templateFolderPath.
                    // If templateFolderPath has subfolders with templates, those will retain their original names within the replicated subfolder structure in output.
                    // A more robust approach for renaming multiple templates would require a mapping or more complex UI.
                    // For now, if outputFileNameFromUI is set, it primarily affects the first template encountered or templates directly in the root.

                    // Simplified naming: If outputFileNameFromUI is given, and we are in the top-level template folder, use it.
                    // Otherwise, use the original template name.
                    // This assumes outputFileNameFromUI is meant for a primary template.
                    val resolvedOutputName =
                        if (outputFileNameFromUI.isNotBlank() && entry.parentFile.absolutePath == currentTemplateDir.absolutePath) { // A simple check, might need to be initial root path
                            if (outputFileNameFromUI.endsWith(
                                    ".docx",
                                    ignoreCase = true
                                )
                            ) outputFileNameFromUI else "$outputFileNameFromUI.docx"
                        } else {
                            entry.name
                        }

                    val finalOutputFile = File(
                        rootOutputDir,
                        resolvedOutputName
                    ) // Place in the correct output directory (could be a sub-directory)


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

    // --- Helper functions (extractTextFromDocx, fillTemplate, processParagraphRuns, extractStyle, applyStyleProperties) ---
    // These functions (fillTemplate, processParagraphRuns, etc.) do NOT need to change.
    // They operate on a single document using the provided dataMap. The magic is in constructing
    // the dataMap correctly in processDocuments.
    // ... (keep these functions as they are) ...
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
        data: Map<String, String>, // This is your comprehensive dataMap
        placeholderRegex: Regex,
        globalIsBold: Boolean,
        globalIsItalic: Boolean,
        globalFontFamily: String?
    ) {
        // Log the entire paragraph's text as POI sees it first
        logger.debug("Processing Paragraph. Full Text: \"{}\". Number of runs: {}", paragraph.text, paragraph.runs.size)

        val originalRuns = ArrayList(paragraph.runs)
        var needsRebuilding = false

        // Check if placeholder exists in the full paragraph text (even if runs are empty or don't show it)
        if (placeholderRegex.containsMatchIn(paragraph.text)) {
            logger.debug("Placeholder pattern found in paragraph full text: \"{}\"", paragraph.text)
            // This suggests we should try to process it, even if runs seem empty or don't individually contain the full placeholder
        }

        if (originalRuns.isEmpty() && placeholderRegex.containsMatchIn(paragraph.text)) {
            logger.debug("Paragraph has 0 runs, but placeholder found in paragraph.text. Attempting rebuild based on paragraph.text.")
            needsRebuilding = true
        } else {
            for ((runIndex, run) in originalRuns.withIndex()) {
                val runText = run.text() // Text from the current run
                logger.debug("Examining Run #{}: Text=\"{}\"", runIndex, runText)
                if (runText != null && placeholderRegex.containsMatchIn(runText)) {
                    logger.debug("Placeholder pattern found in Run #{} text: \"{}\"", runIndex, runText)
                    needsRebuilding = true
                    break // Found a run that needs processing, so the paragraph needs rebuilding
                }
            }
        }

        if (!needsRebuilding) {
            logger.debug("Paragraph does not appear to need rebuilding. Skipping detailed placeholder search.")
            return
        }
        logger.debug("Paragraph needs rebuilding. Proceeding with detailed placeholder search and replacement logic.")


        val newRunsData = mutableListOf<Triple<String, StyleProperties?, Boolean>>()

        if (originalRuns.isEmpty()) {
            // This block handles paragraphs with no runs but where placeholders were detected in the paragraph's full text
            logger.debug("Processing paragraph based on its full text because originalRuns is empty.")
            val paragraphText = paragraph.text
            var currentSegmentStartIndex = 0
            placeholderRegex.findAll(paragraphText).forEach { matchResult ->
                val placeholderFound = matchResult.value // e.g., "{object_name_1}"
                val key = (matchResult.groupValues.getOrNull(1) ?: "").trim() // e.g., "object_name_1"
                val replacementText = data[key]

                logger.debug(
                    "Paragraph (no runs) - Matched Full Placeholder: \"{}\", Extracted Key: \"{}\", Replacement from dataMap: \"{}\"",
                    placeholderFound,
                    key,
                    replacementText
                )

                val placeholderStart = matchResult.range.first
                val placeholderEnd = matchResult.range.last + 1

                if (placeholderStart > currentSegmentStartIndex) {
                    newRunsData.add(
                        Triple(
                            paragraphText.substring(currentSegmentStartIndex, placeholderStart),
                            StyleProperties(), // Default style
                            false
                        )
                    )
                }
                if (replacementText != null) {
                    newRunsData.add(Triple(replacementText, null, true)) // Replacement
                } else {
                    logger.warn(
                        "Paragraph (no runs) - Key \"{}\" not found in dataMap. Placeholder \"{}\" will remain.",
                        key,
                        placeholderFound
                    )
                    newRunsData.add(Triple(placeholderFound, StyleProperties(), false)) // Original placeholder
                }
                currentSegmentStartIndex = placeholderEnd
            }
            if (currentSegmentStartIndex < paragraphText.length) {
                newRunsData.add(Triple(paragraphText.substring(currentSegmentStartIndex), StyleProperties(), false))
            }
        } else { // Process existing runs
            logger.debug("Processing paragraph based on its existing runs.")
            originalRuns.forEachIndexed { runIndex, run ->
                val runText = run.text()
                if (runText == null) {
                    logger.debug("Run #{} text is null. Skipping.", runIndex)
                    return@forEachIndexed // Should use return@forEachIndexed to skip current iteration of forEach
                }
                logger.debug("Detailed processing of Run #{}: \"{}\"", runIndex, runText)
                val originalRunStyle = extractStyle(run)

                if (!placeholderRegex.containsMatchIn(runText)) {
                    newRunsData.add(Triple(runText, originalRunStyle, false))
                } else {
                    var lastIndex = 0
                    placeholderRegex.findAll(runText).forEach { matchResult ->
                        val placeholderFound = matchResult.value
                        val key = (matchResult.groupValues.getOrNull(1) ?: "").trim()
                        val replacementText = data[key]

                        logger.debug(
                            "Run #{} - Matched Full Placeholder: \"{}\", Extracted Key: \"{}\", Replacement from dataMap: \"{}\"",
                            runIndex,
                            placeholderFound,
                            key,
                            replacementText
                        )

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
                            logger.warn(
                                "Run #{} - Key \"{}\" not found in dataMap. Placeholder \"{}\" will remain.",
                                runIndex,
                                key,
                                placeholderFound
                            )
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
            logger.debug("Rebuilding paragraph with {} new run segments.", newRunsData.size)
            while (paragraph.runs.isNotEmpty()) {
                paragraph.removeRun(0)
            }
            newRunsData.forEach { (text, originalStyle, isReplacement) ->
                val newRun = paragraph.createRun()
                newRun.setText(text)
                // logger.debug("Added new run segment: Text=\"{}\", IsReplacement={}", text, isReplacement) // Can be too verbose

                if (isReplacement) {
                    // ... (style application logic remains the same) ...
                } else {
                    // ... (style application logic remains the same) ...
                }
            }
        } else {
            logger.debug("No new run data was generated, paragraph remains unchanged from replacement perspective.")
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