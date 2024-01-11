#!/bin/bash

# Domain Configuration
WEBADMIN_BASE_URL="http://localhost:8000"
DOMAIN_NAME="domain.local"

# IMAP Configuration
IMAP_SERVER="localhost"
IMAP_PORT=993
ALICE_USERNAME="alice@${DOMAIN_NAME}"
ALICE_PASSWORD="secret"

BOB_USERNAME="bob@${DOMAIN_NAME}"
BOB_PASSWORD="secret"

# SMTP Configuration
SMTP_SERVER="localhost"
SMTP_PORT=25
SMTP_FROM="${BOB_USERNAME}"
SMTP_TO="${ALICE_USERNAME}"
SMTP_SUBJECT="Test Email"
SMTP_BODY="Hello Alice, this is a test email."

# Function to create a new domain using curl
create_domain() {
  echo "Creating domain: ${DOMAIN_NAME}"
  curl -X PUT ${WEBADMIN_BASE_URL}/domains/${DOMAIN_NAME}
}

# Function to create a new user using curl
create_user() {
  local username="$1"
  local password="$2"
  echo "Creating user: ${username}"
  curl -L -X PUT "${WEBADMIN_BASE_URL}/users/${username}" \
    -H 'Content-Type: application/json' \
    -d "{\"password\":\"${password}\"}"
}

# Function to send email using telnet
send_email() {
  {
    sleep 2
    echo "EHLO example.com"
    sleep 2
    echo "MAIL FROM:<${SMTP_FROM}>"
    sleep 2
    echo "RCPT TO:<${SMTP_TO}>"
    sleep 2
    echo "DATA"
    sleep 2
    echo "Subject: ${SMTP_SUBJECT}"
    echo ""
    echo "${SMTP_BODY}"
    echo "."
    sleep 2
    echo "QUIT"
  } | telnet ${SMTP_SERVER} ${SMTP_PORT}
}

# Function to fetch emails using openssl
fetch_emails() {
  {
    sleep 2
    echo "a1 LOGIN ${ALICE_USERNAME} ${ALICE_PASSWORD}"
    sleep 2
    echo "a2 SELECT INBOX"
    sleep 2
    echo "a3 SEARCH UNSEEN SUBJECT \"${SMTP_SUBJECT}\" FROM \"${SMTP_FROM}\""
    sleep 2
    echo "a4 LOGOUT"
  } | openssl s_client -connect ${IMAP_SERVER}:${IMAP_PORT} -quiet
}

# Create domain and users
create_domain
create_user "${ALICE_USERNAME}" "${ALICE_PASSWORD}"
create_user "${BOB_USERNAME}" "${BOB_PASSWORD}"

# SMTP Test
echo "Sending test email..."
send_email

# IMAP Test
echo "Fetching emails from ${ALICE_USERNAME}..."
# Fetch emails using openssl and check if the email from Bob is present
if fetch_emails | grep -q "a3 OK"; then
  echo "Email from Bob found in Alice's inbox."
else
  echo "Email from Bob not found in Alice's inbox."
fi

echo "Test script completed."
