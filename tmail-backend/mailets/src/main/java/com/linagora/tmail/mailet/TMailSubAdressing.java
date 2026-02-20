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

package com.linagora.tmail.mailet;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.ExactNameCaseInsensitive;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.transport.mailets.SubAddressing;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.team.TeamMailbox;

import scala.jdk.javaapi.OptionConverters;

public class TMailSubAdressing extends SubAddressing {
    private static final String TEAM_MAILBOX_NAMESPACE = "#TeamMailbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(TMailSubAdressing.class);

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    public TMailSubAdressing(UsersRepository usersRepository, @Named("mailboxmanager") MailboxManager mailboxManager) {
        super(usersRepository, mailboxManager);
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    @Override
    protected Optional<MailboxPath> getPathWithCorrectCase(MailAddress recipient, String encodedTargetFolder) throws UsersRepositoryException, MailboxException {
        MailAddress strippedRecipient = recipient.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER);
        Optional<TeamMailbox> maybeTeamMailbox = OptionConverters.toJava(TeamMailbox.asTeamMailbox(strippedRecipient));
        if (maybeTeamMailbox.isPresent()) {
            return findTeamMailboxFolder(maybeTeamMailbox.get(), encodedTargetFolder);
        }
        return super.getPathWithCorrectCase(recipient, encodedTargetFolder);
    }

    private Optional<MailboxPath> findTeamMailboxFolder(TeamMailbox teamMailbox, String encodedTargetFolder) {
        String decodedTargetFolder = URLDecoder.decode(encodedTargetFolder, StandardCharsets.UTF_8);
        String fullFolderName = teamMailbox.mailboxPath(decodedTargetFolder).getName();
        Comparator<MailboxPath> exactMatchFirst = Comparator.comparing(path -> path.getName().equals(fullFolderName) ? 0 : 1);

        MailboxSession session = mailboxManager.createSystemSession(teamMailbox.self());
        return mailboxManager.search(
                MailboxQuery.builder()
                    .namespace(TEAM_MAILBOX_NAMESPACE)
                    .username(teamMailbox.owner())
                    .expression(new ExactNameCaseInsensitive(fullFolderName))
                    .build(),
                session)
            .toStream()
            .map(MailboxMetaData::getPath)
            .sorted(exactMatchFirst)
            .findFirst();
    }

    protected void postIfHasRight(Mail mail, MailAddress recipient, Optional<MailboxPath> targetFolderPath) throws UsersRepositoryException, MailboxException {
        if (hasPostRight(mail, recipient, targetFolderPath)) {
            StorageDirective.builder().targetFolders(ImmutableList.of(extractTeamMailboxSubpart(targetFolderPath.get().getName()))).build()
                .encodeAsAttributes(usersRepository.getUsername(recipient))
                .forEach(mail::setAttribute);
        } else {
            LOGGER.info("{} tried to address {}'s subfolder `{}` but they did not have the right to",
                mail.getMaybeSender().toString(), recipient, targetFolderPath);
        }
    }

    private String extractTeamMailboxSubpart(String mailboxName) {
        int position = mailboxName.indexOf(MailboxConstants.FOLDER_DELIMITER);
        if (position > 0 && position + 1 < mailboxName.length()) {
            return mailboxName.substring(position + 1);
        }
        return mailboxName;
    }
}
