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

package com.linagora.tmail.webadmin;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Preconditions;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxNameConflictException;
import com.linagora.tmail.team.TeamMailboxNameSpace;
import com.linagora.tmail.team.TeamMailboxNotFoundException;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMemberRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;
import spark.HaltException;
import spark.Request;
import spark.Route;
import spark.Service;

public class TeamMailboxManagementRoutes implements Routes {
    public record TeamMailboxFolderResponse(String mailboxName, String mailboxId) {}

    public record TeamMailboxResponse(String name, String emailAddress) {
            static TeamMailboxResponse from(TeamMailbox teamMailbox) {
                Preconditions.checkNotNull(teamMailbox);
                return new TeamMailboxResponse(teamMailbox.mailboxName().asString(), teamMailbox.asMailAddress().asString());
            }

            static TeamMailboxResponse of(String name, String emailAddress) {
                Preconditions.checkNotNull(name);
                Preconditions.checkNotNull(emailAddress);
                return new TeamMailboxResponse(name, emailAddress);
            }

    }

    static class TeamMailboxMemberResponse {
        private final Username username;
        private final String role;

        TeamMailboxMemberResponse(Username username, String role) {
            this.username = username;
            this.role = role;
        }

        public String getUsername() {
            return username.asString();
        }

        public String getRole() {
            return role;
        }
    }

    private static final String TEAM_MAILBOX_DOMAIN_PARAM = ":dom";
    private static final String TEAM_MAILBOX_NAME_PARAM = ":name";
    private static final String MEMBER_USERNAME_PARAM = ":username";
    private static final String FOLDER_NAME_PARAM = ":folderName";
    private static final String ACL_USER_PARAM = ":aclUser";
    private static final String ROLE_PARAM = "role";
    public static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN_PARAM + Constants.SEPARATOR + "team-mailboxes";
    public static final String MEMBER_BASE_PATH = BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM + Constants.SEPARATOR + "members";
    public static final String MAILBOX_BASE_PATH = BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM + Constants.SEPARATOR + "mailboxes";

    private final TeamMailboxRepository teamMailboxRepository;
    private final DomainList domainList;
    private final MailboxManager mailboxManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public TeamMailboxManagementRoutes(TeamMailboxRepository teamMailboxRepository,
                                       DomainList domainList,
                                       MailboxManager mailboxManager,
                                       JsonTransformer jsonTransformer) {
        this.teamMailboxRepository = teamMailboxRepository;
        this.domainList = domainList;
        this.mailboxManager = mailboxManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, getTeamMailboxesByDomain(), jsonTransformer);
        service.delete(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, deleteTeamMailbox(), jsonTransformer);
        service.put(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, addTeamMailbox(), jsonTransformer);

        service.get(MAILBOX_BASE_PATH, getMailboxFolders(), jsonTransformer);
        service.put(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM, createMailboxFolder(), jsonTransformer);
        service.delete(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM, deleteMailboxFolder(), jsonTransformer);
        service.get(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/messageCount", messageCount());
        service.get(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/unseenMessageCount", unseenMessageCount());
        service.get(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/extraAcl", getExtraAcl(), jsonTransformer);
        service.put(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/extraAcl" + Constants.SEPARATOR + ACL_USER_PARAM, setExtraAclUser(), jsonTransformer);
        service.delete(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/extraAcl" + Constants.SEPARATOR + ACL_USER_PARAM, deleteExtraAclUser(), jsonTransformer);
        service.delete(MAILBOX_BASE_PATH + Constants.SEPARATOR + FOLDER_NAME_PARAM + "/extraAcl", deleteExtraAcl(), jsonTransformer);

        service.get(MEMBER_BASE_PATH, getMembers(), jsonTransformer);
        service.delete(MEMBER_BASE_PATH + Constants.SEPARATOR + MEMBER_USERNAME_PARAM, deleteMember(), jsonTransformer);
        service.put(MEMBER_BASE_PATH + Constants.SEPARATOR + MEMBER_USERNAME_PARAM, addMember(), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(TEAM_MAILBOX_DOMAIN_PARAM));
    }

    private TeamMailboxName extractName(Request request) {
        return TeamMailboxName.fromString(request.params(TEAM_MAILBOX_NAME_PARAM))
            .fold(e -> {
                throw e;
            }, x -> x);
    }

    private Username extractMemberUser(Request request) {
        return Username.of(request.params(MEMBER_USERNAME_PARAM));
    }

    private TeamMemberRole extractRole(Request request) {
        String role = request.queryParams(ROLE_PARAM);
        if (Objects.isNull(role)) {
            return new TeamMemberRole(TeamMemberRole.MemberRole());
        }
        return OptionConverters.toJava(TeamMemberRole.from(role)).orElseThrow(() -> ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Wrong role: " + role)
            .haltError());
    }

    private HaltException teamMailboxNotFoundException(TeamMailbox teamMailbox, Exception exception) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message("The requested team mailbox does not exists")
            .cause(exception)
            .haltError();
    }

    public Route getTeamMailboxesByDomain() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            return Flux.from(teamMailboxRepository.listTeamMailboxes(domain))
                .map(TeamMailboxResponse::from)
                .collectList().block();
        };
    }

    public Route deleteTeamMailbox() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            TeamMailboxName teamMailboxName = extractName(request);
            TeamMailbox teamMailbox = new TeamMailbox(domain, teamMailboxName);

            Mono.from(teamMailboxRepository.deleteTeamMailbox(teamMailbox))
                .block();
            return Responses.returnNoContent(response);
        };
    }

    public Route addTeamMailbox() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            TeamMailboxName teamMailboxName = extractName(request);
            if (!domainList.containsDomain(domain)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The domain do not exist: " + domain.asString())
                    .haltError();
            }

            TeamMailbox teamMailbox = new TeamMailbox(domain, teamMailboxName);

            return Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox))
                .then(Mono.just(Responses.returnNoContent(response)))
                .onErrorResume(TeamMailboxNameConflictException.class, e -> {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.CONFLICT_409)
                        .type(ErrorResponder.ErrorType.WRONG_STATE)
                        .message(e.getMessage())
                        .haltError();
                })
                .block();
        };
    }

    public Route getMailboxFolders() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.admin());
            String teamMailboxNameStr = teamMailbox.mailboxName().asString();
            String folderPrefix = teamMailboxNameStr + MailboxConstants.FOLDER_DELIMITER;

            MailboxQuery query = MailboxQuery.builder()
                .namespace(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())
                .user(teamMailbox.owner())
                .expression(new PrefixedWildcard(teamMailboxNameStr))
                .build();

            return mailboxManager.search(query, MailboxManager.MailboxSearchFetchType.Minimal, session)
                .map(metaData -> {
                    String fullName = metaData.getPath().getName();
                    String mailboxName = fullName.startsWith(folderPrefix) ? fullName.substring(folderPrefix.length()) : fullName;
                    return new TeamMailboxFolderResponse(mailboxName, metaData.getId().serialize());
                })
                .collectList()
                .block();
        };
    }

    public Route createMailboxFolder() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
            try {
                mailboxManager.createMailbox(teamMailbox.mailboxPath(folderName), session);
            } catch (MailboxExistsException e) {
                // Idempotent
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteMailboxFolder() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
            try {
                mailboxManager.deleteMailbox(teamMailbox.mailboxPath(folderName), session);
            } catch (MailboxNotFoundException e) {
                // Idempotent - folder does not exist
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route messageCount() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
            try {
                return mailboxManager.getMailbox(teamMailbox.mailboxPath(folderName), session)
                    .getMessageCount(session);
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
        };
    }

    public Route unseenMessageCount() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
            try {
                return mailboxManager.getMailbox(teamMailbox.mailboxPath(folderName), session)
                    .getMailboxCounters(session)
                    .getUnseen();
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
        };
    }

    private Username extractAclUser(Request request) {
        return Username.of(request.params(ACL_USER_PARAM));
    }

    private void assertNotProtectedAclUser(Username aclUser, TeamMailbox teamMailbox) {
        Set<String> systemUsernames = Set.of(teamMailbox.owner().asString(), teamMailbox.admin().asString());
        if (systemUsernames.contains(aclUser.asString())) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(aclUser.asString() + " is a system user and cannot be managed via extraAcl")
                .haltError();
        }
        boolean isMember = Flux.from(teamMailboxRepository.listMembers(teamMailbox))
            .map(member -> member.username().asString())
            .any(aclUser.asString()::equals)
            .block();
        if (isMember) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(aclUser.asString() + " is a team mailbox member and cannot be managed via extraAcl")
                .haltError();
        }
    }

    public Route getExtraAcl() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.admin());

            Set<String> systemUsernames = Set.of(teamMailbox.owner().asString(), teamMailbox.admin().asString());
            Set<String> memberUsernames = Flux.from(teamMailboxRepository.listMembers(teamMailbox))
                .map(member -> member.username().asString())
                .collect(Collectors.toSet())
                .block();

            try {
                MailboxACL acl = mailboxManager.listRights(teamMailbox.mailboxPath(folderName), session);
                return acl.getEntries().entrySet().stream()
                    .filter(e -> !e.getKey().isNegative())
                    .filter(e -> MailboxACL.NameType.user.equals(e.getKey().getNameType()))
                    .filter(e -> !systemUsernames.contains(e.getKey().getName()))
                    .filter(e -> !memberUsernames.contains(e.getKey().getName()))
                    .collect(Collectors.toMap(e -> e.getKey().getName(), e -> e.getValue().serialize()));
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
        };
    }

    public Route setExtraAclUser() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            Username aclUser = extractAclUser(request);
            assertNotProtectedAclUser(aclUser, teamMailbox);

            MailboxACL.Rfc4314Rights rights;
            try {
                rights = MailboxACL.Rfc4314Rights.deserialize(request.body().trim());
            } catch (UnsupportedRightException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Invalid rights: " + request.body())
                    .cause(e)
                    .haltError();
            }

            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.admin());
            try {
                mailboxManager.getMailbox(teamMailbox.mailboxPath(folderName), session);
                mailboxManager.applyRightsCommand(
                    teamMailbox.mailboxPath(folderName),
                    MailboxACL.command().forUser(aclUser).rights(rights).asReplacement(),
                    session);
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteExtraAclUser() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            Username aclUser = extractAclUser(request);
            assertNotProtectedAclUser(aclUser, teamMailbox);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.admin());

            try {
                MailboxACL currentAcl = mailboxManager.listRights(teamMailbox.mailboxPath(folderName), session);
                MailboxACL.EntryKey userKey = MailboxACL.EntryKey.createUserEntryKey(aclUser);
                MailboxACL.Rfc4314Rights userRights = currentAcl.getEntries().get(userKey);
                if (userRights != null) {
                    mailboxManager.setRights(teamMailbox.mailboxPath(folderName), currentAcl.except(userKey, userRights), session);
                }
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteExtraAcl() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));

            boolean exists = Mono.from(teamMailboxRepository.exists(teamMailbox)).map(Boolean.TRUE::equals).block();
            if (!exists) {
                throw teamMailboxNotFoundException(teamMailbox, new TeamMailboxNotFoundException(teamMailbox));
            }

            String folderName = request.params(FOLDER_NAME_PARAM);
            MailboxSession session = mailboxManager.createSystemSession(teamMailbox.admin());

            Set<String> systemUsernames = Set.of(teamMailbox.owner().asString(), teamMailbox.admin().asString());
            Set<String> memberUsernames = Flux.from(teamMailboxRepository.listMembers(teamMailbox))
                .map(member -> member.username().asString())
                .collect(Collectors.toSet())
                .block();

            try {
                MailboxACL currentAcl = mailboxManager.listRights(teamMailbox.mailboxPath(folderName), session);
                Map<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights> standardEntries = currentAcl.getEntries().entrySet().stream()
                    .filter(e -> !MailboxACL.NameType.user.equals(e.getKey().getNameType())
                        || e.getKey().isNegative()
                        || systemUsernames.contains(e.getKey().getName())
                        || memberUsernames.contains(e.getKey().getName()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                mailboxManager.setRights(teamMailbox.mailboxPath(folderName), new MailboxACL(standardEntries), session);
            } catch (MailboxNotFoundException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The requested mailbox folder does not exist")
                    .cause(e)
                    .haltError();
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route getMembers() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            return Flux.from(teamMailboxRepository.listMembers(teamMailbox))
                .map(teamMailboxMember -> new TeamMailboxMemberResponse(teamMailboxMember.username(), teamMailboxMember.role().value().toString()))
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .collectList()
                .block();
        };
    }

    public Route addMember() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            Username addUser = extractMemberUser(request);
            TeamMemberRole role = extractRole(request);
            Mono.from(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.of(addUser, role)))
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .block();
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteMember() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            Username removeUser = extractMemberUser(request);
            Mono.from(teamMailboxRepository.removeMember(teamMailbox, removeUser))
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .block();
            return Responses.returnNoContent(response);
        };
    }

}
