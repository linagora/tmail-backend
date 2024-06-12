# Twake Mail Server


[![Join the chat at https://gitter.im/linagora/team-mail](https://badges.gitter.im/linagora/team-mail.svg)](https://gitter.im/linagora/team-mail?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Images docker](https://img.shields.io/badge/Images-docker-blue.svg)](https://hub.docker.com/r/linagora/tmail-backend)
[![Contributors](https://img.shields.io/github/contributors/linagora/tmail-backend?label=Contributors
)](
  https://github.com/linagora/tmail-backend/graphs/contributors
)
[![Issues](https://img.shields.io/github/issues/linagora/tmail-backend?label=Issues
)](https://github.com/linagora/tmail-backend/issues)
[![Documentation](https://img.shields.io/badge/Documentation-green.svg)](docs)
[![Android application](https://img.shields.io/badge/App-Android-blue.svg)](https://play.google.com/store/apps/dev?id=8845244706987756601)
[![Ios application](https://img.shields.io/badge/App-iOS-red.svg)](https://apps.apple.com/gr/developer/linagora/id1110867042)

<p align="center">
  <a href="https://github.com/linagora/twake-mail">
   <img src="https://github.com/linagora/tmail-backend/assets/146178981/594cb3ae-cef6-4dbd-b91e-814d4312b967" alt="Logo">
  </a>

  <h3 align="center">twake-mail.com</h3>

 <p align="center">
    <a href="https://twake-mail.com">Website</a>
    •
    <a href="https://beta.twake.app/web/#/rooms">View Demo</a>
    •
    <a href="https://github.com/linagora/tmail-backend/issues">Report Bug</a>
    •
    <a href="https://github.com/linagora/tmail-backend/milestones">Roadmap</a>
  </p>
</p>

---



This project adapts and enhance [Apache James project](https://james.apache.org) with a goal to provide a complete, enterpriseready collaborative email solution adapted to the rest of 
the [Linagora](https://linagora.com) eco-system.

Team-mail relies on [TeamMail Flutter](https://github.com/linagora/tmail-flutter) as a frontend.

Team-mail is developed with love by [Linagora](https://linagora.com).

## Useful links

 - We maintain a [CHANGELOG](CHANGELOG.md) and [upgrade instructions](upgrade-instructions.md)

 - [Building + Running the memory server](tmail-backend/apps/memory/README.md)

 - [Building + Running the distributed server](tmail-backend/apps/distributed/README.md)

 - [Project documentation](docs)

### Additional features

Apache James extensions includes:
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

<details>
  <summary>Read more...</summary>
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
</details>

## FAQ

### **What would your roadmap look like?**

<details>
  <summary>Read more...</summary>
By the end of 2023 we expect to add the following extensions:

 - Provide Archiving and automated actions
 - Provide a JMAP extension for thumbnails
 - Download all attachments at once
</details>

### **Your work is awesome! I would like to help you. What can I do?**

<details>
  <summary>Read more...</summary>
Thanks for the enthousiasm!

There are many ways to help us, and amongst them:

   - **Spread the word**: Tell people you like **Team Mail**, on social medias, via blog posts etc... 
   - **Give us feedbacks**... It's hard to make all good decisions from the first time. It is very likely we can benefit from *your* experience. Did you encountered annoying bugs? Do you think we are missing some features critical to you? Tell us in the [issues](https://github.com/linagora/tmail-backend/issues).
   - I can code! **I wanna help ;-)**. Wow thanks! Let's discuss your project together in the [issues](https://github.com/linagora/tmail-backend/issues) to get you on track!
</details>
