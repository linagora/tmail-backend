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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail;

public class DockerOpenPaasSetupSingleton {
    public static final DockerOpenPaasSetup singleton = new DockerOpenPaasSetup();
    private static final int MAX_TEST_PLAYED = 100;

    private static int testsPlayedCount = 0;

    static {
        singleton.start();
    }

    public static void incrementTestsPlayed() {
        testsPlayedCount += 1;
    }

    /*
     * Call this method to ensure that OpenPaas setup is restarted every MAX_TEST_PLAYED tests.
     */
    public static void restartIfMaxTestsPlayed() {
        if (testsPlayedCount > MAX_TEST_PLAYED) {
            testsPlayedCount = 0;
            restart();
        }
    }

    private static void restart() {
        singleton.stop();
        singleton.start();
    }
}
