# LLM Mail Prioritization Classifier Listener

The `LlmMailPrioritizationClassifierListener` is an event-driven listener that automatically analyzes incoming emails
using a Large Language Model (LLM) to determine if they require user action. 

When an email is classified as needing action, it is automatically tagged with the `needs-action` flag.

## Configuration

Example:

```xml
<listener>
   <class>com.linagora.tmail.listener.rag.LlmMailPrioritizationClassifierListener</class>
   <configuration>
      <filter>
         <and>
            <isInINBOX/>
            <isMainRecipient/>
            <not>
               <isAutoSubmitted/>
            </not>
         </and>
      </filter>
      <systemPrompt>
         You are an email assistant that determines if emails require user action.
         Consider: urgency, deadlines, direct questions, and required responses.
         Respond only with 'YES' (needs action) or 'NO' (informational).
      </systemPrompt>
      <maxBodyLength>5000</maxBodyLength>
   </configuration>
</listener>
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `systemPrompt` | String | See DEFAULT_SYSTEM_PROMPT | Custom system prompt sent to the LLM |
| `maxBodyLength` | Integer | 5000 | Maximum length of email body to send to LLM |
| `filter` | Filter DSL | ALL | Filter rules to determine which emails to process |

#### Filter System

The filter system allows you to configure which emails should be processed by the LLM. If no filter is configured, all delivered emails are processed (equivalent to `MessageFilter.ALL`).

##### Heuristic Filters

**1. InboxFilter (`isInINBOX`)**
- Matches only emails delivered to the user's INBOX
- Useful to skip processing for emails in other folders

**2. MainRecipientFilter (`isMainRecipient`)**
- Matches only when the user is in the `To` field (not just Cc or Bcc)
- Helps prioritize emails directly addressed to the user

**3. AutoSubmittedFilter (`isAutoSubmitted`)**
- Detects auto-generated emails (per RFC 3834)
- Checks for `Auto-Submitted` header with values other than `no`
- Typically used with `NOT` to exclude automated messages

**4. HasHeaderFilter (`hasHeader`)**
- Matches emails with a specific header
- Optionally checks for a specific header value
- Useful for priority headers like `X-Priority`, `Importance`, etc.

##### Logical Operators

**1. AndFilter (`and`)**
- Matches when ALL child filters match
- Example: Email must be in inbox AND user must be main recipient

**2. OrFilter (`or`)**
- Matches when AT LEAST ONE child filter matches
- Example: Email has priority header OR user is main recipient

**3. NotFilter (`not`)**
- Inverts the result of the child filter
- Example: Exclude auto-submitted messages

### Default System Prompt

The default system prompt instructs the LLM to:
- Classify emails as needing action or not
- Consider urgency, deadlines, and required responses
- Distinguish between informational emails and actionable ones
- Respond with only `true` or `false`

## User Settings

Users can enable or disable the AI needs-action feature through JMAP settings:

- **Setting Key**: `ai.needs-action.enabled`
- **Values**: `true` (enabled) or `false` (disabled)
- **Default**: `false` (disabled by default in order to collect explicit user consent)

When disabled, the listener will not process any emails for that user, regardless of filter configuration.

## Best Practices

### Recommended Configurations

1. **Production Environment**:

```xml
   <filter>
       <and>
           <isInINBOX/>
           <or>
             <isMainRecipient>true</isMainRecipient>
             <!-- user might be mentioned in a list, it may be important to process -->
             <hasHeader name="List-Id"/>
           </or>
           <not>
             <and>
               <hasHeader>List-Unsubscribe</hasHeader>
                <not><hasHeader>List-Id</hasHeader></not>
             </and>
           </not> 
           <not><isAutoSubmitted/></not>
       </and>
   </filter>
```

### Security Considerations

1. **Content Privacy**: Email content is sent to the configured LLM provider
2. **User Consent**: Ensure users are aware and have consented to LLM processing
3. **Settings Respect**: Always honor user preference settings