import com.day.cq.dam.api.AssetManager
import com.day.cq.replication.Replicator
import com.day.cq.replication.ReplicationActionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import javax.jcr.Session
import javax.jcr.Node
import java.io.ByteArrayOutputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

def profileCfBasePath = "/content/dam/au/cf/profiles"
def dryRun = false  // Set true for testing

def session = resourceResolver.adaptTo(Session)
def assetManager = resourceResolver.adaptTo(AssetManager)
def replicator = getService(Replicator)

def reportData = []

def convertLinksHtmlToBulletedList = { String html ->
    if (!html?.trim()) {
        return html
    }

    def doc = Jsoup.parseBodyFragment(html)
    def anchors = doc.select("a")

    if (anchors.isEmpty()) {
        anchors = doc.select("p")
        if (anchors.isEmpty()) {
            if (html.contains("<br")) {
                def listItems = html.split(/<br\s*\/?>/)
                def ul = new Element("ul")
                listItems.each { item ->
                    def li = new Element("li")
                    li.text(item.trim())
                    ul.appendChild(li)
                }
                println "Length: ${listItems.length}"
                if (listItems.length > 1) {
                    return ul.outerHtml()
                } else {
                    return html
                }
            } else {
                def ul = new Element("ul")
                def li = new Element("li")
                li.text(html)
                ul.appendChild(li)
                return ul.outerHtml()
            }
        }
    }

    def ul = new Element("ul")
    anchors.each { a ->
        def li = new Element("li")
        li.appendChild(a.clone())
        ul.appendChild(li)
    }

    return ul.outerHtml()
}

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
      def seeAlsoLinks = dataNode.hasProperty("contactLinks") ? dataNode.getProperty("contactLinks").string : null
      def additionalPositionsLinks = dataNode.hasProperty("additionalPositions") ? dataNode.getProperty("additionalPositions").string : null

        
        def convertedSeeAlsoLinks = convertLinksHtmlToBulletedList(seeAlsoLinks)
        def convertedAdditionalPositionsLinks = convertLinksHtmlToBulletedList(additionalPositionsLinks)

        def changed = false

        if (seeAlsoLinks != null && convertedSeeAlsoLinks != seeAlsoLinks) {
            changed = true
            if (!dryRun) {
                dataNode.setProperty("contactLinks", convertedSeeAlsoLinks)
            }
        }

        if (additionalPositionsLinks != null && convertedAdditionalPositionsLinks != additionalPositionsLinks) {
            changed = true
            if (!dryRun) {
                dataNode.setProperty("additionalPositions", convertedAdditionalPositionsLinks)
            }
        }

        if (changed && !dryRun) {
            session.save()
            replicator.replicate(session, ReplicationActionType.ACTIVATE, cfPath)
        }

        reportData << [
                cfPath,
                seeAlsoLinks,
                convertedSeeAlsoLinks,
                additionalPositionsLinks,
                convertedAdditionalPositionsLinks,
                changed ? (dryRun ? "Would Update" : "Updated") : "No Change"
        ]
     } catch (Exception e) {
        reportData << [
                res.path,
                "",
                "",
                "",
                "",
                e.message
        ]
     }
        
}

println "\nGenerating Excel report..."

// --- EXCEL REPORT ---
def workbook = new XSSFWorkbook()
def sheet = workbook.createSheet("Convert To List Report")

def headers = [
        "CF Path",
        "Original SeeAlsoLinks",
        "Updated SeeAlsoLinks",
        "Original AdditionalPositionsLinks",
        "Converted AdditionalPositionsLinks",
        "Status"
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

def reportAssetPath = "/content/dam/reports/Convert-To-List-Report-${System.currentTimeMillis()}.xlsx"
def baos = new ByteArrayOutputStream()
workbook.write(baos)
def inputStream = new ByteArrayInputStream(baos.toByteArray())

assetManager.createAsset(reportAssetPath, inputStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true)

println "Report saved in DAM at: ${reportAssetPath}"
workbook.close()

println "Processing completed!"
