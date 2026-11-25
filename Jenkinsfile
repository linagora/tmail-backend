// Files that the pipeline itself executes, or that decide what it executes.
// Jenkins loads the Jenkinsfile from the trusted revision - the target branch -
// so a pull request can never change the pipeline definition. Everything else
// below is read from the workspace, which holds the contributor's code, so a
// pull request changing them would be running its own code inside our CI.
def protectedCiPaths() {
    return ['Jenkinsfile', 'ci/', '.mvn/', '.gitmodules']
}

// A contributor is trusted when they can already push to the repository: either
// the change request comes from a branch of linagora/tmail-backend, or from a
// fork owned by a member of the linagora organisation.
def isTrustedContributor() {
    if (!env.CHANGE_FORK) {
        return true
    }
    def owner = forkOwner()
    // Jenkins may set CHANGE_FORK to 'linagora' for a branch of the repository itself: only someone
    // with write access pushes there, and an organization is not a member of itself.
    if (owner == 'linagora') {
        return true
    }
    def trusted = false
    withGithubCredential {
        trusted = isOrgMember(owner)
    }
    return trusted
}

// CHANGE_FORK is 'owner', or 'owner/repository' for a fork renamed on GitHub
def forkOwner() {
    return env.CHANGE_FORK.tokenize('/')[0]
}

// Makes the target branch reachable as origin/<target> so that the change
// request can be diffed against it, whatever pull request discovery strategy
// the job is configured with.
def fetchChangeTarget() {
    if (!env.CHANGE_TARGET) {
        error('CHANGE_TARGET is not set: cannot determine what this change request modifies.')
    }
    sh "git fetch --no-tags --quiet origin '+refs/heads/${env.CHANGE_TARGET}:refs/remotes/origin/${env.CHANGE_TARGET}'"
}

def changedCiFiles() {
    def changed = sh(
        script: "git diff --name-only 'origin/${env.CHANGE_TARGET}...HEAD'",
        returnStdout: true
    )

    def offending = []
    for (String path : changed.split('\n')) {
        String candidate = path.trim()
        if (candidate.isEmpty()) {
            continue
        }
        for (String protectedPath : protectedCiPaths()) {
            if (candidate == protectedPath || candidate.startsWith(protectedPath)) {
                offending.add(candidate)
                break
            }
        }
    }
    return offending
}

// The report script is handed the GitHub token, so it has to be a reviewed
// version of it. On a branch or tag build, and for a contributor allowed to
// change CI files, the workspace copy is that version. For anybody else the
// workspace copy is unreviewed code, so the target branch one is extracted
// instead - the guard stage already refuses such a change request, this is the
// second lock. Returns null when no trusted copy can be found, and the failure
// is then simply not reported rather than reported by untrusted code.
def trustedReportScript() {
    if (!env.CHANGE_ID || isTrustedContributor()) {
        return 'ci/report-build-failure.sh'
    }

    fetchChangeTarget()
    // mktemp, in the temporary directory Jenkins owns next to the workspace: a
    // predictable path under a world writable /tmp could be pre-created by
    // anything else running on this shared agent, and what lands there is about
    // to be run with the GitHub token.
    def tmpDir = env.WORKSPACE_TMP ?: "${env.WORKSPACE}@tmp"
    def scriptPath = sh(
        returnStdout: true,
        script: "mkdir -p '${tmpDir}' && mktemp '${tmpDir}/report-build-failure-XXXXXXXX.sh'"
    ).trim()

    def extracted = sh(
        returnStatus: true,
        script: "git show 'origin/${env.CHANGE_TARGET}:ci/report-build-failure.sh' > '${scriptPath}'"
    )
    if (extracted != 0) {
        echo "No report script on ${env.CHANGE_TARGET}: the build failure is not reported."
        sh "rm -f '${scriptPath}'"
        return null
    }
    return scriptPath
}

// Posts the failing tests - or, when none was reported, the tail of the failing
// stage output - back to the pull request, so that a coding agent can pick the
// build failure up and drive it back to green on its own.
//
// Only the stages that hold no credential are teed to ci-logs: the tee step
// captures the raw stream, before the console log filter that masks secrets, so
// a teed log of a stage handling credentials would carry them in clear text -
// straight into a public pull request comment.
// Reporting is best effort: a GitHub API hiccup or a missing target branch must
// not hide the failure being reported, nor turn an unstable build into a failed
// one, so nothing here is allowed to escape.
def reportBuildFailure() {
    def reportScript = null
    try {
        archiveArtifacts artifacts: 'ci-logs/*.log', allowEmptyArchive: true, fingerprint: false

        reportScript = trustedReportScript()
        if (reportScript != null) {
            // The newest ci-logs/*.log can be the Test branch aborted by the failing one: name the failing one instead
            def failedTestBranch = env.FAILED_TEST_BRANCH ?
                ["BUILD_LOG=${env.WORKSPACE}/ci-logs/Test - ${env.FAILED_TEST_BRANCH}.log", "STAGE_NAME=Test - ${env.FAILED_TEST_BRANCH}"] : []
            withEnv(failedTestBranch) {
                withCredentials([usernamePassword(credentialsId: 'github',
                        usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_TOKEN')]) {
                    sh "bash '${reportScript}' || true"
                }
            }
        }
    } catch (Exception e) {
        echo "Could not report the build failure: ${e.message}"
    } finally {
        if (reportScript != null && reportScript != 'ci/report-build-failure.sh') {
            sh "rm -f '${reportScript}'"
        }
    }
}

// One branch of the Test stage. The modules under integration-tests/ and deployment-tests/ run in one branch, every
// other module in the other: the split is computed from the directories, so a new module needs no change here or
// in a pom, whatever its parent. The two branches share the agent's memory, which has no swap: its use and the load
// are sampled every 30 s to ci-logs/test-resources-<branch>.txt (not .log: the failure report reads the newest
// ci-logs/*.log as the failing stage output). Both Maven builds share ~/.m2, hence the resolver file locks.
// BUILD_ID is hidden from Maven: James names its Cassandra test image after it, so every test JVM of the build would
// build and tag the same image, and two of them doing it at once can leave the tag missing for a moment (PR-2723 #1
// hung on it). Without it, each JVM builds its own image, a few seconds at most.
def runTestBranch(String branch, boolean integrationModules) {
    tee("ci-logs/Test - ${branch}.log") {
        withEnv(["INTEGRATION_BRANCH=${integrationModules}", "RESOURCES_FILE=${env.WORKSPACE}/ci-logs/test-resources-${branch}.txt"]) {
            dir('tmail-backend') {
                sh '''
                    (set +x; free -m | head -n 1; while true; do echo "$(date +%T) $(free -m | sed -n 2p) load $(cut -d ' ' -f 1-3 /proc/loadavg)"; sleep 30; done) > "$RESOURCES_FILE" 2>&1 &
                    trap "kill $! 2>/dev/null || true" EXIT
                    INTEGRATION_MODULES=$(find integration-tests deployment-tests -name pom.xml -not -path '*/target/*' -not -path '*/src/*' | sed 's#/pom.xml$##' | sort | paste -sd , -)
                    test -n "$INTEGRATION_MODULES"
                    if [ "$INTEGRATION_BRANCH" = true ]; then
                        PROJECTS="$INTEGRATION_MODULES"
                    else
                        PROJECTS=$(echo "$INTEGRATION_MODULES" | sed 's/^/!/; s/,/,!/g')
                    fi
                    env -u BUILD_ID mvn -B -Dapi.version=1.43 surefire:test -Pci-test -pl "$PROJECTS" \\
                        -Daether.syncContext.named.factory=file-lock -Daether.syncContext.named.nameMapper=file-gav
                '''
            }
        }
    }
}

pipeline {
    agent {
        label 'heavy'
    }

    tools {
        jdk 'jdk_25'
    }

    options {
        // Configure an overall timeout for the build.
        timeout(time: 4, unit: 'HOURS')
        disableConcurrentBuilds()
    }

    // No pipeline-level credentials, see withGithubCredential and withDockerHubLogin.
    stages {
        // First stages on purpose: nothing from the change request has been run
        // yet at this point, not even `git submodule update`.
        stage('Authorize fork') {
            // A fork pull request runs no code of its own before an org member vouches for its head
            // commit, see authorizeFork
            when {
                changeRequest()
                expression { env.CHANGE_FORK != null }
            }
            steps {
                script {
                    authorizeFork()
                }
            }
        }
        stage('Validate CI files') {
            when {
                changeRequest()
            }
            steps {
                script {
                    fetchChangeTarget()
                    def offending = changedCiFiles()
                    if (offending.isEmpty()) {
                        echo 'No CI file touched by this change request.'
                    } else if (isTrustedContributor()) {
                        echo "Trusted contributor, changes to ${offending.join(', ')} allowed."
                    } else {
                        error("""This change request modifies files the CI executes: ${offending.join(', ')}.
Contributors outside the linagora organisation cannot change them, as the build would then run unreviewed code with access to the CI credentials.
Please drop these changes from the pull request, or ask a linagora member to carry them.""")
                    }
                }
            }
        }
        stage('Git submodule init') {
            steps {
                sh 'mkdir -p ci-logs'
                tee('ci-logs/Git submodule init.log') {
                    sh 'git submodule init'
                    sh 'git submodule update'
                }
            }
        }
        stage('Compile') {
            steps {
                tee('ci-logs/Compile.log') {
                    // custom-james-assembly runs jib in another class loader than the James apps,
                    // on the same base image: concurrent builds fail with OverlappingFileLockException
                    // (GoogleContainerTools/jib#3347). TMail does not need it.
                    sh "mvn clean install -Dmaven.javadoc.skip=true -DskipTests -T1C -pl '!org.apache.james.examples:custom-james-assembly'"
                }
            }
        }
        stage('Test') {
            // Two Maven builds side by side, to use the CPU and memory the agent leaves idle with one (see runTestBranch).
            // A failing branch aborts the other one, as the single build stopped at the first failing module
            failFast true
            parallel {
                stage('Integration and deployment tests') {
                    steps {
                        runTestBranch('integration', true)
                    }
                    post {
                        failure {
                            script {
                                env.FAILED_TEST_BRANCH = 'integration'
                            }
                        }
                    }
                }
                stage('Other module tests') {
                    steps {
                        runTestBranch('modules', false)
                    }
                    post {
                        failure {
                            script {
                                env.FAILED_TEST_BRANCH = 'modules'
                            }
                        }
                    }
                }
            }
            post {
                always {
                    junit(testResults: '**/surefire-reports/*.xml', allowEmptyResults: false)
                    archiveArtifacts artifacts: 'ci-logs/test-resources-*.txt', allowEmptyArchive: true, fingerprint: false
                }
                failure {
                    archiveArtifacts artifacts: '**/target/test-run.log' , fingerprint: true
                    archiveArtifacts artifacts: '**/surefire-reports/*' , fingerprint: true
                }
            }
        }
        stage('Unstable Tests') {
            steps {
                echo 'Running unstable tests'
                dir("tmail-backend") {
                    // surefire:test does not clean: drop the reports already recorded by the Test stage
                    sh 'find . -path "*/target/surefire-reports" -type d -prune -exec rm -rf {} +'
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                        // Without BUILD_ID, each test JVM builds its own Cassandra test image: see runTestBranch
                        sh 'env -u BUILD_ID mvn -B -e -fae -Dapi.version=1.43 surefire:test -Punstable-tests'
                    }
                }
            }
            post {
                always {
                    // The stage stays red on failing unstable tests, but the build result is not UNSTABLE: no failure comment on the pull request
                    junit(testResults: '**/surefire-reports/*.xml', allowEmptyResults: true, skipMarkingBuildUnstable: true)
                }
                failure {
                    archiveArtifacts artifacts: '**/target/test-run.log' , fingerprint: true
                    archiveArtifacts artifacts: '**/surefire-reports/*' , fingerprint: true
                }
            }
        }
        stage('Deliver Docker images for PR') {
          when {
            changeRequest()
          }
          steps {
            script {
              // A fork pull request reaches this stage only once authorized, see authorizeFork.
              dir("tmail-backend") {
                  // Rebuild the artifacts first so a reused workspace cannot
                  // push a stale image.
                  sh 'mvn -f apps/distributed/pom.xml package -DskipTests -T1C'
                  sh 'mvn -f apps/distributed/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency'
                  // jib runs the change request's own pom.xml: it gets the Docker Hub login of
                  // withDockerHubLogin (DOCKER_CONFIG), never the credential in its environment.
                  withDockerHubLogin {
                    // The distributed image is built from a Dockerfile (non-root)
                    // instead of jib. The runtime deps and the module jars must
                    // be in place before the docker build: dependency:copy-
                    // dependencies fills target/dependency (FLAT layout — no
                    // useRepositoryLayout, the classpath wildcard /root/libs/*
                    // only matches jars in that exact directory), consumed as
                    // /root/libs by the Dockerfile. docker login/logout wraps
                    // all pushes.
                    sh 'docker build --pull -f dockerfiles/distributed/Dockerfile -t linagora/tmail-backend-pr:$CHANGE_ID apps/distributed'
                    sh 'docker push linagora/tmail-backend-pr:$CHANGE_ID'
                    // Build tmail distributed AI PR image: same Dockerfile with the
                    // AI bot extension jar placed in extensions-jars before the build.
                    sh 'cp tmail-third-party/ai-bot/target/tmail-ai-bot-jar-with-dependencies.jar apps/distributed/src/main/extensions-jars'
                    sh 'docker build --pull -f dockerfiles/distributed/Dockerfile -t linagora/tmail-backend-distributed-pr:ai-$CHANGE_ID apps/distributed'
                    sh 'docker push linagora/tmail-backend-distributed-pr:ai-$CHANGE_ID'
                    sh 'rm -f apps/distributed/src/main/extensions-jars/tmail-ai-bot-jar-with-dependencies.jar'
                    // migration-proxy still builds via jib, also for PRs.
                    sh 'mvn -Pci jib:build -Djib.to.image=linagora/tmail-migration-proxy-pr -Djib.to.tags=$CHANGE_ID -pl apps/migration-proxy'
                  }
              }

              withGithubCredential {
                sh """
                  HTTP_STATUS=\$(curl -s -o /tmp/gh_comment_response.json -w "%{http_code}" -X POST \\
                    -H "Authorization: token \${GITHUB_CREDENTIAL_PSW}" \\
                    -H "Content-Type: application/json" \\
                    -d "{\\"body\\": \\"Docker images published for this PR:\\\\n - linagora/tmail-backend-pr:\${CHANGE_ID}\\\\n - linagora/tmail-backend-distributed-pr:ai-\${CHANGE_ID}\\\\n - linagora/tmail-migration-proxy-pr:\${CHANGE_ID}\\"}" \\
                    "https://api.github.com/repos/linagora/tmail-backend/issues/\${CHANGE_ID}/comments")
                  if [ "\$HTTP_STATUS" -lt 200 ] || [ "\$HTTP_STATUS" -ge 300 ]; then
                    echo "WARNING: GitHub API comment failed with HTTP \$HTTP_STATUS"
                    cat /tmp/gh_comment_response.json
                  fi
                """
              }
            }
          }
        }
        stage('Deliver Docker images') {
          when {
            anyOf {
              branch 'master'
              buildingTag()
            }
          }
          steps {
            script {
              env.DOCKER_TAG = 'branch-master'
              if (env.TAG_NAME) {
                env.DOCKER_TAG = env.TAG_NAME
              }

              echo "Docker tag: ${env.DOCKER_TAG}"
              // build and push docker images
              dir("tmail-backend") {
                  // The distributed image is built from a Dockerfile (non-root):
                  // the runtime deps must be in place before the docker build,
                  // dependency:copy-dependencies fills target/dependency (FLAT
                  // layout — no useRepositoryLayout), consumed as /root/libs by
                  // the Dockerfile. memory/postgresql/migration-proxy keep
                  // their jib-based builds below.
                  // Rebuild the artifacts first so a reused workspace cannot
                  // push a stale image.
                  sh 'mvn -f apps/distributed/pom.xml package -DskipTests -T1C'
                  sh 'mvn -f apps/distributed/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency'
                  // jib gets the Docker Hub login of withDockerHubLogin (DOCKER_CONFIG), as for the PR
                  // images: no password on its command line.
                  withDockerHubLogin {
                    sh 'docker build --pull -f dockerfiles/distributed/Dockerfile -t linagora/tmail-backend:distributed-$DOCKER_TAG apps/distributed'
                    sh 'docker push linagora/tmail-backend:distributed-$DOCKER_TAG'
                    sh 'mvn -Pci jib:build -Djib.to.tags=memory-$DOCKER_TAG -pl apps/memory -X'
                    sh 'mvn -Pci jib:build -Djib.to.tags=postgresql-$DOCKER_TAG -pl apps/postgres -X'
                    sh 'mvn -Pci jib:build -Djib.to.tags=$DOCKER_TAG -pl apps/migration-proxy -X'

                    // Build tmail distributed AI image: same Dockerfile with the
                    // AI bot extension jar placed in extensions-jars before the build.
                    sh 'cp tmail-third-party/ai-bot/target/tmail-ai-bot-jar-with-dependencies.jar apps/distributed/src/main/extensions-jars'
                    sh 'docker build --pull -f dockerfiles/distributed/Dockerfile -t linagora/tmail-backend:distributed-ai-$DOCKER_TAG apps/distributed'
                    sh 'docker push linagora/tmail-backend:distributed-ai-$DOCKER_TAG'
                    sh 'rm -f apps/distributed/src/main/extensions-jars/tmail-ai-bot-jar-with-dependencies.jar'
                  }
              }
            }
          }
          post {
              always {
                  script {
                      if (env.BRANCH_NAME == "master") {
                          emailext(
                                  subject: "[${currentBuild.result}]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                                  body: """
${currentBuild.result}: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]:
Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]</a>'"
""",
                                  to: "openpaas-james@linagora.com"
                          )
                      }
                  }
              }
          }
        }
    }
    post {
        failure {
            reportBuildFailure()
        }
        unstable {
            reportBuildFailure()
        }
        success {
            script {
                if (env.BRANCH_NAME == "master") {
                    build (job: 'Gatling Imap build/master', propagate: false, wait: false)
                    build (job: 'James Gatling build/master', propagate: false, wait: false)
                }
            }
        }
        cleanup {
            deleteDir() /* clean up our workspace */
        }
    }
}

def githubRepository() {
  return 'linagora/tmail-backend'
}

// No credential is bound at pipeline level: every stage runs code of the checked out sources (build
// plugins, package scripts, tests, scripts/), so secrets are bound only around the steps that need
// them, see withGithubCredential and withDockerHubLogin.

// A fork pull request runs no code of its own before an org member vouches for its head commit: the
// owner of the fork is a linagora member, or a member approved that very commit (see
// memberReviewerOf). An approval names one commit, so a commit pushed after it is not built until a
// member approves it again.
def authorizeFork() {
  if (isTrustedContributor()) {
    echo "Fork owner '${forkOwner()}' is a linagora org member, proceeding."
    return
  }
  withGithubCredential {
    def headSha = pullRequestHead()
    def approver = memberReviewerOf(headSha)
    if (approver) {
      echo "Commit ${headSha} approved by linagora member '${approver}', proceeding."
      requireApprovedCheckout(headSha)
      return
    }
    error("Fork owner '${forkOwner()}' is not a linagora org member and no member reviewed commit ${headSha}. " +
      "A linagora member must approve the pull request on this commit, or comment 'Build this please ${headSha}', " +
      "then re-run the build.")
  }
}

// The head commit of the pull request, as GitHub reports it now. It need not be the commit Jenkins
// checked out: the contributor can push between the checkout and this call. See
// requireApprovedCheckout.
def pullRequestHead() {
  return githubApi("pulls/${env.CHANGE_ID}").head.sha
}

// Fails the build unless the checked out tree is the approved commit, or its merge with the target
// branch as fetched from this repository. A merge build checks out a merge commit Jenkins made locally:
// its first parent is the pull request revision, its second the target. The contributor writes the
// commits they push, parents and tree included, so the merge is redone and its tree compared: no ref
// of the workspace tells which revision Jenkins pinned. Whatever it reports the status on, that
// revision holds the tree that was tested.
def requireApprovedCheckout(String sha) {
  withEnv(["APPROVED_SHA=${sha}"]) {
    sh '''
      head=$(git rev-parse HEAD)
      if [ "$head" = "$APPROVED_SHA" ]; then
        exit 0
      fi
      refuse() {
        echo "The checkout of $head is not the approved commit $APPROVED_SHA, nor its merge with $CHANGE_TARGET: $1. Re-run the build." >&2
        exit 1
      }
      target=$(git rev-parse -q --verify "refs/remotes/origin/$CHANGE_TARGET^{commit}") || refuse "origin/$CHANGE_TARGET was not fetched"
      [ "$(git rev-parse -q --verify 'HEAD^1' || true)" = "$APPROVED_SHA" ] || refuse "its first parent is not the approved commit"
      [ "$(git rev-parse -q --verify 'HEAD^2' || true)" = "$target" ] || refuse "its second parent is not origin/$CHANGE_TARGET"
      [ -z "$(git rev-parse -q --verify 'HEAD^3' || true)" ] || refuse "it has more than two parents"
      # The contributor may have written this merge commit with a tree of their own: redo the merge
      # in a scratch worktree, outside the workspace, and compare the trees.
      scratch="$WORKSPACE@tmp/approved-merge"
      rm -rf "$scratch"
      git worktree add -q --detach "$scratch" "$APPROVED_SHA"
      git -C "$scratch" -c user.name=Jenkins -c user.email=jenkins@localhost merge -q --no-edit "$target" >/dev/null 2>&1 || true
      expected=$(git -C "$scratch" rev-parse 'HEAD^{tree}')
      clean=$(git -C "$scratch" status --porcelain)
      git worktree remove --force "$scratch"
      [ -z "$clean" ] && [ "$expected" = "$(git rev-parse 'HEAD^{tree}')" ] || refuse "its tree is not the merge of the approved commit with origin/$CHANGE_TARGET"
      echo "Building $head, the merge of the approved commit $APPROVED_SHA with origin/$CHANGE_TARGET"
    '''
  }
}

// The login of a linagora member who vouched for the given commit: an approving review of it, a review
// 'Build this please' of it (submitted, not dismissed), or a comment 'Build this please <sha>' naming its full id. A prefix is not
// accepted: the contributor writes the commits, and can craft one sharing a short prefix with the
// reviewed one.
def memberReviewerOf(String sha) {
  for (review in githubApiAllPages("pulls/${env.CHANGE_ID}/reviews")) {
    def approves = review.state == 'APPROVED' ||
      (review.state != 'DISMISSED' && review.state != 'PENDING' && isBuildThisPlease(review.body, sha, false))
    if (review.commit_id == sha && approves && isOrgMember(review.user.login)) {
      return review.user.login
    }
  }
  for (comment in githubApiAllPages("issues/${env.CHANGE_ID}/comments")) {
    if (isBuildThisPlease(comment.body, sha, true) && isOrgMember(comment.user.login)) {
      return comment.user.login
    }
  }
  return null
}

// Whether the text is 'Build this please' followed by the given commit id - optional unless required -
// whatever its case and spacing. A review already names its commit, a comment does not.
def isBuildThisPlease(Object text, String sha, boolean shaRequired) {
  def normalized = "${text}".trim().toLowerCase().replaceAll('\\s+', ' ')
  return normalized == "build this please ${sha}".toString() || (!shaRequired && normalized == 'build this please')
}

def isOrgMember(String login) {
  def status
  withEnv(["GITHUB_LOGIN=${login}"]) {
    status = sh(
      script: 'curl -s -o /dev/null -w "%{http_code}" -H "Authorization: token $GITHUB_CREDENTIAL_PSW" ' +
        '"https://api.github.com/orgs/linagora/members/$GITHUB_LOGIN"',
      returnStdout: true
    ).trim()
  }
  echo "GitHub org membership check returned HTTP ${status} for '${login}'"
  if (status == '204') {
    return true
  }
  if (status == '404') {
    return false
  }
  error("GitHub API error ${status} while checking the membership of '${login}'")
}

def githubApi(String path) {
  def json
  withEnv(["GITHUB_API_PATH=${path}", "GITHUB_API_REPOSITORY=${githubRepository()}"]) {
    json = sh(
      script: 'curl -sf -H "Authorization: token $GITHUB_CREDENTIAL_PSW" ' +
        '"https://api.github.com/repos/$GITHUB_API_REPOSITORY/$GITHUB_API_PATH"',
      returnStdout: true
    )
  }
  return readJSON(text: json)
}

// Every item of a listing of the GitHub API, which serves 100 of them per page at most
def githubApiAllPages(String path) {
  def items = []
  def page = 1
  while (true) {
    def pageItems = githubApi("${path}?per_page=100&page=${page}")
    items.addAll(pageItems)
    if (pageItems.size() < 100) {
      return items
    }
    page++
  }
}

// Binds the GitHub token as GITHUB_CREDENTIAL_USR / GITHUB_CREDENTIAL_PSW around the given steps only
def withGithubCredential(Closure body) {
  withCredentials([usernamePassword(credentialsId: 'github',
      usernameVariable: 'GITHUB_CREDENTIAL_USR', passwordVariable: 'GITHUB_CREDENTIAL_PSW')]) {
    body()
  }
}

// Logs in Docker Hub in a throwaway Docker config deleted afterwards, so that the credential never
// stays on the agent where a later build could read it
def withDockerHubLogin(Closure body) {
  withEnv(["DOCKER_CONFIG=${pwd(tmp: true)}/docker-config"]) {
    try {
      withCredentials([usernamePassword(credentialsId: 'dockerHub',
          usernameVariable: 'DOCKER_HUB_CREDENTIAL_USR', passwordVariable: 'DOCKER_HUB_CREDENTIAL_PSW')]) {
        sh 'mkdir -p "$DOCKER_CONFIG" && printf \'%s\' "$DOCKER_HUB_CREDENTIAL_PSW" | docker login -u "$DOCKER_HUB_CREDENTIAL_USR" --password-stdin'
      }
      body()
    } finally {
      sh 'docker logout || true; rm -rf "$DOCKER_CONFIG"'
    }
  }
}
