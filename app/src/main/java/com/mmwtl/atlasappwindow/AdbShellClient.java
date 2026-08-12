package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.util.Base64;

import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AdbShellClient implements AutoCloseable {
    private static final int TIMEOUT_MS = 5_000;
    private static final int SHELL_TIMEOUT_MS = 10_000;
    private static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;
    private static final String DONE_PREFIX = "__ATLAS_WINDOW_DONE__:";
    private static final String PRIVATE_KEY = "adbkey";
    private static final String PUBLIC_KEY = "adbkey.pub";
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "atlas-adb-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        boolean ok() { return exitCode == 0; }
    }

    private final Context context;
    private Socket socket;
    private AdbConnection connection;

    AdbShellClient(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized void connect(int port) throws IOException, InterruptedException,
            NoSuchAlgorithmException, InvalidKeySpecException {
        if (port <= 0 || port > 65_535) throw new IllegalArgumentException("Invalid ADB port");
        if (connection != null && socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        close();
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), TIMEOUT_MS);
        connection = AdbConnection.create(socket, loadOrCreateCrypto());
        boolean connected = connection.connect(TIMEOUT_MS, TimeUnit.MILLISECONDS, false);
        if (!connected) {
            close();
            throw new IOException("ADB authorization timed out");
        }
    }

    synchronized Result execute(String command) throws Exception {
        if (connection == null) throw new IOException("ADB is not connected");
        if (command == null || command.isBlank() || command.indexOf('\n') >= 0
                || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("ADB command must be one non-empty line");
        }
        String marker = DONE_PREFIX + Long.toUnsignedString(System.nanoTime()) + ":";
        String effective = command.trim() + "; echo " + marker + "$?";
        AdbStream stream = connection.open("shell:" + effective);
        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> timeout = WATCHDOG.schedule(() -> {
            timedOut.set(true);
            try { stream.close(); } catch (IOException ignored) {}
        }, SHELL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            StringBuilder out = new StringBuilder(256);
            int outputBytes = 0;
            while (!stream.isClosed()) {
                byte[] chunk;
                try {
                    chunk = stream.read();
                } catch (IOException closed) {
                    break;
                }
                if (chunk == null || chunk.length == 0) break;
                if (chunk.length > MAX_OUTPUT_BYTES - outputBytes) {
                    throw new IOException("ADB shell output exceeded safety limit");
                }
                outputBytes += chunk.length;
                out.append(new String(chunk, StandardCharsets.UTF_8));
                int markerIndex = out.indexOf(marker);
                if (markerIndex >= 0) {
                    Integer exit = parseLeadingInt(out.substring(markerIndex + marker.length()));
                    if (exit != null) {
                        return new Result(exit, out.substring(0, markerIndex).trim());
                    }
                }
            }
            if (timedOut.get()) throw new IOException("ADB shell command timed out");
            throw new IOException("ADB shell closed before completion marker");
        } finally {
            timeout.cancel(false);
            try { stream.close(); } catch (Throwable ignored) {}
        }
    }

    private AdbCrypto loadOrCreateCrypto()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        File privateKey = new File(context.getNoBackupFilesDir(), PRIVATE_KEY);
        File publicKey = new File(context.getNoBackupFilesDir(), PUBLIC_KEY);
        AdbBase64 encoder = bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP);
        if (privateKey.isFile() && publicKey.isFile()) {
            try {
                return AdbCrypto.loadAdbKeyPair(encoder, privateKey, publicKey);
            } catch (IOException | InvalidKeySpecException error) {
                AppLog.warn("Stored ADB key is unreadable; replacing it", error);
                boolean privateDeleted = !privateKey.exists() || privateKey.delete();
                boolean publicDeleted = !publicKey.exists() || publicKey.delete();
                if (!privateDeleted || !publicDeleted) {
                    throw new IOException("Cannot replace invalid ADB key", error);
                }
            }
        }
        AdbCrypto crypto = AdbCrypto.generateAdbKeyPair(encoder);
        crypto.saveAdbKeyPair(privateKey, publicKey);
        return crypto;
    }

    private static Integer parseLeadingInt(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        if (index >= value.length() || !Character.isDigit(value.charAt(index))) return null;
        int number = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            number = number * 10 + (value.charAt(index++) - '0');
        }
        return number;
    }

    @Override public synchronized void close() {
        if (connection != null) try { connection.close(); } catch (IOException ignored) {}
        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
        connection = null;
        socket = null;
    }
}
