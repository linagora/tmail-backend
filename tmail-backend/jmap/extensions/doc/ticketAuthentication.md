# Ticket authentication

We added support for WebSocket transport for JMAP. According to RFC-8887, the authentication is performed using 
the `Authorization` header positioned upon the HTTP handshake when establishing the websocket connection.

However, browser native APIs do not support setting additional headers upon the websocket HTTP handshake, defeating this
strategy.

To mitigate that, we cary the authentication payload in the URL, using a query parameter.

For security reasons, use a single use, short-lived, ip scoped, secondary ticket as an authentication mechanism.

## Specification

An authenticated client request a temporary token

```
curl -XPOST https://james/jmap/websocket/ticket
```

Will return :

```
{
    "ticket":"WHATEVER",
    "clientAddress": "1.234.458.56",
    "generatedOn": "UTCDate",
    "validUntil": "UTCDate"
}
```

The client can revoke the ticket:

```
curl -XDELETE https://james/jmap/websocket/ticket/WHATEVER
```

`WHATEVER` being a UUID string

During the next 60s this ticket can be used a single time via query parameters as an authentication mechanism. Thus web-browser clients can use it to open a secure connection:

```
wss://james/jmap/js?ticket=UUID
```

The client discovers the ticket endpoints via an added capability in the session object:

```
"capabilities.com:linagora:params:jmap:ws:ticket": {
  "generationEndpoint":"http://localhost/jmap/ws/ticket",
  "revocationEndpoint":"http://localhost/jmap/ws/ticket"
}
```