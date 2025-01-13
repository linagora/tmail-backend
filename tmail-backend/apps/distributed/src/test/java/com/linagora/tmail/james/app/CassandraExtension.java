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

package com.linagora.tmail.james.app;

import org.apache.james.GuiceModuleTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class CassandraExtension implements GuiceModuleTestExtension {

    private final DockerCassandraRule cassandra;

    public CassandraExtension() {
        this.cassandra = new DockerCassandraRule();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        cassandra.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        cassandra.stop();
    }

    @Override
    public Module getModule() {
        return cassandra.getModule();
    }

    public DockerCassandraRule getCassandra() {
        return cassandra;
    }

    public void pause() {
        cassandra.pause();
    }

    public void unpause() {
        cassandra.unpause();
    }
}
