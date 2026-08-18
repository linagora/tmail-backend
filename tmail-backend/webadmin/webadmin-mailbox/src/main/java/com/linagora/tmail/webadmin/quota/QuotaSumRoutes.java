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

package com.linagora.tmail.webadmin.quota;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Parsers;

import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.webadmin.quota.dto.QuotaSumDTO;

import reactor.core.publisher.Mono;
import spark.Service;

public class QuotaSumRoutes implements Routes {
    private static final String BASE_PATH = "/quota";
    private static final String GLOBAL_SUM_ENDPOINT = BASE_PATH + "/sum";
    private static final String DOMAIN = "domainId";
    private static final String DOMAIN_SUM_ENDPOINT = BASE_PATH + "/domains/:" + DOMAIN + "/sum";

    private final QuotaSumDao quotaSumDao;
    private final JsonTransformer jsonTransformer;

    @Inject
    public QuotaSumRoutes(QuotaSumDao quotaSumDao, JsonTransformer jsonTransformer) {
        this.quotaSumDao = quotaSumDao;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        defineGlobalSum(service);
        defineDomainSum(service);
    }

    private void defineGlobalSum(Service service) {
        service.get(GLOBAL_SUM_ENDPOINT,
            (request, response) -> Mono.from(quotaSumDao.globalUsage())
                .map(QuotaSumDTO::from)
                .block(),
            jsonTransformer);
    }

    private void defineDomainSum(Service service) {
        service.get(DOMAIN_SUM_ENDPOINT,
            (request, response) -> {
                Domain domain = Parsers.parseDomain(request.params(DOMAIN));
                return Mono.from(quotaSumDao.domainUsage(domain))
                    .map(QuotaSumDTO::from)
                    .block();
            },
            jsonTransformer);
    }
}
