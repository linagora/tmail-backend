#!/bin/sh

SERVER_URL="http://tmail-backend.local:8000"
SIDE_SERVICE_WEBADMIN_URL="http://esn:8000"
PASSWORD="secret"

until curl -s -f -o /dev/null "$SIDE_SERVICE_WEBADMIN_URL/healthcheck"; do
  echo "Waiting for the Twake Calendar side service..."
  sleep 2
done

# Loop through users from user1 to user9
for i in $(seq 1 9); do
  USER="user$i@open-paas.org"
  curl -X PUT "$SERVER_URL/users/$USER" \
    -H "Content-Type: application/json" \
    -d "{\"password\":\"$PASSWORD\"}"
  # The Twake Calendar side service does not provision users on its own, unlike ESN
  curl -X POST "$SIDE_SERVICE_WEBADMIN_URL/registeredUsers" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$USER\",\"firstname\":\"User$i\",\"lastname\":\"Sample\"}"
done
