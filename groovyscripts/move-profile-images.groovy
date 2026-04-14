import com.day.cq.dam.api.AssetManager
import com.day.cq.replication.Replicator
import com.day.cq.replication.ReplicationActionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.*
import javax.jcr.Session
import javax.jcr.Node
import org.apache.commons.io.FilenameUtils
import java.io.ByteArrayOutputStream

def basePath = "/content/dam/au/cf/profiles/00"
def profileImageDefault = "/content/dam/au/assets/global/images/au_profile.jpg";
def dryRun = false  // Set true for testing

def assetManager = resourceResolver.adaptTo(AssetManager)
def replicator = getService(Replicator)

def reportData = []

println "Starting processing..."

def query = """
SELECT * FROM [dam:Asset] AS s
WHERE ISDESCENDANTNODE(s, '${basePath}')
AND s.[jcr:content/contentFragment] = true
"""

def result = resourceResolver.findResources(query, "JCR-SQL2")

result.each { res ->
    try {
        def cfPath = res.path
        def cfNode = res.adaptTo(Node)

        def dataNode = cfNode.getNode("jcr:content/data/master")

        def username = dataNode.hasProperty("username") ? dataNode.getProperty("username").string : null
        def profileImage = dataNode.hasProperty("photo") ? dataNode.getProperty("photo").string : null
        def resume = dataNode.hasProperty("resume") ? dataNode.getProperty("resume").string : null

        def cfFolderPath = cfPath.substring(0, cfPath.lastIndexOf("/"))

        def newImagePath = ""
        def newResumePath = ""
        def profileImageMoveStatus = ""
        def resumeMoveStatus = ""
        def isCFUpdated = false

        println "\nProcessing CF: ${cfPath}"
        
        if (username) {
            // --- PROFILE IMAGE ---
            if (profileImage && profileImage && profileImage.contains("migrated-profile-images")) {
                profileImage = profileImage.replace("migrated-profile-images/", "")
            }
            if (profileImage && profileImage.contains("/cf/profiles/")) {
                println "Profile image path already contains /cf/profiles/: ${profileImage}"
                profileImageMoveStatus = "Already in correct location"
            } else if (profileImage && getResource(profileImage) != null) {
                def imageExt = FilenameUtils.getExtension(profileImage)
                newImagePath = "${cfFolderPath}/${username}.${imageExt}"
    
                println "Moving profile image: ${profileImage} → ${newImagePath}"
    
                if (!dryRun) {
                    def existingPhoto = getResource(newImagePath)
                    if (existingPhoto != null) {
                        resourceResolver.delete(existingPhoto)
                        resourceResolver.commit()
                    }
                    session.workspace.copy(profileImage, newImagePath)
                    resourceResolver.commit()
                    dataNode.setProperty("photo", newImagePath)
                    resourceResolver.commit()
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, newImagePath)
                    isCFUpdated = true
                }
                profileImageMoveStatus = "Success!"
            } else  {
                def imageExt = FilenameUtils.getExtension(profileImageDefault)
                newImagePath = "${cfFolderPath}/${username}.${imageExt}"
                println "Profile image not available for profile: ${cfPath}"
                println "Setting Default Image"

                // Set default image if original image is missing
                if (!dryRun) {
                    def existingPhoto = getResource(newImagePath)
                    if (existingPhoto != null) {
                        resourceResolver.delete(existingPhoto)
                        resourceResolver.commit()
                    }
                    session.workspace.copy(profileImageDefault, newImagePath)
                    resourceResolver.commit()
                    dataNode.setProperty("photo", newImagePath)
                    resourceResolver.commit()
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, newImagePath)
                    isCFUpdated = true
                }
                profileImageMoveStatus = "Not Found -> Default Image Set"
            }
            
            // --- RESUME ---
            if (resume && (resume.contains("migrated-profile-resumes/http") || resume.contains("/content/dam/au/assets/http"))) {
                newResumePath = resume.replace("migrated-profile-resumes/", "")
                newResumePath = newResumePath.replace("/content/dam/au/assets/", "")
                println "Updating external resume path: ${resume} -> ${newResumePath}"
                if (!dryRun) {
                    dataNode.setProperty("resume", newResumePath)
                    resourceResolver.commit()
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, newResumePath)
                    isCFUpdated = true
                    resumeMoveStatus = "External Path Updated!"
                }
            } else if (resume && resume.contains("migrated-profile-resumes")) {
                resume = resume.replace("migrated-profile-resumes/","")
                def resumeExt = FilenameUtils.getExtension(resume)
                def fileName = "${username}-resume.${resumeExt}"
                newResumePath = "${cfFolderPath}/${fileName}"
    
                if (!dryRun && getResource(resume) != null) {
    
                    println "Moving resume: ${resume} → ${newResumePath}"
                    def existingResume = getResource(newResumePath)
                    if (existingResume != null) {
                        resourceResolver.delete(existingResume)
                        resourceResolver.commit()
                    }
                    session.workspace.copy(resume, newResumePath)
                    resourceResolver.commit()
                    dataNode.setProperty("resume", newResumePath)
                    resourceResolver.commit()
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, newResumePath)
                    isCFUpdated = true
                    resumeMoveStatus = "Success!"
                }  else {
                    resumeMoveStatus = "Resume Populated but not moved (missing source)"
                    println "Resume path is set but source file is missing for profile: ${cfPath}"
                }
            } else if (resume && !resume.contains("migrated-profile-resumes")){
                resumeMoveStatus = "already in correct location"
                println "Resume already moved!"
            } else {
                println "Resume not available for profile: ${cfPath}"
                resumeMoveStatus = "Not Found"
            }
            
            if (isCFUpdated) {
               replicator.replicate(session, ReplicationActionType.ACTIVATE, cfPath) 
            }
        } else {
            println("Username is missing for profile: ${cfPath}")
            profileImageMoveStatus = "Skipped"
            resumeMoveStatus = "Skipped"
        }

        reportData.add([
                cfPath,
                username,
                profileImage,
                newImagePath,
                resume ?: "",
                newResumePath ?: "",
                profileImageMoveStatus,
                resumeMoveStatus,
                ""
        ])

    } catch (Exception e) {
        log.error("Error processing CF", e)

        reportData.add([
                res.path,
                "",
                "",
                "",
                "",
                "",
                "FAILED",
                e.message
        ])
    }
}

println "\nGenerating Excel report..."

// --- EXCEL REPORT ---
def workbook = new XSSFWorkbook()
def sheet = workbook.createSheet("CF Migration Report")

def headers = [
        "CF Path",
        "Username",
        "Old Image Path",
        "New Image Path",
        "Old Resume Path",
        "New Resume Path",
        "Profile Image Status",
        "Resume Status",
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

def reportAssetPath = "/content/dam/reports/cf-migration-report-${System.currentTimeMillis()}.xlsx"
def baos = new ByteArrayOutputStream()
workbook.write(baos)
def inputStream = new ByteArrayInputStream(baos.toByteArray())

assetManager.createAsset(reportAssetPath, inputStream, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true)

println "Report saved in DAM at: ${reportAssetPath}"
workbook.close()

println "Processing completed!"