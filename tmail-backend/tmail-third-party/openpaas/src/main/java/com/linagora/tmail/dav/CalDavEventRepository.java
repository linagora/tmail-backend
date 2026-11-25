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

package com.linagora.tmail.dav;

import static com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults.AttendanceResult;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.CalendarEventCancelledException;
import com.linagora.tmail.james.jmap.CalendarEventNotFoundException;
import com.linagora.tmail.james.jmap.CalendarEventRepository;
import com.linagora.tmail.james.jmap.MessagePartBlobId;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier.AttendeeNotFoundException;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarUidField;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;
import com.linagora.tmail.james.jmap.model.RecurrenceIdField;
import com.linagora.tmail.mailet.AlignmentMode;
import com.linagora.tmail.mailet.ITIPSenderAlignment;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventRepository implements CalendarEventRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavEventRepository.class);

    public enum FreeBusyStatus {
        BUSY,
        FREE;

        static FreeBusyStatus isBusy(boolean isBusy) {
            if (isBusy) {
                return BUSY;
            }
            return FREE;
        }
    }

    // Declared by decreasing relevance: the most relevant outcome among the calendar objects of an event wins
    private enum UpdateOutcome {
        UPDATED,
        CANCELLED,
        // The user does not attend this copy of the event, eg one held by the calendar of a colleague the user subscribed to
        NOT_ATTENDEE,
        SKIPPED;

        static UpdateOutcome mostRelevant(UpdateOutcome outcome1, UpdateOutcome outcome2) {
            if (outcome1.compareTo(outcome2) <= 0) {
                return outcome1;
            }
            return outcome2;
        }

        Mono<Void> asResult(Username username, CalendarUidField eventUid) {
            return switch (this) {
                case UPDATED -> Mono.empty();
                case CANCELLED -> Mono.error(new CalendarEventCancelledException(username, eventUid));
                case NOT_ATTENDEE -> Mono.error(new AttendeeNotFoundException(username.asString()));
                case SKIPPED -> Mono.error(new CalendarEventNotFoundException(username, eventUid));
            };
        }
    }

    private final DavClient davClient;
    private final SessionProvider sessionProvider;
    private final DavUserProvider davUserProvider;
    private final CalendarResolver calendarResolver;
    private final MessageIdManager messageIdManager;
    private final MessageId.Factory messageIdFactory;
    private final AliasReverseResolver aliasReverseResolver;

    public CalDavEventRepository(DavClient davClient,
                                 SessionProvider sessionProvider,
                                 DavUserProvider davUserProvider,
                                 CalendarResolver calendarResolver,
                                 MessageIdManager messageIdManager,
                                 MessageId.Factory messageIdFactory,
                                 AliasReverseResolver aliasReverseResolver) {
        this.davClient = davClient;
        this.sessionProvider = sessionProvider;
        this.davUserProvider = davUserProvider;
        this.calendarResolver = calendarResolver;
        this.messageIdManager = messageIdManager;
        this.messageIdFactory = messageIdFactory;
        this.aliasReverseResolver = aliasReverseResolver;
    }

    @Override
    public Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, List<BlobId> blobIds) {
        return davUserProvider.provide(username)
            .flatMapMany(davUser -> Flux.fromIterable(blobIds)
                .flatMap(blobId -> getAttendanceStatus(davUser, blobId), DEFAULT_CONCURRENCY))
            .reduce(CalendarEventAttendanceResults::merge);
    }

    private Mono<CalendarEventAttendanceResults> getAttendanceStatus(DavUser davUser, BlobId blobId) {
        return fetchCalendarObject(davUser, blobId)
            .flatMap(calendarEventParsed -> addressesOf(davUser.username())
                // As for the update: the user may be listed under one of its aliases, its username wins
                .flatMap(addresses -> Mono.justOrEmpty(addresses.stream()
                    .flatMap(address -> OptionConverters.toJava(calendarEventParsed.getAttendanceStatus(address)).stream())
                    .findFirst()))
                .flatMap(attendanceStatus -> freeBusyQuery(davUser, calendarEventParsed)
                    .map(freeBusyStatus -> EventAttendanceStatusEntry.of(blobId, attendanceStatus, FreeBusyStatus.FREE.equals(freeBusyStatus)))))
            .map(AttendanceResult()::done)
            .switchIfEmpty(Mono.just(AttendanceResult().notFound(blobId)))
            .onErrorResume(error -> Mono.just(AttendanceResult().notDone(blobId, error)));
    }

    private Mono<CalendarEventParsed> fetchCalendarObject(DavUser davUser, BlobId blobId) {
        MailboxSession session = sessionProvider.createSystemSession(davUser.username());

        return Mono.from(calendarResolver.resolveRequestCalendar(blobId, session, OptionConverters.toScala(Optional.empty())))
            .flatMap(requestCalendar -> {
                DavUid eventUid = DavUid.fromCalendarUidField(CalendarUidField.getEventUidFromCalendar(requestCalendar));
                Optional<String> recurrenceId = RecurrenceIdField.getRecurrenceIdAsString(requestCalendar);
                return davClient.caldav(davUser.username()).getCalendarObject(davUser, eventUid)
                    .switchIfEmpty(Mono.error(() -> new DavClientException("Unable to find any calendar objects containing VEVENT with id '%s'".formatted(eventUid))))
                    .map(davCalendarObject -> davCalendarObject.parse(recurrenceId));
            });
    }

    public Mono<FreeBusyStatus> freeBusyQuery(DavUser davUser, CalendarEventParsed calendarEventParsed) {
        Function<FreeBusyResponse, Boolean> isBusyFunction = freeBusyResponse -> freeBusyResponse.users()
            .stream().anyMatch(user -> user.calendars()
                .stream().anyMatch(FreeBusyResponse.Calendar::isBusy));

        return Mono.justOrEmpty(FreeBusyRequest.tryFromCalendarEventParsed(calendarEventParsed))
            .map(builder -> builder.user(davUser.userId()).build())
            .flatMap(request -> davClient.caldav(davUser.username()).freeBusyQuery(davUser, request))
            .map(isBusyFunction)
            .map(FreeBusyStatus::isBusy)
            .defaultIfEmpty(FreeBusyStatus.FREE);
    }

    @Override
    public Mono<Void> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus, BlobId eventBlobId) {
        return davUserProvider.provide(username)
            .flatMap(davUser -> setAttendanceStatus(username, eventBlobId, attendanceStatus, sessionProvider.createSystemSession(username)));
    }

    private Mono<Void> setAttendanceStatus(Username username, BlobId blobId, AttendanceStatus attendanceStatus, MailboxSession session) {
        return calendarResolver.resolveRequestCalendar(blobId, session, OptionConverters.toScala(Optional.of(ImmutableMethod.REQUEST))).asJava()
            .flatMap(calendar -> addressesOf(username)
                .flatMap(addresses -> {
                    CalendarUidField eventUid = CalendarUidField.getEventUidFromCalendar(calendar);
                    // The user may be invited through one of its aliases: its copy of the event then lists it under that alias
                    CalendarEventModifier eventModifier = CalendarEventModifier.withPartStatOfAnyOf(addresses, attendanceStatus.toPartStat(), calendar);
                    return updateEvent(username, eventUid, eventModifier)
                        .onErrorResume(error -> error instanceof CalendarEventNotFoundException || error instanceof AttendeeNotFoundException,
                            error -> mayImport(username, eventUid)
                                .flatMap(mayImport -> {
                                    if (mayImport) {
                                        return importEventViaITIP(username, blobId, calendar, session)
                                            .then(updateEvent(username, eventUid, eventModifier));
                                    }
                                    return Mono.error(error);
                                }));
                }));
    }

    private Mono<List<String>> addressesOf(Username username) {
        return Flux.concat(Mono.just(username.asString()),
                Flux.from(aliasReverseResolver.listAddresses(username)).map(MailAddress::asString))
            .distinct(address -> address.toLowerCase(Locale.US))
            .collectList();
    }

    /**
     * The event is imported in the user calendar when missing from it. It may also be held only by calendars the user does not
     * own, eg the one of a colleague the user subscribed to, that do not list the user: the event is then imported as well.
     *
     * A copy held by a calendar of the user is never overwritten by an import: esn-sabre would merge the ITIP payload, coming
     * from a mail, into it.
     *
     * The lookup is done again even when the update found no copy: that lookup ignores the calendars it can not query. Only
     * the calendars the user owns are looked up, and a failed lookup of one of them must not pass for the absence of a copy:
     * a calendar the user subscribed to that can no longer be queried does not prevent the import.
     */
    private Mono<Boolean> mayImport(Username username, CalendarUidField eventUid) {
        DavUid davUid = DavUid.fromCalendarUidField(eventUid);
        return davUserProvider.provide(username)
            .flatMap(davUser -> davClient.caldav(username).findUserOwnedCalendars(davUser)
                .concatMap(ownedCalendar -> davClient.caldav(username).mayHoldEventOrFail(ownedCalendar, davUid))
                .any(held -> held)
                .map(heldByAnOwnedCalendar -> !heldByAnOwnedCalendar));
    }

    private Mono<Void> importEventViaITIP(Username username, BlobId blobId, Calendar calendar, MailboxSession session) {
        return senderOfTheMailHolding(blobId, session)
            .switchIfEmpty(Mono.error(() -> new CalendarEventImportRefusedException("Can not import the event of blob '%s': it is not part of a mail with a known sender"
                .formatted(blobId.value()))))
            .flatMap(sender -> davUserProvider.provide(username)
                .flatMap(davUser -> importEvent(username, davUser, blobId, calendar, sender)));
    }

    /**
     * The event is handed to esn-sabre as sent by its organizer, as only then it creates it, or by the user when it names
     * none. This can not overwrite a copy of the user: the import only happens once no calendar the user owns holds the
     * event, see {@link #mayImport}.
     *
     * When the mail does not come from the organizer, nor from the delegate its SENT-BY names, the user was warned by the
     * X-TWP-Message the CalDavCollect mailets added (see ITIPSenderMismatchWarning) and still asked for the event: the
     * sender of the mail is recorded as the SENT-BY of the organizer, so that the calendar does not present the event as
     * coming from the organizer alone.
     */
    private Mono<Void> importEvent(Username username, DavUser davUser, BlobId blobId, Calendar calendar, MailAddress sender) {
        Optional<MailAddress> organizer = ITIPSenderAlignment.firstOrganizer(calendar);
        return Mono.fromCallable(() -> organizer.isPresent() ? organizer.get() : username.asMailAddress())
            .flatMap(itipSender -> sendITIPRequest(username, davUser, recordingTheSender(username, blobId, calendar, sender, organizer), itipSender));
    }

    private static Calendar recordingTheSender(Username username, BlobId blobId, Calendar calendar, MailAddress sender, Optional<MailAddress> organizer) {
        if (organizer.isEmpty() || ITIPSenderAlignment.isSatisfied(AlignmentMode.STRICT, sender, calendar)) {
            return calendar;
        }
        LOGGER.info("Importing the event of blob '{}' for {}: its mail comes from {}, not from its organizer {}",
            blobId.value(), username.asString(), sender.asString(), organizer.get().asString());
        return ITIPSenderAlignment.withOrganizerSentBy(copyOf(calendar), sender);
    }

    private static Calendar copyOf(Calendar calendar) {
        return CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendar.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private Mono<Void> sendITIPRequest(Username username, DavUser davUser, Calendar calendar, MailAddress sender) {
        try {
            VEvent vevent = (VEvent) calendar.getComponent(Component.VEVENT)
                .orElseThrow(() -> new RuntimeException("No VEVENT found in calendar"));

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = mapper.createObjectNode();
            node.put("ical", calendar.toString());
            node.put("sender", sender.asString());
            node.put("recipient", username.asString());
            node.put("replyTo", sender.asString());
            vevent.getUid().ifPresent(uid -> node.put("uid", uid.getValue()));
            vevent.getDateStamp().ifPresent(ds -> node.put("dtstamp", ds.getValue()));
            calendar.getProperty(Property.METHOD).ifPresent(m -> node.put("method", m.getValue()));
            Optional.ofNullable(vevent.getSequence()).ifPresent(seq -> node.put("sequence", seq.getValue()));
            Optional.ofNullable(vevent.getRecurrenceId()).ifPresent(rid -> node.put("recurrence-id", rid.getValue()));

            return davClient.caldav(username).sendITIPRequest(
                URI.create(CalDavClient.CALENDAR_PATH + davUser.userId().value()),
                mapper.writeValueAsBytes(node));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    // The From of the mail, as the mailets feeding esn-sabre with the ITIP messages of incoming mails use it
    private Mono<MailAddress> senderOfTheMailHolding(BlobId blobId, MailboxSession session) {
        return Mono.justOrEmpty(OptionConverters.toJava(MessagePartBlobId.tryParse(messageIdFactory, blobId.value().toString()).toOption())
                .map(MessagePartBlobId::getMessageId))
            .flatMap(messageId -> Flux.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.HEADERS, session)).next())
            .flatMap(message -> Mono.fromCallable(() -> fromAddress(message)).flatMap(Mono::justOrEmpty));
    }

    private static Optional<MailAddress> fromAddress(MessageResult message) throws MailboxException {
        return Streams.stream(message.getHeaders().headers())
            .filter(header -> header.getName().equalsIgnoreCase("From"))
            .findFirst()
            .flatMap(header -> {
                try {
                    return Arrays.stream(InternetAddress.parseHeader(MimeUtility.unfold(header.getValue()), false))
                        .findFirst()
                        .flatMap(address -> MaybeSender.getMailSender(address.getAddress()).asOptional());
                } catch (AddressException e) {
                    return Optional.empty();
                }
            });
    }

    // Signals, from within the calendar object updater, that the fetched version is cancelled and must not be written
    private static class CancelledCalendarObjectException extends RuntimeException {
        CancelledCalendarObjectException(URI calendarObjectUri) {
            super("Calendar object '%s' is cancelled".formatted(calendarObjectUri));
        }
    }

    @Override
    public Mono<Void> updateEvent(Username username, CalendarUidField eventUid, CalendarEventModifier eventModifier) {
        return davUserProvider.provide(username)
            .flatMap(davUser -> davClient.caldav(davUser.username()).getCalendarObjects(davUser, DavUid.fromCalendarUidField(eventUid))
                .switchIfEmpty(Flux.error(new CalendarEventNotFoundException(username, eventUid)))
                .flatMap(calendarObject -> doUpdateCalendarObject(davUser, calendarObject, eventModifier))
                .takeUntil(UpdateOutcome.UPDATED::equals)
                .reduce(UpdateOutcome::mostRelevant)
                .flatMap(outcome -> outcome.asResult(username, eventUid)));
    }

    private Mono<UpdateOutcome> doUpdateCalendarObject(DavUser davUser, DavCalendarObject calendarObject, CalendarEventModifier eventModifier) {
        // PermissionDenied remains the only reliable delegated-calendar guard, hence the outcome aggregation in updateEvent.
        return davClient.caldav(davUser.username()).updateCalendarObject(calendarObject.uri(), cancellationAwareUpdater(eventModifier))
            .thenReturn(UpdateOutcome.UPDATED)
            .onErrorResume(CancelledCalendarObjectException.class, e -> {
                LOGGER.debug("Skipping calendar object '{}': event is cancelled", calendarObject.uri());
                return Mono.just(UpdateOutcome.CANCELLED);
            })
            .onErrorResume(AttendeeNotFoundException.class, e -> {
                LOGGER.debug("Skipping calendar object '{}': the user does not attend this event", calendarObject.uri());
                return Mono.just(UpdateOutcome.NOT_ATTENDEE);
            })
            .onErrorResume(DavClientException.PermissionDenied.class, e -> {
                LOGGER.debug("Skipping calendar object '{}': permission denied, likely a delegated calendar", calendarObject.uri());
                return Mono.just(UpdateOutcome.SKIPPED);
            });
    }

    // Cancellation is checked on every fetched version, ETag retries included: a cancelled event must be left untouched.
    private UnaryOperator<DavCalendarObject> cancellationAwareUpdater(CalendarEventModifier eventModifier) {
        return calendarObject -> {
            if (isCancelled(calendarObject, eventModifier)) {
                throw new CancelledCalendarObjectException(calendarObject.uri());
            }
            return calendarObject.withUpdatePatches(eventModifier);
        };
    }

    // Inspects the VEVENT the modifier patches, or the one a missing occurrence override is copied from (eg a cancelled series)
    private boolean isCancelled(DavCalendarObject calendarObject, CalendarEventModifier eventModifier) {
        return OptionConverters.toJava(eventModifier.targetedVEvent(calendarObject.calendarData()))
            .map(VEvent::getStatus)
            .map(status -> Status.VALUE_CANCELLED.equals(status.getValue()))
            .orElse(false);
    }

}