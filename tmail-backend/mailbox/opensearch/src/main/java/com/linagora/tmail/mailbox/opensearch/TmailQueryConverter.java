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
 ********************************************************************/

package com.linagora.tmail.mailbox.opensearch;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.query_dsl.DecayFunction;
import org.opensearch.client.opensearch._types.query_dsl.DecayPlacement;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScore;
import org.opensearch.client.opensearch._types.query_dsl.FunctionScoreQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class TmailQueryConverter extends QueryConverter {
    private final boolean dateBasedDecayEnabled;

    @Inject
    public TmailQueryConverter(CriterionConverter criterionConverter,
                               TmailOpenSearchMailboxConfiguration tmailConfiguration) {
        super(criterionConverter);
        this.dateBasedDecayEnabled = tmailConfiguration.dateBasedDecayEnabled();
    }

    @Override
    public Query from(Collection<MailboxId> mailboxIds, SearchQuery query) {
        Query baseQuery = super.from(mailboxIds, query);
        if (dateBasedDecayEnabled && query.getSorts().isEmpty()) {
            return wrapWithDateDecay(baseQuery);
        }
        return baseQuery;
    }

    private Query wrapWithDateDecay(Query baseQuery) {
        FunctionScore dateDecay = new FunctionScore.Builder()
            .gauss(new DecayFunction.Builder()
                .field(JsonMessageConstants.DATE)
                .placement(new DecayPlacement.Builder()
                    .origin(JsonData.of(LocalDate.now(ZoneOffset.UTC).toString()))
                    .scale(JsonData.of("365d"))
                    .offset(JsonData.of("30d"))
                    .decay(0.5)
                    .build())
                .build())
            .build();
        return new FunctionScoreQuery.Builder()
            .query(baseQuery)
            .functions(dateDecay)
            .build()
            .toQuery();
    }
}
