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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Team mailboxes together with the space binding stored on their root. The annotations are written through the
 * {@link org.apache.james.mailbox.store.mail.AnnotationMapper}, as the mailbox manager refuses read only keys.
 */
public class SpaceMailboxes {
    public record SpaceMailbox(TeamMailbox teamMailbox, MailboxId rootId, Optional<SpaceBinding> binding) {
        public boolean isLiveFor(String spaceId) {
            return binding.filter(SpaceBinding::isLive)
                .filter(value -> value.spaceId().equals(spaceId))
                .isPresent();
        }

        public boolean isLive() {
            return binding.filter(SpaceBinding::isLive).isPresent();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceMailboxes.class);

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mapperFactory;
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public SpaceMailboxes(MailboxManager mailboxManager, MailboxSessionMapperFactory mapperFactory, TeamMailboxRepository teamMailboxRepository) {
        this.mailboxManager = mailboxManager;
        this.mapperFactory = mapperFactory;
        this.teamMailboxRepository = teamMailboxRepository;
    }

    /** Empty when the team mailbox does not exist. */
    public Mono<SpaceMailbox> read(TeamMailbox teamMailbox) {
        MailboxSession session = session(teamMailbox);
        return mapperFactory.getMailboxMapper(session).findMailboxByPath(teamMailbox.mailboxPath())
            .flatMap(root -> Flux.from(mapperFactory.getAnnotationMapper(session).getAnnotationsByKeysReactive(root.getMailboxId(), SpaceAnnotations.KEYS))
                .collectMap(MailboxAnnotation::getKey, Function.identity())
                .map(annotations -> new SpaceMailbox(teamMailbox, root.getMailboxId(), binding(teamMailbox, annotations))));
    }

    /** Every team mailbox: a full scan, only for repairs. Lookups go through the {@link SpaceIndex}. */
    public Flux<SpaceMailbox> listAll() {
        return Flux.from(teamMailboxRepository.listTeamMailboxes())
            .concatMap(this::read);
    }

    public Mono<Void> bind(SpaceMailbox spaceMailbox, String organizationId, String spaceId) {
        return write(spaceMailbox, ImmutableMap.of(
            SpaceAnnotations.ORGANIZATION_ID, organizationId,
            SpaceAnnotations.SPACE_ID, spaceId));
    }

    public Mono<Void> markDeleted(SpaceMailbox spaceMailbox, Instant deletedAt) {
        return write(spaceMailbox, ImmutableMap.of(SpaceAnnotations.DELETED_AT, deletedAt.toString()));
    }

    public Mono<Void> unmarkDeleted(SpaceMailbox spaceMailbox) {
        return Mono.from(mapperFactory.getAnnotationMapper(session(spaceMailbox.teamMailbox()))
            .deleteAnnotationReactive(spaceMailbox.rootId(), SpaceAnnotations.DELETED_AT));
    }

    private Mono<Void> write(SpaceMailbox spaceMailbox, Map<MailboxAnnotationKey, String> values) {
        var annotationMapper = mapperFactory.getAnnotationMapper(session(spaceMailbox.teamMailbox()));
        return Flux.fromIterable(values.entrySet())
            .concatMap(entry -> annotationMapper.insertAnnotationReactive(spaceMailbox.rootId(),
                MailboxAnnotation.newInstance(entry.getKey(), entry.getValue())))
            .then();
    }

    private Optional<SpaceBinding> binding(TeamMailbox teamMailbox, Map<MailboxAnnotationKey, MailboxAnnotation> annotations) {
        Optional<String> organizationId = value(annotations, SpaceAnnotations.ORGANIZATION_ID);
        Optional<String> spaceId = value(annotations, SpaceAnnotations.SPACE_ID);
        if (organizationId.isEmpty() || spaceId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SpaceBinding(organizationId.get(), spaceId.get(), deletedAt(teamMailbox, annotations)));
    }

    private Optional<Instant> deletedAt(TeamMailbox teamMailbox, Map<MailboxAnnotationKey, MailboxAnnotation> annotations) {
        return value(annotations, SpaceAnnotations.DELETED_AT)
            .map(value -> {
                try {
                    return Instant.parse(value);
                } catch (DateTimeParseException e) {
                    // Still closed: a deleted space stays deleted, it is only kept until an operator fixes the date
                    LOGGER.warn("Team mailbox {} has an invalid {} annotation: {}", teamMailbox.asMailAddress().asString(),
                        SpaceAnnotations.DELETED_AT.asString(), value);
                    return Instant.MAX;
                }
            });
    }

    private Optional<String> value(Map<MailboxAnnotationKey, MailboxAnnotation> annotations, MailboxAnnotationKey key) {
        return Optional.ofNullable(annotations.get(key))
            .flatMap(MailboxAnnotation::getValue)
            .filter(value -> !value.isBlank());
    }

    private MailboxSession session(TeamMailbox teamMailbox) {
        return mailboxManager.createSystemSession(teamMailbox.owner());
    }
}
