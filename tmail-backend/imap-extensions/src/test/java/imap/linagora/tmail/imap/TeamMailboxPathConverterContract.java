package imap.linagora.tmail.imap;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

public interface TeamMailboxPathConverterContract {
    boolean RELATIVE = true;

    PathConverter pathConverter();

    Username teamMailboxUsername();

    MailboxSession mailboxSession();

    @Test
    default void buildFullPathShouldAcceptTeamMailboxName() {
        assertThat(pathConverter().buildFullPath("#TeamMailbox.sale"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    default void buildFullPathShouldShouldReturnFullTeamMailboxName() {
        assertThat(pathConverter().buildFullPath("#TeamMailbox.sale.INBOX"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale.INBOX"));
    }

    @Test
    default void buildFullPathWithTeamMailboxNamespaceShouldIgnoreCase() {
        assertThat(pathConverter().buildFullPath("#teammailbox.sale"))
            .isEqualTo(new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"));
    }

    @Test
    default void mailboxNameShouldReturnNamespaceAndNameWhenRelative() {
        assertThat(pathConverter().mailboxName(RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession()))
            .contains("#TeamMailbox.sale");
    }

    @Test
    default void mailboxNameShouldReturnNamespaceAndNameWhenNotRelative() {
        assertThat(pathConverter().mailboxName(!RELATIVE, new MailboxPath("#TeamMailbox", teamMailboxUsername(), "sale"), mailboxSession()))
            .contains("#TeamMailbox.sale");
    }
}
