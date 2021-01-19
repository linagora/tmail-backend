# OpenPaaS James

This project adapts and enhance [Apache James project](https://james.apache.org)

## Useful links

 - We maintain a [CHANGELOG](CHANGELOG.md) and [upgrade instructions](upgrade-instructions.md)

 - [Building + Running the memory server](openpaas-james/apps/memory/README.md)

- [Building + Running the distributed server](openpaas-james/apps/distributed/README.md)

## Additional features

Additional features includes:
 - JMAP PUSH over AMQP (WIP)
 - JMAP Filters/get and Filters/set (WIP)

## Building the project

This projects uses git submodules to track the latest branch of [the Apache James project](https://james.apache.org)

After cloning this repository, you need to init the `james-project` submodule:

```
git submodule init
git submodule update
```

It is possible that the `james-project` submodule is not in its latest state as well. If you want the latest changes
of the Apache James project, you can run as well:

```
git submodule update --remote
```

**Note**: Don't hesitate to push the latest state of the submodule in a commit if it was not up-to-date!

Then you can compile both `apache/james-project` and `linagora/openpaas-james` together.

```
mvn clean install -Dmaven.javadoc.skip=true
```

You can add the `-DskipTests` flag as well if you don't want to run the tests of the `apache/james-project`.