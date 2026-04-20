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

package com.linagora.tmail.webadmin.data;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewProjectionItemsTask;
import org.apache.james.webadmin.service.ClearMailboxContentTask;
import org.apache.james.webadmin.service.DeleteUserDataTask;
import org.apache.james.webadmin.service.DeleteUsersDataOfDomainTask;
import org.apache.james.webadmin.service.ExpireMailboxTask;
import org.apache.james.webadmin.service.SubscribeAllTask;
import org.apache.james.webadmin.service.UsernameChangeTask;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRestoreTask;
import org.apache.mailbox.tools.indexer.UserReindexingTask;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class DomainTasksModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(DomainTasksRoutes.class);

        Multibinder<TaskBelongsToDomainPredicate> predicates =
            Multibinder.newSetBinder(binder(), TaskBelongsToDomainPredicate.class);

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof ClearMailboxContentTask.AdditionalInformation)
                .map(info -> (ClearMailboxContentTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof DeletedMessagesVaultRestoreTask.AdditionalInformation)
                .map(info -> (DeletedMessagesVaultRestoreTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof UserReindexingTask.AdditionalInformation)
                .map(info -> (UserReindexingTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof SubscribeAllTask.AdditionalInformation)
                .map(info -> (SubscribeAllTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof RecomputeUserFastViewProjectionItemsTask.AdditionalInformation)
                .map(info -> (RecomputeUserFastViewProjectionItemsTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof ExpireMailboxTask.AdditionalInformation)
                .map(info -> (ExpireMailboxTask.AdditionalInformation) info)
                .flatMap(info -> info.getRunningOptions().getUser())
                .map(user -> userBelongsToDomain(domain, user))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof UsernameChangeTask.AdditionalInformation)
                .map(info -> (UsernameChangeTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getOldUser()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof DeleteUserDataTask.AdditionalInformation)
                .map(info -> (DeleteUserDataTask.AdditionalInformation) info)
                .map(info -> userBelongsToDomain(domain, info.getUsername()))
                .orElse(false));

        predicates.addBinding().toInstance(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof DeleteUsersDataOfDomainTask.AdditionalInformation)
                .map(info -> (DeleteUsersDataOfDomainTask.AdditionalInformation) info)
                .map(info -> info.getDomain().equals(domain))
                .orElse(false));
    }

    private static boolean userBelongsToDomain(Domain domain, Username username) {
        return username.getDomainPart()
            .map(domain::equals)
            .orElse(false);
    }

    private static boolean userBelongsToDomain(Domain domain, String username) {
        return userBelongsToDomain(domain, Username.of(username));
    }
}
