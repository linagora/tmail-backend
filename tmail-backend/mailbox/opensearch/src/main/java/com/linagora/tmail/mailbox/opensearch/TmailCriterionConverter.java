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

import static org.apache.james.backends.opensearch.IndexCreationFactory.RAW;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchNoneQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.WildcardQuery;

import com.google.common.collect.ImmutableList;

public class TmailCriterionConverter extends DefaultCriterionConverter {
    private static final String NGRAM = "ngram";
    private static final String NGRAM_MIN_SHOULD_MATCH = "80%";
    private static final int NGRAM_MAX_INPUT_LENGTH = 6;

    private final TmailOpenSearchMailboxConfiguration tmailOpenSearchMailboxConfiguration;

    @Inject
    public TmailCriterionConverter(OpenSearchMailboxConfiguration openSearchMailboxConfiguration,
                                   TmailOpenSearchMailboxConfiguration tmailOpenSearchMailboxConfiguration) {
        super(openSearchMailboxConfiguration);

        this.tmailOpenSearchMailboxConfiguration = tmailOpenSearchMailboxConfiguration;
    }

    @Override
    public Query convertCriterion(SearchQuery.Criterion criterion) {
        return criterionConverterMap.get(criterion.getClass()).apply(criterion);
    }

    @Override
    protected Query convertSubject(SearchQuery.SubjectCriterion headerCriterion) {
        String normalizedValue = SearchUtil.getBaseSubject(headerCriterion.getSubject());
        Query existingQuery;
        if (isNgramSubject(headerCriterion)) {
            existingQuery = new BoolQuery.Builder()
                .should(convertRawSubject(headerCriterion))
                .should(new MatchQuery.Builder()
                    .field(JsonMessageConstants.SUBJECT + "." + NGRAM)
                    .query(new FieldValue.Builder()
                        .stringValue(headerCriterion.getSubject())
                        .build())
                    .minimumShouldMatch(NGRAM_MIN_SHOULD_MATCH)
                    .build().toQuery())
                .build()
                .toQuery();
        } else {
            existingQuery = convertRawSubject(headerCriterion);
        }
        return new BoolQuery.Builder()
            .should(existingQuery)
            .should(new WildcardQuery.Builder()
                .field(JsonMessageConstants.SUBJECT + "." + RAW)
                .value("*" + normalizedValue.toLowerCase() + "*")
                .caseInsensitive(true)
                .build().toQuery())
            .build()
            .toQuery();
    }

    private boolean isNgramSubject(SearchQuery.SubjectCriterion headerCriterion) {
        return tmailOpenSearchMailboxConfiguration.subjectNgramEnabled() &&
            (!tmailOpenSearchMailboxConfiguration.subjectNgramHeuristicEnabled() || headerCriterion.getSubject().length() <= NGRAM_MAX_INPUT_LENGTH);
    }

    private Query convertRawSubject(SearchQuery.SubjectCriterion headerCriterion) {
        String normalizedValue = SearchUtil.getBaseSubject(headerCriterion.getSubject());
        if (useQueryStringQuery && matchesQueryStringHeuristic(headerCriterion.getSubject())) {
            return new QueryStringQuery.Builder()
                .fields(ImmutableList.of(JsonMessageConstants.SUBJECT))
                .query(normalizedValue)
                .fuzziness(textFuzzinessSearchValue)
                .build().toQuery();
        } else {
            return new MatchQuery.Builder()
                .field(JsonMessageConstants.SUBJECT)
                .query(new FieldValue.Builder()
                    .stringValue(normalizedValue)
                    .build())
                .fuzziness(textFuzzinessSearchValue)
                .operator(Operator.And)
                .build()
                .toQuery();
        }
    }

    @Override
    protected Query manageAddressFields(String headerName, String value) {
        if (useQueryStringQuery && matchesQueryStringHeuristic(value)) {
            return new MatchNoneQuery.Builder()
                .build()
                .toQuery();
        }
        return super.manageAddressFields(headerName, value);
    }

    @Override
    protected Query convertTextCriterion(SearchQuery.TextCriterion textCriterion) {
        switch (textCriterion.getType()) {
            case ATTACHMENT_FILE_NAME:
                if (useQueryStringQuery && matchesQueryStringHeuristic(textCriterion.getOperator().getValue())) {
                    return new MatchNoneQuery.Builder()
                        .build()
                        .toQuery();
                }
                if (isNgramFilename(textCriterion)) {
                    return new BoolQuery.Builder()
                        .should(new MatchQuery.Builder()
                            .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILENAME)
                            .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                            .fuzziness(textFuzzinessSearchValue)
                            .operator(Operator.And)
                            .boost(2.0F)
                            .build()
                            .toQuery())
                        .should(new MatchQuery.Builder()
                            .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILENAME + "." + NGRAM)
                            .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                            .minimumShouldMatch(NGRAM_MIN_SHOULD_MATCH)
                            .build()
                            .toQuery())
                        .should(new TermQuery.Builder()
                            .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILE_EXTENSION)
                            .value(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                            .build()
                            .toQuery())
                        .build()
                        .toQuery();
                } else {
                    return new BoolQuery.Builder()
                        .should(new MatchQuery.Builder()
                            .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILENAME)
                            .query(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                            .fuzziness(textFuzzinessSearchValue)
                            .operator(Operator.And)
                            .build()
                            .toQuery())
                        .should(new TermQuery.Builder()
                            .field(JsonMessageConstants.ATTACHMENTS + "." + JsonMessageConstants.Attachment.FILE_EXTENSION)
                            .value(new FieldValue.Builder().stringValue(textCriterion.getOperator().getValue()).build())
                            .build()
                            .toQuery())
                        .build()
                        .toQuery();
                }
            default:
                return super.convertTextCriterion(textCriterion);
        }
    }

    private boolean isNgramFilename(SearchQuery.TextCriterion textCriterion) {
        return tmailOpenSearchMailboxConfiguration.attachmentFilenameNgramEnabled() &&
            (!tmailOpenSearchMailboxConfiguration.attachmentFilenameNgramHeuristicEnabled() || textCriterion.getOperator().getValue().length() <= NGRAM_MAX_INPUT_LENGTH);
    }
}
