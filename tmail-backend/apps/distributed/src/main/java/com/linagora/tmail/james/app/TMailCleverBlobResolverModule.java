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

import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.jmap.routes.MessageBlobResolver;
import org.apache.james.jmap.routes.UploadResolver;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class TMailCleverBlobResolverModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<BlobResolver> blobResolverMultibinder = Multibinder.newSetBinder(binder(), BlobResolver.class);
        blobResolverMultibinder.addBinding().to(MessageBlobResolver.class);
        blobResolverMultibinder.addBinding().to(UploadResolver.class);
        blobResolverMultibinder.addBinding().to(TMailCleverBlobResolver.class);
    }
}
