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

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.projections.CassandraKeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.CassandraKeywordEmailQueryViewDataDefinition;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;

public class CassandraKeywordEmailQueryViewModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(KeywordEmailQueryView.class).to(CassandraKeywordEmailQueryView.class);
        bind(CassandraKeywordEmailQueryView.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), CassandraDataDefinition.class)
            .addBinding()
            .toInstance(CassandraKeywordEmailQueryViewDataDefinition.MODULE);
    }
}
