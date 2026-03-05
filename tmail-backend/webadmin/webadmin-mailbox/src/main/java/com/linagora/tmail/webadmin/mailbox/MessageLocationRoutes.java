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

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.publisher.Flux;
import spark.Service;

public class MessageLocationRoutes implements Routes {
    private static final String BASE_PATH = "/messages";
    private static final String MESSAGE_ID_PARAM = ":messageId";
    private static final String MESSAGE_PATH = BASE_PATH + "/" + MESSAGE_ID_PARAM;
    private static final Username SYSTEM_USER = Username.of("admin");

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final MessageId.Factory messageIdFactory;
    private final JsonTransformer jsonTransformer;

    @Inject
    public MessageLocationRoutes(MailboxManager mailboxManager,
                                 MailboxSessionMapperFactory mapperFactory,
                                 MessageId.Factory messageIdFactory,
                                 JsonTransformer jsonTransformer) {
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.messageIdFactory = messageIdFactory;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(MESSAGE_PATH, (request, response) -> {
            MessageId messageId = parseMessageId(request.params(MESSAGE_ID_PARAM));
            MailboxSession session = mailboxManager.createSystemSession(SYSTEM_USER);

            MessageIdMapper messageIdMapper = mapperFactory.getMessageIdMapper(session);
            List<MailboxId> mailboxIds = messageIdMapper.findMailboxes(messageId);

            if (mailboxIds.isEmpty()) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("Message " + messageId.serialize() + " not found in any mailbox")
                    .haltError();
            }

            MailboxMapper mailboxMapper = mapperFactory.getMailboxMapper(session);
            List<MessageLocationResponse.MailboxEntry> mailboxEntries = Flux.fromIterable(mailboxIds)
                .flatMap(mailboxMapper::findMailboxById)
                .map(mailbox -> new MessageLocationResponse.MailboxEntry(
                    mailbox.getMailboxId().serialize(),
                    mailbox.generateAssociatedPath().asString()))
                .collectList()
                .block();

            return new MessageLocationResponse(mailboxEntries);
        }, jsonTransformer);
    }

    private MessageId parseMessageId(String messageIdString) {
        try {
            return messageIdFactory.fromString(messageIdString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid messageId: " + messageIdString)
                .cause(e)
                .haltError();
        }
    }
}
