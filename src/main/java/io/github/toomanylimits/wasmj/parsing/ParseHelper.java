package io.github.toomanylimits.wasmj.parsing;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.util.funcs.BiThrowingBiFunction;
import io.github.toomanylimits.wasmj.util.funcs.BiThrowingFunction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ParseHelper {

    public static <R> List<R> readVector(InputStream stream, BiThrowingFunction<InputStream, R, ? extends IOException, ? extends ModuleParseException> func) throws IOException, ModuleParseException {
        int len = readUnsignedWasmInt(stream);
        ArrayList<R> result = new ArrayList<>(len);
        for (int i = 0; i < len; i++)
            result.add(func.accept(stream));
        return result;
    }
    public static <R> List<R> readVectorIndexed(InputStream stream, BiThrowingBiFunction<Integer, InputStream, R, ? extends IOException, ? extends ModuleParseException> func) throws IOException, ModuleParseException {
        int len = readUnsignedWasmInt(stream);
        ArrayList<R> result = new ArrayList<>(len);
        for (int i = 0; i < len; i++)
            result.add(func.accept(i, stream));
        return result;
    }

    public static int readUnsignedWasmInt(InputStream stream) throws IOException, ModuleParseException {
        int result = 0;
        int shift = 0;
        while (true) {
            if (shift > 5 * 7) throw new ModuleParseException("Failed to read unsigned WASM integer - int too long!");
            int b = stream.read();
            result = result | ((b & 0x7F) << shift);
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (result < 0)
            throw new ModuleParseException("Failed to read unsigned WASM integer - int was above signed limit!");
        return result;
    }

    public static int readSignedWasmInt(InputStream stream) throws IOException, ModuleParseException {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (shift > 5 * 7) throw new ModuleParseException("Failed to read signed WASM integer - int too long!");
            b = stream.read();
            result = result | ((b & 0x7F) << shift);
            shift += 7;
        } while ((b & 0x80) != 0);

        if ((shift < 32) && ((b & 0x40) != 0))
            result = result | ((~0) << shift);
        return result;
    }

    public static long readUnsignedWasmLong(InputStream stream) throws IOException, ModuleParseException {
        long result = 0L;
        int shift = 0;
        while (true) {
            if (shift > 9 * 7) throw new ModuleParseException("Failed to read unsigned WASM integer - int too long!");
            long b = stream.read();
            result = result | ((b & 0x7FL) << shift);
            if ((b & 0x80L) == 0) break;
            shift += 7;
        }
        if (result < 0)
            throw new ModuleParseException("Failed to read unsigned WASM integer - int was above signed limit!");
        return result;
    }

    public static long readSignedWasmLong(InputStream stream) throws IOException, ModuleParseException {
        long result = 0L;
        int shift = 0;
        long b;
        do {
            if (shift > 9 * 7) throw new ModuleParseException("Failed to read signed WASM integer - int too long!");
            b = stream.read();
            result = result | ((b & 0x7FL) << shift);
            shift += 7;
        } while ((b & 0x80L) != 0L);

        if ((shift < 64) && (b & 0x40L) != 0L)
            result = result | ((~0L) << shift);
        return result;
    }

    public static float readFloat(InputStream stream) throws IOException {
        return Float.intBitsToFloat(
            stream.read() |
            stream.read() << 8 |
            stream.read() << 16 |
            stream.read() << 24
        );
    }

    public static double readDouble(InputStream stream) throws IOException {
        return Double.longBitsToDouble(
                (long) stream.read() |
                (long) stream.read() << 8 |
                (long) stream.read() << 16 |
                (long) stream.read() << 24 |
                (long) stream.read() << 32 |
                (long) stream.read() << 40 |
                (long) stream.read() << 48 |
                (long) stream.read() << 56
        );
    }

    public static boolean readBoolean(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        return switch (b) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new ModuleParseException("Expected boolean, received invalid byte " + b);
        };
    }

    public static byte[] readByteArray(InputStream stream) throws IOException, ModuleParseException {
        int length = readUnsignedWasmInt(stream);
        if (length < 0) throw new ModuleParseException("Byte array too long, max length is ${Int.MAX_VALUE}");
        return stream.readNBytes(length);
    }

    public static String readString(InputStream stream) throws IOException, ModuleParseException {
        return new String(readByteArray(stream), StandardCharsets.UTF_8);
    }
}