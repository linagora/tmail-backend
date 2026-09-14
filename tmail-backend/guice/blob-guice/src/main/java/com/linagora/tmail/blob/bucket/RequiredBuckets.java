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

package com.linagora.tmail.blob.bucket;

import java.util.Collection;
import java.util.List;

import org.apache.james.blob.api.BucketName;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

/**
 * Logical buckets a feature writes to, to be provisioned at startup. Contributed through a Guice set multibinder.
 */
@FunctionalInterface
public interface RequiredBuckets {
    static Module module(BucketName... bucketNames) {
        List<BucketName> buckets = ImmutableList.copyOf(bucketNames);
        return binder -> Multibinder.newSetBinder(binder, RequiredBuckets.class)
            .addBinding()
            .toInstance(() -> buckets);
    }

    Collection<BucketName> requiredBuckets();
}
