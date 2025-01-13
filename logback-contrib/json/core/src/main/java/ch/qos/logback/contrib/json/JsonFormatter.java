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

package ch.qos.logback.contrib.json;

import java.util.Map;

/**
 * A {@code JsonFormatter} formats a data {@link Map Map} into a JSON string.
 *
 * @author Les Hazlewood
 * @since 0.1
 */
public interface JsonFormatter {

    /**
     * Converts the specified map into a JSON string.
     *
     * @param m the map to be converted.
     * @return a JSON String representation of the specified Map instance.
     * @throws Exception if there is a problem converting the map to a String.
     */
    String toJsonString(Map m) throws Exception;
}
