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

package com.linagora.tmail.imap;

import org.apache.james.modules.protocols.ImapPackage;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TMailImapPackage extends ImapPackage.Impl {

    static final ImapPackage AGGREGATE = ImapPackage.and(ImmutableList.of(new DefaultNoAuth(), new TMailImapAuthPackage()));

    @Inject
    public TMailImapPackage() {
        super(AGGREGATE.processors()
                .stream().collect(ImmutableList.toImmutableList()),
            AGGREGATE.decoders()
                .stream().collect(ImmutableList.toImmutableList()),
            AGGREGATE.encoders()
                .stream().collect(ImmutableList.toImmutableList()));
    }
}
