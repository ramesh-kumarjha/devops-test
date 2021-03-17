

class DockerEcr implements Serializable {

    private final def script

    final String awsRegion
    final String dockerUser = "AWS"
    final String dockerRegistryIdentifier
    final String dockerRegistryUrl
    String awsProfile = ""
    
    String tag = "latest"

    DockerEcr(def script, String awsAccountId, String awsRegion) {
        this.script = script
        this.awsRegion = awsRegion
        this.dockerRegistryIdentifier = "${awsAccountId}.dkr.ecr.${awsRegion}.amazonaws.com"
        this.dockerRegistryUrl = "https://${dockerRegistryIdentifier}"
    }

    void createGitTag(Git git) {
        this.tag = "${git.branch()}-${git.commitHash()}"
    }

    void createTag(String tag) {
        this.tag = tag
    }

    private boolean checkIfCurrentEcrTokenIsValid() {
        def configFile = this.script.readFile("/home/ubuntu/.docker/config.json")
        def jsonObj = this.script.readJSON(text: configFile)
        def token = jsonObj['auths'][dockerRegistryIdentifier]['auth']
        def statusCode = this.script.sh (script: "curl --silent --output /dev/stderr --write-out \"%{http_code}\" --header \"Authorization: Basic ${token}\" ${dockerRegistryUrl}/v2/", returnStdout: true)
        if (statusCode != 200)
            return false
        return true
    }

    private void loginToAWSECRDockerRegistry(int awsCliVersion) {
        if (awsCliVersion == 1) {
            this.script.sh(
                    """
                    aws ecr get-login --region ${awsRegion} --no-include-email --profile ${awsProfile} \
                    | awk '{printf \$6}' \
                    | docker login -u ${dockerUser} ${dockerRegistryUrl} --password-stdin
                    """
            )
        } else if (awsCliVersion == 2) {
            this.script.sh(
                    """
                    aws ecr get-login-password --region ${awsRegion} --profile ${awsProfile} \
                    | docker login -u ${dockerUser} --password-stdin ${dockerRegistryUrl}
                    """
            )
        }
    }

    void pruneDanglingImages() {
        this.script.sh("docker image prune -f")
    }

    void pruneImagesOlderThan7Days() {
        this.script.sh("docker image prune -a -f --filter \"until=168h\"")
    }

    void buildDockerImage(String microserviceName, String releaseEnv) {
        this.script.sh("docker build -t ${dockerRegistryIdentifier}/${microserviceName}:${this.tag} --build-arg RELEASE_ENV=${releaseEnv}  --build-arg  .")
    }



    void publishDockerImageToECR(String microserviceName) {
        //if (!checkIfCurrentEcrTokenIsValid())
        loginToAWSECRDockerRegistry(2)
        this.script.sh("docker push ${dockerRegistryIdentifier}/${microserviceName}:${this.tag}")
    }

    void publishDockerImageToECRWithTag(String microserviceName, String tag) {
        //if (!checkIfCurrentEcrTokenIsValid())
        loginToAWSECRDockerRegistry(2)
        this.script.sh("docker push ${dockerRegistryIdentifier}/${microserviceName}:${tag}")
    }

    void validateDockerImage(String microserviceName) {
        def imageId = this.script.sh(script: "docker images -q ${dockerRegistryIdentifier}/${microserviceName}:${this.tag}", returnStdout: true).trim()
        if (!imageId?.trim())
            throw new Exception('Error occurred while creating Docker Image')
    }

    void validateDockerImageWithTag(String microserviceName, String tag) {
        def imageId = this.script.sh(script: "docker images -q ${dockerRegistryIdentifier}/${microserviceName}:${tag}", returnStdout: true).trim()
        if (!imageId?.trim())
            throw new Exception('Error occurred while creating Docker Image')
    }
}
