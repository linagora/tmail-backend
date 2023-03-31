#!/bin/sh

set -eux

./dev.sh start
# Get form token
TOKEN=`curl http://sso.example.com/ 2>/dev/null|grep token|perl -pe 's/.*?value="(\w+)".*$/$1/'|head -n1`

# Get cookie
COOKIE=`curl -X POST -d user='james-user@localhost' -d password=secret -d lmAuth=LDAP -d token=$TOKEN -H 'Accept: application/json' http://sso.example.com/ 2>/dev/null|perl -pe 's/.*"id":"(.*?)".*$/$1/'`

echo "Identified by LLNG"

# Get code
CODE=`curl -s -D - -o /dev/null -b lemonldap=$COOKIE 'http://sso.example.com/oauth2/authorize?response_type=code&client_id=james&scope=openid+profile+email&redirect_uri=http://test.sso.example.com&confirm=1' 2>/dev/null|grep Location|perl -pe 's/.*?code=(\w+).*?$/$1/'`

echo "Got a code in authorization flow ($CODE)"

# Get ID Token

ACCESS_TOKEN=`curl -X POST -d grant_type=authorization_code -d 'redirect_uri=http://test.sso.example.com' -d code=$CODE -u 'james:james' 'http://sso.example.com/oauth2/token' 2>/dev/null|perl -pe 's/^.*"access_token"\s*:\s*"(.*?)".*$/$1/'`

echo "Got an access_token"

if curl -H "Authorization: Bearer $ACCESS_TOKEN" http://sso.example.com/oauth2/userinfo 2>/dev/null| grep james >/dev/null; then
	echo "Access_token is valid"
else
	echo "ACCESS_TOKEN VERIFICATION FAILED"
fi

echo -n "Trying James: "

if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Bearer $ACCESS_TOKEN" test.sso.example.com:8090/oidc/jmap 2>/dev/null | grep uploadUrl >/dev/null; then
	echo "OK"
else
	echo "Not OK"
fi

# Logout

curl -s -D - -o /dev/null -b lemonldap=$COOKIE http://sso.example.com/?logout=1 >/dev/null 2>&1

if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Bearer $ACCESS_TOKEN" test.sso.example.com:8090/oidc/jmap 2>/dev/null | grep uploadUrl >/dev/null; then
	echo "LOGOUT FAILED"
else
	echo "Logout OK"
fi
