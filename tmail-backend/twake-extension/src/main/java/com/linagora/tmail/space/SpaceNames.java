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

package com.linagora.tmail.space;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;

/**
 * The team mailbox name a space gets: the same as the Twake Mail side service, so a migrated space keeps its address.
 */
public final class SpaceNames {
    private static final int MAX_LENGTH = 64;
    private static final String FALLBACK = "space";
    private static final int CANDIDATES = 20;

    public static String mailboxName(String spaceName, String prefix) {
        String name = trimDashes(Normalizer.normalize(spaceName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("-{2,}", "-"));
        String truncated = trimDashes(name.substring(0, Math.min(name.length(), MAX_LENGTH - prefix.length())));
        return prefix + (truncated.isEmpty() ? FALLBACK : truncated);
    }

    /** The name, then the name with {@code -2} up to {@code -20}. */
    public static List<String> candidates(String name) {
        return IntStream.rangeClosed(1, CANDIDATES)
            .mapToObj(index -> {
                if (index == 1) {
                    return name;
                }
                String suffix = "-" + index;
                return trimDashes(name.substring(0, Math.min(name.length(), MAX_LENGTH - suffix.length()))) + suffix;
            })
            .collect(ImmutableList.toImmutableList());
    }

    private static String trimDashes(String value) {
        return value.replaceAll("^-+|-+$", "");
    }

    private SpaceNames() {
    }
}
