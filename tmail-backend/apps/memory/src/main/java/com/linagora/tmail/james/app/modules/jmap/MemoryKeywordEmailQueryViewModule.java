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

package com.linagora.tmail.james.app.modules.jmap;

import org.apache.james.events.EventListener;
import org.apache.james.jmap.method.EmailQueryOptimizer;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.event.KeywordEmailQueryViewListener;
import com.linagora.tmail.james.jmap.method.KeywordEmailQueryViewOptimizer;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MemoryKeywordEmailQueryView;

public class MemoryKeywordEmailQueryViewModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryKeywordEmailQueryView.class).in(Scopes.SINGLETON);
        bind(KeywordEmailQueryView.class).to(MemoryKeywordEmailQueryView.class);

        Multibinder<EmailQueryOptimizer> emailQueryOptimizerMultibinder = Multibinder.newSetBinder(binder(), EmailQueryOptimizer.class);
        emailQueryOptimizerMultibinder.addBinding().to(KeywordEmailQueryViewOptimizer.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(KeywordEmailQueryViewListener.class);
    }
}
