#!/bin/bash
# This script monitors the health of a container (the container needs to be configured with a healthcheck).
# If the container is unhealthy, the script restarts the container.

# Function to get the current timestamp
timestamp() {
  date +"%Y-%m-%d %H:%M:%S %Z"
}

# Check if CONTAINER_NAME argument is provided
if [ -z "$1" ]; then
  echo "Error: Please provide the CONTAINER_NAME as an argument."
  exit 1
fi

CONTAINER_NAME="$1"
INTERVAL_IN_SECOND=30

echo "[$(timestamp)]: Start monitoring container $CONTAINER_NAME health."

while true; do
  CONTAINER_HEALTH_STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME")

  if [ "$CONTAINER_HEALTH_STATUS" == "unhealthy" ]; then
    echo "[$(timestamp)]: Container $CONTAINER_NAME is unhealthy! Restarting the container..."

    if docker restart "$CONTAINER_NAME"; then
      echo "[$(timestamp)]: Container $CONTAINER_NAME restarted."
    else
      echo "[$(timestamp)]: Failed to restart container $CONTAINER_NAME."
    fi
  fi

  sleep "$INTERVAL_IN_SECOND"
done
