package com.example.expensetracker;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.*;

public class AnalyticsActivity extends AppCompatActivity {

    DBHelper dbHelper;
    int userId;

    TextView tvTotalExpense, tvTotalSavings, tvBudgetLeft, tvTransactionCount,
            tvAvgExpense, tvBudgetInfo, tvBudgetPct, tvSpentHighlight, tvIncomeCard;
    ProgressBar budgetProgress;
    PieChart pieChart, modeChart;
    BarChart barChart;
    LinearLayout transactionList, budgetCard;
    Spinner filterSpinner;
    Button btnPickYear;
    ImageButton btnSetBudget, btnBack;

    double monthlyIncome = 0, monthlyBudgetLimit = 0;
    int selectedYear;

    int[] CHART_COLORS = {
            Color.parseColor("#7C3AED"), Color.parseColor("#F97316"),
            Color.parseColor("#3B82F6"), Color.parseColor("#10B981"),
            Color.parseColor("#EF4444"), Color.parseColor("#EC4899"),
            Color.parseColor("#06B6D4"), Color.parseColor("#F59E0B")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        userId = prefs.getInt("user_id", -1);
        if (userId == -1) { startActivity(new Intent(this, LoginActivity.class)); finish(); return; }

        setContentView(R.layout.activity_analytics);
        dbHelper = new DBHelper(this);
        selectedYear = Calendar.getInstance().get(Calendar.YEAR);

        tvTotalExpense     = findViewById(R.id.totalExpense);
        tvIncomeCard       = findViewById(R.id.tvIncomeCard);
        tvTotalSavings     = findViewById(R.id.totalSavings);
        tvBudgetLeft       = findViewById(R.id.budgetLeft);
        tvTransactionCount = findViewById(R.id.tvTransactionCount);
        tvAvgExpense       = findViewById(R.id.tvAvgExpense);
        tvBudgetInfo       = findViewById(R.id.tvBudgetInfo);
        tvBudgetPct        = findViewById(R.id.tvBudgetPct);
        tvSpentHighlight   = findViewById(R.id.tvSpentHighlight);
        budgetProgress     = findViewById(R.id.budgetProgress);
        filterSpinner      = findViewById(R.id.filterSpinner);
        pieChart           = findViewById(R.id.pieChart);
        modeChart          = findViewById(R.id.modeChart);
        barChart           = findViewById(R.id.barChart);
        transactionList    = findViewById(R.id.transactionList);
        btnPickYear        = findViewById(R.id.btnPickYear);
        btnSetBudget       = findViewById(R.id.btnSetBudget);
        btnBack            = findViewById(R.id.btnBack);
        budgetCard         = findViewById(R.id.budgetCard);

        btnPickYear.setText("📅 " + selectedYear);
        setupFilters();
        filterSpinner.setSelection(2);

        btnPickYear.setOnClickListener(v -> showYearPickerDialog());
        btnSetBudget.setOnClickListener(v -> showBudgetDialog());
        budgetCard.setOnClickListener(v -> showBudgetDialog());
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupFilters() {
        String[] filters = {"All Time", "This Year", "This Month", "Last Month"};
        filterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, filters));
        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshData(buildWhere(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private String buildWhere(int pos) {
        String base = " WHERE user_id = " + userId;
        if (pos == 1) return base + " AND strftime('%Y', date) = '" + selectedYear + "'";
        if (pos == 2) return base + " AND strftime('%m-%Y', date) = strftime('%m-%Y', 'now')";
        if (pos == 3) {
            Calendar c = Calendar.getInstance(); c.add(Calendar.MONTH, -1);
            String lm = String.format(Locale.US, "%02d-%d", c.get(Calendar.MONTH)+1, c.get(Calendar.YEAR));
            return base + " AND strftime('%m-%Y', date) = '" + lm + "'";
        }
        return base;
    }

    private void showYearPickerDialog() {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(2020); p.setMaxValue(2030); p.setValue(selectedYear);
        new AlertDialog.Builder(this).setTitle("Select Year").setView(p)
                .setPositiveButton("Apply", (d, w) -> {
                    selectedYear = p.getValue();
                    btnPickYear.setText("📅 " + selectedYear);
                    refreshData(" WHERE user_id=" + userId + " AND strftime('%Y', date)='" + selectedYear + "'");
                }).setNegativeButton("Cancel", null).show();
    }

    private void showBudgetDialog() {
        fetchBudget();
        View dv = getLayoutInflater().inflate(R.layout.dialog_budget, null);
        EditText etIncome = dv.findViewById(R.id.etIncome);
        EditText etBudget = dv.findViewById(R.id.etBudget);
        if (monthlyIncome > 0) etIncome.setText(String.format(Locale.US, "%.0f", monthlyIncome));
        if (monthlyBudgetLimit > 0) etBudget.setText(String.format(Locale.US, "%.0f", monthlyBudgetLimit));

        new AlertDialog.Builder(this)
                .setTitle("💰 Financial Settings")
                .setMessage("Applied to the current month only.")
                .setView(dv)
                .setPositiveButton("Save", (dialog, which) -> {
                    String inc = etIncome.getText().toString().trim();
                    String bud = etBudget.getText().toString().trim();
                    if (inc.isEmpty() || bud.isEmpty()) { toast("Enter both values"); return; }
                    saveBudget(Double.parseDouble(inc), Double.parseDouble(bud));
                    refreshData(buildWhere(filterSpinner.getSelectedItemPosition()));
                    toast("✅ Budget saved!");
                }).setNegativeButton("Cancel", null).show();
    }

    private void saveBudget(double inc, double bud) {
        String month = new SimpleDateFormat("MM-yyyy", Locale.US).format(new Date());
        ContentValues cv = new ContentValues();
        cv.put("month_year", month); cv.put("user_id", userId);
        cv.put("income", inc); cv.put("budget_limit", bud);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "budget_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        monthlyIncome = inc; monthlyBudgetLimit = bud;
    }

    private void fetchBudget() {
        monthlyIncome = 0; monthlyBudgetLimit = 0;
        String month = new SimpleDateFormat("MM-yyyy", Locale.US).format(new Date());
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT income, budget_limit FROM budget_settings WHERE month_year=? AND user_id=?",
                new String[]{month, String.valueOf(userId)});
        if (c.moveToFirst()) { monthlyIncome = c.getDouble(0); monthlyBudgetLimit = c.getDouble(1); }
        c.close();
    }

    private void refreshData(String where) {
        fetchBudget();
        double spent = loadTotal(where);
        loadStats(where, spent);
        updateSummary(spent);
        loadPie(pieChart, "category", where);
        loadPie(modeChart, "mode", where);
        loadBar(where);
        loadList(where);
    }

    private double loadTotal(String where) {
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT SUM(amount) FROM expenses" + where, null);
        double t = 0;
        if (c.moveToFirst() && !c.isNull(0)) t = c.getDouble(0);
        c.close();
        tvTotalExpense.setText(String.format(Locale.US, "₹%.0f", t));
        tvSpentHighlight.setText(String.format(Locale.US, "₹%.0f", t));
        tvIncomeCard.setText(String.format(Locale.US, "₹%.0f", monthlyIncome));
        return t;
    }

    private void loadStats(String where, double spent) {
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM expenses" + where, null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        tvTransactionCount.setText(n + " Transaction" + (n == 1 ? "" : "s"));
        tvAvgExpense.setText(String.format(Locale.US, "Avg: ₹%.0f", n > 0 ? spent / n : 0));
    }

    private void updateSummary(double spent) {
        if (monthlyIncome == 0 && monthlyBudgetLimit == 0) {
            tvTotalSavings.setText("Not set");
            tvTotalSavings.setTextColor(Color.parseColor("#94A3B8"));
            tvBudgetLeft.setText("Set budget ⚙");
            tvBudgetLeft.setTextColor(Color.parseColor("#7C3AED"));
            tvBudgetInfo.setText("Tap here to set your income and monthly budget");
            tvBudgetPct.setText("0%");
            budgetProgress.setProgress(0);
            return;
        }

        double savings = monthlyIncome - spent;
        double remain  = monthlyBudgetLimit - spent;
        int    pct     = monthlyBudgetLimit > 0
                ? (int) Math.min((spent / monthlyBudgetLimit) * 100, 100) : 0;

        // ===== COLOR CODING BASED ON BUDGET USAGE =====
        String progressColor, remainColor;
        if (pct >= 100) {
            progressColor = "#EF4444"; remainColor = "#EF4444"; // red — exceeded
        } else if (pct >= 95) {
            progressColor = "#F97316"; remainColor = "#F97316"; // orange — critical
        } else if (pct >= 80) {
            progressColor = "#F59E0B"; remainColor = "#F59E0B"; // amber — warning
        } else {
            progressColor = "#7C3AED"; remainColor = "#7C3AED"; // purple — healthy
        }

        tvTotalSavings.setText(String.format(Locale.US, "₹%.0f", savings));
        tvTotalSavings.setTextColor(savings >= 0
                ? Color.parseColor("#10B981") : Color.parseColor("#EF4444"));

        tvBudgetLeft.setText(remain >= 0
                ? String.format(Locale.US, "₹%.0f left", remain)
                : String.format(Locale.US, "₹%.0f over!", Math.abs(remain)));
        tvBudgetLeft.setTextColor(Color.parseColor(remainColor));

        tvBudgetPct.setText(pct + "%");
        tvBudgetPct.setTextColor(Color.parseColor(progressColor));

        tvBudgetInfo.setText(String.format(Locale.US,
                "Income: ₹%.0f  ·  Budget: ₹%.0f  ·  Used: %d%%",
                monthlyIncome, monthlyBudgetLimit, pct));

        budgetProgress.setProgress(pct);
        budgetProgress.getProgressDrawable().setColorFilter(
                Color.parseColor(progressColor),
                android.graphics.PorterDuff.Mode.SRC_IN);

        // Show inline alert banner if budget nearly exhausted
        showInlineBudgetBanner(pct, remain);
    }

    private void showInlineBudgetBanner(int pct, double remaining) {
        // Only show popup alert when viewing current month (filter pos 2)
        if (filterSpinner.getSelectedItemPosition() != 2) return;
        if (monthlyBudgetLimit <= 0) return;

        if (pct >= 100) {
            showStyledAlert(
                    "🚨",
                    "Budget Exceeded!",
                    "You've gone over your monthly budget by ₹"
                            + String.format(Locale.US, "%.0f", Math.abs(remaining))
                            + ".\n\nConsider reviewing your expenses.",
                    "#EF4444"
            );
        } else if (pct >= 95) {
            showStyledAlert(
                    "🔴",
                    "Almost Out of Budget",
                    "You've used " + pct + "% of your budget.\n\n" +
                            "Only ₹" + String.format(Locale.US, "%.0f", remaining) + " remaining.\n" +
                            "Be careful with your spending!",
                    "#F97316"
            );
        } else if (pct >= 80) {
            showStyledAlert(
                    "⚠️",
                    "Budget Warning",
                    "You've used " + pct + "% of your budget.\n\n" +
                            "₹" + String.format(Locale.US, "%.0f", remaining) + " left this month.\n" +
                            "Keep an eye on your expenses.",
                    "#F59E0B"
            );
        }
    }

    private void showStyledAlert(String emoji, String title, String message, String colorHex) {
        // Build a custom view for the alert
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        // Big emoji
        android.widget.TextView tvEmoji = new android.widget.TextView(this);
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(48);
        tvEmoji.setGravity(android.view.Gravity.CENTER);
        tvEmoji.setPadding(0, 0, 0, 16);
        layout.addView(tvEmoji);

        // Message
        android.widget.TextView tvMsg = new android.widget.TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextSize(14);
        tvMsg.setTextColor(Color.parseColor("#374151"));
        tvMsg.setLineSpacing(4, 1);
        layout.addView(tvMsg);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Got it", (d, w) -> d.dismiss())
                .setNegativeButton("View Details", (d, w) -> {
                    d.dismiss();
                    // Scroll to budget card (already on this screen)
                    budgetCard.requestFocus();
                })
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(colorHex));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#7C3AED"));
    }

    private void loadPie(PieChart chart, String col, String where) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT " + col + ", SUM(amount) FROM expenses" + where
                        + " GROUP BY " + col + " ORDER BY SUM(amount) DESC", null);
        while (c.moveToNext()) {
            String lbl = c.getString(0);
            if (lbl == null || lbl.isEmpty()) lbl = "Other";
            entries.add(new PieEntry(c.getFloat(1), lbl));
        }
        c.close();

        if (entries.isEmpty()) {
            chart.setNoDataText("No data for this period");
            chart.setNoDataTextColor(Color.parseColor("#94A3B8"));
            chart.invalidate(); return;
        }

        PieDataSet ds = new PieDataSet(entries, "");
        ds.setColors(CHART_COLORS);
        ds.setValueTextColor(Color.WHITE);
        ds.setValueTextSize(10f);
        ds.setSliceSpace(3f);

        chart.setData(new PieData(ds));
        chart.setBackgroundColor(Color.WHITE);
        chart.setHoleColor(Color.WHITE);
        chart.setHoleRadius(44f);
        chart.setDrawEntryLabels(false);
        chart.setDescription(null);
        Legend l = chart.getLegend();
        l.setTextColor(Color.parseColor("#475569"));
        l.setOrientation(Legend.LegendOrientation.VERTICAL);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        chart.animateXY(700, 700);
        chart.invalidate();
    }

    private void loadBar(String where) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        String[] labels = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT strftime('%m', date), SUM(amount) FROM expenses" + where
                        + " GROUP BY strftime('%m', date) ORDER BY strftime('%m', date)", null);
        while (c.moveToNext())
            entries.add(new BarEntry(Integer.parseInt(c.getString(0)) - 1, c.getFloat(1)));
        c.close();

        if (entries.isEmpty()) {
            barChart.setNoDataText("No data for this period");
            barChart.setNoDataTextColor(Color.parseColor("#94A3B8"));
            barChart.invalidate(); return;
        }

        BarDataSet ds = new BarDataSet(entries, "Monthly (₹)");
        ds.setColor(Color.parseColor("#7C3AED"));
        ds.setValueTextColor(Color.parseColor("#64748B"));
        ds.setValueTextSize(8f);
        BarData bd = new BarData(ds); bd.setBarWidth(0.55f);
        barChart.setData(bd);
        barChart.setBackgroundColor(Color.WHITE);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setTextColor(Color.parseColor("#94A3B8"));
        barChart.getXAxis().setGranularity(1f);
        barChart.getAxisLeft().setTextColor(Color.parseColor("#94A3B8"));
        barChart.getAxisRight().setEnabled(false);
        barChart.setDescription(null);
        barChart.getLegend().setTextColor(Color.parseColor("#64748B"));
        barChart.animateY(700);
        barChart.invalidate();
    }

    private void loadList(String where) {
        transactionList.removeAllViews();
        Cursor c = dbHelper.getReadableDatabase().rawQuery(
                "SELECT id, date, category, amount, mode, description FROM expenses"
                        + where + " ORDER BY date DESC LIMIT 50", null);

        if (!c.moveToFirst()) {
            TextView empty = new TextView(this);
            empty.setText("No transactions for this period");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setPadding(20, 40, 20, 40);
            empty.setGravity(android.view.Gravity.CENTER);
            transactionList.addView(empty);
            c.close(); return;
        }

        do {
            int    id    = c.getInt(0);
            String date  = c.getString(1);
            String cat   = c.getString(2);
            double amt   = c.getDouble(3);
            String mode  = c.getString(4);
            String desc  = c.isNull(5) ? null : c.getString(5);

            View row = getLayoutInflater().inflate(R.layout.item_transaction, transactionList, false);
            ((TextView) row.findViewById(R.id.tvCategoryIcon)).setText(categoryEmoji(cat));
            ((TextView) row.findViewById(R.id.tvCat)).setText(cat);
            ((TextView) row.findViewById(R.id.tvAmt)).setText(
                    String.format(Locale.US, "-₹%.2f", amt));
            ((TextView) row.findViewById(R.id.tvDate)).setText(date);
            ((TextView) row.findViewById(R.id.tvMode)).setText(mode);

            TextView tvDesc = row.findViewById(R.id.tvDesc);
            if (desc != null && !desc.isEmpty()) {
                tvDesc.setText(desc); tvDesc.setVisibility(View.VISIBLE);
            } else { tvDesc.setVisibility(View.GONE); }

            int finalId = id; double finalAmt = amt;
            row.findViewById(R.id.btnDeleteTx).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Expense")
                            .setMessage("Delete ₹" + String.format(Locale.US, "%.2f", finalAmt) + " (" + cat + ")?")
                            .setPositiveButton("Delete", (d, w) -> {
                                dbHelper.getWritableDatabase()
                                        .delete("expenses", "id=?", new String[]{String.valueOf(finalId)});
                                refreshData(buildWhere(filterSpinner.getSelectedItemPosition()));
                            }).setNegativeButton("Cancel", null).show()
            );

            transactionList.addView(row);
        } while (c.moveToNext());
        c.close();
    }

    private String categoryEmoji(String cat) {
        if (cat == null) return "💸";
        String l = cat.toLowerCase();
        if (l.contains("food") || l.contains("eat") || l.contains("restaurant")) return "🍔";
        if (l.contains("travel") || l.contains("flight") || l.contains("fuel")) return "✈️";
        if (l.contains("shop") || l.contains("cloth")) return "🛍️";
        if (l.contains("health") || l.contains("medic") || l.contains("doctor")) return "💊";
        if (l.contains("bill") || l.contains("electric") || l.contains("util")) return "🧾";
        if (l.contains("fun") || l.contains("entertain") || l.contains("movie")) return "🎬";
        if (l.contains("grocery") || l.contains("vegeta")) return "🛒";
        if (l.contains("edu") || l.contains("school") || l.contains("book")) return "📚";
        return "💸";
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}