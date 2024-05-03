# TMail apisix plugin runner 

The plugin for handler revoked token:
- Provide the http server to receive logout_token from lemonLdap when has event logout channel
- Pre-filter for Apisix by checking token was revoked or not

## How to compile it 

- Java 11
- Run maven command:
```
mvn clean package
```

## How to use it
- Build Apisix docker image that support Java plugin runner : `docker build -t linagora/apisix:3.2.0-debian-javaplugin .`
- Configuration the plugin in `/usr/local/apisix/conf/config.yaml` of apisix container
    - Append: 
```yaml
ext-plugin:
  path_for_test: /tmp/runner.sock
  cmd: ['java', '-jar', '-Xmx1g', '-Xms1g', '/usr/local/apisix/token_revoker_plugin.jar']
```
- Use `TokenRevokedFilter` Plugin for special route:
  - Eg: for `/ping` route
```yaml
- routes:
    -
      id: test    
      uri: /ping
      plugins:
        ext-plugin-pre-req:
          conf:
            - name: TokenRevokedFilter
              value: '{"enable":"feature"}'
```

## Choice revoked token repository
- Be default the plugin use in-memory for storage. 
### Redis
- Provide the properties
    - `redis.url` (or environment `REDIS_URL`): [String] the redis url. For several url, use `,` character for separate
    - `redis.password` (or environment `REDIS_PASSWORD`): [String] the redis password (Optional).
    - `redis.cluster.enabled` (or environment `REDIS_CLUSTER_ENABLE`): [Boolean]. `true` for redis master-replicas topology, 
    `false` for standalone topology
    - `redis.timeout` (or environment `REDIS_TIMEOUT`): [Integer] the timeout for redis command when client send to Redis server. Default to 5000 (5 seconds)
    - `redis.ignoreErrors` (or environment `REDIS_IGNORE_ERRORS`): [Boolean]. This configuration determines whether errors from Redis should be ignored or not. If set to `true`, when the application is unable to connect to Redis, the revoked token will not be checked. Default to true

Eg: 
```yaml
redis:
  url: redis.example.com:6379
  password: secret1
  cluster.enable: false 
  timeout: 5000
  ignoreErrors: true
```
or
```yaml
redis:
  url: redis-master.example.com:6379,redis-replica1.example.com:6379
  password: secret1
  cluster.enable: true
  timeout: 5000
  ignoreErrors: true
```