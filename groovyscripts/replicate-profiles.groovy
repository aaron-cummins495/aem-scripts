import com.day.cq.replication.Replicator

def replicator = getService(Replicator)

def profilesFolder = getResource("/content/dam/au/cf/profiles")
def profilesFolderChildren = profilesFolder.getChildren()

replicateProfiles(profilesFolderChildren, replicator)

profilesFolder = getResource("/content/dam/au/cf/profiles/not_in_workday_report")
profilesFolderChildren = profilesFolder.getChildren()
replicateProfiles(profilesFolderChildren, replicator)

def replicateProfiles(prefixFolders, replicator) {
    def count = 0
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

            replicator.replicate(session, ReplicationActionType.ACTIVATE, cfResource.path)
            println "Replicated Profile CF: ${cfResource.path}" 
            
            if (cfNode.hasProperty("jcr:content/data/master/photo")) {
                def photo = cfNode.getProperty("jcr:content/data/master/photo").getString()
                if (getResource(photo)) {
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, photo)
                    println "Replicated Profile photo: ${photo}" 
                }
            }

            if (cfNode.hasProperty("jcr:content/data/master/resume")) {
                def resume = cfNode.getProperty("jcr:content/data/master/resume").getString()
                if (getResource(resume)) {
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, resume)
                    println "Replicated Profile resume: ${resume}" 
                }
            }

            if (cfNode.hasProperty("jcr:content/data/master/defaultProfilePage")) {
                def defaultProfilePage = cfNode.getProperty("jcr:content/data/master/defaultProfilePage").getString()
                defaultProfilePage = "/content/au" + defaultProfilePage
                if (getResource(defaultProfilePage)) {
                    replicator.replicate(session, ReplicationActionType.ACTIVATE, defaultProfilePage)
                    println "Replicated Profile page: ${defaultProfilePage}" 
                }
            }
            count = count + 1
        }
    }
}