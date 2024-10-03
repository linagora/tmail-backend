package com.linagora.tmail.mailet;

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LDAPMailingListTest {
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Test
    void shouldResolveGroup() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("mygroup@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void shouldSupportOpenList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("group2@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user3@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void shouldAvoidLoopUsingRecordedRecipients() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("group2@lists.james.org")
            .build();
        LoopPrevention.RecordedRecipients recordedRecipients = LoopPrevention.RecordedRecipients.fromMail(mail);
        recordedRecipients.merge(new MailAddress("group2@lists.james.org")).recordOn(mail);
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void shouldNotFailWhenUnknownCategory() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("unknown-category@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user3@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldSanitizeCategory() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("format@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("bob@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("format@lists.james.org"));
    }

    @Test
    void shouldRejectNonLocalSendersWithInternalList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("group3@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("bob@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group3@lists.james.org"));
    }

    @Test
    void shouldRejectNonLocalSendersWithInternalListWhenCustomBusinessCategorySchema() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .setProperty("businessCategoryExtractAttribute", "cn")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("group30@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("bob@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group30@lists.james.org"));
    }

    @Test
    void shouldSupportInternalList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@localhost") // local server for FakeMailContext
            .recipient("group3@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void shouldSupportInternalListWhenCustomBusinessCategorySchema() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .setProperty("businessCategoryExtractAttribute", "cn")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@localhost") // local server for FakeMailContext
            .recipient("group30@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void shouldSupportMemberRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user4@james.org")
            .recipient("group4@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user@james.org"));
    }

    @Test
    void shouldSupportMemberRestrictedListWithUnrelatedMembers() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user6@james.org")
            .recipient("group5bis@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user6@james.org"));
    }

    @Test
    void shouldSupportOwnerRestrictedListWithUnrelatedOwners() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user6@james.org")
            .recipient("group5tier@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user6@james.org"));
    }

    @Test
    void shouldSupportMemberRestrictedListWhenMultivalued() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user@james.org")
            .recipient("group4@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user@james.org"));
    }

    @Test
    void shouldRejectNonGroupMembersWhenMemberRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@james.org")
            .recipient("group4@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("james-user3@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group4@lists.james.org"));
    }

    @Test
    void shouldSupportOwnerRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user4@james.org")
            .recipient("group5@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"),
                new MailAddress("james-user3@james.org"),
                new MailAddress("james-user4@james.org"));
    }

    @Test
    void shouldSupportOwnerRestrictedListAsMultivaluedField() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@james.org")
            .recipient("group5@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"),
                new MailAddress("james-user3@james.org"),
                new MailAddress("james-user4@james.org"));
    }

    @Test
    void shouldRejectNonOwnerWhenOwnerRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@james.org")
            .recipient("group5@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("james-user3@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group5@lists.james.org"));
    }

    @Test
    void shouldSplitMailWhenRejecting() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@james.org")
            .recipients("group5@lists.james.org", "james-user4@james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).containsOnly(new MailAddress("james-user4@james.org"));
    }

    @Test
    void shouldRejectWhenOwnerRestrictedListAnNoOwner() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@james.org")
            .recipient("noowner@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("james-user3@james.org"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("noowner@lists.james.org"));
    }

    @Test
    void shouldBreakLoopsByRemovingThem() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@james.org")
            .recipient("loop1@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void shouldSupportDomainRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@james.org")
            .recipient("group6@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldSupportAnyLocalOption() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "any-local")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@james.org")
            .recipient("group7@localhost")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user2@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldSupportDomainRestrictedListWithListsPrefix() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@lists.james.org")
            .recipient("group6@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user4@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldSupportLDAPInconsistencies() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@lists.james.org")
            .recipient("group8@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"));
    }

    @Test
    void shouldSupportGroupNesting() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@lists.james.org")
            .recipient("nested@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldSupportGroupNestingForOwners() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user2@lists.james.org")
            .recipient("nestedOwner@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user3@james.org"),
                new MailAddress("james-user5@james.org"));
    }

    @Test
    void shouldRejectWrongDomainWhenDomainRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("james-user3@localhost")
            .recipient("group6@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getSender()).isEqualTo(new MailAddress("james-user3@localhost"));
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group6@lists.james.org"));
    }

    @Test
    void shouldRejectNullSenderWhenDomainRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group6@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group6@lists.james.org"));
    }

    @Test
    void shouldRejectNullSenderWhenOwnerRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group5@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group5@lists.james.org"));
    }

    @Test
    void shouldRejectNullSenderWhenMemberRestrictedList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group4@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group4@lists.james.org"));
    }

    @Test
    void shouldRejectNullSenderWhenInternalList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group3@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients()).isEmpty();
        assertThat(mailetContext.getSentMails()).hasSize(1);
        FakeMailContext.SentMail sentMail = mailetContext.getSentMails().get(0);
        assertThat(sentMail.getState()).isEqualTo("rejectedSender");
        assertThat(sentMail.getRecipients()).containsOnly(new MailAddress("group3@lists.james.org"));
    }

    @Test
    void shouldAcceptNullSenderOpenList() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group2@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user3@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void shouldAcceptLargerBaseDN() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .recipient("group2@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user3@james.org"),
                new MailAddress("james-user2@james.org"));
    }

    @Test
    void requiredProcessingStateShouldReturnConfiguredValue() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();

        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        assertThat(testee.requiredProcessingState())
            .containsOnly(new ProcessingState("rejectedSender"));
    }

    @Test
    void shouldAddListHeaders() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("mygroup@lists.james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getPerRecipientSpecificHeaders().getHeadersByRecipient().get(new MailAddress("james-user@james.org")))
            .containsOnly(PerRecipientHeaders.Header.builder()
                    .name("List-Id")
                    .value("<mygroup@lists.james.org>")
                    .build(),
                PerRecipientHeaders.Header.builder()
                    .name("List-Post")
                    .value("<mailto:mygroup@lists.james.org>")
                    .build());
    }

    @Test
    void shouldPreserveOtherRecipients() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipients("mygroup@lists.james.org", "unrelated@james.org")
            .build();
        testee.service(mail);

        assertThat(mail.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"),
                new MailAddress("unrelated@james.org"));
    }

    @Test
    void cacheShouldReturnSameValue() throws Exception {
        LDAPMailingList testee = new LDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        FakeMailContext mailetContext = FakeMailContext.defaultContext();
        FakeMailetConfig config = FakeMailetConfig.builder()
            .mailetName("LDAPMailingList")
            .setProperty("baseDN", "ou=lists,dc=james,dc=org")
            .setProperty("rejectedSenderProcessor", "rejectedSender")
            .setProperty("mailingListPredicate", "lists-prefix")
            .setProperty("mailAttributeForGroups", "description")
            .mailetContext(mailetContext)
            .build();
        testee.init(config);

        FakeMail mail1 = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipients("mygroup@lists.james.org", "unrelated@james.org")
            .build();
        testee.service(mail1);

        FakeMail mail2 = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipients("mygroup@lists.james.org", "unrelated@james.org")
            .build();
        testee.service(mail2);

        assertThat(mail2.getRecipients())
            .containsOnly(new MailAddress("james-user@james.org"),
                new MailAddress("james-user2@james.org"),
                new MailAddress("unrelated@james.org"));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
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
}