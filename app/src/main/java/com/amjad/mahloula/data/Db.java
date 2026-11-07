package com.amjad.mahloula.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** قاعدة البيانات المحلية — كل شيء على جهازك، بدون سحابة وبدون إنترنت. */
public final class Db extends SQLiteOpenHelper {

    private static final String NAME = "mahloula.db";
    private static final int VERSION = 1;

    private static Db sInstance;

    public static synchronized Db get(Context c) {
        if (sInstance == null) sInstance = new Db(c.getApplicationContext());
        return sInstance;
    }

    private Db(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tx ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kind INTEGER NOT NULL,"
                + "amount REAL NOT NULL,"
                + "cat INTEGER NOT NULL DEFAULT 12,"
                + "wallet INTEGER NOT NULL DEFAULT 1,"
                + "note TEXT DEFAULT '',"
                + "method TEXT DEFAULT '',"
                + "ts INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_tx_ts ON tx(ts)");
        db.execSQL("CREATE INDEX idx_tx_kind ON tx(kind, ts)");

        db.execSQL("CREATE TABLE debts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "kind INTEGER NOT NULL,"
                + "person TEXT DEFAULT '',"
                + "title TEXT DEFAULT '',"
                + "total REAL NOT NULL,"
                + "due INTEGER DEFAULT 0,"
                + "note TEXT DEFAULT '',"
                + "created INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE debt_pays ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "debt INTEGER NOT NULL,"
                + "amount REAL NOT NULL,"
                + "ts INTEGER NOT NULL,"
                + "note TEXT DEFAULT '')");

        db.execSQL("CREATE TABLE notes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT DEFAULT '',"
                + "body TEXT DEFAULT '',"
                + "color INTEGER DEFAULT 0,"
                + "pinned INTEGER DEFAULT 0,"
                + "ts INTEGER NOT NULL,"
                + "upd INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE wallets ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "kind INTEGER DEFAULT 0,"
                + "initial REAL DEFAULT 0,"
                + "def INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE goals ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL,"
                + "target REAL NOT NULL,"
                + "saved REAL DEFAULT 0,"
                + "due INTEGER DEFAULT 0)");

        // محفظة افتراضية
        db.execSQL("INSERT INTO wallets (name, kind, initial, def) VALUES ('المحفظة الرئيسية', 0, 0, 1)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // ترحيلات مستقبلية
    }
}
