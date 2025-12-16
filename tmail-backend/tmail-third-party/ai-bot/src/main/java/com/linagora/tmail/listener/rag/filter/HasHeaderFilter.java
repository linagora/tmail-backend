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

import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public class HasHeaderFilter implements MessageFilter {
    private final String headerName;
    private final Optional<String> expectedValue;

    public HasHeaderFilter(String headerName) {
        this(headerName, Optional.empty());
    }

    public HasHeaderFilter(String headerName, Optional<String> expectedValue) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(headerName), "Header name cannot be null or empty");
        Preconditions.checkNotNull(expectedValue, "Expected value cannot be null");
        this.headerName = headerName;
        this.expectedValue = expectedValue;
    }

    @Override
    public Mono<Boolean> matches(FilterContext context) {
        return Mono.fromCallable(() -> {
            var field = context.mimeMessage().getHeader().getField(headerName);
            if (field == null) {
                return false;
            }

            if (expectedValue.isEmpty()) {
                return true;
            }

            String actualValue = field.getBody().trim();
            return actualValue.equalsIgnoreCase(expectedValue.get());
        });
    }

    public String getHeaderName() {
        return headerName;
    }

    public Optional<String> getExpectedValue() {
        return expectedValue;
    }
}
