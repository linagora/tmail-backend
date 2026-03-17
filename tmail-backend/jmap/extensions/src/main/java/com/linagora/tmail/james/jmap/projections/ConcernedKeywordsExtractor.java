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

package com.linagora.tmail.james.jmap.projections;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.jmap.mail.Keyword;

import com.google.common.collect.ImmutableSet;

public class ConcernedKeywordsExtractor {
    private static final Keyword FLAGGED = new Keyword("$flagged");

    @Inject
    public ConcernedKeywordsExtractor() {

    }

    public Set<Keyword> extract(Flags flags) {
        Stream<Keyword> concernedSystemKeywords = flags.contains(Flags.Flag.FLAGGED) ? Stream.of(FLAGGED) : Stream.empty();
        Stream<Keyword> userKeywords = Arrays.stream(flags.getUserFlags())
            .map(flagName -> new Keyword(flagName.toLowerCase(Locale.US)));

        return Stream.concat(concernedSystemKeywords, userKeywords)
            .collect(ImmutableSet.toImmutableSet());
    }

    public boolean hasChanges(Flags oldFlags, Flags newFlags) {
        return !extract(oldFlags).equals(extract(newFlags));
    }
}
