package com.narmanb.bmhero;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class DiagnosticFilesHarness {
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("bmhero-diagnostics").toFile();
        DiagnosticFiles first = new DiagnosticFiles(directory);
        first.beginAttempt();
        first.stage("loading libBMHero");
        Files.write(new File(directory, "native-startup.log").toPath(),
            (new String(new char[100000]).replace('\0', 'x') + "\nVulkan failed\n").getBytes(StandardCharsets.UTF_8));
        // Simulate another process reading the files after the game died.
        String report = new DiagnosticFiles(directory).report();
        if (!report.contains("loading libBMHero") || !report.contains("Vulkan failed"))
            throw new AssertionError("Lost evidence across process restart");
        if (report.length() > 70000) throw new AssertionError("Report is not bounded");
        first.failure(new IOException("asset extraction failed"));
        if (!new DiagnosticFiles(directory).report().contains("asset extraction failed"))
            throw new AssertionError("Java failure was not persisted");
        first.beginAttempt();
        if (first.report().contains("Vulkan failed") || first.report().contains("asset extraction failed"))
            throw new AssertionError("New attempt mixed in stale evidence");
        System.out.println("Diagnostics survive restart, keep log tail, and reset per attempt");
    }
}
