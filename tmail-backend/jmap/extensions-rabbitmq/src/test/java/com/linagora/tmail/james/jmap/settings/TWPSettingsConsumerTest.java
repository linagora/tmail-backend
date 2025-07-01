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

package com.linagora.tmail.james.jmap.settings;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class TWPSettingsConsumerTest {
    private static final String LANGUAGE_KEY = "language";
    private static final JmapSettingsKey JMAP_LANGUAGE_KEY = JmapSettingsKey.liftOrThrow(LANGUAGE_KEY);
    private static final String LANGUAGE_FR = "fr";
    private static final String LANGUAGE_EN = "en";
    private static final String EXCHANGE_NAME = "settings";
    private static final String ROUTING_KEY = "user.settings.updated";
    private static final Username ALICE = Username.of("alice@james.org");
    private static final Username NON_EXISTING_USER = Username.of("nonExisting@james.org");

    private final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(Duration.ofMillis(500))
        .and()
        .with()
        .pollDelay(Duration.ofMillis(500))
        .await();
    private final ConditionFactory awaitAtMost = calmlyAwait.atMost(10, TimeUnit.SECONDS);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    UsersRepositoryContract.UserRepositoryExtension userRepositoryExtension = UsersRepositoryContract.UserRepositoryExtension.withVirtualHost();

    private static final LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .dockerFilePrefix("localpartLogin/")
        .build();

    public static ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> configuration,
                                                                   DomainList domainList) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList, new NoopGaugeRegistry(),
            LdapRepositoryConfiguration.from(configuration));
        ldapRepository.configure(configuration);
        ldapRepository.init();
        return ldapRepository;
    }

    public static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<String> resolveLocalPartAttribute) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN), resolveLocalPartAttribute);
    }

    public static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator, Optional<String> resolveLocalPartAttribute) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        resolveLocalPartAttribute.ifPresent(attribute -> configuration.addProperty("[@resolveLocalPartAttribute]", attribute));
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    public static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        return configuration;
    }

    private JmapSettingsRepository jmapSettingsRepository;
    private TWPSettingsConsumer testee;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @BeforeEach
    void setUp(UsersRepositoryContract.TestSystem testSystem) throws Exception {
        Optional<String> resolveLocalPartAttribute = Optional.of("uid");
        ReadOnlyUsersLDAPRepository usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, resolveLocalPartAttribute), testSystem.getDomainList());
        jmapSettingsRepository = new MemoryJmapSettingsRepository();

        TWPCommonSettingsConfiguration twpCommonSettingsConfiguration = new TWPCommonSettingsConfiguration(
            true,
            Optional.empty(),
            false,
            EXCHANGE_NAME,
            ROUTING_KEY);

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQExtension.getRabbitMQ().amqpUri())
            .managementUri(rabbitMQExtension.getRabbitMQ().managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        testee = new TWPSettingsConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            usersRepository,
            jmapSettingsRepository,
            twpCommonSettingsConfiguration);

        testee.init();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void shouldSetLanguageSettingForExistingUser() {
        String languageUpdate = createSettingsUpdateMessage(ALICE,
            Map.of(LANGUAGE_KEY, LANGUAGE_FR),
            1L);
        publishAmqpSettingsMessage(languageUpdate);

        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(ALICE)).block();
            assertThat(settings).isNotNull();
            assertThat(settings.settings().get(JMAP_LANGUAGE_KEY).get())
                .isEqualTo(new JmapSettingsValue(LANGUAGE_FR));
        });
    }

    @Test
    void shouldNotSetLanguageSettingForNonExistingUser() {
        String languageUpdate = createSettingsUpdateMessage(NON_EXISTING_USER,
            Map.of(LANGUAGE_KEY, LANGUAGE_FR),
            1L);
        publishAmqpSettingsMessage(languageUpdate);

        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(NON_EXISTING_USER)).block();
            assertThat(settings).isNull();
        });
    }

    @Test
    void shouldUpdateLanguageSettingWhenUserAlreadyHadLanguageSetting() {
        Mono.from(jmapSettingsRepository.updatePartial(ALICE,
            JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_EN))).block();

        String languageUpdate = createSettingsUpdateMessage(ALICE,
            Map.of(LANGUAGE_KEY, LANGUAGE_FR),
            1L);
        publishAmqpSettingsMessage(languageUpdate);

        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(ALICE)).block();
            assertThat(settings).isNotNull();
            assertThat(settings.settings().get(JMAP_LANGUAGE_KEY).get())
                .isEqualTo(new JmapSettingsValue(LANGUAGE_FR));
        });
    }

    @Test
    void shouldNotChangeTheExistingLanguageWhenMessageWithoutLanguageUpdate() {
        Mono.from(jmapSettingsRepository.updatePartial(ALICE, JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_EN))).block();

        String emptyUpdate = createSettingsUpdateMessage(ALICE,
            Map.of(),
            1L);
        publishAmqpSettingsMessage(emptyUpdate);

        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(ALICE)).block();
            assertThat(settings).isNotNull();
            assertThat(settings.settings().get(JMAP_LANGUAGE_KEY).get())
                .isEqualTo(new JmapSettingsValue(LANGUAGE_EN));
        });
    }

    @Test
    void shouldNotUpdateOtherUsersSetting() {
        // GIVEN james-user already has a language setting
        Mono.from(jmapSettingsRepository.updatePartial(JAMES_USER,
            JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_EN))).block();

        // WHEN alice updates her language setting
        String languageUpdate = createSettingsUpdateMessage(ALICE,
            Map.of(LANGUAGE_KEY, LANGUAGE_FR),
            1L);
        publishAmqpSettingsMessage(languageUpdate);

        // THEN only james-user's language setting is not updated
        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(JAMES_USER)).block();
            assertThat(settings).isNotNull();
            assertThat(settings.settings().get(JMAP_LANGUAGE_KEY).get())
                .isEqualTo(new JmapSettingsValue(LANGUAGE_EN));
        });
    }

    @Test
    void invalidAmqpMessageShouldNotCrashConsumer() {
        String invalidJson = "{ invalid json }";
        publishAmqpSettingsMessage(invalidJson);

        // Verify that the consumer doesn't crash and continues to process valid messages
        String languageUpdate = createSettingsUpdateMessage(ALICE,
            Map.of(LANGUAGE_KEY, LANGUAGE_FR),
            1L);
        publishAmqpSettingsMessage(languageUpdate);

        awaitAtMost.untilAsserted(() -> {
            JmapSettings settings = Mono.from(jmapSettingsRepository.get(ALICE)).block();
            assertThat(settings).isNotNull();
            assertThat(settings.settings().get(JMAP_LANGUAGE_KEY).get())
                .isEqualTo(new JmapSettingsValue(LANGUAGE_FR));
        });
    }

    private String createSettingsUpdateMessage(Username username, Map<String, String> settingsUpdatePayload, long version) {
        ImmutableMap<String, String> payload = ImmutableMap.<String, String>builder()
            .putAll(settingsUpdatePayload)
            .put("email", username.asString())
            .build();

        String settingsUpdatePayloadAsJson = payload.entrySet().stream()
            .map(entry -> String.format("\"%s\": \"%s\"", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(",\n"));

        return String.format("""
        {
            "source": "twake-mail",
            "nickname": "%s",
            "request_id": "%s",
            "timestamp": %d,
            "payload": {
                %s
            },
            "version": %d
        }
        """, username.asString(),
            UUID.randomUUID(),
            System.currentTimeMillis(),
            settingsUpdatePayloadAsJson,
            version);
    }

    private void publishAmqpSettingsMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                EXCHANGE_NAME,
                ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }
}
