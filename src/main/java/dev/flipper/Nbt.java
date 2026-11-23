package dev.flipper;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Minimal NBT reader: just enough to read Hypixel's item_bytes. */
public final class Nbt {
    private Nbt() {}

    public static Map<String, Object> parseB64(String b64) throws IOException {
        byte[] gz = Base64.getDecoder().decode(b64);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(gz))))) {
            int t = in.readByte();
            if (t != 10) throw new IOException("NBT root is not a compound");
            in.readUTF();
            return readCompound(in);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> m = new HashMap<>();
        while (true) {
            int t = in.readByte();
            if (t == 0) break;
            String name = in.readUTF();
            m.put(name, read(in, t));
        }
        return m;
    }

    private static Object read(DataInputStream in, int t) throws IOException {
        switch (t) {
            case 1: return (int) in.readByte();
            case 2: return (int) in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return (double) in.readFloat();
            case 6: return in.readDouble();
            case 7: { int n = in.readInt(); in.skipNBytes(n); return null; }
            case 8: return in.readUTF();
            case 9: {
                int it = in.readByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, Math.min(n, 4096)));
                for (int k = 0; k < n; k++) l.add(read(in, it));
                return l;
            }
            case 10: return readCompound(in);
            case 11: { int n = in.readInt(); in.skipNBytes(4L * n); return null; }
            case 12: { int n = in.readInt(); in.skipNBytes(8L * n); return null; }
            default: throw new IOException("bad NBT tag " + t);
        }
    }
}
