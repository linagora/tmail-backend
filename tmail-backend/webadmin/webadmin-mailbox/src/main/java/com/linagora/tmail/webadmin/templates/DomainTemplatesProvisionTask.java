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

package com.linagora.tmail.webadmin.templates;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;

public class DomainTemplatesProvisionTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("DomainTemplatesProvisionTask");

    public record AdditionalInformation(Instant timestamp,
                                        String domain,
                                        String sourceUser,
                                        String folderName,
                                        boolean overwriteExisting,
                                        boolean prune,
                                        int usersPerSecond,
                                        long processedUsers,
                                        long appliedTemplates,
                                        long skippedTemplates,
                                        long removedTemplates,
                                        ImmutableList<String> failedUsers) implements TaskExecutionDetails.AdditionalInformation {
        static AdditionalInformation from(DomainTemplatesProvisionTask task) {
            TemplatesProvisionContext.Snapshot snapshot = task.context.snapshot();
            return new AdditionalInformation(Clock.systemUTC().instant(),
                task.domain.asString(),
                task.sourceUser.asString(),
                task.folderName,
                task.options.overwriteExisting(),
                task.options.prune(),
                task.usersPerSecond,
                snapshot.processedUsers(),
                snapshot.appliedTemplates(),
                snapshot.skippedTemplates(),
                snapshot.removedTemplates(),
                snapshot.failedUsers());
        }
    }

    private final TemplatesProvisionService service;
    private final Domain domain;
    private final Username sourceUser;
    private final String folderName;
    private final ProvisionOptions options;
    private final int usersPerSecond;
    private final TemplatesProvisionContext context;

    public DomainTemplatesProvisionTask(TemplatesProvisionService service, Domain domain, Username sourceUser,
                                        String folderName, ProvisionOptions options, int usersPerSecond) {
        this.service = service;
        this.domain = domain;
        this.sourceUser = sourceUser;
        this.folderName = folderName;
        this.options = options;
        this.usersPerSecond = usersPerSecond;
        this.context = new TemplatesProvisionContext();
    }

    @Override
    public Result run() {
        return service.provisionDomain(domain, sourceUser, folderName, options, usersPerSecond, context).block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(this));
    }

    public Domain getDomain() {
        return domain;
    }

    public Username getSourceUser() {
        return sourceUser;
    }

    public String getFolderName() {
        return folderName;
    }

    public ProvisionOptions getOptions() {
        return options;
    }

    public int getUsersPerSecond() {
        return usersPerSecond;
    }
}
