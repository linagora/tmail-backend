/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package com.linagora.tmail.encrypted;

import java.util.Optional;

import org.apache.james.mailbox.exception.MailboxException;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public abstract class MailboxReactorUtils {
    public static <T> T block(Mono<T> publisher) throws MailboxException {
        try {
            return publisher.block();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxException mailboxException) {
                throw mailboxException;
            }

            throw e;
        }
    }

    public static <T> T block(Publisher<T> publisher) throws MailboxException {
        return block(Mono.from(publisher));
    }

    public static <T> Optional<T> blockOptional(Mono<T> publisher) throws MailboxException {
        try {
            return publisher.blockOptional();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxException mailboxException) {
                throw mailboxException;
            }

            throw e;
        }
    }
}
