package com.amjad.mahloula.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** مزوّد ملفات مصغّر لمشاركة نسخ الاحتياط محليًا (بدل FileProvider الخارجي). */
public class BackupFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() { return true; }

    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return null;
        File dir = new File(getContext().getFilesDir(), "backups");
        return new File(dir, name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null || !f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String getType(Uri uri) { return "application/json"; }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues v) { return null; }

    @Override
    public int delete(Uri uri, String s, String[] a) { return 0; }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
