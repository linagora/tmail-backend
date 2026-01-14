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

package com.linagora.tmail.mailet.conf;

import static com.linagora.tmail.mailet.rag.RagDeletionListener.RAG_DELETION_LISTENER_GROUP;
import static org.apache.james.events.EventDeadLettersHealthCheck.DEAD_LETTERS_IGNORED_GROUPS;

import org.apache.james.events.EventListener;
import org.apache.james.events.Group;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.linagora.tmail.mailet.rag.RagDeletionListener;

public class RagDeletionModule extends AbstractModule {
    private static final String CONTENT_DELETION = "contentDeletion";

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(CONTENT_DELETION))
            .addBinding()
            .to(RagDeletionListener.class);

        Multibinder.newSetBinder(binder(), Group.class, Names.named(DEAD_LETTERS_IGNORED_GROUPS))
            .addBinding()
            .toInstance(RAG_DELETION_LISTENER_GROUP);
    }
}
