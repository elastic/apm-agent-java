package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * An abstraction similar to {@link MappedByteBuffer} that allows to read the content of a file with an API that is similar to
 * {@link ByteBuffer}.
 * <p>
 * Instances of this class hold a reusable buffer that contains a subset of the file,
 * or the whole file if the buffer's capacity is greater or equal to the file's size.
 * </p>
 * <p>
 * Whenever calling a method like {@link #getLong()} or {@link #position(long)} would exceed the currently buffered range
 * the same buffer is filled with a different range of the file.
 * </p>
 * <p>
 * The downside of {@link MappedByteBuffer} (and the reason for implementing this abstraction)
 * is that calling methods like {@link MappedByteBuffer#get()} can increase time-to-safepoint.
 * This is because these methods are implemented as JVM intrinsics.
 * When the JVM executes an intrinsic, it does not switch to the native execution context which means that it's not ready to enter a safepoint
 * whenever a intrinsic runs.
 * As reading a file from disk can get stuck (for example when the disk is busy) calling {@link MappedByteBuffer#get()} may take a while to execute.
 * While it's executing other threads have to wait for it to finish if the JVM wants to reach a safe point.
 * </p>
 */
class BufferedFile implements Recyclable {

    private static final int SIZE_OF_BYTE = 1;
    private static final int SIZE_OF_SHORT = 2;
    private static final int SIZE_OF_INT = 4;
    private static final int SIZE_OF_LONG = 8;
    private final ByteBuffer buffer;
    private final int capacity;
    /**
     * The offset of the file from where the {@link #buffer} starts
     */
    private long offset;
    private boolean wholeFileInBuffer;
    @Nullable
    private FileChannel fileChannel;

    public BufferedFile(ByteBuffer buffer) {
        this.buffer = buffer;
        capacity = buffer.capacity();
    }

    /**
     * Starts reading the file into the {@linkplain #buffer buffer}
     *
     * @param file the file to read from
     * @throws IOException If some I/O error occurs
     */
    public void setFile(File file) throws IOException {
        fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
        if (fileChannel.size() <= capacity) {
            read(0, capacity);
            wholeFileInBuffer = true;
        } else {
            buffer.flip();
        }
    }

    /**
     * Returns the position of the file
     *
     * @return the position of the file
     */
    public long position() {
        return offset + buffer.position();
    }

    /**
     * Skips the provided number of bytes in the file without reading new data.
     *
     * @param bytesToSkip the number of bytes to skip
     */
    public void skip(int bytesToSkip) {
        position(position() + bytesToSkip);
    }

    /**
     * Sets the position of the file without reading new data.
     *
     * @param pos the new position
     */
    public void position(long pos) {
        long bufferDelta = pos - (offset + buffer.position());
        long newBufferPos = buffer.position() + bufferDelta;
        if (0 <= newBufferPos && newBufferPos <= buffer.capacity()) {
            buffer.position((int) newBufferPos);
        } else {
            // makes sure that the next ensureRemaining will load from file
            buffer.position(0);
            buffer.limit(0);
            offset = pos;
        }
    }

    /**
     * Ensures that the provided number of bytes are available in the {@linkplain #buffer buffer}
     *
     * @param minRemaining the number of bytes which are guaranteed to be available in the {@linkplain #buffer buffer}
     * @throws IOException           If some I/O error occurs
     * @throws IllegalStateException If the provided number of bytes is greater thatn the buffer's capacity
     */
    public void ensureRemaining(int minRemaining) throws IOException {
        ensureRemaining(minRemaining, capacity);
    }

    public void ensureRemaining(int minRemaining, int maxRead) throws IOException {
        if (wholeFileInBuffer) {
            return;
        }
        if (minRemaining > capacity) {
            throw new IllegalStateException(String.format("Length (%d) greater than buffer capacity (%d)", minRemaining, capacity));
        }
        if (buffer.remaining() < minRemaining) {
            read(position(), maxRead);
        }
    }

    /**
     * Gets a byte from the current {@linkplain #position() position} of this file.
     * If the {@linkplain #buffer buffer} does not fully contain this byte, loads another slice of the file into the buffer.
     *
     * @return The byte at the file's current position
     * @throws IOException If some I/O error occurs
     */
    public short get() throws IOException {
        ensureRemaining(SIZE_OF_BYTE);
        return buffer.get();
    }

    /**
     * Gets a short from the current {@linkplain #position() position} of this file.
     * If the {@linkplain #buffer buffer} does not fully contain this short, loads another slice of the file into the buffer.
     *
     * @return The short at the file's current position
     * @throws IOException If some I/O error occurs
     */
    public short getShort() throws IOException {
        ensureRemaining(SIZE_OF_SHORT);
        return buffer.getShort();
    }

    /**
     * Gets a short from the current {@linkplain #position() position} of this file.
     * If the {@linkplain #buffer buffer} does not fully contain this short, loads another slice of the file into the buffer.
     *
     * @return The short at the file's current position
     * @throws IOException If some I/O error occurs
     */
    public int getUnsignedShort() throws IOException {
        return getShort() & 0xff;
    }

    /**
     * Gets a int from the current {@linkplain #position() position} of this file and converts it to an unsigned short.
     * If the {@linkplain #buffer buffer} does not fully contain this int, loads another slice of the file into the buffer.
     *
     * @return The int at the file's current position
     * @throws IOException If some I/O error occurs
     */
    public int getInt() throws IOException {
        ensureRemaining(SIZE_OF_INT);
        return buffer.getInt();
    }

    /**
     * Gets a long from the current {@linkplain #position() position} of this file.
     * If the {@linkplain #buffer buffer} does not fully contain this long, loads another slice of the file into the buffer.
     *
     * @return The long at the file's current position
     * @throws IOException If some I/O error occurs
     */
    public long getLong() throws IOException {
        ensureRemaining(SIZE_OF_LONG);
        return buffer.getLong();
    }

    /**
     * Gets a byte from the underlying buffer without checking if this part of the file is actually in the buffer.
     * <p>
     * Always mare sure to call {@link #ensureRemaining} before.
     * </p>
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If the buffer's current position is not smaller than its limit
     */
    public byte getUnsafe() {
        return buffer.get();
    }

    /**
     * Gets a short from the underlying buffer without checking if this part of the file is actually in the buffer.
     * <p>
     * Always mare sure to call {@link #ensureRemaining} before.
     * </p>
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than two bytes remaining in this buffer
     */
    public short getUnsafeShort() {
        return buffer.getShort();
    }

    /**
     * Gets an int from the underlying buffer without checking if this part of the file is actually in the buffer.
     * <p>
     * Always mare sure to call {@link #ensureRemaining} before.
     * </p>
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than four bytes remaining in this buffer
     */
    public int getUnsafeInt() {
        return buffer.getInt();
    }

    /**
     * Gets a long from the underlying buffer without checking if this part of the file is actually in the buffer.
     * <p>
     * Always mare sure to call {@link #ensureRemaining} before.
     * </p>
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than eight bytes remaining in this buffer
     */
    public long getUnsafeLong() {
        return buffer.getLong();
    }

    public long size() throws IOException {
        if (fileChannel == null) {
            throw new IllegalStateException("setFile has not been called yet");
        }
        return fileChannel.size();
    }

    public boolean isSet() {
        return fileChannel != null;
    }

    @Override
    public void resetState() {
        if (fileChannel == null) {
            throw new IllegalStateException("setFile has not been called yet");
        }
        buffer.clear();
        offset = 0;
        wholeFileInBuffer = false;
        try {
            fileChannel.close();
        } catch (IOException ignore) {
        }
        fileChannel = null;
    }

    private void read(long offset, int limit) throws IOException {
        if (limit > capacity) {
            limit = capacity;
        }
        buffer.clear();
        fileChannel.position(offset);
        buffer.limit(limit);
        fileChannel.read(buffer);
        buffer.flip();
        this.offset = offset;
    }
}
