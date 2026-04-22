package com.example.expensetracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword, etName, etConfirmPass;
    Button btnAction;
    TextView tvTitle, tvSubtitle, tvToggle;
    LinearLayout nameLayout, confirmLayout;
    DBHelper dbHelper;
    boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        if (prefs.getInt("user_id", -1) != -1) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        dbHelper = new DBHelper(this);

        tvTitle       = findViewById(R.id.tvTitle);
        tvSubtitle    = findViewById(R.id.tvSubtitle);
        etName        = findViewById(R.id.etName);
        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        etConfirmPass = findViewById(R.id.etConfirmPass);
        btnAction     = findViewById(R.id.btnAction);
        tvToggle      = findViewById(R.id.tvToggle);
        nameLayout    = findViewById(R.id.nameLayout);
        confirmLayout = findViewById(R.id.confirmLayout);

        setLoginMode(true);
        btnAction.setOnClickListener(v -> { if (isLoginMode) handleLogin(); else handleRegister(); });
        tvToggle.setOnClickListener(v -> setLoginMode(!isLoginMode));
    }

    private void setLoginMode(boolean login) {
        isLoginMode = login;
        nameLayout.setVisibility(login ? View.GONE : View.VISIBLE);
        confirmLayout.setVisibility(login ? View.GONE : View.VISIBLE);
        tvTitle.setText(login ? "Welcome Back 👋" : "Create Account ✨");
        tvSubtitle.setText(login ? "Sign in to your account" : "Start tracking your finances");
        btnAction.setText(login ? "Sign In" : "Create Account");
        tvToggle.setText(login ? "Don't have an account? Register" : "Already have an account? Login");
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) { toast("Please fill all fields"); return; }

        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT id, name FROM users WHERE email=? AND password=?",
                new String[]{email, pass});
        if (c.moveToFirst()) {
            int uid = c.getInt(0); String name = c.getString(1); c.close();
            SharedPreferences.Editor e = getSharedPreferences("UserSession", MODE_PRIVATE).edit();
            e.putInt("user_id", uid); e.putString("user_name", name); e.apply();
            startActivity(new Intent(this, MainActivity.class)); finish();
        } else { c.close(); toast("Invalid email or password"); }
    }

    private void handleRegister() {
        String name  = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();
        String conf  = etConfirmPass.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || conf.isEmpty()) { toast("Fill all fields"); return; }
        if (!pass.equals(conf)) { toast("Passwords don't match"); return; }
        if (pass.length() < 6) { toast("Password must be 6+ characters"); return; }

        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE email=?", new String[]{email});
        if (c.moveToFirst()) { c.close(); toast("Email already registered"); return; }
        c.close();

        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("name", name); cv.put("email", email); cv.put("password", pass);
        long id = dbHelper.getWritableDatabase().insert("users", null, cv);
        if (id != -1) { toast("Account created! Please sign in ✅"); setLoginMode(true); }
        else toast("Registration failed");
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}