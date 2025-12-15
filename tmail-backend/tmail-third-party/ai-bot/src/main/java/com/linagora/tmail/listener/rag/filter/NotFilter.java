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

package com.linagora.tmail.listener.rag.filter;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class NotFilter implements MessageFilter {
    private final MessageFilter filter;

    public NotFilter(MessageFilter filter) {
        Preconditions.checkArgument(filter != null, "NotFilter requires a child filter");
        this.filter = filter;
    }

    @Override
    public Mono<Boolean> matches(FilterContext context) {
        return filter.matches(context).map(result -> !result);
    }

    public MessageFilter getFilter() {
        return filter;
    }
}
