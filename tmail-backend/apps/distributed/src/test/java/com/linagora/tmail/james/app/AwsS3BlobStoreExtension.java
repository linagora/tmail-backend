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

package com.linagora.tmail.james.app;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Singleton;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class AwsS3BlobStoreExtension implements GuiceModuleTestExtension {

    private final DockerAwsS3TestRule awsS3TestRule;

    public AwsS3BlobStoreExtension() {
        this.awsS3TestRule = new DockerAwsS3TestRule();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        ensureAwsS3started();
    }

    private void ensureAwsS3started() {
        DockerAwsS3Singleton.singleton.dockerAwsS3();
    }

    @Override
    public Module getModule() {
        return awsS3TestRule.getModule();
    }
}
