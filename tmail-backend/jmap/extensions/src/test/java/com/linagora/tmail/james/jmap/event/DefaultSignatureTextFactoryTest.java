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

import reactor.core.publisher.Mono;

public class DefaultSignatureTextFactoryTest {
    private static final Username USER = Username.of("bob@domain.tld");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final String EN_TEXT = "Register on https://sign-up.twake.app !";
    private static final String EN_HTML = "<p>Register on <a href=\"https://sign-up.twake.app\">Twake</a> !</p>";
    private static final String FR_TEXT = "Inscrivez vous sur https://sign-up.twake.app !";
    private static final String FR_HTML = "<p>Inscrivez vous sur <a href=\"https://sign-up.twake.app\">Twake</a> !</p>";

    @Test
    void shouldReturnEmptyWhenApplyWhenFilterReturnsFalse() {
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
            signatureConfiguration(),
            username -> Mono.just(false));

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnSignatureWhenApplyWhenFilterIsAlways() {
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
            signatureConfiguration(),
            new ApplyWhenFilter.Always());

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(EN_TEXT, EN_HTML));
    }

    @Test
    void shouldReturnEmptyWhenSignaturesAreNotConfigured() {
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
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
    void shouldReturnDefaultLanguageSignatureWhenUserHasNoConfiguredLanguage() {
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(), signatureConfiguration());

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(EN_TEXT, EN_HTML));
    }

    @Test
    void shouldReturnConfiguredLanguageSignatureWhenLanguageIsSupported() {
        MemoryJmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(jmapSettingsRepository, signatureConfiguration());
        Mono.from(jmapSettingsRepository.updatePartial(USER, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(FR_TEXT, FR_HTML));
    }

    @Test
    void shouldFallbackToDefaultLanguageWhenLanguageIsUnsupported() {
        MemoryJmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(jmapSettingsRepository, signatureConfiguration());
        Mono.from(jmapSettingsRepository.updatePartial(USER, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "vi"))).block();

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(EN_TEXT, EN_HTML));
    }

    @Test
    void shouldUseConfiguredDefaultLanguageWhenUserLanguageIsAbsent() {
        DefaultSignatureTextFactory testee = new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
            signatureConfiguration("fr"));

        Optional<SignatureText> result = testee.forUser(USER).block();
        assertThat(result).contains(new SignatureText(FR_TEXT, FR_HTML));
    }

    @Test
    void shouldThrowWhenDefaultLanguageIsNotDeclaredInI18NSignatures() {
        assertThatThrownBy(() -> new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
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
        assertThatThrownBy(() -> new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
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
        assertThatThrownBy(() -> new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
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
        assertThatThrownBy(() -> new DefaultSignatureTextFactory(new MemoryJmapSettingsRepository(),
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

    private HierarchicalConfiguration<ImmutableNode> signatureConfiguration() {
        return signatureConfiguration("en");
    }

    private HierarchicalConfiguration<ImmutableNode> signatureConfiguration(String defaultLanguage) {
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
