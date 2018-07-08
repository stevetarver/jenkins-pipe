/**
 * I remove docker build artifacts that may not be cleaned up by Jenkins, either
 * through a build job abort, bug, etc.
 *
 * I focus on containers and images associated with DOCKER_BUILD_IMAGE_NAME, but also
 * do 'safe' global cleanup.
 */
def call(Closure unused) {

    if('minikube' == env.K8S_CLUSTER_TYPE) {
        echo "Skipping docker cleanup phase while in MiniKube"
    } else {
        sh '''
        ECHO_PREFIX='===>'
        
        # Get all containers for this project and branch
        CONTAINERS=$(docker ps -a | grep ${DOCKER_BUILD_IMAGE_NAME} | cut -f1 -d' ')
    
        if [ -n "${CONTAINERS}" ]; then
            echo "${ECHO_PREFIX} Stopping and removing ${DOCKER_BUILD_IMAGE_NAME} containers"
            docker rm -f -v ${CONTAINERS}
        else
            echo "${ECHO_PREFIX} No ${DOCKER_BUILD_IMAGE_NAME} containers running"
        fi
    
        IMAGES=$(docker images -q ${DOCKER_BUILD_IMAGE_NAME} | uniq)
    
        if [ -n "${IMAGES}" ]; then
            echo "${ECHO_PREFIX} Removing ${DOCKER_BUILD_IMAGE_NAME} images"
            docker rmi -f ${IMAGES}
        else
            echo "${ECHO_PREFIX} No ${DOCKER_BUILD_IMAGE_NAME} images exist"
        fi
    
        echo "${ECHO_PREFIX} Removing orphaned docker volumes"
        docker volume prune --force
         
        # see https://www.projectatomic.io/blog/2015/07/what-are-docker-none-none-images/
        # Currently, this casts too wide a net - deleting things k8s may have cached. Research
        # and test prior to enabling
        # DANGLING=$(docker images -q -f 'dangling=true')
        # if [ -n "${DANGLING}" ]; then
        #     echo "${ECHO_PREFIX} Removing dangling images"
        #     docker rmi -f ${DANGLING}
        # else
        #     echo "${ECHO_PREFIX} No dangling images exist"
        # fi
     '''
    }
}

return this
