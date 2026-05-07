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

package com.linagora.tmail.james.jmap.domainsignature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.domainsignature.Options;
import com.linagora.tmail.james.jmap.event.DomainBasedSignatureTextFactory;
import com.linagora.tmail.james.jmap.event.DomainSignatureTemplate;
import com.linagora.tmail.james.jmap.event.IdentityCreationRequestBuilder;
import com.linagora.tmail.james.jmap.event.MemoryDomainSignatureTemplateRepository;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DomainSignatureTemplateApplyServiceTest {

    private static final String LDAP_ADMIN_PASSWORD = "mysecretpassword";
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final SignatureText EN_SIG = new SignatureText("en text", "<p>en html</p>");
    private static final SignatureText FR_SIG = new SignatureText("fr text", "<p>fr html</p>");

    static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain("domain.tld")
        .password(LDAP_ADMIN_PASSWORD)
        .dockerFilePrefix("domain-signature-ldap/")
        .build();

    private MemoryDomainSignatureTemplateRepository signatureRepo;
    private MemoryJmapSettingsRepository jmapSettingsRepo;
    private DomainBasedSignatureTextFactory signatureFactory;
    private IdentityRepository identityRepository;
    private ReadOnlyUsersLDAPRepository usersRepository;
    private LdapRepositoryConfiguration ldapRepositoryConfiguration;
    private LDAPConnectionPool ldapConnectionPool;
    private DomainSignatureTemplateApplyService testee;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        signatureRepo = new MemoryDomainSignatureTemplateRepository();
        jmapSettingsRepo = new MemoryJmapSettingsRepository();
        signatureFactory = new DomainBasedSignatureTextFactory(signatureRepo, jmapSettingsRepo);

        InVMEventBus eventBus = new InVMEventBus(
            new InVmEventDelivery(new RecordingMetricFactory()),
            RetryBackoffConfiguration.FAST,
            new MemoryEventDeadLetters());
        MemoryCustomIdentityDAO identityDAO = new MemoryCustomIdentityDAO(eventBus);

        SimpleDomainList identityDomainList = new SimpleDomainList();
        identityDomainList.addDomain(DOMAIN);
        MemoryRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
        rrt.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        CanSendFromImpl canSendFrom = new CanSendFromImpl(new AliasReverseResolverImpl(rrt));
        MemoryUsersRepository usersRepoForIdentity = MemoryUsersRepository.withVirtualHosting(identityDomainList);
        DefaultIdentitySupplier defaultIdentitySupplier = new DefaultIdentitySupplier(canSendFrom, usersRepoForIdentity);
        identityRepository = new IdentityRepository(identityDAO, defaultIdentitySupplier);

        HierarchicalConfiguration<ImmutableNode> ldapConfig = ldapConfig();
        ldapRepositoryConfiguration = LdapRepositoryConfiguration.from(ldapConfig);
        ldapConnectionPool = new LDAPConnectionFactory(ldapRepositoryConfiguration).getLdapConnectionPool();

        usersRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList(), new NoopGaugeRegistry(),
            ldapConnectionPool, ldapRepositoryConfiguration);
        usersRepository.configure(ldapConfig);
        usersRepository.init();

        testee = new DomainSignatureTemplateApplyService(
            signatureRepo, usersRepository, signatureFactory,
            identityRepository, ldapConnectionPool, ldapRepositoryConfiguration);
    }

    @Test
    void applyShouldSetSignatureOnUserWithEmptyDefaultIdentity() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        saveDefaultIdentity(BOB, "", "");

        ApplyResult result = testee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1); // ALICE: no saved identity → skipped
        assertThat(result.error()).isEqualTo(0);
        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("en text");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>en html</p>");
    }

    @Test
    void applyShouldSkipUserWhoAlreadyHasSignature() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        saveDefaultIdentity(BOB, "existing text", "<p>existing html</p>");

        ApplyResult result = testee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(result.applied()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(2); // BOB: existing sig + ALICE: no saved identity
        assertThat(result.error()).isEqualTo(0);
    }

    @Test
    void applyShouldOverwriteExistingSignatureWhenOptionEnabled() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        saveDefaultIdentity(BOB, "existing text", "<p>existing html</p>");

        ApplyResult result = testee.apply(DOMAIN, new Options(true)).block();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1); // ALICE: no saved identity → skipped
        assertThat(result.error()).isEqualTo(0);
        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("en text");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>en html</p>");
    }

    @Test
    void applyShouldReturn404WhenNoDomainTemplate() {
        org.junit.jupiter.api.Assertions.assertThrows(
            DomainTemplateNotFoundException.class,
            () -> testee.apply(DOMAIN, Options.DEFAULT()).block());
    }

    @Test
    void applyShouldInterpolateLdapPlaceholders() throws Exception {
        SignatureText template = new SignatureText(
            "Regards, {ldap:givenName} {ldap:sn}",
            "<p>Regards, {ldap:givenName} {ldap:sn}</p>");
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, template))).block();
        saveDefaultIdentity(BOB, "", "");

        testee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("Regards, John Doe");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>Regards, John Doe</p>");
    }

    @Test
    void applyShouldCountErrorWithoutFailing() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        saveDefaultIdentity(BOB, "", "");
        saveDefaultIdentity(ALICE, "", "");

        LDAPConnectionPool closedPool = new LDAPConnectionFactory(ldapRepositoryConfiguration).getLdapConnectionPool();
        closedPool.close();
        DomainSignatureTemplateApplyService brokenTestee = new DomainSignatureTemplateApplyService(
            signatureRepo, usersRepository, signatureFactory, identityRepository, closedPool, ldapRepositoryConfiguration);

        ApplyResult result = brokenTestee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(result.error()).isEqualTo(2);
        assertThat(result.applied()).isEqualTo(0);
    }

    @Test
    void applyShouldRespectUserLanguageSetting() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, EN_SIG,
            Locale.FRENCH, FR_SIG))).block();
        Mono.from(jmapSettingsRepo.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();
        saveDefaultIdentity(BOB, "", "");

        testee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("fr text");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>fr html</p>");
    }

    @Test
    void applyShouldAggregateResultsAcrossMultipleUsers() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        saveDefaultIdentity(BOB, "", "");
        saveDefaultIdentity(ALICE, "existing", "<p>existing</p>");

        ApplyResult result = testee.apply(DOMAIN, Options.DEFAULT()).block();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.error()).isEqualTo(0);
    }

    private static HierarchicalConfiguration<ImmutableNode> ldapConfig() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=domain,dc=tld");
        configuration.addProperty("[@credentials]", LDAP_ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=domain,dc=tld");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        configuration.addProperty("supportsVirtualHosting", true);
        return configuration;
    }

    private void saveDefaultIdentity(Username user, String text, String html) throws Exception {
        Mono.from(identityRepository.save(user, IdentityCreationRequestBuilder.builder()
            .email(user.asMailAddress())
            .name(user.asString())
            .sortOrder(0)
            .textSignature(text)
            .htmlSignature(html)
            .build())).block();
    }

    private String defaultIdentitySignatureText(Username user) {
        return Flux.from(identityRepository.list(user))
            .cast(Identity.class)
            .filter(identity -> identity.mayDelete())
            .blockFirst()
            .textSignature();
    }

    private String defaultIdentitySignatureHtml(Username user) {
        return Flux.from(identityRepository.list(user))
            .cast(Identity.class)
            .filter(identity -> identity.mayDelete())
            .blockFirst()
            .htmlSignature();
    }
}
