package org.etsdb;

//import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.etsdb.impl.Input;
import org.etsdb.impl.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.charset.Charset;

/**
 * A class that behaves much like a ByteBuffer, but automatically expands as required.
 * <p>
 * Put methods write values into the array at the current write offset, and update the write offset. Get methods read
 * values at the current read offset, and update the read offset. Read methods read values at the current read offset
 * but do not update the read offset.
 *
 * @author Matthew
 */
public class ByteArrayBuilder {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int DEFAULT_CAPACITY = 32;

    private byte[] buffer;
    private int writeOffset;
    private int readOffset;

    public ByteArrayBuilder() {
        this(DEFAULT_CAPACITY);
    }

    public ByteArrayBuilder(int initialSize) {
        buffer = new byte[initialSize];
    }

//    @SuppressFBWarnings("EI_EXPOSE_REP")
    public ByteArrayBuilder(byte[] buffer) {
        this.buffer = buffer;
        this.writeOffset = buffer.length;
    }

    public void clear() {
        writeOffset = 0;
        readOffset = 0;
    }

//    @SuppressFBWarnings("EI_EXPOSE_REP")
    public byte[] getBuffer() {
        return buffer;
    }

    public int getReadOffset() {
        return readOffset;
    }

    public int getAvailable() {
        return writeOffset - readOffset;
    }

    public void resetCapacity() {
        if (buffer.length > DEFAULT_CAPACITY)
            buffer = new byte[DEFAULT_CAPACITY];
    }

    //
    // 
    // Put
    //
    public void put(byte b) {
        ensureCapacity(1);
        buffer[writeOffset++] = b;
    }

    public void put(byte[] src) {
        put(src, 0, src.length);
    }

    public void put(byte[] src, int offset, int length) {
        ensureCapacity(length);
        System.arraycopy(src, offset, buffer, writeOffset, length);
        writeOffset += length;
    }

    public void putBoolean(boolean b) {
        ensureCapacity(1);
        buffer[writeOffset++] = (byte) (b ? 0x1 : 0x0);
    }

    public void putShort(short s) {
        ensureCapacity(2);
        buffer[writeOffset++] = (byte) (s >> 8);
        buffer[writeOffset++] = (byte) s;
    }

    public void putInt(int i) {
        ensureCapacity(4);
        buffer[writeOffset++] = (byte) (i >> 24);
        buffer[writeOffset++] = (byte) (i >> 16);
        buffer[writeOffset++] = (byte) (i >> 8);
        buffer[writeOffset++] = (byte) i;
    }

    public void putLong(long l) {
        ensureCapacity(8);
        buffer[writeOffset++] = (byte) (l >> 56);
        buffer[writeOffset++] = (byte) (l >> 48);
        buffer[writeOffset++] = (byte) (l >> 40);
        buffer[writeOffset++] = (byte) (l >> 32);
        buffer[writeOffset++] = (byte) (l >> 24);
        buffer[writeOffset++] = (byte) (l >> 16);
        buffer[writeOffset++] = (byte) (l >> 8);
        buffer[writeOffset++] = (byte) l;
    }

    public void putFloat(float f) {
        putInt(Float.floatToIntBits(f));
    }

    public void putDouble(double d) {
        putLong(Double.doubleToLongBits(d));
    }

    /**
     * String serialization with optimization for short strings.
     *
     * @param s String
     */
    public void putString(String s) {
        byte[] bytes = null;
        if (s != null)
            bytes = s.getBytes(UTF8);

        // Ensure necessary capacity for the string.
        int ensureLength;
        if (bytes == null)
            ensureLength = 1;
        else {
            if (bytes.length >= 0x20000000)
                throw new IllegalArgumentException("Value too big for compact int");
            if (bytes.length >= 0x200000)
                ensureLength = 4;
            else if (bytes.length >= 0x2000)
                ensureLength = 3;
            else if (bytes.length >= 0x20)
                ensureLength = 2;
            else
                ensureLength = 1;
            ensureLength += bytes.length;
        }
        ensureCapacity(ensureLength);

        // The first bit of the stored values determines if the string is null. The next two bits determine how many 
        // bytes are used to store the string length. The rest of the value without these bits is the length.
        // 100 = null.
        // 011 = 4 bytes
        // 010 = 3 bytes
        // 001 = 2 bytes
        // 000 = 1 byte
        //
        // This method is able to store string lengths up to 536870911 bytes.

        if (bytes == null)
            buffer[writeOffset++] = (byte) 0x80;
        else {
            if (bytes.length >= 0x200000) {
                buffer[writeOffset++] = (byte) ((bytes.length >> 24) | 0x60);
                buffer[writeOffset++] = (byte) (bytes.length >> 16);
                buffer[writeOffset++] = (byte) (bytes.length >> 8);
                buffer[writeOffset++] = (byte) bytes.length;
            } else if (bytes.length >= 0x2000) {
                buffer[writeOffset++] = (byte) ((bytes.length >> 16) | 0x40);
                buffer[writeOffset++] = (byte) (bytes.length >> 8);
                buffer[writeOffset++] = (byte) bytes.length;
            } else if (bytes.length >= 0x20) {
                buffer[writeOffset++] = (byte) ((bytes.length >> 8) | 0x20);
                buffer[writeOffset++] = (byte) bytes.length;
            } else
                buffer[writeOffset++] = (byte) bytes.length;

            // Write the actual string.
            System.arraycopy(bytes, 0, buffer, writeOffset, bytes.length);
            writeOffset += bytes.length;
        }
    }

    public void put(Input in, int length) throws IOException {
        ensureCapacity(length);
        int done = 0;
        while (done < length) {
            int count;
            try {
                count = in.read(buffer, writeOffset + done, length - done);
            } catch (IndexOutOfBoundsException e) {
                throw new RuntimeException("buf: " + buffer.length + ", writeOffset=" + writeOffset + ", length="
                        + length + ", done=" + done, e);
            }
            if (count == -1)
                break;
            done += count;
        }
        writeOffset += done;
    }

    //
    // 
    // Get
    //
    public byte get() {
        ensureAvailable(1);
        return buffer[readOffset++];
    }

    public int getByte() {
        ensureAvailable(1);
        return buffer[readOffset++] & 0xff;
    }

    public void get(byte[] dst) {
        get(dst, 0, dst.length);
    }

    public void get(byte[] dst, int offset, int length) {
        ensureAvailable(length);
        System.arraycopy(buffer, readOffset, dst, offset, length);
        readOffset += length;
    }

    public boolean getBoolean() {
        ensureAvailable(1);
        return !(buffer[readOffset++] == 0);
    }

    public short getShort() {
        ensureAvailable(2);
        return makeShort(buffer[readOffset++], buffer[readOffset++]);
    }

    public int getInt() {
        ensureAvailable(4);
        return makeInt(buffer[readOffset++], buffer[readOffset++], buffer[readOffset++], buffer[readOffset++]);
    }

    public long getLong() {
        ensureAvailable(8);
        return makeLong(buffer[readOffset++], buffer[readOffset++], buffer[readOffset++], buffer[readOffset++],
                buffer[readOffset++], buffer[readOffset++], buffer[readOffset++], buffer[readOffset++]);
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public String getString() {
        // Get the length
        byte b = get();

        // Null?
        if ((b & 0x80) == 0x80)
            return null;

        int length = 0;
        if ((b & 0x60) == 0x60) {
            ensureAvailable(3);
            length |= (b & 0x1f) << 24;
            length |= (buffer[readOffset++] & 0xff) << 16;
            length |= (buffer[readOffset++] & 0xff) << 8;
            length |= buffer[readOffset++] & 0xff;
        } else if ((b & 0x40) == 0x40) {
            ensureAvailable(2);
            length |= (b & 0x1f) << 16;
            length |= (buffer[readOffset++] & 0xff) << 8;
            length |= buffer[readOffset++] & 0xff;
        } else if ((b & 0x20) == 0x20) {
            ensureAvailable(1);
            length |= (b & 0x1f) << 8;
            length |= buffer[readOffset++] & 0xff;
        } else
            length = b & 0xff;

        ensureAvailable(length);
        String s = new String(buffer, readOffset, length, UTF8);
        readOffset += length;
        return s;
    }

    public void get(OutputStream out, int length) throws IOException {
        ensureAvailable(length);
        out.write(buffer, readOffset, length);
        readOffset += length;
    }

    //
    //
    // Private
    //
    private void ensureAvailable(int len) {
        if (getAvailable() < len)
            throw new BufferOverflowException();
    }

    private void ensureCapacity(int len) {
        int min = writeOffset + len;
        if (buffer.length < min) {
            int newLength = buffer.length << 1;
            while (newLength < min)
                newLength <<= 1;
            if (newLength > Utils.MAX_DATA_LENGTH)
                throw new RuntimeException("Data length can not exceed " + Utils.MAX_DATA_LENGTH);
            byte[] b = new byte[newLength];
            System.arraycopy(buffer, 0, b, 0, buffer.length);
            buffer = b;
        }
    }

    private short makeShort(byte b1, byte b2) {
        return (short) (((b1 & 0xff) << 8) | (b2 & 0xff));
    }

    private int makeInt(byte b1, byte b2, byte b3, byte b4) {
        return ((b1 & 0xff) << 24) | ((b2 & 0xff) << 16) | ((b3 & 0xff) << 8) | (b4 & 0xff);
    }

    private long makeLong(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        long l = (b1 & 0xffL) << 56;
        l |= (b2 & 0xffL) << 48;
        l |= (b3 & 0xffL) << 40;
        l |= (b4 & 0xffL) << 32;
        l |= (b5 & 0xffL) << 24;
        l |= (b6 & 0xffL) << 16;
        l |= (b7 & 0xffL) << 8;
        l |= b8 & 0xffL;
        return l;
    }
}
