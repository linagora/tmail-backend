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

package com.linagora.tmail.james.app.modules.jmap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.linagora.tmail.james.jmap.publicAsset.MemoryPublicAssetRepository;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepository;

public class PublicAssetsMemoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryPublicAssetRepository.class).in(Scopes.SINGLETON);
        bind(PublicAssetRepository.class).to(MemoryPublicAssetRepository.class);
    }
}
