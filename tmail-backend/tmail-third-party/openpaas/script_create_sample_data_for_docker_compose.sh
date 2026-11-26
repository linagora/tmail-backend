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
  # 409 means the user already exists (provisioning re-run): keep it, do not reset its password
  STATUS=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT "$SERVER_URL/users/$USER" \
    -H "Content-Type: application/json" \
    -d "{\"password\":\"$PASSWORD\"}")
  case "$STATUS" in
    204|409) ;;
    *)
      echo "Failed to create $USER in tmail-backend (HTTP $STATUS)" >&2
      exit 1
      ;;
  esac
  # The Twake Calendar side service does not provision users on its own, unlike ESN
  # 409 means the user is already registered (provisioning re-run): keep it
  STATUS=$(curl -sS -o /dev/null -w "%{http_code}" -X POST "$SIDE_SERVICE_WEBADMIN_URL/registeredUsers" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$USER\",\"firstname\":\"User$i\",\"lastname\":\"Sample\"}")
  case "$STATUS" in
    201|409) ;;
    *)
      echo "Failed to register $USER in the Twake Calendar side service (HTTP $STATUS)" >&2
      exit 1
      ;;
  esac
done
