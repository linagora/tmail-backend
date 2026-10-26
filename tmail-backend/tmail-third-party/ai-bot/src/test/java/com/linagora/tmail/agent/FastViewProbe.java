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
package com.linagora.tmail.agent;

import java.util.Optional;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Inject;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.utils.GuiceProbe;
import reactor.core.publisher.Mono;

public class FastViewProbe implements GuiceProbe {
    public static class Module extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), GuiceProbe.class)
                .addBinding()
                .to(FastViewProbe.class);
        }
    }

    private final MessageFastViewProjection projection;

    @Inject
    public FastViewProbe(MessageFastViewProjection projection) {
        this.projection = projection;
    }

    public void store(MessageId messageId, String preview) {
        Mono.from(projection.store(messageId, MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from(preview))
                .hasAttachment(false)
                .build()))
            .block();
    }

    public void delete(MessageId messageId) {
        Mono.from(projection.delete(messageId)).block();
    }

    public Optional<String> preview(MessageId messageId) {
        return Mono.from(projection.retrieve(messageId))
            .map(fastView -> fastView.getPreview().getValue())
            .blockOptional();
    }
}
