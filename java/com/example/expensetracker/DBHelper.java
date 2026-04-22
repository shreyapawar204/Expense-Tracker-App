package com.example.expensetracker;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "ExpenseDB";
    public static final int DB_VERSION = 6; // Bumped for users table

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Users table
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "email TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now'))" +
                ")");

        // Expenses table (with user_id foreign key)
        db.execSQL("CREATE TABLE expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER NOT NULL," +
                "amount REAL NOT NULL," +
                "category TEXT NOT NULL," +
                "description TEXT," +
                "date TEXT NOT NULL," +
                "mode TEXT NOT NULL," +
                "created_at TEXT DEFAULT (datetime('now'))" +
                ")");

        // Budget settings (per user, per month)
        db.execSQL("CREATE TABLE budget_settings (" +
                "month_year TEXT NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "income REAL DEFAULT 0," +
                "budget_limit REAL DEFAULT 0," +
                "PRIMARY KEY (month_year, user_id)" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS expenses");
        db.execSQL("DROP TABLE IF EXISTS budget_settings");
        db.execSQL("DROP TABLE IF EXISTS users");
        onCreate(db);
    }
}