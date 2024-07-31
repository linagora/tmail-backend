#!/bin/bash

# URL of the server
SERVER_URL="http://tmail-backend.local:8000"

# Create the domain
curl -X PUT "$SERVER_URL/domains/domain.tld"

# Create the minister account
curl -X PUT "$SERVER_URL/users/minister@domain.tld" \
  -H "Content-Type: application/json" \
  -d '{"password":"misecret"}'

# Create the secretary account
curl -X PUT "$SERVER_URL/users/secretary@domain.tld" \
  -H "Content-Type: application/json" \
  -d '{"password":"sesecret"}'

# Create the other3 account
curl -X PUT "$SERVER_URL/users/other3@domain.tld" \
  -H "Content-Type: application/json" \
  -d '{"password":"other3secret"}'

# Delegate permissions, allowing secretary to access minister
curl -X PUT "$SERVER_URL/users/minister@domain.tld/authorizedUsers/secretary@domain.tld"
