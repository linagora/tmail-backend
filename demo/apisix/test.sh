APISIX_URL="127.0.0.1:9080"
curl http://$APISIX_URL/ping

# Expected response: APISIX already up!

set -eux

## Get Access Token
ACCESS_TOKEN=`curl -L 'http://sso.example.com/oauth2/token?lmAuth=LDAP' \
-H 'Content-Type: application/x-www-form-urlencoded' \
-d 'grant_type=password' \
-d 'scope=openid%20profile' \
-d 'client_id=james_apisix' \
-d 'client_secret=james_apisix' \
-d 'username=james-user%40tmail.com' \
-d 'password=secret' 2>/dev/null|perl -pe 's/^.*"access_token"\s*:\s*"(.*?)".*$/$1/'`

# Try to upload and get blobId
BLOB_ID=`curl -L 'http://apisix.example.com:9080/oidc/upload/5c9114dc1e97a68ba79b06a892d8d87153924deeba06b3fc10e7102dc18c481b' \
-H 'Accept: application/json; jmapVersion=rfc-8621' \
-H 'Content-Type: image/jpeg' \
-H 'Authorization: Bearer '$ACCESS_TOKEN \
-d './conf/apisix.yaml' 2>/dev/null|perl -pe 's/^.*"blobId"\s*:\s*"(.*?)".*$/$1/'`

# Try to download
if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Bearer $ACCESS_TOKEN" http://apisix.example.com:9080/oidc/download/5c9114dc1e97a68ba79b06a892d8d87153924deeba06b3fc10e7102dc18c481b/$BLOB_ID 2>/dev/null | grep apisix.yaml >/dev/null; then
	echo "Download OK"
else
	echo "Download Not OK"
fi

# X-JMAP-PREFIX test
if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Basic amFtZXMtdXNlckB0bWFpbC5jb206c2VjcmV0" http://apisix.example.com:9080/jmap/session 2>/dev/null | grep ws://apisix.example.com:9080/jmap/ws >/dev/null; then
	echo "X-JMAP-PREFIX work"
else
	echo "X-JMAP-PREFIX Not work"
fi

# X-JMAP-WEBSOCKET-PREFIX test
if curl -v -H 'Accept: application/json; jmapVersion=rfc-8621' -H "Authorization: Basic amFtZXMtdXNlckB0bWFpbC5jb206c2VjcmV0" http://apisix.example.com:9080/jmap/session 2>/dev/null | grep "\"http://apisix.example.com:9080/jmap\"" >/dev/null; then
	echo "X-JMAP-WEBSOCKET-PREFIX work"
else
	echo "X-JMAP-WEBSOCKET-PREFIX Not work"
fi