package io.cpogx.lambdatest.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileUtil {

    private FileUtil() {
    }

    public static String writeString(String relativePath, String content) {
        try {
            Path path = Path.of(relativePath).toAbsolutePath().normalize();
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return path.toString();
        } catch (Exception e) {
            throw new RuntimeException("failed writing file: " + relativePath, e);
        }
    }
}
