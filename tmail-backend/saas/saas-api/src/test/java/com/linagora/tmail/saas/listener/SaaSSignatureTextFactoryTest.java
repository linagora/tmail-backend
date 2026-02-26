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

package com.linagora.tmail.saas.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSSignatureTextFactoryTest {
    private static final Username USER = Username.of("bob@domain.tld");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final String EN_TEXT = "Register on https://sign-up.twake.app !";
    private static final String EN_HTML = "<p>Register on <a href=\"https://sign-up.twake.app\">Twake</a> !</p>";
    private static final String FR_TEXT = "Inscrivez vous sur https://sign-up.twake.app !";
    private static final String FR_HTML = "<p>Inscrivez vous sur <a href=\"https://sign-up.twake.app\">Twake</a> !</p>";

    @Test
    void shouldReturnEmptyWhenPromotionSignaturesAreNotConfigured() {
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            configuration("""
                <listeners>
                    <listener>
                        <configuration>
                        </configuration>
                    </listener>
                </listeners>
                """));

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenUserIsPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(saaSAccountRepository, new MemoryJmapSettingsRepository(), promotionConfiguration());

        Mono.from(saaSAccountRepository.upsertSaasAccount(USER, new SaaSAccount(true, true))).block();

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnDefaultLanguageSignatureWhenNonPayingUserHasNoConfiguredLanguage() {
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(), promotionConfiguration());

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(EN_TEXT, EN_HTML));
    }

    @Test
    void shouldReturnConfiguredLanguageSignatureWhenLanguageIsSupported() {
        MemoryJmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), jmapSettingsRepository, promotionConfiguration());
        Mono.from(jmapSettingsRepository.updatePartial(USER, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(FR_TEXT, FR_HTML));
    }

    @Test
    void shouldFallbackToDefaultLanguageWhenLanguageIsUnsupported() {
        MemoryJmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), jmapSettingsRepository, promotionConfiguration());
        Mono.from(jmapSettingsRepository.updatePartial(USER, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "vi"))).block();

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(EN_TEXT, EN_HTML));
    }

    @Test
    void shouldUseConfiguredDefaultLanguageWhenUserLanguageIsAbsent() {
        SaaSSignatureTextFactory testee = new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            promotionConfiguration("fr"));

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(FR_TEXT, FR_HTML));
    }

    @Test
    void shouldThrowWhenDefaultLanguageIsNotDeclaredInPromotionSignatures() {
        assertThatThrownBy(() -> new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            configuration("""
                <listeners>
                    <listener>
                        <configuration>
                            <defaultText>
                                <defaultLanguage>en</defaultLanguage>
                                <fr>
                                    <textSignature><![CDATA[%s]]></textSignature>
                                    <htmlSignature><![CDATA[%s]]></htmlSignature>
                                </fr>
                            </defaultText>
                        </configuration>
                    </listener>
                </listeners>
                """.formatted(FR_TEXT, FR_HTML))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("`defaultLanguage` should be one of configured `defaultText` languages");
    }

    @Test
    void shouldThrowWhenTextSignatureIsMissing() {
        assertThatThrownBy(() -> new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            configuration("""
                <listeners>
                    <listener>
                        <configuration>
                            <defaultText>
                                <defaultLanguage>en</defaultLanguage>
                                <en>
                                    <htmlSignature><![CDATA[%s]]></htmlSignature>
                                </en>
                            </defaultText>
                        </configuration>
                    </listener>
                </listeners>
                """.formatted(EN_HTML))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing `textSignature`");
    }

    @Test
    void shouldThrowWhenHtmlSignatureIsMissing() {
        assertThatThrownBy(() -> new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            configuration("""
                <listeners>
                    <listener>
                        <configuration>
                            <defaultText>
                                <defaultLanguage>en</defaultLanguage>
                                <en>
                                    <textSignature><![CDATA[%s]]></textSignature>
                                </en>
                            </defaultText>
                        </configuration>
                    </listener>
                </listeners>
                """.formatted(EN_TEXT))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing `htmlSignature`");
    }

    @Test
    void shouldThrowWhenLocaleIsInvalid() {
        assertThatThrownBy(() -> new SaaSSignatureTextFactory(new MemorySaaSAccountRepository(), new MemoryJmapSettingsRepository(),
            configuration("""
                <listeners>
                    <listener>
                        <configuration>
                            <defaultText>
                                <defaultLanguage>invalidLocale</defaultLanguage>
                                <en>
                                    <textSignature><![CDATA[%s]]></textSignature>
                                    <htmlSignature><![CDATA[%s]]></htmlSignature>
                                </en>
                            </defaultText>
                        </configuration>
                    </listener>
                </listeners>
                """.formatted(EN_TEXT, EN_HTML))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("The provided language 'invalidLocale' can not be parsed to a valid Locale.");
    }

    private HierarchicalConfiguration<ImmutableNode> promotionConfiguration() {
        return promotionConfiguration("en");
    }

    private HierarchicalConfiguration<ImmutableNode> promotionConfiguration(String defaultLanguage) {
        return configuration("""
            <listeners>
                <listener>
                    <configuration>
                        <defaultText>
                            <defaultLanguage>%s</defaultLanguage>
                            <en>
                                <textSignature><![CDATA[%s]]></textSignature>
                                <htmlSignature><![CDATA[%s]]></htmlSignature>
                            </en>
                            <fr>
                                <textSignature><![CDATA[%s]]></textSignature>
                                <htmlSignature><![CDATA[%s]]></htmlSignature>
                            </fr>
                        </defaultText>
                    </configuration>
                </listener>
            </listeners>
            """.formatted(defaultLanguage, EN_TEXT, EN_HTML, FR_TEXT, FR_HTML));
    }

    private HierarchicalConfiguration<ImmutableNode> configuration(String rawConfiguration) {
        XMLConfiguration configuration = new XMLConfiguration();
        try {
            FileHandler fileHandler = new FileHandler(configuration);
            fileHandler.load(new StringReader(rawConfiguration));
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
        return configuration.configurationAt("listener.configuration");
    }
}
