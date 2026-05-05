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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.event.DomainBasedSignatureTextFactory;
import com.linagora.tmail.james.jmap.event.DomainSignatureTemplate;
import com.linagora.tmail.james.jmap.event.IdentityCreationRequestBuilder;
import com.linagora.tmail.james.jmap.event.MemoryDomainSignatureTemplateRepository;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DomainSignatureTemplateApplyServiceTest {

    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final SignatureText EN_SIG = new SignatureText("en text", "<p>en html</p>");
    private static final SignatureText FR_SIG = new SignatureText("fr text", "<p>fr html</p>");

    private MemoryDomainSignatureTemplateRepository signatureRepo;
    private MemoryJmapSettingsRepository jmapSettingsRepo;
    private DomainBasedSignatureTextFactory signatureFactory;
    private IdentityRepository identityRepository;
    private MemoryCustomIdentityDAO identityDAO;
    private UsersRepository usersRepository;
    private LDAPConnectionPool ldapConnectionPool;
    private LdapRepositoryConfiguration ldapConfiguration;
    private DomainSignatureTemplateApplyService testee;

    @BeforeEach
    void setUp() throws Exception {
        signatureRepo = new MemoryDomainSignatureTemplateRepository();
        jmapSettingsRepo = new MemoryJmapSettingsRepository();
        signatureFactory = new DomainBasedSignatureTextFactory(signatureRepo, jmapSettingsRepo);

        InVMEventBus eventBus = new InVMEventBus(
            new InVmEventDelivery(new RecordingMetricFactory()),
            RetryBackoffConfiguration.FAST,
            new MemoryEventDeadLetters());
        identityDAO = new MemoryCustomIdentityDAO(eventBus);
        DefaultIdentitySupplier defaultIdentitySupplier = mock(DefaultIdentitySupplier.class);
        when(defaultIdentitySupplier.listIdentities(any())).thenReturn(Flux.empty());
        identityRepository = new IdentityRepository(identityDAO, defaultIdentitySupplier);

        usersRepository = mock(UsersRepository.class);
        ldapConnectionPool = mock(LDAPConnectionPool.class);
        ldapConfiguration = mock(LdapRepositoryConfiguration.class);

        when(ldapConfiguration.getUserBase()).thenReturn("ou=people,dc=domain,dc=tld");
        when(ldapConfiguration.getUserIdAttribute()).thenReturn("mail");
        when(ldapConfiguration.getPerDomainBaseDN()).thenReturn(ImmutableMap.of());
        when(ldapConfiguration.getResolveLocalPartAttribute()).thenReturn(Optional.empty());

        testee = new DomainSignatureTemplateApplyService(
            signatureRepo, usersRepository, signatureFactory,
            identityRepository, ldapConnectionPool, ldapConfiguration);
    }

    @Test
    void applyShouldSetSignatureOnUserWithEmptyDefaultIdentity() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB));
        mockLdapReturnsAttributes(Map.of());
        saveDefaultIdentity(BOB, "", "");

        ApplyResult result = testee.apply(DOMAIN).block();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.error()).isEqualTo(0);
        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("en text");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>en html</p>");
    }

    @Test
    void applyShouldSkipUserWhoAlreadyHasSignature() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB));
        saveDefaultIdentity(BOB, "existing text", "<p>existing html</p>");

        ApplyResult result = testee.apply(DOMAIN).block();

        assertThat(result.applied()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.error()).isEqualTo(0);
    }

    @Test
    void applyShouldReturn404WhenNoDomainTemplate() {
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB));

        org.junit.jupiter.api.Assertions.assertThrows(
            DomainTemplateNotFoundException.class,
            () -> testee.apply(DOMAIN).block());
    }

    @Test
    void applyShouldInterpolateLdapPlaceholders() throws Exception {
        SignatureText template = new SignatureText(
            "Regards, {ldap:givenName} {ldap:sn}",
            "<p>Regards, {ldap:givenName} {ldap:sn}</p>");
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, template))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB));
        mockLdapReturnsAttributes(Map.of("givenName", "John", "sn", "Doe"));
        saveDefaultIdentity(BOB, "", "");

        testee.apply(DOMAIN).block();

        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("Regards, John Doe");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>Regards, John Doe</p>");
    }

    @Test
    void applyShouldCountErrorWithoutFailing() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB, ALICE));
        mockLdapThrows();
        saveDefaultIdentity(BOB, "", "");
        saveDefaultIdentity(ALICE, "", "");

        ApplyResult result = testee.apply(DOMAIN).block();

        assertThat(result.error()).isEqualTo(2);
        assertThat(result.applied()).isEqualTo(0);
    }

    @Test
    void applyShouldRespectUserLanguageSetting() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, EN_SIG,
            Locale.FRENCH, FR_SIG))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB));
        mockLdapReturnsAttributes(Map.of());
        Mono.from(jmapSettingsRepo.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();
        saveDefaultIdentity(BOB, "", "");

        testee.apply(DOMAIN).block();

        assertThat(defaultIdentitySignatureText(BOB)).isEqualTo("fr text");
        assertThat(defaultIdentitySignatureHtml(BOB)).isEqualTo("<p>fr html</p>");
    }

    @Test
    void applyShouldAggregateResultsAcrossMultipleUsers() throws Exception {
        signatureRepo.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();
        when(usersRepository.listUsersOfADomainReactive(DOMAIN)).thenReturn(Flux.just(BOB, ALICE));
        mockLdapReturnsAttributes(Map.of());
        saveDefaultIdentity(BOB, "", "");
        saveDefaultIdentity(ALICE, "existing", "<p>existing</p>");

        ApplyResult result = testee.apply(DOMAIN).block();

        assertThat(result.applied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.error()).isEqualTo(0);
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

    private void mockLdapReturnsAttributes(Map<String, String> attributes) throws Exception {
        SearchResult searchResult = mock(SearchResult.class);
        SearchResultEntry entry = mock(SearchResultEntry.class);
        List<Attribute> attrs = attributes.entrySet().stream()
            .map(e -> {
                Attribute attr = mock(Attribute.class);
                when(attr.getName()).thenReturn(e.getKey());
                when(attr.getValue()).thenReturn(e.getValue());
                return attr;
            })
            .toList();
        when(entry.getAttributes()).thenReturn(attrs);
        when(searchResult.getSearchEntries()).thenReturn(List.of(entry));
        when(ldapConnectionPool.search(anyString(), any(SearchScope.class), any(Filter.class))).thenReturn(searchResult);
    }

    private void mockLdapThrows() throws Exception {
        when(ldapConnectionPool.search(anyString(), any(SearchScope.class), any(Filter.class)))
            .thenThrow(new com.unboundid.ldap.sdk.LDAPSearchException(
                com.unboundid.ldap.sdk.ResultCode.SERVER_DOWN, "LDAP unavailable"));
    }
}
