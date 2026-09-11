package com.opencity.radio;

import java.io.*;
import java.util.*;
import java.util.function.LongConsumer;
import java.util.zip.*;

/** Streams into a caller-owned staging directory; caller commits only a validated pack. */
public final class SafeZip {
    public static final long MAX_TOTAL = 8L * 1024 * 1024 * 1024;
    public static final long MAX_FILE = 2L * 1024 * 1024 * 1024;
    private SafeZip() {}
    public static File resolve(File root, String name) throws IOException {
        if (name.trim().isEmpty() || name.startsWith("/") || name.contains("\\") || name.matches("^[A-Za-z]:.*"))
            throw new IOException("Unsafe content path");
        File file = new File(root, name).getCanonicalFile();
        if (!file.getPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("Content path escapes pack");
        return file;
    }
    public static void extract(InputStream input, File target, LongConsumer progress) throws IOException {
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create staging folder");
        Set<String> names = new HashSet<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] bytes = new byte[65536];
            while ((entry = zip.getNextEntry()) != null) {
                if (names.size() >= 20000) throw new IOException("Too many ZIP entries");
                File file = resolve(target, entry.getName());
                if (!names.add(file.getPath())) throw new IOException("Duplicate ZIP entry");
                if (entry.isDirectory()) {
                    if (!file.isDirectory() && !file.mkdirs()) throw new IOException("Cannot create ZIP folder");
                } else {
                    if (entry.getSize() > MAX_FILE) throw new IOException("ZIP entry exceeds 2 GiB");
                    File parent = file.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create ZIP folder");
                    long size = 0;
                    try (OutputStream output = new FileOutputStream(file)) {
                        int n;
                        while ((n = zip.read(bytes)) != -1) {
                            size += n; total += n;
                            if (size > MAX_FILE || total > MAX_TOTAL) throw new IOException("ZIP exceeds extraction limits");
                            if (target.getUsableSpace() <= n + 16L * 1024 * 1024) throw new IOException("Insufficient free storage");
                            output.write(bytes, 0, n); progress.accept(total);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }
}
