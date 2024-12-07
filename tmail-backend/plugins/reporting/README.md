# TMail Reports Plugin

This plugin allows you to collect some info about the emails was processed by James mailet
and provide webadmin routes to access these reports.

The email information collected is:

- subject
- sender
- recipient
- date

## How to use

### Build the plugin

```shell
mvn clean install -DskipTests
```

The jar file is located in the target folder (`./target/tmail-report-plugin-jar-with-dependencies.jar`)

### Add the plugin to the TMail server

- Moving the jar plugin to the TMail server (default path: `/root/extensions-jars/`)
- Declare the plugin in the `extensions.properties` file

```properties
guice.extension.module=com.linagora.tmail.TMailReportModule
guice.extension.startable=com.linagora.tmail.cassandra.CassandraMailReportGenerator
```

### Mailet configuration
- Customize the `mailetcontainer.xml` by adding the `TMailReceivedReportRecorder` mailet
  eg:

```xml

<mailet match="HasMailAttribute=senderIsLocal" class="com.linagora.tmail.mailet.TMailReceivedReportRecorder">
    <kind>SENT</kind>
    <onMailetException>ignore</onMailetException>
</mailet>
```

The mailet configuration requires the following parameters:
- `kind`: the kind of the report, which can be `SENT` or `RECEIVED`.


### Webadmin routes configuration

Declare the webadmin routes extension in the `webadmin.properties` file

```properties
extensions.routes=com.linagora.tmail.route.TMailReportsRoute
```

### Access the webadmin routes to see the reports

- URL to access the reports: `http://{webadminBaseUrl}/mu/reports/mails`
- Query parameters:
  - `duration`: mandatory, duration (support many time units, default in seconds), only messages between `now` and `now - duration` are reported. These inputs represent the same duration: `1d`, `1day`, `86400 seconds`, `86400`...
- Example: `http://localhost:8000/mu/reports/mails?duration=1d`
- The response is a JSON object with the following structure:

```json
[
  {
    "kind": "Sent",
    "subject": "test",
    "sender": "bob@domain.tld",
    "recipient": "cedric@domain.tld",
    "date": "2024-12-06T11:37:23.804Z"
  },
  {
    "kind": "Received",
    "subject": "test",
    "sender": "bob@domain.tld",
    "recipient": "cedric@domain.tld",
    "date": "2024-12-06T11:37:23.804Z"
  }
]
```

The docker-compose sample file: [docker-compose.yml](docker-compose.yml)