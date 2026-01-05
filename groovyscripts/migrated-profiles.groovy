def getSubFolderResource(parentFolderResource, subFolderName) throws PersistenceException {
    subFolderName = subFolderName.replaceAll("\\W", "_");
    subFolderResource = parentFolderResource.getChild(subFolderName);
    if (subFolderResource == null) {
        def nodeParams = new HashMap<>();
        nodeParams.put("jcr:primaryType", "sling:Folder");
        subFolderResource = resourceResolver.create(parentFolderResource, subFolderName, nodeParams);
        resourceResolver.commit();
    }
    return subFolderResource;
}

def profilesFolder = getResource("/content/dam/au/cf/profiles-migrated")
def targetProfilesFolder = getResource("/content/dam/au/cf")
targetProfilesFolder = getSubFolderResource(targetProfilesFolder, "profiles")
def profilesFolderChildren = profilesFolder.getChildren()

println("${profilesFolderChildren.size()}")

def count = 0

for (def prefixFolder : profilesFolderChildren) {
    
    for (def cfFolder : prefixFolder.getChildren()) {
        if(cfFolder.name == "jcr:content") {
            continue
        }
        
        def cfResource = cfFolder.getChild("profileCF")
        if (cfResource == null) {
            println('oopsie')
            continue
        }
        def cfNode = getNode(cfResource.path)
        
        def newPath = cfResource.path.replace("profiles-migrated", "profiles/not_in_workday_report")
        newPath = newPath.replace("-", "_")
        def notInWorkdayReportFolder = getSubFolderResource(targetProfilesFolder, "not_in_workday_report")
        if (cfNode.hasProperty("jcr:content/data/master/workdayID")) {
            def workdayId = cfNode.getProperty('jcr:content/data/master/workdayID').getString()
            def subFolderName = "00";
            if (workdayId.length() >= 6) {
                subFolderName = workdayId.substring(workdayId.length() - 6, workdayId.length() - 4);
            }
            newPath = "/content/dam/au/cf/profiles/${subFolderName}/${workdayId}/${workdayId}"
            def newPrefixFolder = getSubFolderResource(targetProfilesFolder, subFolderName)
            def newCfFolder = getSubFolderResource(newPrefixFolder, workdayId)
            //println(newPath)

            
        } else {
            // create last two folders if they don't exist
            // println(newPath.split("/")[7])
            // println(newPath.split("/")[8])
            def newPrefixFolder = getSubFolderResource(notInWorkdayReportFolder, newPath.split("/")[7])
            def newCfFolder = getSubFolderResource(newPrefixFolder, newPath.split("/")[8])
            println(newCfFolder.path)
        }

        if (cfNode.hasProperty("jcr:content/data/master/authorizedAdminsMigrated")) {
            def authorizedAdminsMigrated = cfNode.getProperty('jcr:content/data/master/authorizedAdminsMigrated').getString()
            
            // Assuming authorizedAdminsMigrated is a pipe-separated list of admin names
            def adminNames = authorizedAdminsMigrated.split("\\|")
            def propertiesNode = getNode("${cfNode.path}/jcr:content/data/master")
            println("Migrated admins for ${propertiesNode.path}: ${adminNames}")

            // Write String[] prop to node
            propertiesNode.setProperty("authorizedAdminsCMF", adminNames)
            resourceResolver.commit()
        }
        
        def existingCF = getResource(newPath.toString())
        if (existingCF != null) {
            resourceResolver.delete(existingCF)
            resourceResolver.commit()
        }
        session.workspace.copy(cfResource.path, newPath.toString())
        println(newPath.toString())
        
    }
}