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

package com.linagora.tmail;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;

public class RabbitMQDisconnectorModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DisconnectorNotifier.class).to(RabbitMQDisconnectorNotifier.class);
    }

    @ProvidesIntoSet
    InitializationOperation init(RabbitMQDisconnectorOperator operator) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQDisconnectorOperator.class)
            .init(operator::init);
    }
}