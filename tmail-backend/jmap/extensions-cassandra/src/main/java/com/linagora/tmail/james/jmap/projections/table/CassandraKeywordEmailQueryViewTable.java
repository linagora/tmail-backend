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

package com.linagora.tmail.james.jmap.projections.table;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface CassandraKeywordEmailQueryViewTable {
    String TABLE_NAME = "keyword_view";

    CqlIdentifier USERNAME = CqlIdentifier.fromCql("username");
    CqlIdentifier KEYWORD = CqlIdentifier.fromCql("keyword");
    CqlIdentifier RECEIVED_AT = CqlIdentifier.fromCql("received_at");
    CqlIdentifier MESSAGE_ID = CqlIdentifier.fromCql("message_id");
    CqlIdentifier THREAD_ID = CqlIdentifier.fromCql("thread_id");
}
