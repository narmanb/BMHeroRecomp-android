package com.narmanb.bmhero;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** This screen never loads game libraries; it remains usable after a game crash. */
public final class LauncherActivity extends Activity {
    private static final int PICK_ROM = 72, SAVE_REPORT = 73;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File data;
    private TextView status;
    private Button start, pick, save, copy;
    private boolean busy;
    private String report = "Collecting crash details…";
    private byte[] trace;
    private String traceName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        data = new File(getFilesDir(), "bmhero");
        data.mkdirs();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("Bomberman Hero — test 2"); title.setTextSize(24);
        layout.addView(title);
        status = new TextView(this); status.setTextSize(16); layout.addView(status);
        pick = button(layout, "Select USA ROM or ZIP", () -> {
            try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_ROM); }
            catch (RuntimeException e) { error(e); }
        });
        start = button(layout, "Start", () -> {
            ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            boolean running = false;
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
                if ((getPackageName() + ":game").equals(process.processName)) running = true;
            if (!running) new DiagnosticFiles(data).beginAttempt();
            startActivity(new Intent(this, MainActivity.class));
        });
        copy = button(layout, "Copy crash report", () -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Bomberman Hero crash", report));
            Toast.makeText(this, "Crash report copied", Toast.LENGTH_SHORT).show();
        });
        save = button(layout, "Save crash report", () -> {
            try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "Bomberman-Hero-crash-report.zip"), SAVE_REPORT); }
            catch (RuntimeException e) { error(e); }
        });
        button(layout, "View crash report", () -> new AlertDialog.Builder(this)
            .setTitle("Crash report").setMessage(report).setPositiveButton("OK", null).show());
        updateStatus();
    }
    private Button button(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this); button.setText(label);
        button.setOnClickListener(view -> action.run()); layout.addView(button); return button;
    }
    private void updateStatus() {
        boolean rom = new File(data, "bmhero.z64").length() == 12 * 1024 * 1024;
        status.setText(busy ? "Working…" : rom ? "USA ROM imported. Ready to start." :
            "Select your Bomberman Hero USA ROM first. No ROM is bundled.\nIf Start crashes, reopen this app and save the crash report.");
        start.setEnabled(rom && !busy); pick.setEnabled(!busy);
    }
    @Override protected void onResume() {
        super.onResume();
        copy.setEnabled(false); save.setEnabled(false);
        io.execute(() -> {
            StringBuilder text = new StringBuilder("Bomberman Hero 0.7.3-android.2\n");
            text.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" Android ").append(Build.VERSION.RELEASE).append(" API ").append(Build.VERSION.SDK_INT).append('\n');
            text.append(new DiagnosticFiles(data).report());
            byte[] lastTrace = null; String lastName = null;
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
                    for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(getPackageName(), 0, 8)) {
                        text.append("\nProcess: ").append(exit.getProcessName()).append(" PID ").append(exit.getPid())
                            .append("\nTime: ").append(new Date(exit.getTimestamp()))
                            .append("\nReason: ").append(exit.getReason()).append(" status/signal: ").append(exit.getStatus())
                            .append("\nDescription: ").append(exit.getDescription()).append('\n');
                        if (lastTrace == null && (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                                || exit.getReason() == ApplicationExitInfo.REASON_ANR)) {
                            try (InputStream in = exit.getTraceInputStream()) {
                                if (in != null) {
                                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                    byte[] buffer = new byte[8192]; int n;
                                    while ((n = in.read(buffer)) != -1 && bytes.size() < 4 * 1024 * 1024)
                                        bytes.write(buffer, 0, Math.min(n, 4 * 1024 * 1024 - bytes.size()));
                                    lastTrace = bytes.toByteArray();
                                    lastName = (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE ? "native-tombstone-" : "anr-trace-")
                                        + exit.getPid() + "-" + exit.getTimestamp()
                                        + (exit.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE ? ".pb" : ".txt");
                                    text.append("Attached historical trace: ").append(lastName)
                                        .append("\nOrigin: ").append(exit.getProcessName()).append(" at ").append(new Date(exit.getTimestamp()))
                                        .append("\nThis trace may predate the current launch attempt.\n");
                                }
                            } catch (IOException e) { text.append("Trace unavailable: ").append(e).append('\n'); }
                        }
                    }
                } catch (RuntimeException e) { text.append("System crash history unavailable: ").append(e); }
            }
            final byte[] captured = lastTrace; final String name = lastName;
            final String complete = text.toString();
            postIfAlive(() -> { report = complete; trace = captured; traceName = name;
                copy.setEnabled(true); save.setEnabled(true); updateStatus(); });
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (request == PICK_ROM) {
            busy = true; updateStatus();
            io.execute(() -> {
                try { RomImporter.importRom(getContentResolver().openInputStream(uri), new File(data, "bmhero.z64")); }
                catch (Exception e) { postIfAlive(() -> error(e)); }
                finally { postIfAlive(() -> { busy = false; updateStatus(); }); }
            });
        } else if (request == SAVE_REPORT) {
            final String snapshot = report; final byte[] captured = trace; final String name = traceName;
            io.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Cannot create report");
                    try (ZipOutputStream zip = new ZipOutputStream(out)) {
                        zip.putNextEntry(new ZipEntry("crash-report.txt")); zip.write(snapshot.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                        if (captured != null) { zip.putNextEntry(new ZipEntry(name)); zip.write(captured); zip.closeEntry(); }
                    }
                    postIfAlive(() -> Toast.makeText(this, "Crash report saved", Toast.LENGTH_LONG).show());
                } catch (Exception e) { postIfAlive(() -> error(e)); }
            });
        }
    }
    private void postIfAlive(Runnable action) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) action.run(); });
    }
    private void error(Exception e) {
        if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this)
            .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
    }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
}
