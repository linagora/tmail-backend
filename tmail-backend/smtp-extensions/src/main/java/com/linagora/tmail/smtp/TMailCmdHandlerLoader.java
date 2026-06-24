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

package com.linagora.tmail.smtp;

import java.util.List;

import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.smtpserver.CoreCmdHandlerLoader;

public class TMailCmdHandlerLoader implements HandlersPackage {
    private final List<String> commands;

    public TMailCmdHandlerLoader() {
        this.commands = new CoreCmdHandlerLoader().getHandlers();
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
