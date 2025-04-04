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

package com.linagora.calendar.storage.mongodb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.calendar.storage.OpenPaaSUserDAO;
import com.linagora.calendar.storage.OpenPaaSUserDAOContract;

public class MongoDBOpenPaaSUserDAOTest implements OpenPaaSUserDAOContract {
    @RegisterExtension
    DockerMongoDBExtension mongo = new DockerMongoDBExtension();

    private MongoDBOpenPaaSUserDAO mongoDBOpenPaaSUserDAO;

    @BeforeEach
    void setUp() {
        MongoDBOpenPaaSDomainDAO domainDAO = new MongoDBOpenPaaSDomainDAO(mongo.getDb());
        domainDAO.add(USERNAME.getDomainPart().get()).block();
        domainDAO.add(USERNAME_2.getDomainPart().get()).block();
        mongoDBOpenPaaSUserDAO = new MongoDBOpenPaaSUserDAO(mongo.getDb(), domainDAO);
    }

    @Override
    public OpenPaaSUserDAO testee() {
        return mongoDBOpenPaaSUserDAO;
    }
}
