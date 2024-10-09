package com.linagora.tmail.james.jmap.contact;

public record ContactAddedRabbitMqMessage(String bookId, String bookName, String contactId,
                                          String userId, JCardObject vcard) {
}
