package com.narmanb.bmhero;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Files are shared with the game process and survive a native crash. */
final class DiagnosticFiles {
    private final File directory;
    DiagnosticFiles(File directory) { this.directory = directory; }
    void beginAttempt() {
        directory.mkdirs();
        for (String name : new String[]{"startup-stage.txt", "native-startup.log", "java-failure.txt"})
            new File(directory, name).delete();
        stage("Starting game process");
    }
    void stage(String stage) { write("startup-stage.txt", System.currentTimeMillis() + " " + stage + "\n", true); }
    void failure(Throwable error) {
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        write("java-failure.txt", text.toString(), false);
    }
    private void write(String name, String text, boolean append) {
        try (FileOutputStream out = new FileOutputStream(new File(directory, name), append)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException ignored) { /* Diagnostics must not cause a second failure. */ }
    }
    String report() {
        StringBuilder out = new StringBuilder();
        for (String name : new String[]{"startup-stage.txt", "java-failure.txt", "native-startup.log"}) {
            File file = new File(directory, name);
            if (!file.isFile()) continue;
            out.append("\n--- ").append(name).append(" ---\n");
            try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                int size = (int)Math.min(in.length(), name.equals("native-startup.log") ? 65536 : 8192);
                in.seek(in.length() - size);
                byte[] bytes = new byte[size];
                in.readFully(bytes);
                out.append(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException e) { out.append("Cannot read: ").append(e); }
        }
        return out.toString();
    }
}
