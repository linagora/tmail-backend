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

package com.linagora.tmail.james.common.probe;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.utils.GuiceProbe;

import reactor.core.publisher.Mono;

public class MessageFastViewProjectionProbe implements GuiceProbe {
    private final MessageFastViewProjection messageFastViewProjection;

    @Inject
    public MessageFastViewProjectionProbe(MessageFastViewProjection messageFastViewProjection) {
        this.messageFastViewProjection = messageFastViewProjection;
    }

    public Optional<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId) {
        return Mono.from(messageFastViewProjection.retrieve(messageId))
            .blockOptional();
    }

}
