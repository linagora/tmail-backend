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

package com.linagora.calendar.storage;

import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.core.Domain;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class DomainConfiguration {


    public static DomainConfiguration parseConfiguration(Configuration configuration) {
        return new DomainConfiguration(Splitter.on(',').splitToStream(configuration.getString("domains", "linagora.com"))
            .map(Domain::of)
            .collect(ImmutableList.toImmutableList()));
    }

    private final List<Domain> domains;

    DomainConfiguration(List<Domain> domains) {
        this.domains = domains;
    }

    public List<Domain> getDomains() {
        return domains;
    }
}
