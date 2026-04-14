import groovy.transform.Field

@Field packagesPath = "/etc/packages"
def packageName = "profile-resumes-package"
def definitionPath = "$packagesPath/${packageName}.zip/jcr:content/vlt:definition"

def definitionNode = getOrAddDefinitionNode(packageName, definitionPath)
def filterNode = getOrAddFilterNode(definitionNode)

def profilesFolder = getResource("/content/dam/au/cf/profiles")
def profilesFolderChildren = profilesFolder.getChildren()
def imagesArray = []

addProfileImages(profilesFolderChildren, imagesArray)

profilesFolder = getResource("/content/dam/au/cf/profiles/not_in_workday_report")
profilesFolderChildren = profilesFolder.getChildren()
addProfileImages(profilesFolderChildren, imagesArray)

def cutoff = 500

imagesArray.eachWithIndex { path, i ->
    // if (i == cutoff) {
    //     save()
    //     newPackageName = "${packageName}${i}"
    //     definitionPath = "$packagesPath/${newPackageName}.zip/jcr:content/vlt:definition"
    //     definitionNode = getOrAddDefinitionNode(newPackageName, definitionPath)
    //     filterNode = getOrAddFilterNode(definitionNode)
    //     cutoff = cutoff + 500
    // }
    def f = filterNode.addNode("filter$i")

    f.set("mode", "replace")
    f.set("root", path)
    f.set("rules", new String[0])
}

save()

def getOrAddDefinitionNode(packageName, definitionPath) {
    

    if (session.nodeExists(definitionPath)) {
        definitionNode = getNode(definitionPath)
    } else {
        def fileNode = getNode(packagesPath).addNode("${packageName}.zip", "nt:file")

        def contentNode = fileNode.addNode("jcr:content", "nt:resource")

        contentNode.addMixin("vlt:Package")
        contentNode.set("jcr:mimeType", "application/zip")

        def stream = new ByteArrayInputStream("".bytes)
        def binary = session.valueFactory.createBinary(stream)

        contentNode.set("jcr:data", binary)

        definitionNode = contentNode.addNode("vlt:definition", "vlt:PackageDefinition")

        definitionNode.set("sling:resourceType", "cq/packaging/components/pack/definition")
        definitionNode.set("name", packageName)
        definitionNode.set("path", "$packagesPath/$packageName")
    }

    definitionNode
}

def getOrAddFilterNode(definitionNode) {
    def filterNode

    if (definitionNode.hasNode("filter")) {
        filterNode = definitionNode.getNode("filter")

        filterNode.nodes.each {
            it.remove()
        }
    } else {
        filterNode = definitionNode.addNode("filter")

        filterNode.set("sling:resourceType", "cq/packaging/components/pack/definition/filterlist")
    }

    filterNode
}

def addProfileImages(prefixFolders, imagesArray) {
    for (def prefixFolder : prefixFolders) {
        for (def cfFolder : prefixFolder.getChildren()) {
            if(cfFolder.name == "jcr:content") {
                continue
            }
            
            def cfResourceName = cfFolder.getName()
            def cfResource = cfFolder.getChild(cfResourceName)
            if (cfResource == null) {
                cfResource = cfFolder.getChild('profileCF')
            }
            
            if (cfResource == null) {
                continue
            }
            def cfNode = getNode(cfResource.path)
            
            if (cfNode.hasProperty("jcr:content/data/master/resume")) {
                def photo = cfNode.getProperty("jcr:content/data/master/resume").getString()
                if (photo == null || photo.isEmpty() || photo.contains("/content/dam/au/assets/migrated-profile-resumes/http")) {
                    continue
                }
                photo = photo.replace("migrated-profile-resumes/", "")
                imagesArray.add(photo)
                println("Added profile Resume for profile: ${cfResource.path},\n ${photo}\n")
            }
        }
    }
}