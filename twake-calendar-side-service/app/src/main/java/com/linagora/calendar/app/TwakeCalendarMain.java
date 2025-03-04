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

package com.linagora.calendar.app;

import org.apache.james.ExtraProperties;

public class TwakeCalendarMain {
    public static void main(String[] args) throws Exception {
        main(createServer(TwakeCalendarConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build()));
    }

    static void main(TwakeCalendarGuiceServer server) throws Exception {
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    public static TwakeCalendarGuiceServer createServer(TwakeCalendarConfiguration configuration) {
        ExtraProperties.initialize();

        return TwakeCalendarGuiceServer.forConfiguration(configuration);
    }
}
