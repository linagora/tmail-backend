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

package com.linagora.calendar.app;

import java.io.File;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;

import com.github.fge.lambdas.Throwing;

public record TwakeCalendarConfiguration(ConfigurationPath configurationPath, JamesDirectoriesProvider directories, UserChoice userChoice) implements Configuration {
    public static class Builder {
        private Optional<String> rootDirectory;
        private Optional<ConfigurationPath> configurationPath;
        private Optional<UserChoice> userChoice;

        private Builder() {
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
            userChoice = Optional.empty();
        }

        public Builder workingDirectory(String path) {
            rootDirectory = Optional.of(path);
            return this;
        }

        public Builder workingDirectory(File file) {
            rootDirectory = Optional.of(file.getAbsolutePath());
            return this;
        }

        public Builder userChoice(UserChoice choice) {
            userChoice = Optional.of(choice);
            return this;
        }

        public Builder useWorkingDirectoryEnvProperty() {
            rootDirectory = Optional.ofNullable(System.getProperty(WORKING_DIRECTORY));
            if (rootDirectory.isEmpty()) {
                throw new MissingArgumentException("Server needs a working.directory env entry");
            }
            return this;
        }

        public Builder configurationPath(ConfigurationPath path) {
            configurationPath = Optional.of(path);
            return this;
        }

        public Builder configurationFromClasspath() {
            configurationPath = Optional.of(new ConfigurationPath(FileSystem.CLASSPATH_PROTOCOL));
            return this;
        }

        public TwakeCalendarConfiguration build() {
            ConfigurationPath configurationPath = this.configurationPath.orElse(new ConfigurationPath(FileSystem.FILE_PROTOCOL_AND_CONF));
            JamesServerResourceLoader directories = new JamesServerResourceLoader(rootDirectory
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")));
            FileSystemImpl fileSystem = new FileSystemImpl(directories);
            FileConfigurationProvider configurationProvider = new FileConfigurationProvider(fileSystem, Basic.builder()
                .configurationPath(configurationPath)
                .workingDirectory(directories.getRootDirectory())
                .build());
            UserChoice userChoice = this.userChoice.orElseGet(Throwing.supplier(() -> {
                var configuration = configurationProvider.getConfiguration("usersrepository");
                if (configuration.isEmpty()) {
                    return UserChoice.MEMORY;
                }
                return UserChoice.LDAP;
            }));

            return new TwakeCalendarConfiguration(
                configurationPath,
                directories,
                userChoice);
        }
    }

    enum UserChoice {
        LDAP,
        MEMORY
    }

    public static Builder builder() {
        return new Builder();
    }

}
