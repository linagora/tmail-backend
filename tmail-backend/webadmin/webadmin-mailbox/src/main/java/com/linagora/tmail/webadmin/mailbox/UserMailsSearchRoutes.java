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

package com.linagora.tmail.webadmin.mailbox;

import java.net.InetSocketAddress;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery.PersonalNamespace;
import org.apache.james.mailbox.model.SearchOptions;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Address;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Group;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.dom.field.MailboxField;
import org.apache.james.mime4j.dom.field.MailboxListField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Service;

public class UserMailsSearchRoutes implements Routes {

    private static final String BASE_PATH = "/users";
    private static final String USERNAME_PARAM = ":username";
    private static final String MAILS_PATH = BASE_PATH + "/" + USERNAME_PARAM + "/mails";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    public static class FilterRequest {
        public Optional<String> text = Optional.empty();
        public Optional<Boolean> hasAttachment = Optional.empty();
        public Optional<List<String>> hasKeywords = Optional.empty();
        public Optional<String> from = Optional.empty();
        public Optional<String> to = Optional.empty();
        public Optional<List<String>> inMailboxes = Optional.empty();
        public Optional<String> before = Optional.empty();
        public Optional<String> after = Optional.empty();
        public Optional<List<String>> inMailboxOtherThan = Optional.empty();
        public Optional<String> subject = Optional.empty();
    }

    public static class SortRequest {
        public String property;
        @JsonProperty("isAscending")
        public Boolean isAscending;
    }

    public static class SearchRequest {
        public String reason;
        public FilterRequest filter;
        public List<SortRequest> sort;
    }

    public static class EmailAddressDTO {
        public final String name;
        public final String email;

        public EmailAddressDTO(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    public static class MailSummaryDTO {
        public final String id;
        public final Map<String, Boolean> keywords;
        public final Map<String, Boolean> mailboxIds;
        public final String receivedAt;
        public final List<EmailAddressDTO> from;
        public final List<EmailAddressDTO> to;
        public final boolean hasAttachment;
        public final String preview;
        public final String subject;

        public MailSummaryDTO(String id, Map<String, Boolean> keywords, Map<String, Boolean> mailboxIds,
                              String receivedAt, List<EmailAddressDTO> from, List<EmailAddressDTO> to,
                              boolean hasAttachment, String preview, String subject) {
            this.id = id;
            this.keywords = keywords;
            this.mailboxIds = mailboxIds;
            this.receivedAt = receivedAt;
            this.from = from;
            this.to = to;
            this.hasAttachment = hasAttachment;
            this.preview = preview;
            this.subject = subject;
        }
    }

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection messageFastViewProjection;
    private final MailboxId.Factory mailboxIdFactory;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<SearchRequest> requestExtractor;

    @Inject
    public UserMailsSearchRoutes(MailboxManager mailboxManager,
                                 MessageIdManager messageIdManager,
                                 MessageFastViewProjection messageFastViewProjection,
                                 MailboxId.Factory mailboxIdFactory,
                                 JsonTransformer jsonTransformer) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.messageFastViewProjection = messageFastViewProjection;
        this.mailboxIdFactory = mailboxIdFactory;
        this.jsonTransformer = jsonTransformer;
        this.requestExtractor = new JsonExtractor<>(SearchRequest.class);
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(MAILS_PATH, (request, response) -> {
            Username username = parseUsername(request.params(USERNAME_PARAM));
            int limit = parseLimit(request.queryParams("limit"));
            int offset = parseOffset(request.queryParams("offset"));
            SearchRequest searchRequest = parseSearchRequest(request.body());
            auditLog(request, username, searchRequest.reason, limit, offset, searchRequest.filter);
            MailboxSession session = mailboxManager.createSystemSession(username);
            List<MessageId> messageIds = searchMessages(searchRequest, session, limit, offset);
            return buildSummaries(messageIds, session);
        }, jsonTransformer);
    }

    private SearchRequest parseSearchRequest(String body) {
        if (body == null || body.isBlank()) {
            throw invalidArgument("Request body with a 'reason' field is required");
        }
        try {
            SearchRequest searchRequest = requestExtractor.parse(body);
            validateReason(searchRequest.reason);
            return searchRequest;
        } catch (HaltException e) {
            throw e;
        } catch (Exception e) {
            throw invalidArgument("Invalid request body: " + e.getMessage());
        }
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalidArgument("'reason' field is required and must not be empty");
        }
    }

    private void auditLog(Request request, Username targetUser, String reason, int limit, int offset,
                          FilterRequest filter) {
        AuditTrail.entry()
            .remoteIP(() -> Optional.of(new InetSocketAddress(request.ip(), 0)))
            .protocol("webadmin")
            .action("user_mails_search")
            .parameters(() -> buildAuditParameters(targetUser, reason, limit, offset, filter))
            .log("Admin searched user mails");
    }

    private ImmutableMap<String, String> buildAuditParameters(Username targetUser, String reason,
                                                               int limit, int offset, FilterRequest filter) {
        ImmutableMap.Builder<String, String> params = ImmutableMap.<String, String>builder()
            .put("targetUser", targetUser.asString())
            .put("reason", reason)
            .put("limit", String.valueOf(limit))
            .put("offset", String.valueOf(offset));
        if (filter != null) {
            filter.text.ifPresent(v -> params.put("filterText", v));
            filter.from.ifPresent(v -> params.put("filterFrom", v));
            filter.to.ifPresent(v -> params.put("filterTo", v));
            filter.subject.ifPresent(v -> params.put("filterSubject", v));
            filter.hasAttachment.ifPresent(v -> params.put("filterHasAttachment", String.valueOf(v)));
            filter.hasKeywords.ifPresent(v -> params.put("filterHasKeywords", String.join(",", v)));
            filter.before.ifPresent(v -> params.put("filterBefore", v));
            filter.after.ifPresent(v -> params.put("filterAfter", v));
            filter.inMailboxes.ifPresent(v -> params.put("filterInMailboxes", String.join(",", v)));
            filter.inMailboxOtherThan.ifPresent(v -> params.put("filterInMailboxOtherThan", String.join(",", v)));
        }
        return params.build();
    }

    private List<MessageId> searchMessages(SearchRequest searchRequest, MailboxSession session, int limit, int offset) {
        MultimailboxesSearchQuery query = buildSearchQuery(searchRequest, session);
        List<MessageId> result = Flux.from(mailboxManager.search(query, session,
                SearchOptions.of(Offset.from(offset), Limit.from(limit))))
            .collectList().block();
        return result != null ? result : List.of();
    }

    private List<MailSummaryDTO> buildSummaries(List<MessageId> messageIds, MailboxSession session) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        Map<MessageId, MessageFastViewPrecomputedProperties> fastViews = fetchFastViews(messageIds);
        Map<MessageId, List<MessageResult>> messagesById = fetchMessages(messageIds, session);
        return messageIds.stream()
            .filter(messagesById::containsKey)
            .map(id -> buildMailSummary(id, messagesById.get(id), fastViews.get(id)))
            .collect(Collectors.toList());
    }

    private Map<MessageId, MessageFastViewPrecomputedProperties> fetchFastViews(List<MessageId> messageIds) {
        return Optional.ofNullable(Mono.from(messageFastViewProjection.retrieve(messageIds)).block())
            .orElse(Map.of());
    }

    private Map<MessageId, List<MessageResult>> fetchMessages(List<MessageId> messageIds, MailboxSession session) {
        Map<MessageId, Collection<MessageResult>> result = Flux.from(
                messageIdManager.getMessagesReactive(messageIds, FetchGroup.HEADERS, session))
            .collectMultimap(MessageResult::getMessageId)
            .block();
        if (result == null) {
            return Map.of();
        }
        Map<MessageId, List<MessageResult>> listResult = new HashMap<>();
        result.forEach((k, v) -> listResult.put(k, new ArrayList<>(v)));
        return listResult;
    }

    private MultimailboxesSearchQuery buildSearchQuery(SearchRequest request, MailboxSession session) {
        SearchQuery.Builder queryBuilder = new SearchQuery.Builder();
        queryBuilder.andCriteria(SearchQuery.flagIsUnSet(Flags.Flag.DELETED));
        if (request.filter != null) {
            applyFilterCriteria(queryBuilder, request.filter);
        }
        Optional.ofNullable(request.sort)
            .filter(s -> !s.isEmpty())
            .ifPresent(sorts -> queryBuilder.sorts(buildSorts(sorts)));
        return buildMultiMailboxQuery(queryBuilder.build(), request.filter, session);
    }

    private void applyFilterCriteria(SearchQuery.Builder queryBuilder, FilterRequest filter) {
        filter.text.ifPresent(text -> queryBuilder.andCriteria(buildTextCriterion(text)));
        filter.hasAttachment.ifPresent(has -> queryBuilder.andCriteria(SearchQuery.hasAttachment(has)));
        filter.hasKeywords.ifPresent(kws -> kws.forEach(kw -> queryBuilder.andCriteria(keywordCriterion(kw))));
        filter.from.ifPresent(from -> queryBuilder.andCriteria(SearchQuery.address(AddressType.From, from)));
        filter.to.ifPresent(to -> queryBuilder.andCriteria(SearchQuery.address(AddressType.To, to)));
        filter.subject.ifPresent(subject -> queryBuilder.andCriteria(SearchQuery.subject(subject)));
        filter.before.ifPresent(before -> queryBuilder.andCriteria(buildBeforeCriterion(before)));
        filter.after.ifPresent(after -> queryBuilder.andCriteria(buildAfterCriterion(after)));
    }

    private SearchQuery.Criterion buildTextCriterion(String text) {
        return SearchQuery.or(List.of(
            SearchQuery.address(AddressType.To, text),
            SearchQuery.address(AddressType.Cc, text),
            SearchQuery.address(AddressType.Bcc, text),
            SearchQuery.address(AddressType.From, text),
            SearchQuery.subject(text),
            SearchQuery.bodyContains(text),
            SearchQuery.attachmentContains(text),
            SearchQuery.attachmentFileName(text)));
    }

    private SearchQuery.Criterion buildBeforeCriterion(String before) {
        try {
            Date date = Date.from(ZonedDateTime.parse(before).toInstant());
            return SearchQuery.or(List.of(
                SearchQuery.internalDateBefore(date, SearchQuery.DateResolution.Second),
                SearchQuery.internalDateOn(date, SearchQuery.DateResolution.Second)));
        } catch (Exception e) {
            throw invalidArgument("Invalid 'before' date format: " + before);
        }
    }

    private SearchQuery.Criterion buildAfterCriterion(String after) {
        try {
            Date date = Date.from(ZonedDateTime.parse(after).toInstant());
            return SearchQuery.internalDateAfter(date, SearchQuery.DateResolution.Second);
        } catch (Exception e) {
            throw invalidArgument("Invalid 'after' date format: " + after);
        }
    }

    private MultimailboxesSearchQuery buildMultiMailboxQuery(SearchQuery searchQuery, FilterRequest filter,
                                                             MailboxSession session) {
        MultimailboxesSearchQuery.Builder multiBuilder = MultimailboxesSearchQuery.from(searchQuery)
            .inNamespace(new PersonalNamespace(session));
        if (filter != null) {
            filter.inMailboxes.ifPresent(ids -> multiBuilder.inMailboxes(parseMailboxIds(ids)));
            filter.inMailboxOtherThan.ifPresent(ids -> multiBuilder.notInMailboxes(parseMailboxIds(ids)));
        }
        return multiBuilder.build();
    }

    private List<MailboxId> parseMailboxIds(List<String> ids) {
        return ids.stream().map(this::parseMailboxId).collect(Collectors.toList());
    }

    private SearchQuery.Criterion keywordCriterion(String keyword) {
        return switch (keyword.toLowerCase()) {
            case "$seen" -> SearchQuery.flagIsSet(Flags.Flag.SEEN);
            case "$answered" -> SearchQuery.flagIsSet(Flags.Flag.ANSWERED);
            case "$flagged" -> SearchQuery.flagIsSet(Flags.Flag.FLAGGED);
            case "$draft" -> SearchQuery.flagIsSet(Flags.Flag.DRAFT);
            default -> SearchQuery.flagIsSet(keyword);
        };
    }

    private List<Sort> buildSorts(List<SortRequest> sortRequests) {
        return sortRequests.stream()
            .map(s -> {
                SortClause clause = switch (s.property) {
                    case "receivedAt" -> SortClause.Arrival;
                    case "from" -> SortClause.MailboxFrom;
                    case "subject" -> SortClause.BaseSubject;
                    default -> throw invalidArgument("Unsupported sort property: " + s.property);
                };
                Order order = Boolean.TRUE.equals(s.isAscending) ? Order.NATURAL : Order.REVERSE;
                return new Sort(clause, order);
            })
            .collect(Collectors.toList());
    }

    private MailSummaryDTO buildMailSummary(MessageId messageId, List<MessageResult> messages,
                                            MessageFastViewPrecomputedProperties fastView) {
        MessageResult first = messages.get(0);
        Message mime4j = parseHeadersQuietly(first);
        return new MailSummaryDTO(
            messageId.serialize(),
            extractKeywords(mergeFlags(messages)),
            extractMailboxIds(messages),
            formatDate(first.getInternalDate()),
            mime4j != null ? extractAddresses(mime4j, "From") : List.of(),
            mime4j != null ? extractAddresses(mime4j, "To") : List.of(),
            fastView != null && fastView.hasAttachment(),
            fastView != null ? fastView.getPreview().getValue() : "",
            mime4j != null ? extractSubject(mime4j) : null);
    }

    private Map<String, Boolean> extractMailboxIds(List<MessageResult> messages) {
        return messages.stream()
            .collect(Collectors.toMap(
                m -> m.getMailboxId().serialize(),
                m -> Boolean.TRUE,
                (a, b) -> a));
    }

    private Flags mergeFlags(List<MessageResult> messages) {
        Flags merged = new Flags();
        for (MessageResult message : messages) {
            merged.add(message.getFlags());
        }
        return merged;
    }

    private Map<String, Boolean> extractKeywords(Flags flags) {
        Map<String, Boolean> keywords = new HashMap<>();
        if (flags.contains(Flags.Flag.SEEN)) keywords.put("$seen", true);
        if (flags.contains(Flags.Flag.ANSWERED)) keywords.put("$answered", true);
        if (flags.contains(Flags.Flag.FLAGGED)) keywords.put("$flagged", true);
        if (flags.contains(Flags.Flag.DRAFT)) keywords.put("$draft", true);
        for (String userFlag : flags.getUserFlags()) keywords.put(userFlag, true);
        return keywords;
    }

    private String formatDate(Date date) {
        return ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private Message parseHeadersQuietly(MessageResult messageResult) {
        try {
            DefaultMessageBuilder builder = new DefaultMessageBuilder();
            builder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            builder.setDecodeMonitor(DecodeMonitor.SILENT);
            return builder.parseMessage(messageResult.getHeaders().getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    private List<EmailAddressDTO> extractAddresses(Message mime4jMessage, String fieldName) {
        org.apache.james.mime4j.stream.Field field = mime4jMessage.getHeader().getField(fieldName);
        if (field == null) return List.of();
        if (field instanceof AddressListField f) return toEmailAddresses(f.getAddressList());
        if (field instanceof MailboxListField f) return toEmailAddresses(f.getMailboxList());
        if (field instanceof MailboxField f) {
            return Optional.ofNullable(f.getMailbox())
                .map(m -> List.of(new EmailAddressDTO(m.getName(), m.getAddress())))
                .orElse(List.of());
        }
        return List.of();
    }

    private List<EmailAddressDTO> toEmailAddresses(AddressList addressList) {
        if (addressList == null) return List.of();
        List<EmailAddressDTO> result = new ArrayList<>();
        for (Address address : addressList) {
            if (address instanceof Mailbox m) {
                result.add(new EmailAddressDTO(m.getName(), m.getAddress()));
            } else if (address instanceof Group g) {
                g.getMailboxes().forEach(m -> result.add(new EmailAddressDTO(m.getName(), m.getAddress())));
            }
        }
        return result;
    }

    private List<EmailAddressDTO> toEmailAddresses(MailboxList mailboxList) {
        if (mailboxList == null) return List.of();
        return mailboxList.stream()
            .map(m -> new EmailAddressDTO(m.getName(), m.getAddress()))
            .collect(Collectors.toList());
    }

    private String extractSubject(Message mime4jMessage) {
        return Optional.ofNullable(mime4jMessage.getSubject())
            .map(MimeUtil::unscrambleHeaderValue)
            .orElse(null);
    }

    private Username parseUsername(String usernameString) {
        try {
            return Username.of(usernameString);
        } catch (Exception e) {
            throw invalidArgument("Invalid username: " + usernameString);
        }
    }

    private int parseLimit(String limitParam) {
        if (limitParam == null || limitParam.isBlank()) return DEFAULT_LIMIT;
        try {
            int limit = Integer.parseInt(limitParam);
            if (limit <= 0 || limit > MAX_LIMIT) throw invalidArgument("limit must be between 1 and " + MAX_LIMIT);
            return limit;
        } catch (NumberFormatException e) {
            throw invalidArgument("Invalid limit parameter: " + limitParam);
        }
    }

    private int parseOffset(String offsetParam) {
        if (offsetParam == null || offsetParam.isBlank()) return 0;
        try {
            int offset = Integer.parseInt(offsetParam);
            if (offset < 0) throw invalidArgument("offset must be non-negative");
            return offset;
        } catch (NumberFormatException e) {
            throw invalidArgument("Invalid offset parameter: " + offsetParam);
        }
    }

    private MailboxId parseMailboxId(String mailboxIdString) {
        try {
            return mailboxIdFactory.fromString(mailboxIdString);
        } catch (Exception e) {
            throw invalidArgument("Invalid mailboxId: " + mailboxIdString);
        }
    }

    private static HaltException invalidArgument(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(message)
            .haltError();
    }
}
