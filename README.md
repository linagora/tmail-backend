# Team-mail backend server

[![Join the chat at https://gitter.im/linagora/team-mail](https://badges.gitter.im/linagora/team-mail.svg)](https://gitter.im/linagora/team-mail?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adapts and enhance [Apache James project](https://james.apache.org)

## Useful links

 - We maintain a [CHANGELOG](CHANGELOG.md) and [upgrade instructions](upgrade-instructions.md)

 - [Building + Running the memory server](tmail-backend/apps/memory/README.md)

 - [Building + Running the distributed server](tmail-backend/apps/distributed/README.md)

## Additional features

Additional features includes:
 - Email filtering
 - Encrypted mailboxes
 - Team mailboxes
 - Rate limiting
 - Contacts autocomplete
 - Email forwarding
 - Push subscription with Firebase Cloud Messaging
 - Restore deleted emails
 - Labels
 - Settings

[More detail...](docs/modules/ROOT/pages/tmail-backend/features/index.adoc)

## Building the project

### Manual building

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

Then you can compile both `apache/james-project` and `linagora/tmail-backend` together.

```
mvn clean install -Dmaven.javadoc.skip=true
```

You can add the `-DskipTests` flag as well if you don't want to run the tests of the `apache/james-project`.

### Building with a local jenkins runner

You can use a custom local jenkins runner with the `Jenkinsfile` at the root of this project to build the project. 
This will automatically do for you:

* checkout and compile latest code of Apache James project alongside `tmail-backend`
* generate docker images for `memory` and `distributed` flavors of the project
* launch unit, integration and deployment tests on `tmail-backend`

To launch it you need to have docker installed. From the root of this project, you can build the 
Jenkins runner locally yourself:

```
docker build -t local-jenkins-runner dockerfiles/jenkins-runner
```

And then you need to launch it with the Jenkinsfile:

```
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v $(pwd)/dockerfiles/jenkins-runner/Jenkinsfile:/workspace/Jenkinsfile local-jenkins-runner
```

If you don't want the build to redownload everytime all the maven dependencies (it can be heavy) you can mount
your local maven repository as a volume by adding `-v $HOME/.m2:/root/.m2` to the above command.
