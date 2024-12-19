#!/bin/sh

SERVER_URL="http://tmail-backend.local:8000"
PASSWORD="secret"

# Loop through users from user1 to user9
for i in $(seq 1 9); do
  USER="user$i@open-paas.org"
  curl -X PUT "$SERVER_URL/users/$USER" \
    -H "Content-Type: application/json" \
    -d "{\"password\":\"$PASSWORD\"}"
done
