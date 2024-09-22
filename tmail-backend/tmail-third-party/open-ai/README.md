# OpenAI third party

We provide a mailet extension for OpenAI integration. The goal is to provide a mail bot at e.g. `gpt@example.com` that replies to users' questions.

To use this extension, please plug the external jar `tmail-open-ai-jar-with-dependencies.jar` into your TMail application.

Sample `mailetcontainer.xml` configuration:

```xml

<processor state="local-delivery" enableJmx="true">
    ...
    <mailet match="All" class="com.linagora.tmail.mailets.TmailLocalDelivery">
        <consume>false</consume>
    </mailet>

    <!-- Put the MailBotMailet after LocalDelivery so the GPT reply would come after the asking question -->
    <mailet match="com.linagora.tmail.mailet.RecipientsContain=gpt@tmail.com"
            class="com.linagora.tmail.mailet.MailBotMailet">
        <apiKey>demo</apiKey>
        <gptAddress>gpt@tmail.com</gptAddress>
        <model>gpt-4o-mini</model>
    </mailet>
</processor>
```