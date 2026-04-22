package com.example.expensetracker;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    EditText etAmount, etCategory, etDescription, etDate;
    Button btnSave, btnViewAnalytics;
    Spinner modeSpinner;
    TextView tvTotalBalance, tvBalanceIncome, tvBalanceSpent, tvBalanceSavings, tvAvatarLetter;
    LinearLayout navHome, navAnalytics, navProfile, navLogout;
    CardView fabAdd;
    DBHelper dbHelper;
    int userId;
    String userName;

    // Budget alert thresholds
    private static final int ALERT_WARNING_PCT  = 80; // show warning at 80%
    private static final int ALERT_DANGER_PCT   = 95; // show danger at 95%

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        userId   = prefs.getInt("user_id", -1);
        userName = prefs.getString("user_name", "User");

        if (userId == -1) {
            startActivity(new Intent(this, LoginActivity.class));
            finish(); return;
        }

        setContentView(R.layout.activity_main);
        dbHelper = new DBHelper(this);

        tvAvatarLetter   = findViewById(R.id.tvAvatarLetter);
        tvTotalBalance   = findViewById(R.id.tvTotalBalance);
        tvBalanceIncome  = findViewById(R.id.tvBalanceIncome);
        tvBalanceSpent   = findViewById(R.id.tvBalanceSpent);
        tvBalanceSavings = findViewById(R.id.tvBalanceSavings);
        etAmount         = findViewById(R.id.amount);
        etCategory       = findViewById(R.id.category);
        etDescription    = findViewById(R.id.etDescription);
        etDate           = findViewById(R.id.date);
        btnSave          = findViewById(R.id.saveBtn);
        btnViewAnalytics = findViewById(R.id.viewAnalytics);
        modeSpinner      = findViewById(R.id.modeSpinner);
        navAnalytics     = findViewById(R.id.navAnalytics);
        fabAdd           = findViewById(R.id.fabAdd);
        navLogout        = findViewById(R.id.navLogout);

        tvAvatarLetter.setText(userName.substring(0, 1).toUpperCase());

        // Payment modes spinner
        String[] modes = {"Cash", "UPI / Online", "Credit Card", "Debit Card", "Net Banking", "Other"};
        modeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modes));

        // Date picker
        etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this,
                    (view, y, m, d) -> etDate.setText(
                            String.format(Locale.US, "%d-%02d-%02d", y, m + 1, d)),
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        setupChips();

        btnSave.setOnClickListener(v -> saveExpense());
        btnViewAnalytics.setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));
        fabAdd.setOnClickListener(v -> etAmount.requestFocus());
        navAnalytics.setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));
        navLogout.setOnClickListener(v -> confirmLogout());

        loadBalanceSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBalanceSummary();
    }

    private void loadBalanceSummary() {
        double income = 0, budget = 0;
        String currentMonth = new SimpleDateFormat("MM-yyyy", Locale.US).format(new Date());
        Cursor bc = dbHelper.getReadableDatabase().rawQuery(
                "SELECT income, budget_limit FROM budget_settings WHERE month_year=? AND user_id=?",
                new String[]{currentMonth, String.valueOf(userId)});
        if (bc.moveToFirst()) { income = bc.getDouble(0); budget = bc.getDouble(1); }
        bc.close();

        double spent = 0;
        Cursor sc = dbHelper.getReadableDatabase().rawQuery(
                "SELECT SUM(amount) FROM expenses WHERE user_id=? AND strftime('%m-%Y', date) = strftime('%m-%Y', 'now')",
                new String[]{String.valueOf(userId)});
        if (sc.moveToFirst() && !sc.isNull(0)) spent = sc.getDouble(0);
        sc.close();

        double savings = income - spent;
        tvTotalBalance.setText(String.format(Locale.US, "₹%.2f", Math.max(savings, 0)));
        tvBalanceIncome.setText(String.format(Locale.US, "₹%.0f", income));
        tvBalanceSpent.setText(String.format(Locale.US, "₹%.0f", spent));
        tvBalanceSavings.setText(String.format(Locale.US, "₹%.0f", savings));
    }

    private void setupChips() {
        int[] ids = {R.id.chipFood, R.id.chipTravel, R.id.chipShopping,
                R.id.chipHealth, R.id.chipBills, R.id.chipEntertainment};
        String[] names = {"Food", "Travel", "Shopping", "Health", "Bills", "Entertainment"};
        for (int i = 0; i < ids.length; i++) {
            String name = names[i];
            Button b = findViewById(ids[i]);
            if (b != null) b.setOnClickListener(v -> {
                etCategory.setText(name);
                etCategory.setSelection(name.length());
            });
        }
    }

    private void saveExpense() {
        String amtStr  = etAmount.getText().toString().trim();
        String cat     = etCategory.getText().toString().trim();
        String desc    = etDescription.getText().toString().trim();
        String dateStr = etDate.getText().toString().trim();

        if (amtStr.isEmpty() || cat.isEmpty() || dateStr.isEmpty()) {
            toast("Amount, Category and Date are required"); return;
        }

        double amt;
        try {
            amt = Double.parseDouble(amtStr);
            if (amt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            toast("Enter a valid positive amount"); return;
        }

        ContentValues cv = new ContentValues();
        cv.put("user_id",     userId);
        cv.put("amount",      amt);
        cv.put("category",    cat);
        cv.put("description", desc.isEmpty() ? null : desc);
        cv.put("date",        dateStr);
        cv.put("mode",        modeSpinner.getSelectedItem().toString());

        long result = dbHelper.getWritableDatabase().insert("expenses", null, cv);
        if (result != -1) {
            toast("✅ Expense saved!");
            etAmount.setText(""); etCategory.setText("");
            etDescription.setText(""); etDate.setText("");
            loadBalanceSummary();

            // ✅ Check budget after every save
            checkBudgetAndAlert();
        } else {
            toast("❌ Error saving");
        }
    }

    // =========================================================
    //  BUDGET ALERT SYSTEM
    // =========================================================
    private void checkBudgetAndAlert() {
        String currentMonth = new SimpleDateFormat("MM-yyyy", Locale.US).format(new Date());

        // Get budget limit
        double budgetLimit = 0;
        Cursor bc = dbHelper.getReadableDatabase().rawQuery(
                "SELECT budget_limit FROM budget_settings WHERE month_year=? AND user_id=?",
                new String[]{currentMonth, String.valueOf(userId)});
        if (bc.moveToFirst()) budgetLimit = bc.getDouble(0);
        bc.close();

        if (budgetLimit <= 0) return; // No budget set, skip alert

        // Get total spent this month
        double spent = 0;
        Cursor sc = dbHelper.getReadableDatabase().rawQuery(
                "SELECT SUM(amount) FROM expenses WHERE user_id=? AND strftime('%m-%Y', date) = strftime('%m-%Y', 'now')",
                new String[]{String.valueOf(userId)});
        if (sc.moveToFirst() && !sc.isNull(0)) spent = sc.getDouble(0);
        sc.close();

        int pct = (int) ((spent / budgetLimit) * 100);
        double remaining = budgetLimit - spent;

        if (spent >= budgetLimit) {
            // EXCEEDED — red alert
            showBudgetAlert(
                    "🚨 Budget Exceeded!",
                    "You have exceeded your monthly budget!\n\n" +
                            "Budget: ₹" + String.format(Locale.US, "%.0f", budgetLimit) + "\n" +
                            "Spent:  ₹" + String.format(Locale.US, "%.0f", spent) + "\n" +
                            "Over by: ₹" + String.format(Locale.US, "%.0f", Math.abs(remaining)),
                    "#EF4444", "⚠️ Review Expenses", true
            );
        } else if (pct >= ALERT_DANGER_PCT) {
            // CRITICAL — orange alert
            showBudgetAlert(
                    "🔴 Critical: Almost Out!",
                    "You've used " + pct + "% of your budget.\n\n" +
                            "Only ₹" + String.format(Locale.US, "%.0f", remaining) + " remaining.\n" +
                            "Consider pausing non-essential spending.",
                    "#F97316", "Got it", false
            );
        } else if (pct >= ALERT_WARNING_PCT) {
            // WARNING — yellow alert
            showBudgetAlert(
                    "⚠️ Budget Warning",
                    "You've used " + pct + "% of your budget.\n\n" +
                            "₹" + String.format(Locale.US, "%.0f", remaining) + " remaining this month.\n" +
                            "Keep an eye on your spending!",
                    "#F59E0B", "Got it", false
            );
        }
    }

    private void showBudgetAlert(String title, String message,
                                 String accentHex, String positiveBtn,
                                 boolean showAnalyticsBtn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveBtn, (d, w) -> d.dismiss());

        if (showAnalyticsBtn) {
            builder.setNegativeButton("View Analytics", (d, w) -> {
                d.dismiss();
                startActivity(new Intent(this, AnalyticsActivity.class));
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the positive button with accent color
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(Color.parseColor(accentHex));
        if (showAnalyticsBtn) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(Color.parseColor("#7C3AED"));
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    getSharedPreferences("UserSession", MODE_PRIVATE).edit().clear().apply();
                    startActivity(new Intent(this, LoginActivity.class)); finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}