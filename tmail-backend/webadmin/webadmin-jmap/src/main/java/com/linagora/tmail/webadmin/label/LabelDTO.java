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

package com.linagora.tmail.webadmin.label;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.linagora.tmail.james.jmap.model.Label;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LabelDTO {
    public final String id;
    public final String displayName;
    public final String keyword;
    public final String color;
    public final String description;
    public final boolean readOnly;

    public LabelDTO(String id, String displayName, String keyword, String color, String description, boolean readOnly) {
        this.id = id;
        this.displayName = displayName;
        this.keyword = keyword;
        this.color = color;
        this.description = description;
        this.readOnly = readOnly;
    }

    public static LabelDTO from(Label label) {
        return new LabelDTO(
            label.id().serialize(),
            label.displayName().value(),
            label.keyword(),
            label.color().isDefined() ? label.color().get().value() : null,
            label.description().isDefined() ? label.description().get() : null,
            label.readOnly());
    }
}
