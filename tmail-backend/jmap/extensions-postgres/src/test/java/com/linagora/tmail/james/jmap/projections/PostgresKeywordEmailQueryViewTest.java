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

package com.linagora.tmail.james.jmap.projections;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresKeywordEmailQueryViewTest implements KeywordEmailQueryViewContract {
    public static final PostgresMessageId.Factory MESSAGE_ID_FACTORY = new PostgresMessageId.Factory();
    public static final PostgresMessageId MESSAGE_ID_1 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_2 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_3 = MESSAGE_ID_FACTORY.generate();
    public static final PostgresMessageId MESSAGE_ID_4 = MESSAGE_ID_FACTORY.generate();
    public static final ThreadId THREAD_ID_1 = ThreadId.fromBaseMessageId(MESSAGE_ID_FACTORY.generate());
    public static final ThreadId THREAD_ID_2 = ThreadId.fromBaseMessageId(MESSAGE_ID_FACTORY.generate());
    public static final ThreadId THREAD_ID_3 = ThreadId.fromBaseMessageId(MESSAGE_ID_FACTORY.generate());

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresKeywordEmailQueryViewDataDefinition.MODULE);

    @Override
    public KeywordEmailQueryView testee() {
        return new PostgresKeywordEmailQueryView(postgresExtension.getExecutorFactory());
    }

    @Override
    public MessageId messageId1() {
        return MESSAGE_ID_1;
    }

    @Override
    public MessageId messageId2() {
        return MESSAGE_ID_2;
    }

    @Override
    public MessageId messageId3() {
        return MESSAGE_ID_3;
    }

    @Override
    public MessageId messageId4() {
        return MESSAGE_ID_4;
    }

    @Override
    public ThreadId threadId1() {
        return THREAD_ID_1;
    }

    @Override
    public ThreadId threadId2() {
        return THREAD_ID_2;
    }

    @Override
    public ThreadId threadId3() {
        return THREAD_ID_3;
    }
}
