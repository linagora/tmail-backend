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

### Boring SSL (Mandatory)

Add in `jvm.properties` configuration file:

```
james.tcnative.enabled=true
```

## Tmail deployment

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:postgresql-1.0.17`

## References

* Official Twake Mail release notes: [TBD]
