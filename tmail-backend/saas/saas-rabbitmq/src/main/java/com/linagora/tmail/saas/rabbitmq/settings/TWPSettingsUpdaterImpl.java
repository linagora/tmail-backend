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

package com.linagora.tmail.saas.rabbitmq.settings;


import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;
import com.linagora.tmail.james.jmap.settings.TWPReadOnlyPropertyProvider;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class TWPSettingsUpdaterImpl implements TWPSettingsUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(TWPSettingsUpdaterImpl.class);
    private static final JmapSettingsKey LANGUAGE = JmapSettingsKey.liftOrThrow("language");

    private final UsersRepository usersRepository;
    private final JmapSettingsRepository jmapSettingsRepository;

    @Inject
    public TWPSettingsUpdaterImpl(UsersRepository usersRepository,
                                  JmapSettingsRepository jmapSettingsRepository) {
        this.usersRepository = usersRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
    }

    @Override
    public Mono<Void> updateSettings(TWPCommonSettingsMessage message) {
        return resolveUser(message)
            .flatMap(username -> updateIfNewerVersion(message, username))
            .then();
    }

    private Mono<Username> resolveUser(TWPCommonSettingsMessage message) {
        return Mono.fromCallable(() -> usersRepository.getUserByName(Username.of(message.payload().email())))
            .map(User::getUserName);
    }

    private Mono<Void> updateIfNewerVersion(TWPCommonSettingsMessage message, Username username) {
        return getStoredSettingsVersion(username)
            .flatMap(storedVersion -> {
                if (message.version() <= storedVersion) {
                    LOGGER.warn("Received outdated TWP settings update for user {}. Current stored version: {}, received version: {}. Ignoring update.",
                        username.asString(), storedVersion, message.version());
                    return Mono.empty();
                }
                return applySettingsUpdate(message, username);
            });
    }

    private Mono<Long> getStoredSettingsVersion(Username username) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(jmapSettings -> OptionConverters.toJava(JmapSettingsUtil.getTWPSettingsVersion(jmapSettings))
                .orElse(TWPReadOnlyPropertyProvider.TWP_SETTINGS_VERSION_DEFAULT))
            .switchIfEmpty(Mono.just(TWPReadOnlyPropertyProvider.TWP_SETTINGS_VERSION_DEFAULT));
    }

    private Mono<Void> applySettingsUpdate(TWPCommonSettingsMessage message, Username username) {
        return message.languageSettings()
            .map(languageSetting -> {
                JmapSettingsPatch languagePatch = JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE, languageSetting.language());
                JmapSettingsPatch versionPatch = JmapSettingsPatch$.MODULE$.toUpsert(TWPReadOnlyPropertyProvider.TWP_SETTINGS_VERSION, String.valueOf(languageSetting.version()));
                JmapSettingsPatch combinedPatch = JmapSettingsPatch$.MODULE$.merge(languagePatch, versionPatch);
                return Mono.from(jmapSettingsRepository.updatePartial(username, combinedPatch))
                    .doOnNext(updatedSettings -> LOGGER.info("Updated language setting for user {} to {}", username.asString(), languageSetting.language()))
                    .then();
            })
            .orElse(Mono.empty());
    }
}