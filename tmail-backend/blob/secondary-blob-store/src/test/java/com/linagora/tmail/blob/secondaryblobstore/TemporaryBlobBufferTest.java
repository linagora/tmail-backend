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

package com.linagora.tmail.blob.secondaryblobstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TemporaryBlobBufferTest {
    private static final int THRESHOLD = 16;
    private static final byte[] SMALL = "small".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BIG = "bigger than the sixteen bytes threshold".getBytes(StandardCharsets.UTF_8);

    private final TemporaryBlobBuffer testee = new TemporaryBlobBuffer(THRESHOLD);

    @AfterEach
    void tearDown() {
        testee.dispose();
    }

    @Test
    void contentBelowThresholdShouldStayInMemory() throws IOException {
        testee.write(SMALL);
        testee.close();

        assertThat(testee.temporaryFile()).isEmpty();
        assertThat(testee.asByteSource().read()).isEqualTo(SMALL);
        assertThat(testee.asByteSource().size()).isEqualTo(SMALL.length);
    }

    @Test
    void contentAboveThresholdShouldSpillToANamedTemporaryFile() throws IOException {
        testee.write(BIG);
        testee.close();

        Path temporaryFile = testee.temporaryFile().orElseThrow();
        assertThat(temporaryFile).exists();
        assertThat(temporaryFile.getFileName().toString())
            .startsWith(TemporaryBlobBuffer.TEMPORARY_FILE_PREFIX)
            .endsWith(TemporaryBlobBuffer.TEMPORARY_FILE_SUFFIX);
        assertThat(testee.asByteSource().read()).isEqualTo(BIG);
        assertThat(testee.asByteSource().size()).isEqualTo(BIG.length);
    }

    @Test
    void contentAboveThresholdShouldBeReadableSeveralTimes() throws IOException {
        testee.write(BIG);
        testee.close();

        assertThat(testee.asByteSource().read()).isEqualTo(BIG);
        assertThat(testee.asByteSource().read()).isEqualTo(BIG);
    }

    @Test
    void disposeShouldDeleteTheTemporaryFile() throws IOException {
        testee.write(BIG);
        testee.close();
        Path temporaryFile = testee.temporaryFile().orElseThrow();

        testee.dispose();

        assertThat(Files.exists(temporaryFile)).isFalse();
    }

    @Test
    void disposeShouldDeleteTheTemporaryFileOfAnUnclosedBuffer() throws IOException {
        testee.write(BIG);
        Path temporaryFile = testee.temporaryFile().orElseThrow();

        testee.dispose();

        assertThat(Files.exists(temporaryFile)).isFalse();
    }

    @Test
    void disposeShouldBeIdempotent() throws IOException {
        testee.write(BIG);
        testee.close();

        testee.dispose();
        testee.dispose();

        assertThat(testee.temporaryFile().map(Files::exists)).contains(false);
    }

    @Test
    void writeAfterDisposeShouldFailWithoutCreatingATemporaryFile() {
        testee.dispose();

        assertThatThrownBy(() -> testee.write(BIG))
            .isInstanceOf(IOException.class);
        assertThat(testee.temporaryFile()).isEmpty();
    }

    @Test
    void readAfterDisposeShouldFail() throws IOException {
        testee.write(BIG);
        testee.close();

        testee.dispose();

        assertThatThrownBy(() -> testee.asByteSource().read())
            .isInstanceOf(IOException.class);
    }

    @Test
    void writeAfterCloseShouldFail() throws IOException {
        testee.write(SMALL);
        testee.close();

        assertThatThrownBy(() -> testee.write(SMALL))
            .isInstanceOf(IOException.class);
        assertThat(testee.asByteSource().read()).isEqualTo(SMALL);
    }

    @Test
    void asByteSourceShouldFailWhenNotClosed() throws IOException {
        testee.write(SMALL);

        assertThatThrownBy(testee::asByteSource)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeShouldBeIdempotent() throws IOException {
        testee.write(SMALL);
        testee.close();
        testee.close();

        assertThat(testee.asByteSource().read()).isEqualTo(SMALL);
    }
}
