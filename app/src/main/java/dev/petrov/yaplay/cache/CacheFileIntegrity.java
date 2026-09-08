package dev.petrov.yaplay.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Checksums are separate from track metadata, which playback may refresh. */
final class CacheFileIntegrity {
    enum State { UNKNOWN, VALID, DAMAGED }

    private CacheFileIntegrity() {
    }

    static File checksumFile(File file) {
        return new File(file.getPath() + ".sha256");
    }

    static State check(File file) throws IOException {
        if (!file.isFile() || file.length() == 0) {
            return State.DAMAGED;
        }
        File checksum = checksumFile(file);
        if (!checksum.isFile() || checksum.length() > 128) {
            return State.UNKNOWN;
        }
        String expected = new String(Files.readAllBytes(checksum.toPath()), StandardCharsets.US_ASCII).trim();
        if (!expected.matches("[a-f0-9]{64}")) {
            return State.UNKNOWN;
        }
        return expected.equals(hash(file)) ? State.VALID : State.DAMAGED;
    }

    static void remember(File file) throws IOException {
        File checksum = checksumFile(file);
        File temp = File.createTempFile(checksum.getName(), ".tmp", checksum.getParentFile());
        try {
            Files.write(temp.toPath(), hash(file).getBytes(StandardCharsets.US_ASCII));
            replace(temp, checksum);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    static void replace(File temp, File target) throws IOException {
        // Same-directory rename: never expose a partially copied cache entry.
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            try (InputStream in = Files.newInputStream(file.toPath())) {
                int count;
                while ((count = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(Character.forDigit((value >>> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
