# Twake Mail backend PostgreSQL server

## Run

The CI publishes this app as `linagora/tmail-backend:postgresql-branch-master` (and
`linagora/tmail-backend:postgresql-<release>` for a release tag). Both compose files of this folder
use that image, so they need no local build:

* `docker-compose.yml` - PostgreSQL only, the minimal single node setup.
* `docker-compose-distributed.yml` - PostgreSQL plus OpenSearch, RabbitMQ and S3 object storage.

Generate a JWT key pair first and point `RSA_PUBLICKEY_PATH` / `RSA_PRIVATEKEY_PATH` at it:

```
openssl genrsa -out jwt_privatekey 4096
openssl rsa -in jwt_privatekey -pubout > jwt_publickey

export RSA_PUBLICKEY_PATH=$PWD/jwt_publickey
export RSA_PRIVATEKEY_PATH=$PWD/jwt_privatekey
docker compose up -d
```

To run your own build instead:

```
mvn clean install -DskipTests
mvn compile com.google.cloud.tools:jib-maven-plugin:3.4.3:dockerBuild -pl apps/postgres
```

This produces a *local* image named `linagora/tmail-backend-postgresql-experimental:latest`.

## Administration Operations
## Clean up data

To clean up some data on the specific TMail data structures, that will be redundant again after a long time, you can execute the SQL queries `clean_up_data_tmail.sql`.

The data that in:
- `label_change` table
- `ticket` table

Note that the `clean_up_data_tmail.sql` should be merged with [the SQL clean up script on Apache James](https://github.com/apache/james-project/blob/postgresql/server/apps/postgres-app/clean_up.sql) to clean data on James tables as well.
