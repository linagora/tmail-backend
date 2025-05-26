# AIBot third party

We provide a mailet extension for OpenAI-compatible APIs integration. The goal is to provide a mail bot at e.g. `gpt@example.com` that replies to users' questions.

To use this extension, please plug the external jar `tmail-ai-bot-jar-with-dependencies.jar` into your TMail application.

Sample `mailetcontainer.xml` configuration:

```xml
<processor state="local-delivery" enableJmx="true">
    ...
    <mailet match="All" class="com.linagora.tmail.mailets.TmailLocalDelivery">
        <consume>false</consume>
    </mailet>
    <!-- Put the AIBotMailet after LocalDelivery so the GPT reply would come after the asking question -->
    <mailet match="com.linagora.tmail.mailet.RecipientsContain=gpt@tmail.com"
            class="com.linagora.tmail.mailet.AIBotMailet"/>
</processor>
```

# AI Bot Configuration

## Overview

This document provides the setup instructions for configuring the AI Bot, specifically for the email response agent and suggestion agent. The configuration ensures a seamless setup by centralizing all settings in a single file.

---
## Configuration File

All AI Bot configuration details should be stored in the `ai.properties` file, located in the `conf` directory. Below is an example of the configuration format:

Sample `ai.properties` configuration:
```properties
apiKey=demo
botAddress=gpt@localhost
model=lucie
baseURL=https://chat.lucie.example.com
```

## How Configuration Works

### Centralized Configuration

- The `ai.properties` file contains all key configuration items required for the AI Bot.
- These properties are then mapped to the POJO `AiBot` properties.

### Integration with AiBot

- The `ai.properties` file is used to configure the AI Bot in the `aibot` configuration class.
- This approach replaces the need to configure the AI Bot using the `mailetConfig`.

### Starting the AiBot

To start the AiBot [in memory], follow the steps below:

###  Clean Install

Run the following Maven command to compile the extension JAR:


```bash
mvn clean install -DskipTests --am --pl :tmail-ai-bot
```

To run AiBot in memory using Docker, use the following command:

```bash
docker run \
  --mount type=bind,source="$PWD/sample_conf/jwt_publickey",target="/root/conf/jwt_publickey" \
  --mount type=bind,source="$PWD/sample_conf/jwt_privatekey",target="/root/conf/jwt_privatekey" \
  --mount type=bind,source="$PWD/sample_conf/mailetcontainer.xml",target="/root/conf/mailetcontainer.xml" \
  --mount type=bind,source="$PWD/sample_conf/ai.properties",target="/root/conf/ai.properties" \
  --mount type=bind,source="$PWD/sample_conf/extensions.properties",target="/root/conf/extensions.properties" \
  --volume "$PWD/target/tmail-ai-bot-jar-with-dependencies.jar:/root/libs/tmail-ai-bot-jar-with-dependencies.jar" \
  linagora/tmail-backend-memory
```
