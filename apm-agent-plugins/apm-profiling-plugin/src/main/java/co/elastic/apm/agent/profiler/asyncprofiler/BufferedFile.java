package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

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
    private long offset;
    private long limit;
    private boolean wholeFileInBuffer;
    @Nullable
    private RandomAccessFile randomAccessFile;
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
        randomAccessFile = new RandomAccessFile(file, "r");
        fileChannel = randomAccessFile.getChannel();
        read(0);
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
     * Skips the provided number of bytes in the file and ensures that the new position is available in the {@linkplain #buffer buffer}.
     *
     * @param bytesToSkip the number of bytes to skip
     * @throws IOException If some I/O error occurs
     */
    public void skip(int bytesToSkip) throws IOException {
        position(position() + bytesToSkip);
    }

    /**
     * Sets the position of the file and ensures that the new position is available in the {@linkplain #buffer buffer}.
     *
     * @param pos the new position
     * @throws IOException If some I/O error occurs
     */
    public void position(long pos) throws IOException {
        position(pos, 0);
    }

    public void position(long pos, int length) throws IOException {
        ensureRange(pos, length);
        ((Buffer) buffer).position((int) (pos - offset));
    }

    /**
     * Ensures that the provided number of bytes are available in the {@linkplain #buffer buffer}
     *
     * @param numberOfBytes the number of bytes which are guaranteed to be available in the {@linkplain #buffer buffer}
     * @throws IOException           If some I/O error occurs
     * @throws IllegalStateException If the provided number of bytes is greater thatn the buffer's capacity
     */
    public void ensureRemaining(int numberOfBytes) throws IOException {
        ensureRange(position(), numberOfBytes);
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
     * Gets a int from the current {@linkplain #position() position} of this file.
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
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If the buffer's current position is not smaller than its limit
     */
    public byte getUnsafe() {
        return buffer.get();
    }

    /**
     * Gets a short from the underlying buffer without checking if this part of the file is actually in the buffer.
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than two bytes remaining in this buffer
     */
    public short getUnsafeShort() {
        return buffer.getShort();
    }

    /**
     * Gets an int from the underlying buffer without checking if this part of the file is actually in the buffer.
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than four bytes remaining in this buffer
     */
    public int getUnsafeInt() {
        return buffer.getInt();
    }

    /**
     * Gets a long from the underlying buffer without checking if this part of the file is actually in the buffer.
     *
     * @return The byte at the file's current position
     * @throws java.nio.BufferUnderflowException If there are fewer than eight bytes remaining in this buffer
     */
    public long getUnsafeLong() {
        return buffer.getLong();
    }

    public long size() throws IOException {
        return fileChannel.size();
    }

    public boolean isSet() {
        return fileChannel != null;
    }

    @Override
    public void resetState() {
        buffer.clear();
        offset = 0;
        limit = 0;
        try {
            randomAccessFile.close();
        } catch (IOException ignore) {
        }
        randomAccessFile = null;
        fileChannel = null;
    }

    private void ensureRange(long newOffset, int length) throws IOException {
        if (wholeFileInBuffer) {
            return;
        }
        if (length > capacity) {
            throw new IllegalStateException(String.format("Length (%d) greater than buffer capacity (%d)", length, capacity));
        }
        long minPosInBuffer = newOffset + length;
        if (newOffset < this.offset || limit < minPosInBuffer) {
            read(newOffset);
        }
    }

    private void read(long offset) throws IOException {
        buffer.clear();
        fileChannel.position(offset);
        fileChannel.read(buffer);
        buffer.flip();
        this.offset = offset;
        this.limit = offset + capacity;
        wholeFileInBuffer = fileChannel.size() <= buffer.capacity();
    }
}
