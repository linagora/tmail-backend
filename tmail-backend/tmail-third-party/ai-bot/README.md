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

<!-- Put the OpenAIMailet after LocalDelivery so the GPT reply would come after the asking question -->
    <mailet match="com.linagora.tmail.mailet.RecipientsContain=gpt@localhost" class="com.linagora.tmail.mailet.AIBotMailet">
        <apiKey>demo</apiKey>
        <botAddress>gpt@localhost</botAddress>
        <model>lucie-7b-instruct-v1.1</model>
        <baseURL>https://chat.lucie.exemple.com/v1</baseURL>
    </mailet>
</processor>
```
### Starting the AiBot

To start the AiBot [in memory], follow the steps below:

###  Clean Install

Run the following Maven command to perform a clean install, you can skip tests:

```bash
mvn clean install -DskipTests --am --pl :memory
```

To run AiBot in memory using Docker, use the following command:

```bash
docker run \
  --mount type=bind,source="/path/to/jwt_publickey",target="/root/conf/jwt_publickey" \
  --mount type=bind,source="/path/to/jwt_privatekey",target="/root/conf/jwt_privatekey" \
  --volume "/path/to/extensions-jars:/root/extensions-jars" \
  linagora/tmail-backend-memory
```

**Replace Paths**


/path/to/jwt_publickey: Replace with the actual path to your JWT public key file.

/path/to/jwt_privatekey: Replace with the actual path to your JWT private key file.

/path/to/extensions-jars: Replace with the path to your extensions jars directory.