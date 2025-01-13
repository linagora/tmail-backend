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

package com.linagora.tmail.imap;

import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.NamespaceSupplier;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class TMailIMAPModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NamespaceSupplier.class).to(TMailNamespaceSupplier.class).in(Scopes.SINGLETON);
        bind(PathConverter.Factory.class).to(TMailPathConverter.Factory.class).in(Scopes.SINGLETON);
    }
}
