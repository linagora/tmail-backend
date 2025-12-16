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

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class OrFilter implements MessageFilter {
    private final List<MessageFilter> filters;

    public OrFilter(List<MessageFilter> filters) {
        Preconditions.checkArgument(filters != null && !filters.isEmpty(), "OrFilter requires at least one child filter");
        this.filters = ImmutableList.copyOf(filters);
    }

    @Override
    public Mono<Boolean> matches(FilterContext context) {
        return Flux.fromIterable(filters)
            .flatMap(filter -> filter.matches(context))
            .any(Boolean::booleanValue);
    }

    public List<MessageFilter> getFilters() {
        return filters;
    }
}
