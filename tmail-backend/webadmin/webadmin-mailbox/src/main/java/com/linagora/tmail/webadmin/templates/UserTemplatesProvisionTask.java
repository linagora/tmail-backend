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

import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;

public class UserTemplatesProvisionTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("UserTemplatesProvisionTask");

    public record AdditionalInformation(Instant timestamp,
                                        String sourceUser,
                                        String targetUser,
                                        String folderName,
                                        boolean overwriteExisting,
                                        boolean prune,
                                        long processedUsers,
                                        long appliedTemplates,
                                        long skippedTemplates,
                                        long removedTemplates,
                                        ImmutableList<String> failedUsers) implements TaskExecutionDetails.AdditionalInformation {
        static AdditionalInformation from(UserTemplatesProvisionTask task) {
            TemplatesProvisionContext.Snapshot snapshot = task.context.snapshot();
            return new AdditionalInformation(Clock.systemUTC().instant(),
                task.source.sourceUser().asString(),
                task.targetUser.asString(),
                task.source.folderName(),
                task.options.overwriteExisting(),
                task.options.prune(),
                snapshot.processedUsers(),
                snapshot.appliedTemplates(),
                snapshot.skippedTemplates(),
                snapshot.removedTemplates(),
                snapshot.failedUsers());
        }
    }

    private final TemplatesProvisionService service;
    private final TemplatingSource source;
    private final Username targetUser;
    private final ProvisionOptions options;
    private final TemplatesProvisionContext context;

    public UserTemplatesProvisionTask(TemplatesProvisionService service, Username sourceUser, Username targetUser,
                                      String folderName, ProvisionOptions options) {
        this.service = service;
        this.source = new TemplatingSource(sourceUser, folderName);
        this.targetUser = targetUser;
        this.options = options;
        this.context = new TemplatesProvisionContext();
    }

    @Override
    public Result run() {
        return service.provisionUser(source, targetUser, new TemplatesProvisionService.ProvisionRun(options, context)).block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(this));
    }

    public Username getSourceUser() {
        return source.sourceUser();
    }

    public Username getTargetUser() {
        return targetUser;
    }

    public String getFolderName() {
        return source.folderName();
    }

    public ProvisionOptions getOptions() {
        return options;
    }
}
