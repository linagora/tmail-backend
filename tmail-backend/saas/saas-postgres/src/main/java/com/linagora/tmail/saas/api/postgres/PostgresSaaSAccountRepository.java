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

package com.linagora.tmail.saas.api.postgres;

import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.CAN_UPGRADE;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.IS_PAYING;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.RATE_LIMITING;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.USERNAME;
import static org.jooq.JSONB.jsonb;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.jooq.JSONB;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.RateLimitingDefinition;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class PostgresSaaSAccountRepository implements SaaSAccountRepository {
    private final PostgresExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public PostgresSaaSAccountRepository(PostgresExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(dsl.select(CAN_UPGRADE, IS_PAYING, RATE_LIMITING)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString())))))
            .mapNotNull(row  -> new SaaSAccount(
                Optional.ofNullable(row.get(CAN_UPGRADE)).orElse(SaaSAccount.DEFAULT.canUpgrade()),
                Optional.ofNullable(row.get(IS_PAYING)).orElse(SaaSAccount.DEFAULT.isPaying()),
                getRateLimiting(row)))
            .switchIfEmpty(Mono.just(SaaSAccount.DEFAULT));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        JSONB rateLimitingJsonb = jsonb(asJson(saaSAccount.rateLimiting()));

        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())
            .set(RATE_LIMITING, rateLimitingJsonb)
            .onConflict(USERNAME)
            .doUpdate()
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())
            .set(RATE_LIMITING, rateLimitingJsonb)));
    }

    private RateLimitingDefinition getRateLimiting(org.jooq.Record row) {
        return Optional.ofNullable(row.get(RATE_LIMITING))
            .map(jsonb -> {
                try {
                    return objectMapper.readValue(jsonb.data(), RateLimitingDefinition.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize SaaS rate limiting: " + jsonb.data(), e);
                }
            })
            .orElse(RateLimitingDefinition.UNLIMITED);
    }

    private String asJson(RateLimitingDefinition rateLimitingDefinition) {
        try {
            return objectMapper.writeValueAsString(rateLimitingDefinition);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SaaS rate limiting: " + rateLimitingDefinition, e);
        }
    }
}
