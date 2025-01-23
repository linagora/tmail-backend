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

package com.linagora.tmail.webadmin.contact.aucomplete;

import static org.apache.james.mailbox.store.mail.MessageMapper.UNLIMITED;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mime4j.field.address.LenientAddressParser;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask.RunningOptions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ContactIndexingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContactIndexingService.class);

    private static class BloomFilterConfig {
        static final Funnel<CharSequence> BLOOM_FILTER_FUNNEL = Funnels.stringFunnel(StandardCharsets.US_ASCII);
        static final int EXPECTED_CONTACT_COUNT_DEFAULT = 100_000;
        static final double ASSOCIATED_PROBABILITY_DEFAULT = 0.01;
    }

    public static class ExtractContactException extends RuntimeException {

        public ExtractContactException(String message) {
            super(message);
        }

        public ExtractContactException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final Set<String> CONTACT_HEADER_FIELDS = Set.of("to", "cc", "bcc");

    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final EmailAddressContactSearchEngine contactListPublisher;
    private final ContactAddIndexingProcessor contactAddIndexingProcessor;

    @Inject
    public ContactIndexingService(UsersRepository usersRepository,
                                  MailboxManager mailboxManager,
                                  MailboxSessionMapperFactory mapperFactory,
                                  EmailAddressContactSearchEngine contactListPublisher,
                                  ContactAddIndexingProcessor contactAddIndexingProcessor) {

        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.contactListPublisher = contactListPublisher;
        this.contactAddIndexingProcessor = contactAddIndexingProcessor;
    }

    public Mono<Task.Result> indexAllContacts(RunningOptions runningOptions, ContactIndexingContext context) {
        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.usersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> indexContactsPerUser(username, context)))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Task.Result.COMPLETED));
    }

    private Mono<Task.Result> indexContactsPerUser(Username username, ContactIndexingContext context) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        MessageMapper messageMapper = mapperFactory.getMessageMapper(mailboxSession);
        String bloomFilterSalt = UUID.randomUUID().toString();

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(username, DefaultMailboxes.SENT), mailboxSession))
            .onErrorResume(MailboxNotFoundException.class, error -> Mono.empty())
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .doOnNext(mailbox -> context.increaseProcessedUsersCount())
            .filterWhen(hasMessagesInSentMailbox(messageMapper))
            .flatMap(mailbox -> populatedBloomFilter(username, bloomFilterSalt)
                .flatMap(bloomFilter -> indexContactsPerMailbox(bloomFilter, mailbox, messageMapper, bloomFilterSalt, context)))
            .onErrorResume(error -> {
                LOGGER.error("Error while indexing contacts for user {}", username, error);
                return Mono.just(Task.Result.PARTIAL);
            })
            .doOnNext(result -> {
                if (result == Task.Result.PARTIAL) {
                    context.addToFailedUsers(username.asString());
                }
            });
    }

    private Mono<Task.Result> indexContactsPerMailbox(BloomFilter<CharSequence> bloomFilter,
                                                      Mailbox mailbox,
                                                      MessageMapper messageMapper,
                                                      String bloomFilterSalt,
                                                      ContactIndexingContext context) {
        return messageMapper.findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.HEADERS, UNLIMITED)
            .concatMap(mailboxMessage -> Flux.fromIterable(extractContact(mailboxMessage)))
            .filter(contact -> !bloomFilter.mightContain(bloomFilterSalt + contact.address().asString()))
            .concatMap(contact -> Mono.from(contactAddIndexingProcessor.process(mailbox.getUser(), contact))
                .then(Mono.fromCallable(() -> {
                    context.increaseIndexedContactsCount();
                    return bloomFilterPut(bloomFilter, bloomFilterSalt, contact);
                }))
                .map(any -> Task.Result.COMPLETED)
                .onErrorResume(error -> Mono.fromCallable(() -> {
                    LOGGER.error("Error while indexing contact {} for user {}", contact, mailbox.getUser(), error);
                    context.increaseFailedContactsCount();
                    return Task.Result.PARTIAL;
                })))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Task.Result.COMPLETED));
    }

    private static Function<Mailbox, Publisher<Boolean>> hasMessagesInSentMailbox(MessageMapper messageMapper) {
        return mailbox -> messageMapper.getMailboxCountersReactive(mailbox)
            .map(MailboxCounters::getCount)
            .map(counter -> counter > 0)
            .switchIfEmpty(Mono.just(false));
    }

    private List<ContactFields> extractContact(MailboxMessage mailboxMessage) {
        return Throwing.supplier(() -> ResultUtils.createHeaders(mailboxMessage))
            .get()
            .stream()
            .filter(header -> CONTACT_HEADER_FIELDS.contains(header.getName().toLowerCase(Locale.US)))
            .flatMap(header -> extractContactFromHeader(header).stream())
            .toList();
    }

    private List<ContactFields> extractContactFromHeader(Header header) {
        return LenientAddressParser.DEFAULT.parseAddressList(header.getValue()).stream()
            .filter(address -> address instanceof org.apache.james.mime4j.dom.address.Mailbox)
            .map(address -> (org.apache.james.mime4j.dom.address.Mailbox) address)
            .map(mailbox -> {
                try {
                    return ContactFields.of(new MailAddress(mailbox.getAddress()), mailbox.getName());
                } catch (AddressException e) {
                    throw new ExtractContactException("Could not extract contact from header " + header, e);
                }
            })
            .toList();
    }

    private Mono<BloomFilter<CharSequence>> populatedBloomFilter(Username username, String salt) {
        return Mono.fromCallable(() -> BloomFilter.create(
                BloomFilterConfig.BLOOM_FILTER_FUNNEL,
                BloomFilterConfig.EXPECTED_CONTACT_COUNT_DEFAULT,
                BloomFilterConfig.ASSOCIATED_PROBABILITY_DEFAULT))
            .flatMap(bloomFilter ->
                Flux.from(contactListPublisher.list(AccountId.fromUsername(username)))
                    .map(ref -> bloomFilterPut(bloomFilter, salt, ref.fields()))
                    .then()
                    .thenReturn(bloomFilter));
    }

    private boolean bloomFilterPut(BloomFilter<CharSequence> bloomFilter, String salt, ContactFields contact) {
        return bloomFilter.put(salt + contact.address().asString());
    }
}
