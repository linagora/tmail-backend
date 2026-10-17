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

package com.linagora.tmail.space;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.HexFormat;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.team.TeamMailbox;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

/**
 * Finds the team mailbox of a space without scanning the domain. Each domain with spaces has one index mailbox, in a
 * namespace of its own so that it never clashes with a team mailbox: {@code #TwakeSpace:team-mailbox-admin@<domain>:spaces}.
 * It holds an annotation per space, from the space id to the name of its team mailbox.
 *
 * <p>The index mailbox is created and written through the mappers: no event, no rights, so no client sees it, and the
 * annotation limits of the mailbox manager do not apply. The annotations of the team mailbox root stay the source of
 * truth: an entry is only a hint, checked against the {@code spaceId} of the team mailbox it names.</p>
 */
public class SpaceIndex {
    public record Entry(String spaceId, String teamMailboxName) {
    }

    public static final String NAMESPACE = "#TwakeSpace";
    public static final String MAILBOX_NAME = "spaces";
    public static final String KEY_PREFIX = SpaceAnnotations.PREFIX + "space/";
    private static final HexFormat HEX = HexFormat.of();

    public static MailboxPath path(Domain domain) {
        return new MailboxPath(NAMESPACE, owner(domain), MAILBOX_NAME);
    }

    private static Username owner(Domain domain) {
        return Username.fromLocalPartWithDomain(TeamMailbox.TEAM_MAILBOX_ADMIN_LOCAL_PART(), domain);
    }

    /** Annotation keys only take letters, digits and slashes, and are lowercased: hence the hexadecimal. */
    static MailboxAnnotationKey key(String spaceId) {
        return new MailboxAnnotationKey(KEY_PREFIX + HEX.formatHex(spaceId.getBytes(UTF_8)));
    }

    static Optional<String> spaceId(MailboxAnnotationKey key) {
        String value = key.asString();
        if (!value.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(HEX.parseHex(value.substring(KEY_PREFIX.length())), UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public SpaceIndex(MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory) {
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
    }

    /** The team mailbox the index names for this space, valid or not. */
    public Mono<TeamMailbox> get(Domain domain, String spaceId) {
        MailboxSession session = session(domain);
        return indexMailbox(domain, session)
            .flatMapMany(index -> mapperFactory.getAnnotationMapper(session)
                .getAnnotationsByKeysReactive(index.getMailboxId(), ImmutableSet.of(key(spaceId))))
            .next()
            .flatMap(annotation -> Mono.justOrEmpty(annotation.getValue()))
            .flatMap(name -> Mono.justOrEmpty(OptionConverters.toJava(TeamMailbox.fromJava(domain, name))));
    }

    public Flux<Entry> list(Domain domain) {
        MailboxSession session = session(domain);
        return indexMailbox(domain, session)
            .flatMapMany(index -> mapperFactory.getAnnotationMapper(session).getAllAnnotationsReactive(index.getMailboxId()))
            .flatMap(annotation -> Mono.justOrEmpty(spaceId(annotation.getKey())
                .flatMap(spaceId -> annotation.getValue().map(name -> new Entry(spaceId, name)))));
    }

    public Mono<Void> put(Domain domain, String spaceId, TeamMailbox teamMailbox) {
        MailboxSession session = session(domain);
        return createIndexMailbox(domain, session)
            .flatMap(index -> Mono.from(mapperFactory.getAnnotationMapper(session).insertAnnotationReactive(index.getMailboxId(),
                MailboxAnnotation.newInstance(key(spaceId), teamMailbox.mailboxName().asString()))));
    }

    public Mono<Void> remove(Domain domain, String spaceId) {
        MailboxSession session = session(domain);
        return indexMailbox(domain, session)
            .flatMap(index -> Mono.from(mapperFactory.getAnnotationMapper(session).deleteAnnotationReactive(index.getMailboxId(), key(spaceId))));
    }

    private Mono<Mailbox> indexMailbox(Domain domain, MailboxSession session) {
        return mapperFactory.getMailboxMapper(session).findMailboxByPath(path(domain));
    }

    private Mono<Mailbox> createIndexMailbox(Domain domain, MailboxSession session) {
        MailboxMapper mailboxMapper = mapperFactory.getMailboxMapper(session);
        return indexMailbox(domain, session)
            .switchIfEmpty(Mono.defer(() -> mailboxMapper.create(path(domain), UidValidity.generate()))
                .onErrorResume(MailboxExistsException.class, e -> indexMailbox(domain, session)));
    }

    private MailboxSession session(Domain domain) {
        return mailboxManager.createSystemSession(owner(domain));
    }
}
