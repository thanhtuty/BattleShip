package battleship;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Một thông điệp = một dòng. Base64 từng trường để tránh lỗi dấu phân cách. */
public final class Wire {
    private Wire() {}
    public static String pack(String... fields) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i];
            if (i > 0) s.append('\t');
            s.append(Base64.getEncoder().encodeToString(f.getBytes(StandardCharsets.UTF_8)));
        }
        return s.toString();
    }
    public static String[] unpack(String line) {
        String[] a = line.split("\t", -1);
        for (int i = 0; i < a.length; i++)
            a[i] = new String(Base64.getDecoder().decode(a[i]), StandardCharsets.UTF_8);
        return a;
    }
    /** Không dùng readLine không giới hạn: chặn gói quá dài. */
    public static String read(Reader in) throws IOException {
        StringBuilder s = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1) {
            if (ch == '\n') return s.toString();
            if (ch != '\r') s.append((char) ch);
            if (s.length() > 16384) throw new IOException("Thông điệp quá dài");
        }
        if (s.length() > 0) throw new EOFException("Thông điệp thiếu ký tự xuống dòng");
        return null;
    }
}
