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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteSource;

/**
 * Buffers a blob so that it can be read once per blob store: in memory up to a threshold, then in a temporary
 * file named {@value #TEMPORARY_FILE_PREFIX}*{@value #TEMPORARY_FILE_SUFFIX}.
 *
 * Written while {@link State#OPEN}, read once {@link State#CLOSED}: a closed buffer refuses writes, so the size of
 * its content is final.
 *
 * {@link #dispose()} deletes the temporary file. It can run concurrently with a write that is still going on (a
 * cancelled save): once disposed, the buffer refuses any write, so no temporary file can be created afterwards.
 */
class TemporaryBlobBuffer extends OutputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryBlobBuffer.class);

    static final String TEMPORARY_FILE_PREFIX = "tmail-secondary-blobstore-";
    static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    enum State {
        OPEN,
        CLOSED,
        DISPOSED
    }

    private final DeferredFileOutputStream delegate;
    private State state;

    TemporaryBlobBuffer(int threshold) {
        this.delegate = DeferredFileOutputStream.builder()
            .setThreshold(threshold)
            .setPrefix(TEMPORARY_FILE_PREFIX)
            .setSuffix(TEMPORARY_FILE_SUFFIX)
            .get();
        this.state = State.OPEN;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        ensureOpen();
        delegate.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        delegate.write(b, off, len);
    }

    @Override
    public synchronized void flush() throws IOException {
        ensureOpen();
        delegate.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (state != State.OPEN) {
            return;
        }
        delegate.close();
        state = State.CLOSED;
    }

    /**
     * The buffered content. The buffer must be closed: its size is then final.
     */
    synchronized ByteSource asByteSource() {
        if (state == State.OPEN) {
            throw new IllegalStateException("The temporary blob buffer must be closed before being read");
        }
        long size = size();
        return new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                synchronized (TemporaryBlobBuffer.this) {
                    if (state == State.DISPOSED) {
                        throw new IOException("The temporary blob buffer was disposed");
                    }
                    return delegate.toInputStream();
                }
            }

            @Override
            public com.google.common.base.Optional<Long> sizeIfKnown() {
                return com.google.common.base.Optional.of(size);
            }

            @Override
            public long size() {
                return size;
            }
        };
    }

    synchronized long size() {
        return delegate.getByteCount();
    }

    // Set as soon as a spill starts, even when writing the in-memory bytes to the file then fails: still to delete
    synchronized Optional<Path> temporaryFile() {
        return Optional.ofNullable(delegate.getPath());
    }

    synchronized void dispose() {
        if (state == State.DISPOSED) {
            return;
        }
        state = State.DISPOSED;
        try {
            delegate.close();
        } catch (IOException e) {
            LOGGER.warn("Failed to close a temporary blob buffer", e);
        }
        temporaryFile().ifPresent(TemporaryBlobBuffer::delete);
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.error("Failed to delete the temporary blob file {}", path, e);
        }
    }

    private void ensureOpen() throws IOException {
        switch (state) {
            case OPEN -> {
            }
            case CLOSED -> throw new IOException("The temporary blob buffer is closed");
            case DISPOSED -> throw new IOException("The temporary blob buffer was disposed");
        }
    }
}
