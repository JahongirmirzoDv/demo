import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material.*
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun fillTemplate(inputPath: String, outputPath: String, data: Map<String, String>) {
    val doc = XWPFDocument(FileInputStream(inputPath))
    for (para in doc.paragraphs) {
        for ((key, value) in data) {
            if (para.text.contains("{$key}")) {
                para.runs.forEach {
                    it.setText(it.text().replace("{$key}", value), 0)
                }
            }
        }
    }

    for (table in doc.tables) {
        for (row in table.rows) {
            for (cell in row.tableCells) {
                val newText = data.entries.fold(cell.text) { acc, entry ->
                    acc.replace("{${entry.key}}", entry.value)
                }
                cell.removeParagraph(0)
                cell.addParagraph().createRun().setText(newText)
            }
        }
    }

    FileOutputStream(outputPath).use { doc.write(it) }
}

@Composable
@Preview
fun App() {
    var objectName by remember { mutableStateOf("") }
    var objectDesc by remember { mutableStateOf("") }
    var subContractor by remember { mutableStateOf("") }
    var subContractorName by remember { mutableStateOf("") }
    var contractor by remember { mutableStateOf("") }
    var contractorName by remember { mutableStateOf("") }
    var designOrg by remember { mutableStateOf("") }
    var designOrgName by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var certification by remember { mutableStateOf("") }

    var result by remember { mutableStateOf("") }

    MaterialTheme {
        Column {
            TextField(objectName, { objectName = it }, label = { Text("наименование работ") })
            TextField(objectDesc, { objectDesc = it }, label = { Text("наименование и место расположения объекта") })
            TextField(subContractor, { subContractor = it }, label = { Text("представителя субподрядчика: должность") })
            TextField(
                subContractorName,
                { subContractorName = it },
                label = { Text("представителя субподрядчика: ФИО") })
            TextField(contractor, { contractor = it }, label = { Text("представителя подрядчика: должность") })
            TextField(contractorName, { contractorName = it }, label = { Text("представителя подрядчика: ФИО") })
            TextField(customer, { customer = it }, label = { Text("Представитель Заказчика: должность") })
            TextField(customerName, { customerName = it }, label = { Text("Представитель Заказчика: ФИО") })
            TextField(designOrg, { designOrg = it }, label = { Text("представителя проектной организации: должность") })
            TextField(
                designOrgName,
                { designOrgName = it },
                label = { Text("представителя проектной организации: ФИО") })
            TextField(certification, { certification = it }, label = { Text("наименование скрытых работ") })

            Button(onClick = {
                val data = mapOf(
                    "object_name" to objectName,
                    "object_desc" to objectDesc,
                    "sub_contractor" to subContractor,
                    "sub_contractor_name" to subContractorName,
                    "contractor" to contractor,
                    "contractor_name" to contractorName,
                    "customer" to customer,
                    "customer_name" to customerName,
                    "design_org" to designOrg,
                    "design_org_name" to designOrgName,
                    "certification" to certification
                )
                val templateDir = File("/Users/mobiledv/Desktop/")
                val outputDir = File("/Users/mobiledv/Documents/output/")
                outputDir.mkdirs()

                var count = 0
                templateDir.listFiles()?.forEach { file ->
                    if (file.extension == "docx") {
                        val outFile = File(outputDir, "${file.name}")
                        fillTemplate(file.absolutePath, outFile.absolutePath, data)
                        count++
                    }
                }
                result = "$count ta hujjat to‘ldirildi"
            }) {
                Text("To‘ldirish")
            }

            if (result.isNotEmpty()) Text(result)
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication,
        title = "Hujjat To‘ldiruvchi",
    ) {
        App()
    }
}
