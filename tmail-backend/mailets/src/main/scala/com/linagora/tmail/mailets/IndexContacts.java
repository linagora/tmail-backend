/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.mailets;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.transport.mailets.ContactExtractor;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.TmailContactUserAddedEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p><b>IndexContacts</b> allows indexing user contacts locally by dispatching a <code>ContactUserAddedEvent</code> to the <code>eventBus</code>,
 * in order to index contacts asynchronously.</p>
 *
 * <p>This mailet takes as input an attribute containing the user and the contacts to be added to that user.
 * The input should be in JSON format, and compliant with the format of {@link ContactExtractor.ExtractedContacts}.</p>
 *
 * <p>Here is the JSON format:</p>
 * <pre><code>
 * {
 *   "userEmail" : "sender@james.org",
 *   "emails" : [ "to@james.org", "cc@james.org" ]
 * }
 * </code></pre>
 *
 * <p>Sample configuration:</p>
 * <pre><code>
 * &lt;mailet match="All" class="IndexContacts"&gt;
 *   &lt;attribute&gt;ExtractedContacts&lt;/attribute&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class IndexContacts extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexContacts.class);
    private static final ImmutableSet<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();
    private static final String EMPTY_STRING = "";
    private static final String TMAIL_EVENT_BUS_INJECT_NAME = "TMAIL_EVENT_BUS";

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new GuavaModule());

    private final EventBus eventBus;

    private AttributeName attributeName;

    @Inject
    public IndexContacts(@Named(TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void init() throws MessagingException {
        attributeName = Optional.ofNullable(getInitParameter("attribute"))
            .filter(StringUtils::isNotBlank)
            .map(AttributeName::of)
            .orElseThrow(
                () -> new MailetException("No value for `attribute` parameter was provided."));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        readUserContactsJSONFromAttribute(mail)
            .map(Throwing.function(this::parseUserContacts))
            .flatMap(Throwing.function(this::dispatchContactsAddedEvent))
            .onErrorComplete()
            .doOnError(e -> LOGGER.warn("Failed to index contacts", e))
            .block();
    }

    private Mono<String> readUserContactsJSONFromAttribute(Mail mail) {
        return Mono.justOrEmpty(mail.getAttribute(attributeName))
            .map(attribute -> attribute.getValue().asAttributeValueOf(String.class))
            .flatMap(Mono::justOrEmpty)
            .map(AttributeValue::value);
    }

    private Flux<ContactFields> asContactFields(ImmutableList<String> emails) {
        return Flux.fromIterable(emails)
            .map(Throwing.function(this::emailAttributeToContactFields))
            .onErrorContinue((e, object) -> LOGGER.warn("Failed to parse email address", e));
    }

    private ContactFields emailAttributeToContactFields(String emailAttributeValue) throws MailetException {
        return switch (LenientAddressParser.DEFAULT.parseAddress(emailAttributeValue)) {
            case Mailbox mailbox -> ContactFields.of(mailbox);
            case null, default ->
                throw new MailetException("Can not parse Mailbox address from: " + emailAttributeValue);
        };
    }

    private ContactExtractor.ExtractedContacts parseUserContacts(String attributeValue) throws MailetException {
        try {
            return objectMapper.readValue(attributeValue, ContactExtractor.ExtractedContacts.class);
        } catch (JsonProcessingException e) {
            throw new MailetException("Failed to parse user contacts JSON from mail attribute: " + attributeValue, e);
        }
    }

    private Mono<Void> dispatchContactsAddedEvent(ContactExtractor.ExtractedContacts extractedContacts)
        throws MailetException {
        try {
            MailAddress userMailAddress = new MailAddress(extractedContacts.userEmail());
            return asContactFields(extractedContacts.emails())
                .flatMap(contact ->
                    eventBus.dispatch(
                        new TmailContactUserAddedEvent(
                            Event.EventId.random(),
                            Username.fromMailAddress(userMailAddress),
                            contact),
                        NO_REGISTRATION_KEYS)
                ).collectList()
                .then();
        } catch (AddressException e) {
            throw new MailetException("Failed to parse user mail address", e);
        }
    }

}
