package com.mjod.masroofi;

import android.app.backup.BackupManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Private on-device persistence plus a shared Downloads copy that can survive app removal. */
public final class QNCStorage {
    private static final String BACKUP_NAME = "qnc-wallet-backup.json";
    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/QNC/";
    private final Context context;
    private final AtomicFile internalBackup;

    public QNCStorage(Context context) {
        this.context = context.getApplicationContext();
        this.internalBackup = new AtomicFile(new File(this.context.getFilesDir(), BACKUP_NAME));
    }

    @JavascriptInterface
    public synchronized String load() {
        String internal = readInternal();
        return internal != null ? internal : readSharedCopy();
    }

    @JavascriptInterface
    public synchronized void save(String json) {
        if (json == null || json.length() > 2_000_000) return;
        try {
            new JSONArray(json);
        } catch (Exception invalidData) {
            return;
        }
        writeInternal(json);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeSharedCopy(json);
        new BackupManager(context).dataChanged();
    }

    private String readInternal() {
        try (FileInputStream input = internalBackup.openRead()) {
            return readText(input);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void writeInternal(String json) {
        FileOutputStream output = null;
        try {
            output = internalBackup.startWrite();
            output.write(json.getBytes(StandardCharsets.UTF_8));
            internalBackup.finishWrite(output);
        } catch (IOException error) {
            if (output != null) internalBackup.failWrite(output);
        }
    }

    private String readSharedCopy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        Uri uri = findSharedUri();
        if (uri == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return input == null ? null : readText(input);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private void writeSharedCopy(String json) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = findSharedUri();
        boolean newlyCreated = uri == null;
        if (newlyCreated) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            try {
                uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            } catch (RuntimeException ignored) {
                return;
            }
            if (uri == null) return;
        }
        try (OutputStream output = resolver.openOutputStream(uri, newlyCreated ? "w" : "wt")) {
            if (output == null) return;
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.flush();
            if (newlyCreated) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
            }
        } catch (IOException | RuntimeException ignored) {
            if (newlyCreated) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException ignoredAgain) { }
            }
        }
    }

    private Uri findSharedUri() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] columns = {MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] args = {BACKUP_NAME, RELATIVE_PATH};
        try (Cursor cursor = context.getContentResolver().query(collection, columns, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return Uri.withAppendedPath(collection, cursor.getString(0));
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 2_000_000) return null;
            bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
