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
            class="com.linagora.tmail.mailet.AIBotMailet">
        <apiKey>demo</apiKey>
        <gptAddress>gpt@tmail.com</gptAddress>
        <model>gpt-4o-mini</model>
    </mailet>
</processor>
```