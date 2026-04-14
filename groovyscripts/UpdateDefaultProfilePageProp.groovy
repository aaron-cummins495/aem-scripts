import com.day.cq.dam.api.AssetManager
import com.day.cq.replication.Replicator
import com.day.cq.replication.ReplicationActionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import javax.jcr.Session
import javax.jcr.Node
import java.io.ByteArrayOutputStream

def profileCfBasePath = "/content/dam/au/cf/profiles"
def dryRun = false  // Set true for testing
def contentBasePath = "/content/au"

def session = resourceResolver.adaptTo(Session)
def assetManager = resourceResolver.adaptTo(AssetManager)
def replicator = getService(Replicator)

def reportData = []

println "Starting processing..."

def query = """
SELECT * FROM [dam:Asset] AS s
WHERE ISDESCENDANTNODE(s, '${profileCfBasePath}')
AND s.[jcr:content/contentFragment] = true
"""

def result = resourceResolver.findResources(query, "JCR-SQL2")

result.each { res ->
    try {
        def cfPath = res.path
        def cfNode = res.adaptTo(Node)

        def dataNode = cfNode.getNode("jcr:content/data/master")

        def oldDefaultProfilePage = dataNode.hasProperty("defaultProfilePage") ? dataNode.getProperty("defaultProfilePage").string : null
        if (oldDefaultProfilePage && !oldDefaultProfilePage.contains(contentBasePath)) {
            if (!dryRun) {
                def newDefaultProfilePath = "${contentBasePath}${oldDefaultProfilePage}"
                dataNode.setProperty("defaultProfilePage", newDefaultProfilePath)
                session.save()
                //replicator.replicate(session, ReplicationActionType.ACTIVATE, cfPath)
                println "CF Updated and Published: ${cfPath} with new Path: ${newDefaultProfilePath}"
                reportData.add([
                    cfPath,
                    oldDefaultProfilePage,
                    newDefaultProfilePath,
                    "Property Updated",
                    ""
                ])
            }
        } else {
           reportData.add([
                cfPath,
                oldDefaultProfilePage,
                oldDefaultProfilePage,
                "Property Not Updated",
                ""
            ]) 
        }
        println "CF Processed: ${cfPath} -- ${oldDefaultProfilePage} "
    } catch (Exception e) {
        reportData.add([
            res.path,
            "",
            "",
            "Failed",
            e.getMessage()
        ])
    }
}

println "\nGenerating Excel report..."

// --- EXCEL REPORT ---
def workbook = new XSSFWorkbook()
def sheet = workbook.createSheet("CF Migration Report")

def headers = [
        "CF Path",
        "Old Profile Page Path",
        "Updated Profile Page Path",
        "Status",
        "Error"
]

def headerRow = sheet.createRow(0)
headers.eachWithIndex { h, i ->
    headerRow.createCell(i).setCellValue(h)
}

reportData.eachWithIndex { row, i ->
    def excelRow = sheet.createRow(i + 1)
    row.eachWithIndex { val, j ->
        excelRow.createCell(j).setCellValue(val ?: "")
    }
}

def reportAssetPath = "/content/dam/reports/defaultProfilePage-prop-update-report${System.currentTimeMillis()}.xlsx"
def baos = new ByteArrayOutputStream()
workbook.write(baos)
def inputStream = new ByteArrayInputStream(baos.toByteArray())

assetManager.createAsset(reportAssetPath, inputStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true)

println "Report saved in DAM at: ${reportAssetPath}"
workbook.close()

println "Processing completed!"