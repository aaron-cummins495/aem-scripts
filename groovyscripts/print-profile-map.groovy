def profilesFolder = getResource("/content/dam/au/cf/profiles")
def profilesFolderChildren = profilesFolder.getChildren()

//hitProfileNodes(profilesFolderChildren)

profilesFolder = getResource("/content/dam/au/cf/profiles/not_in_workday_report")
profilesFolderChildren = profilesFolder.getChildren()
hitProfileNodes(profilesFolderChildren)

def hitProfileNodes(prefixFolders) {
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
            
            if (cfNode.hasProperty("jcr:content/data/master/username")) {
                def username = cfNode.getProperty("jcr:content/data/master/username").getString()
                if (username == null || username.isEmpty()) {
                    continue
                }
                println("${username}")
            }
        }
    }
}