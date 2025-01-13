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

import jakarta.inject.Inject;

import org.apache.james.imap.decode.parser.AuthenticateCommandParser;
import org.apache.james.imap.decode.parser.LoginCommandParser;
import org.apache.james.modules.protocols.ImapPackage;
import org.apache.james.utils.ClassName;

import com.google.common.collect.ImmutableList;

public class TMailImapAuthPackage extends ImapPackage.Impl {

    @Inject
    public TMailImapAuthPackage() {
        super(ImmutableList.of(new ClassName(TMailLoginProcessor.class.getCanonicalName()),
                new ClassName(TMailAuthenticateProcessor.class.getCanonicalName())),
            ImmutableList.of(new ClassName(LoginCommandParser.class.getCanonicalName()),
                new ClassName(AuthenticateCommandParser.class.getCanonicalName())),
            ImmutableList.of());
    }
}