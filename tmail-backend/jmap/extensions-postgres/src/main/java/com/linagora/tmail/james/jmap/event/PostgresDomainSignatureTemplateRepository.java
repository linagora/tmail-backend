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

package com.linagora.tmail.james.jmap.event;

import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.SIGNATURE_TEMPLATES;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.domainlist.postgres.PostgresDomainDataDefinition.PostgresDomainTable.DOMAIN;
import static org.jooq.JSONB.jsonb;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.jooq.JSONB;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;

import reactor.core.publisher.Mono;

public class PostgresDomainSignatureTemplateRepository implements DomainSignatureTemplateRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, String>>> TYPE_REF =
        new TypeReference<>() {};

    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresDomainSignatureTemplateRepository(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<Optional<DomainSignatureTemplate>> get(Domain domain) {
        return postgresExecutor.executeRow(dsl -> Mono.from(
                dsl.select(SIGNATURE_TEMPLATES)
                    .from(org.apache.james.domainlist.postgres.PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                    .where(DOMAIN.eq(domain.asString()))))
            .map(record -> Optional.ofNullable(record.get(SIGNATURE_TEMPLATES))
                .map(Throwing.function((JSONB jsonb) -> deserialize(jsonb.data())).sneakyThrow()))
            .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Void> store(Domain domain, DomainSignatureTemplate template) {
        String serialized = serialize(template);
        return postgresExecutor.executeVoid(dsl -> Mono.from(
            dsl.insertInto(org.apache.james.domainlist.postgres.PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME,
                    DOMAIN, SIGNATURE_TEMPLATES)
                .values(domain.asString(), jsonb(serialized))
                .onConflict(DOMAIN)
                .doUpdate()
                .set(SIGNATURE_TEMPLATES, jsonb(serialized))));
    }

    @Override
    public Mono<Void> delete(Domain domain) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(
            dsl.update(org.apache.james.domainlist.postgres.PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .setNull(SIGNATURE_TEMPLATES)
                .where(DOMAIN.eq(domain.asString()))));
    }

    private String serialize(DomainSignatureTemplate template) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                template.templates().entrySet().stream()
                    .collect(Collectors.toMap(
                        entry -> entry.getKey().toLanguageTag(),
                        entry -> Map.of("text", entry.getValue().textSignature(), "html", entry.getValue().htmlSignature()))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize DomainSignatureTemplate", e);
        }
    }

    private DomainSignatureTemplate deserialize(String json) throws JsonProcessingException {
        Map<String, Map<String, String>> raw = OBJECT_MAPPER.readValue(json, TYPE_REF);
        return new DomainSignatureTemplate(
            raw.entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> Locale.forLanguageTag(entry.getKey()),
                    entry -> new SignatureText(
                        entry.getValue().getOrDefault("text", ""),
                        entry.getValue().getOrDefault("html", "")))));
    }
}
