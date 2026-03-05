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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import spark.Service;

public class MailboxDetailsRoutes implements Routes {
    private static final String BASE_PATH = "/mailboxes";
    private static final String MAILBOX_ID_PARAM = ":mailboxId";
    private static final String MAILBOX_PATH = BASE_PATH + "/" + MAILBOX_ID_PARAM;
    private static final Username SYSTEM_USER = Username.of("admin");

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final MailboxId.Factory mailboxIdFactory;
    private final JsonTransformer jsonTransformer;

    @Inject
    public MailboxDetailsRoutes(MailboxManager mailboxManager,
                                MailboxSessionMapperFactory mapperFactory,
                                MailboxId.Factory mailboxIdFactory,
                                JsonTransformer jsonTransformer) {
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.mailboxIdFactory = mailboxIdFactory;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(MAILBOX_PATH, (request, response) -> {
            MailboxId mailboxId = parseMailboxId(request.params(MAILBOX_ID_PARAM));
            MailboxSession session = mailboxManager.createSystemSession(SYSTEM_USER);

            try {
                return Mono.from(mapperFactory.getMailboxMapper(session).findMailboxById(mailboxId))
                    .map(mailbox -> new MessageLocationResponse.MailboxEntry(
                        mailbox.getMailboxId().serialize(),
                        mailbox.generateAssociatedPath().asString()))
                    .block();
            } catch (Exception e) {
                if (Exceptions.unwrap(e) instanceof MailboxNotFoundException) {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .message("Mailbox " + mailboxId.serialize() + " not found")
                        .haltError();
                }
                throw e;
            }
        }, jsonTransformer);
    }

    private MailboxId parseMailboxId(String mailboxIdString) {
        try {
            return mailboxIdFactory.fromString(mailboxIdString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid mailboxId: " + mailboxIdString)
                .cause(e)
                .haltError();
        }
    }
}
