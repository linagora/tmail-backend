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

/**
 * This class is copied & adapted from {@link org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter}
 */

package com.linagora.tmail.mailbox.opensearch;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class TmailCriterionConverter extends DefaultCriterionConverter {
    @Inject
    public TmailCriterionConverter(OpenSearchMailboxConfiguration openSearchMailboxConfiguration) {
        super(openSearchMailboxConfiguration);
    }

    @Override
    public Query convertCriterion(SearchQuery.Criterion criterion) {
        return criterionConverterMap.get(criterion.getClass()).apply(criterion);
    }
}
