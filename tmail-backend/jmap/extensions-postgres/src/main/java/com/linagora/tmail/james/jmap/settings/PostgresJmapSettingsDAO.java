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

package com.linagora.tmail.james.jmap.settings;

import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.SETTINGS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.SETTINGS_STATE;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.TABLE_NAME;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.USERNAME;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.core.UuidState;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresJmapSettingsDAO {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresJmapSettingsDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<JmapSettings> getJmapSettings(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(SETTINGS, SETTINGS_STATE)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .filter(this::settingsExists)
            .map(this::toJmapSettings);
    }

    public Mono<UuidState> getState(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(SETTINGS_STATE)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .filter(this::settingsExists)
            .map(record -> new UuidState(record.get(SETTINGS_STATE)));
    }

    public Mono<Void> saveSettings(Username username, UuidState newState, Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return postgresExecutor.executeVoid(dslContext -> {
            Hstore settings = toHstore(newSettings);
            return Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(USERNAME, username.asString())
                .set(SETTINGS_STATE, newState.value())
                .set(SETTINGS, settings)
                .onConflict(USERNAME)
                .doUpdate()
                .set(SETTINGS_STATE, newState.value())
                .set(SETTINGS, settings));
        });
    }

    public Mono<Void> updateFullSettings(Username username, UuidState newState, Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(SETTINGS_STATE, newState.value())
            .set(SETTINGS, toHstore(newSettings))
            .where(USERNAME.eq(username.asString()))));
    }

    public Mono<Void> updateSettings(Username username,
                                     UuidState newState,
                                     Map<JmapSettingsKey, JmapSettingsValue> addSettings,
                                     List<JmapSettingsKey> removeSettings) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(SETTINGS_STATE, newState.value())
            .set(DSL.field(SETTINGS.getName()),
                (Object) DSL.field("delete(" + SETTINGS.getName() + " || ?, ?)",
                    toHstore(addSettings),
                    removeSettings.stream().map(JmapSettingsKey::asString).toList().toArray(new String[0])))
            .where(USERNAME.eq(username.asString()))));

    }

    public Mono<Void> clearSettings(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(SETTINGS, (Hstore) null)
            .set(SETTINGS_STATE, (UUID) null)
            .where(USERNAME.eq(username.asString()))
        ));
    }

    private JmapSettings toJmapSettings(Record record) {
        return new JmapSettings(CollectionConverters.asScala(toMapSettingKeySettingValue(record.get(SETTINGS, Hstore.class).data())),
            new UuidState(record.get(SETTINGS_STATE)));
    }

    private Map<JmapSettingsKey, JmapSettingsValue> toMapSettingKeySettingValue(Map<String, String> map) {
        return map.entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> JmapSettingsKey.liftOrThrow(entry.getKey()),
                entry -> new JmapSettingsValue(entry.getValue())));
    }

    private Hstore toHstore(Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return Hstore.hstore(newSettings.entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(entry -> entry.getKey().asString(), entry -> entry.getValue().value())));
    }

    private boolean settingsExists(Record record) {
        return record.get(SETTINGS_STATE) != null;
    }
}
