package com.linagora.tmail.contact;

public record ContactAddedRabbitMqMessage(String bookId, String bookName, String contactId,
                                          String userId, JCardObject vcard) {
}
