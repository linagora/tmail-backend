#!/bin/bash
#
# Builds the image of tmail-backend's distributed app from the commit under test (this checkout): Maven in
# its JDK 25 image (the local repository ~/.m2 shared), James as the git submodule of that commit, the image written by
# Jib as a tar and loaded. TMAIL_IMAGE given (a published image): no build, that one is pulled.
#
set -euo pipefail
. "$(dirname "$0")/common.sh"

if [ "$TMAIL_IMAGE" != "$BUILT_IMAGE" ]; then
  echo "==> Using $TMAIL_IMAGE (TMAIL_IMAGE): no build"
  docker image inspect "$TMAIL_IMAGE" >/dev/null 2>&1 || docker pull --quiet "$TMAIL_IMAGE"
else
  echo "==> James, the submodule of the commit"
  git -C "$REPO_DIR" submodule update --init --depth 1 james-project
  M2="${MAVEN_REPOSITORY:-$HOME/.m2}"
  JIB_DIR="${QA_RUN:?}/jib"  # Jib's global configuration and caches: the home of the image is not writable for us
  mkdir -p "$JIB_DIR"
  maven() {
    docker run --rm -u "$(id -u):$(id -g)" -v "$REPO_DIR:/src" -w /src -v "$M2:/var/maven/.m2" -v "$JIB_DIR:/jib" \
      -e MAVEN_CONFIG=/var/maven/.m2 -e HOME=/var/maven -e XDG_CONFIG_HOME=/jib/config -e XDG_CACHE_HOME=/jib/cache \
      "${MAVEN_IMAGE:-maven:3.9-eclipse-temurin-25}" \
      mvn -Duser.home=/var/maven -B -ntp -DskipTests -Dmaven.javadoc.skip=true -Dcheckstyle.skip -Dspotless.check.skip=true \
      -Drat.skip=true -Dmaven.gitcommitid.skip=true -Daether.enhancedLocalRepository.trackingFilename=.no-tracking "$@"
  }
  echo "==> Building tmail-backend's distributed app and what it needs (James included)"
  # Jib skipped: James's apps build their images on install, ours alone is wanted, below
  maven install -pl tmail-backend/apps/distributed -am -T 1C -Djib.skip=true
  echo "==> Its image, by Jib, as a tar"
  maven -pl tmail-backend/apps/distributed jib:buildTar -Djib.to.image="$BUILT_IMAGE" -Djib.outputPaths.tar=/src/target/qa-tmail.tar
  docker load -i "$REPO_DIR/target/qa-tmail.tar"
  rm -f "$REPO_DIR/target/qa-tmail.tar"
fi

echo "==> Pulling the other images"
mkdir -p "$QA_RUN"
compose pull --quiet --ignore-pull-failures cassandra opensearch tika rabbitmq s3 redis mcp-mpt
