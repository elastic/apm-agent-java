package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.tracer.util.HexUtils;

import java.nio.charset.StandardCharsets;

abstract class CharAccessor<T> {

    abstract int length(T text);

    abstract char charAt(T text, int index);

    abstract void readHex(T text, int offset, byte[] into);

    abstract byte readHexByte(T text, int offset);

    abstract String asString(T text);

    int getLeadingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(text, count))) {
            count++;
        }
        return count;
    }

    int getTrailingWhitespaceCount(T text) {
        int count = 0;
        int len = length(text);
        while (count < len && Character.isWhitespace(charAt(text, len - 1 - count))) {
            count++;
        }
        return count;
    }

    public boolean containsAtOffset(T text, int offset, CharSequence substringToCheck) {
        int len = length(text);
        if (offset < 0 || offset > len) {
            throw new IllegalArgumentException("Bad offset: " + offset);
        }
        int subStrLen = substringToCheck.length();
        if (offset + subStrLen > len) {
            return false;
        }

        for (int i = 0; i < subStrLen; i++) {
            if (charAt(text, i + offset) != substringToCheck.charAt(i)) {
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
        char charAt(CharSequence text, int index) {
            return text.charAt(index);
        }

        @Override
        void readHex(CharSequence text, int offset, byte[] into) {
            HexUtils.nextBytes(text, offset, into);
        }

        @Override
        byte readHexByte(CharSequence text, int offset) {
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
        char charAt(byte[] text, int index) {
            return (char) text[index];
        }

        @Override
        void readHex(byte[] text, int offset, byte[] into) {
            HexUtils.nextBytesAscii(text, offset, into);
        }

        @Override
        byte readHexByte(byte[] text, int offset) {
            return HexUtils.getNextByteAscii(text, offset);
        }

        @Override
        String asString(byte[] text) {
            return new String(text, StandardCharsets.UTF_8);
        }
    }

}
