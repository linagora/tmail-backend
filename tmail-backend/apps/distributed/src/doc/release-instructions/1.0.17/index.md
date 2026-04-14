# Distributed Twake Mail backend 1.0.17 release instructions

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

### Tiering on thread table (Optional)

Depending on the environment you might need to enable some tiering option to save data on some Cassandra tables.
One of those is tiering on the `thread_2` Cassandra table.

If you need to turn it on, add in `jvm.properties`:

```
james.thread.window=90d
```

Adapt the value (which represents the threading window before the data gets dropped from the table) to the wanted one.

Ask the java backend team first if you are unsure if this option needs to be enabled or not for the concerned environment.

Also, you might want to tier existing data manually if needed. 

### RabbitMQ event bus partitioning (Optional)

Allow to enable partitioning on RabbitMQ event bus queues, to better leverage CPU resource on the RabbitMQ side.

Note that it is only possible if you are using the RabbitMQ and Redis event bus with Twake Mail.

If you wish to enable it, in `rabbitmq.properties`:

```
event.bus.partition.count=4
```

### Boring SSL (Mandatory)

Add in `jvm.properties` configuration file:

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

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:distributed-1.0.17`

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.17
