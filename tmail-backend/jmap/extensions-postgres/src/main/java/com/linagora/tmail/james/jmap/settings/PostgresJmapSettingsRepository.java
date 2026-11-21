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

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.core.UuidState;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresJmapSettingsRepository implements JmapSettingsRepository {
    private PostgresJmapSettingsDAO jmapSettingsDAO;

    @Inject
    public PostgresJmapSettingsRepository(PostgresJmapSettingsDAO jmapSettingsDAO) {
        this.jmapSettingsDAO = jmapSettingsDAO;
    }

    @Override
    public Publisher<JmapSettings> get(Username username) {
        return jmapSettingsDAO.getJmapSettings(username);
    }

    @Override
    public Publisher<UuidState> getLatestState(Username username) {
        return jmapSettingsDAO.getState(username)
            .defaultIfEmpty(JmapSettingsStateFactory.INITIAL());
    }

    @Override
    public Publisher<SettingsStateUpdate> reset(Username username, JmapSettingsUpsertRequest settings) {
        UuidState newState = JmapSettingsStateFactory.generateState();
        return checkSettingsCount(settings.settings())
            .then(jmapSettingsDAO.getState(username))
            .flatMap(oldState -> jmapSettingsDAO.updateFullSettings(username,
                    newState,
                    CollectionConverters.asJava(settings.settings()))
                .then(Mono.just(new SettingsStateUpdate(oldState, newState))))
            .switchIfEmpty(jmapSettingsDAO.saveSettings(username, newState, CollectionConverters.asJava(settings.settings()))
                .then(Mono.just(new SettingsStateUpdate(JmapSettingsStateFactory.INITIAL(), newState))));
    }

    @Override
    public Publisher<SettingsStateUpdate> updatePartial(Username username, JmapSettingsPatch settingsPatch) {
        Preconditions.checkArgument(!settingsPatch.isEmpty(), "Cannot update when upsert and remove is empty");
        Preconditions.checkArgument(!settingsPatch.isConflict(), "Cannot update and remove the same setting key");
        UuidState newState = JmapSettingsStateFactory.generateState();
        // Read-check-write: like the state computation, the settings count check is not atomic with the update.
        return jmapSettingsDAO.getJmapSettings(username)
            .flatMap(currentSettings -> checkSettingsCount(settingsPatch.applyTo(currentSettings.settings()))
                .thenReturn(currentSettings.state()))
            .flatMap(oldState -> jmapSettingsDAO.updateSettings(username,
                    newState,
                    CollectionConverters.asJava(settingsPatch.toUpsert().settings()),
                    CollectionConverters.asJava(settingsPatch.toRemove()))
                .then(Mono.just(new SettingsStateUpdate(oldState, newState))))
            .switchIfEmpty(Mono.defer(() -> checkSettingsCount(settingsPatch.toUpsert().settings()))
                .then(jmapSettingsDAO.saveSettings(username, newState, CollectionConverters.asJava(settingsPatch.toUpsert().settings())))
                .then(Mono.just(new SettingsStateUpdate(JmapSettingsStateFactory.INITIAL(), newState))));
    }

    private Mono<Void> checkSettingsCount(scala.collection.immutable.Map<JmapSettingsKey, JmapSettingsValue> settings) {
        return JmapSettings.validateSettingsCount(settings)
            .<Mono<Void>>fold(Mono::error, any -> Mono.empty());
    }

    @Override
    public Publisher<Void> delete(Username username) {
        return jmapSettingsDAO.clearSettings(username);
    }
}
