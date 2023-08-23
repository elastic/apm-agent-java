package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.tracer.util.HexUtils;

import java.nio.charset.StandardCharsets;

abstract class CharAccessor<T> {

    abstract int length(T text);

    abstract char charAt(int index, T text);

    abstract void readHex(int offset, byte[] into, T text);

    abstract byte readHexByte(int offset, T text);

    abstract String asString(T text);

    int getLeadingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(count, text))) {
            count++;
        }
        return count;
    }

    int getTrailingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(len - 1 - count, text))) {
            count++;
        }
        return count;
    }

    public boolean equalsAtOffset(int offset, CharSequence substringToCheck, T text) {
        int len = length(text);
        int subStrLen = substringToCheck.length();
        if (offset + subStrLen > len) {
            return false;
        }

        for (int i = 0; i < subStrLen; i++) {
            if (charAt(i + offset, text) != substringToCheck.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public static CharAccessor<CharSequence> forCharSequence() {
        return CharSeq.INSTANCE;
    }

    public static CharAccessor<byte[]> forAsciiBytes() {
        return AsciiBytes.INSTANCE;
    }


    private static class CharSeq extends CharAccessor<CharSequence> {

        static final CharSeq INSTANCE = new CharSeq();

        @Override
        int length(CharSequence text) {
            return text.length();
        }

        @Override
        char charAt(int index, CharSequence text) {
            return text.charAt(index);
        }

        @Override
        void readHex(int offset, byte[] into, CharSequence text) {
            HexUtils.nextBytes(text, offset, into);
        }

        @Override
        byte readHexByte(int offset, CharSequence text) {
            return HexUtils.getNextByte(text, offset);
        }

        @Override
        String asString(CharSequence text) {
            return text.toString();
        }
    }

    private static class AsciiBytes extends CharAccessor<byte[]> {

        static final AsciiBytes INSTANCE = new AsciiBytes();

        @Override
        int length(byte[] text) {
            return text.length;
        }

        @Override
        char charAt(int index, byte[] text) {
            return (char) text[index];
        }

        @Override
        void readHex(int offset, byte[] into, byte[] text) {
            HexUtils.nextBytesAscii(text, offset, into);
        }

        @Override
        byte readHexByte(int offset, byte[] text) {
            return HexUtils.getNextByteAscii(text, offset);
        }

        @Override
        String asString(byte[] text) {
            return new String(text, StandardCharsets.UTF_8);
        }
    }

}
