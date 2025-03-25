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

package com.linagora.tmail.common;

import static org.apache.james.filesystem.api.FileSystem.FILE_PROTOCOL;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.james.server.core.configuration.Configuration;

import com.google.common.collect.ImmutableList;

public class TemporaryTmailServerUtils {
    public static final List<String> BASE_CONFIGURATION_FILE_NAMES = ImmutableList.of(
        "dnsservice.xml",
        "domainlist.xml",
        "imapserver.xml",
        "keystore",
        "listeners.xml",
        "mailetcontainer.xml",
        "mailrepositorystore.xml",
        "smtpserver.xml",
        "webadmin.properties",
        "firebase.properties",
        "jmap.properties");

    private final Path configFolder;
    private final List<String> configFileNames;

    public TemporaryTmailServerUtils(File workingDir, List<String> configFileNames) {
        Path workingDirPath = workingDir.toPath();
        this.configFileNames = configFileNames;
        this.configFolder = workingDirPath.resolve("conf");

        try {
            Files.createDirectories(configFolder);
            copyAllResources();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create configuration folder or copy resources", e);
        }
    }

    private void copyAllResources() {
        configFileNames.forEach(fileName -> copyResource(fileName, fileName));
    }

    public void copyResource(String resourceName, String targetName) {
        copyResource(configFolder, resourceName, targetName);
    }

    public static void copyResource(Path destinationFolder, String resourceName, String targetName) {
        Path targetPath = destinationFolder.resolve(targetName);
        URL resourceUrl = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource(resourceName),
            "Failed to load configuration resource: " + resourceName);

        try (InputStream inputStream = resourceUrl.openStream(); OutputStream outputStream = Files.newOutputStream(targetPath)) {
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Error copying resource: " + resourceName, e);
        }
    }

    public Path getConfigFolder() {
        return configFolder;
    }

    public Configuration.ConfigurationPath getConfigurationPath() {
        return new Configuration.ConfigurationPath(FILE_PROTOCOL + configFolder.toAbsolutePath() + File.separator);
    }
}