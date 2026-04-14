# Postgres Twake Mail backend 1.0.17 release instructions

## Configuration changes

### Default signature provisioning (Optional)

It can be interesting for example to provide a default signature to newly created users, alongside the identity provisioned.

If you already declared the `IdentityProvisionListener` in `listeners.xml` and you wish to have this, add the following:

```xml
<listeners>
    <listener>
        <class>com.linagora.tmail.james.jmap.event.IdentityProvisionListener</class>
        <configuration>
            [...]
            <defaultText>
                <defaultLanguage>en</defaultLanguage>
                <en>
                    <textSignature>Best regards</textSignature>
                    <htmlSignature><![CDATA[<p>Best regard</p>]]></htmlSignature>
                </en>
            </defaultText>
        </configuration>
    </listener>
</listeners>
```

Adapt the signatures to your needs. 

### RabbitMQ event bus partitioning (Optional)

Allow to enable partitioning on RabbitMQ event bus queues, to better leverage CPU resource on the RabbitMQ side.

Note that it is only possible if you are using the RabbitMQ and Redis event bus with Twake Mail.

If you wish to enable it, in `rabbitmq.properties`:

```
event.bus.partition.count=4
```

### Boring SSL

This proves to provide better SSL handshake performance overall, and is recommended to enable.

If you want so, add in `jvm.properties` configuration file:

```
james.tcnative.enabled=true
```

### Webadmin granular access control

In WebAdmin, two new configurations have been added to manage access in a more granular way:

- `password.readonly`: If provided, only GET and HEAD request are allowed, others operations are rejected.
- `password.nodelete`: If provided, all operations except DELETE are permitted.

If you need granular password control in WebAdmin, add in `webadmin.properties`:

```
# Password authentication settings
# Configure one or more passwords (comma separated) for WebAdmin authentication
password=secret1,secret2

# Read-only passwords - only allow GET requests
# These passwords can only perform read operations
password.readonly=aaa,bbb

# No-delete passwords - allow all operations except DELETE
# These passwords can perform read and write operations but not delete
password.nodelete=ccc,ddd
```

## Tmail deployment

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:postgresql-1.0.17`

## References

* Official Twake Mail release notes: [TBD]
