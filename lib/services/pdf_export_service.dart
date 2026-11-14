import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:share_plus/share_plus.dart';

import '../core/constants/categories.dart';
import '../core/constants/currencies.dart';
import '../core/utils/arabic_formatters.dart';
import '../models/budget_plan.dart';
import '../models/expense_item.dart';
import '../models/salary_profile.dart';
import '../models/savings_goal.dart';

class PdfExportService {
  Future<File> generateMonthlyReport({
    required SalaryProfile profile,
    required BudgetPlan plan,
    required List<ExpenseItem> cycleExpenses,
    required Map<BudgetBucket, double> spentByBucket,
    required List<SavingsGoal> goals,
  }) async {
    final regularData = await rootBundle.load('assets/fonts/Cairo-Regular.ttf');
    final boldData = await rootBundle.load('assets/fonts/Cairo-Bold.ttf');
    final regularFont = pw.Font.ttf(regularData);
    final boldFont = pw.Font.ttf(boldData);

    final theme = pw.ThemeData.withFont(base: regularFont, bold: boldFont);
    final currency = AppCurrencies.byCode(profile.currencyCode);
    final totalSpent =
        cycleExpenses.fold<double>(0.0, (sum, e) => sum + e.amount);
    final remaining = profile.totalMonthlyIncome - totalSpent;

    final pdf = pw.Document();
    final emerald = PdfColor.fromInt(0xFF0B6E4F);
    final gold = PdfColor.fromInt(0xFFD4AF37);
    final lightBg = PdfColor.fromInt(0xFFF3F9F6);

    pdf.addPage(
      pw.MultiPage(
        pageTheme: pw.PageTheme(
          pageFormat: PdfPageFormat.a4,
          theme: theme,
          textDirection: pw.TextDirection.rtl,
          margin: const pw.EdgeInsets.all(28),
        ),
        build: (context) => [
          // Header banner
          pw.Container(
            padding: const pw.EdgeInsets.all(18),
            decoration: pw.BoxDecoration(
              color: emerald,
              borderRadius: pw.BorderRadius.circular(12),
            ),
            child: pw.Column(
              crossAxisAlignment: pw.CrossAxisAlignment.stretch,
              children: [
                pw.Text(
                  'تطبيق رَتِّبْ — التقرير المالي الشهري',
                  style: pw.TextStyle(
                    fontSize: 20,
                    fontWeight: pw.FontWeight.bold,
                    color: PdfColors.white,
                  ),
                ),
                pw.SizedBox(height: 4),
                pw.Text(
                  'فلوسك بتستاهل خطة  |  تاريخ التقرير: ${ArabicFormatters.formatDateAr(DateTime.now())}',
                  style: pw.TextStyle(
                    fontSize: 11,
                    color: gold,
                  ),
                ),
              ],
            ),
          ),
          pw.SizedBox(height: 16),

          // Summary row
          pw.Container(
            padding: const pw.EdgeInsets.all(14),
            decoration: pw.BoxDecoration(
              color: lightBg,
              borderRadius: pw.BorderRadius.circular(10),
              border: pw.Border.all(color: emerald, width: 0.8),
            ),
            child: pw.Row(
              mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
              children: [
                _metricCell(
                  'الدخل الشهري',
                  ArabicFormatters.formatMoney(
                    profile.totalMonthlyIncome,
                    currency,
                  ),
                ),
                _metricCell(
                  'إجمالي المصاريف',
                  ArabicFormatters.formatMoney(totalSpent, currency),
                ),
                _metricCell(
                  'المتبقي من الشهر',
                  ArabicFormatters.formatMoney(remaining, currency),
                ),
                _metricCell(
                  'صحة راتبك',
                  '${plan.healthScore} / 100',
                ),
              ],
            ),
          ),
          pw.SizedBox(height: 18),

          pw.Text(
            'توزيع خطة الراتب ومقارنة الواقع',
            style: pw.TextStyle(fontSize: 15, fontWeight: pw.FontWeight.bold),
          ),
          pw.SizedBox(height: 8),

          pw.TableHelper.fromTextArray(
            context: context,
            headerStyle: pw.TextStyle(
              fontWeight: pw.FontWeight.bold,
              color: PdfColors.white,
              fontSize: 10.5,
            ),
            headerDecoration: pw.BoxDecoration(color: emerald),
            cellStyle: const pw.TextStyle(fontSize: 10),
            cellAlignment: pw.Alignment.centerRight,
            headers: ['المتبقي', 'المنصرف', 'المخطط', 'النسبة', 'البند'],
            data: AppCategories.buckets.map((b) {
              final planned = plan.amountFor(b.bucket);
              final spent = spentByBucket[b.bucket] ?? 0.0;
              final rem = planned - spent;
              return [
                ArabicFormatters.formatMoney(rem, currency),
                ArabicFormatters.formatMoney(spent, currency),
                ArabicFormatters.formatMoney(planned, currency),
                '${plan.percentageFor(b.bucket).toStringAsFixed(1)}%',
                b.titleAr,
              ];
            }).toList(),
          ),
          pw.SizedBox(height: 16),

          pw.Text(
            'توقعات الادخار السنوي: ${ArabicFormatters.formatMoney(plan.annualSavingsPotential, currency)}',
            style: pw.TextStyle(
              fontSize: 12,
              fontWeight: pw.FontWeight.bold,
              color: emerald,
            ),
          ),
          pw.SizedBox(height: 14),

          if (goals.isNotEmpty) ...[
            pw.Text(
              'الأهداف المالية',
              style: pw.TextStyle(fontSize: 15, fontWeight: pw.FontWeight.bold),
            ),
            pw.SizedBox(height: 8),
            pw.TableHelper.fromTextArray(
              context: context,
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                color: PdfColors.white,
                fontSize: 10.5,
              ),
              headerDecoration: pw.BoxDecoration(color: emerald),
              cellStyle: const pw.TextStyle(fontSize: 10),
              cellAlignment: pw.Alignment.centerRight,
              headers: ['الإنجاز', 'المدخر', 'المبلغ المطلوب', 'الهدف'],
              data: goals.map((g) {
                return [
                  '${(g.progress * 100).round()}%',
                  ArabicFormatters.formatMoney(g.savedAmount, currency),
                  ArabicFormatters.formatMoney(g.targetAmount, currency),
                  g.title,
                ];
              }).toList(),
            ),
            pw.SizedBox(height: 14),
          ],

          if (cycleExpenses.isNotEmpty) ...[
            pw.Text(
              'أحدث المصاريف المسجلة (${cycleExpenses.length})',
              style: pw.TextStyle(fontSize: 15, fontWeight: pw.FontWeight.bold),
            ),
            pw.SizedBox(height: 8),
            pw.TableHelper.fromTextArray(
              context: context,
              headerStyle: pw.TextStyle(
                fontWeight: pw.FontWeight.bold,
                color: PdfColors.white,
                fontSize: 10,
              ),
              headerDecoration: pw.BoxDecoration(color: emerald),
              cellStyle: const pw.TextStyle(fontSize: 9.5),
              cellAlignment: pw.Alignment.centerRight,
              headers: ['التاريخ', 'ملاحظة', 'المبلغ', 'الفئة'],
              data: cycleExpenses.take(20).map((e) {
                final cat = AppCategories.categoryById(e.categoryId);
                return [
                  ArabicFormatters.formatShortDate(e.createdAt),
                  e.note.isEmpty ? '-' : e.note,
                  ArabicFormatters.formatMoney(e.amount, currency),
                  cat.nameAr,
                ];
              }).toList(),
            ),
          ],

          pw.SizedBox(height: 20),
          pw.Divider(),
          pw.Text(
            'تم إنشاء التقرير بواسطة تطبيق رَتِّبْ — تنفيذ وتطوير: أمجد حبيب',
            style: const pw.TextStyle(fontSize: 10, color: PdfColors.grey700),
          ),
        ],
      ),
    );

    final dir = await getTemporaryDirectory();
    final file = File(
      '${dir.path}/rattib_report_${DateTime.now().millisecondsSinceEpoch}.pdf',
    );
    await file.writeAsBytes(await pdf.save());
    return file;
  }

  pw.Widget _metricCell(String label, String value) {
    return pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.center,
      children: [
        pw.Text(
          label,
          style: const pw.TextStyle(fontSize: 9.5, color: PdfColors.grey700),
        ),
        pw.SizedBox(height: 3),
        pw.Text(
          value,
          style: pw.TextStyle(fontSize: 11.5, fontWeight: pw.FontWeight.bold),
        ),
      ],
    );
  }

  Future<void> shareFile(File file, {String? text}) async {
    await Share.shareXFiles(
      [XFile(file.path)],
      text: text ?? 'التقرير المالي من تطبيق رَتِّبْ',
    );
  }
}
