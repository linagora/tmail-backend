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

package com.linagora.tmail.james.jmap.event;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.model.EmailAddress;

import com.google.common.base.Preconditions;

public class IdentityCreationRequestBuilder {
    private MailAddress email;
    private Optional<String> name = Optional.empty();
    private Optional<List<EmailAddress>> replyTo = Optional.empty();
    private Optional<List<EmailAddress>> bcc = Optional.empty();
    private Optional<Integer> sortOrder = Optional.empty();
    private Optional<String> textSignature = Optional.empty();
    private Optional<String> htmlSignature = Optional.empty();

    public static IdentityCreationRequestBuilder builder() {
        return new IdentityCreationRequestBuilder();
    }

    public IdentityCreationRequestBuilder email(MailAddress email) {
        this.email = email;
        return this;
    }

    public IdentityCreationRequestBuilder name(String name) {
        this.name = Optional.ofNullable(name);
        return this;
    }

    public IdentityCreationRequestBuilder name(Optional<String> name) {
        this.name = name;
        return this;
    }

    public IdentityCreationRequestBuilder replyTo(List<EmailAddress> replyTo) {
        this.replyTo = Optional.ofNullable(replyTo);
        return this;
    }

    public IdentityCreationRequestBuilder replyTo(Optional<List<EmailAddress>> replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    public IdentityCreationRequestBuilder bcc(List<EmailAddress> bcc) {
        this.bcc = Optional.ofNullable(bcc);
        return this;
    }

    public IdentityCreationRequestBuilder bcc(Optional<List<EmailAddress>> bcc) {
        this.bcc = bcc;
        return this;
    }

    public IdentityCreationRequestBuilder sortOrder(int sortOrder) {
        this.sortOrder = Optional.of(sortOrder);
        return this;
    }

    public IdentityCreationRequestBuilder sortOrder(Optional<Integer> sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    public IdentityCreationRequestBuilder textSignature(String textSignature) {
        this.textSignature = Optional.ofNullable(textSignature);
        return this;
    }

    public IdentityCreationRequestBuilder textSignature(Optional<String> textSignature) {
        this.textSignature = textSignature;
        return this;
    }

    public IdentityCreationRequestBuilder htmlSignature(String htmlSignature) {
        this.htmlSignature = Optional.ofNullable(htmlSignature);
        return this;
    }

    public IdentityCreationRequestBuilder htmlSignature(Optional<String> htmlSignature) {
        this.htmlSignature = htmlSignature;
        return this;
    }

    public IdentityCreationRequest build() {
        Preconditions.checkNotNull(email, "'email' is mandatory");

        return IdentityCreationRequest.fromJava(
            email,
            name,
            replyTo,
            bcc,
            sortOrder,
            textSignature,
            htmlSignature);
    }
}
