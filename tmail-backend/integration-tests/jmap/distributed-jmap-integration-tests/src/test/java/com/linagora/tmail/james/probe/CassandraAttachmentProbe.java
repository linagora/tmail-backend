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

package com.linagora.tmail.james.probe;

import jakarta.inject.Inject;

import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.utils.GuiceProbe;

public class CassandraAttachmentProbe implements GuiceProbe {
    private final CassandraAttachmentDAOV2 cassandraAttachmentDAOV2;

    @Inject
    public CassandraAttachmentProbe(CassandraAttachmentDAOV2 cassandraAttachmentDAOV2) {
        this.cassandraAttachmentDAOV2 = cassandraAttachmentDAOV2;
    }

    public void delete(AttachmentId attachmentId) {
        cassandraAttachmentDAOV2.delete(attachmentId)
            .block();
    }
}
