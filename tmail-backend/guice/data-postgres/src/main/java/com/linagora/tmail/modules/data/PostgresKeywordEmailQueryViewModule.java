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

package com.linagora.tmail.modules.data;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.jmap.method.EmailQueryOptimizer;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.method.KeywordEmailQueryViewOptimizer;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.PostgresKeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.PostgresKeywordEmailQueryViewDataDefinition;

public class PostgresKeywordEmailQueryViewModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(KeywordEmailQueryView.class).to(PostgresKeywordEmailQueryView.class);
        bind(PostgresKeywordEmailQueryView.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), PostgresDataDefinition.class)
            .addBinding()
            .toInstance(PostgresKeywordEmailQueryViewDataDefinition.MODULE);

        Multibinder<EmailQueryOptimizer> emailQueryOptimizerMultibinder = Multibinder.newSetBinder(binder(), EmailQueryOptimizer.class);
        emailQueryOptimizerMultibinder.addBinding().to(KeywordEmailQueryViewOptimizer.class);
    }
}
