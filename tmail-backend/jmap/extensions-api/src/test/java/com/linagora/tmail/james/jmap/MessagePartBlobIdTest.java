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
 *******************************************************************/

package com.linagora.tmail.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.TestMessageId;
import org.junit.jupiter.api.Test;

class MessagePartBlobIdTest {
    private static final TestMessageId.Factory MESSAGE_ID_FACTORY = new TestMessageId.Factory();
    private static final String LONG_BLOB_ID = "1" + "_2".repeat(100_000);

    @Test
    void tryParseShouldAcceptALongBlobId() {
        assertThat(MessagePartBlobId.tryParse(MESSAGE_ID_FACTORY, LONG_BLOB_ID).get().getPartIds())
            .hasSize(100_000);
    }

    @Test
    void tryParseShouldRejectALongInvalidBlobId() {
        assertThat(MessagePartBlobId.tryParse(MESSAGE_ID_FACTORY, LONG_BLOB_ID + "_").isFailure())
            .isTrue();
    }
}
