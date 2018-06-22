package org.sufficientlysecure.keychain.provider;


import java.util.ArrayList;
import java.util.List;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteQuery;
import android.database.Cursor;


class AbstractDao {
    private final KeychainDatabase db;
    private final DatabaseNotifyManager databaseNotifyManager;

    AbstractDao(KeychainDatabase db, DatabaseNotifyManager databaseNotifyManager) {
        this.db = db;
        this.databaseNotifyManager = databaseNotifyManager;
    }

    SupportSQLiteDatabase getReadableDb() {
        return db.getReadableDatabase();
    }

    SupportSQLiteDatabase getWritableDb() {
        return db.getWritableDatabase();
    }

    DatabaseNotifyManager getDatabaseNotifyManager() {
        return databaseNotifyManager;
    }

    <T> List<T> mapAllRows(SupportSQLiteQuery query, Mapper<T> mapper) {
        ArrayList<T> result = new ArrayList<>();
        try (Cursor cursor = getReadableDb().query(query)) {
            T item = mapper.map(cursor);
            result.add(item);
        }
        return result;
    }

    interface Mapper<T> {
        T map(Cursor cursor);
    }
}
