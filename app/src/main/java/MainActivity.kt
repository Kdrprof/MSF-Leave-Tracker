package com.msf.jordan.leavetracker

import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

// 1. تعريف أنواع الإجازات والألوان المحدثة
enum class LeaveType(val displayName: String, val colorHex: Long, val affectsPayslip: Boolean) {
    HOLIDAY("Holiday", 0xFF10B981, true),          // أخضر - تؤثر على الراتب
    SICK("Sick", 0xFFEF4444, false),               // أحمر
    PERSONAL("Personal", 0xFF3B82F6, false),       // أزرق
    TRAINING("Training", 0xFF8B5CF6, false),       // بنفسجي
    UNPAID("Unpaid", 0xFFF97316, false),           // برتقالي
    COMPASSIONATE("Compassionate", 0xFF334155, false) // تم التعديل إلى الرمادي الداكن (السكني)
}

// 2. هيكل بيانات الإجازة يدعم الأيام المتغيرة
data class LeaveRecord(
    val id: Long = 0,
    val type: LeaveType,
    val startDate: LocalDate,
    val durationDays: Double, // يدعم 0.5، 1.0، 2.5، الخ
    val note: String = ""
)

// هيكل بيانات سليب الراتب
data class PayslipSummary(
    val previousBalance: Double,
    val accountedThisMonth: Double,
    val acquiredThisMonth: Double = 2.08,
    val remainingBalance: Double
)

// 3. محرك احتساب سليب الراتب والسجل الشامل
object LeaveEngine {
    
    // احتساب خصم سليب الراتب بناءً على قاعدة 15 الشهر لإجازات Holiday فقط
    fun calculatePayslip(
        targetYear: Int, 
        targetMonth: Int, 
        initialBalance: Double, 
        allLeaves: List<LeaveRecord>
    ): PayslipSummary {
        val prevMonthDate = LocalDate.of(targetYear, targetMonth, 1).minusMonths(1)
        val cutoffStart = LocalDate.of(prevMonthDate.year, prevMonthDate.monthValue, 16)
        val cutoffEnd = LocalDate.of(targetYear, targetMonth, 15)

        val accountedDays = allLeaves.filter { 
            it.type == LeaveType.HOLIDAY &&
            !it.startDate.isBefore(cutoffStart) && 
            !it.startDate.isAfter(cutoffEnd)
        }.sumOf { it.durationDays }

        val remaining = initialBalance + 2.08 - accountedDays

        return PayslipSummary(
            previousBalance = initialBalance,
            accountedThisMonth = accountedDays,
            acquiredThisMonth = 2.08,
            remainingBalance = remaining
        )
    }
}

// 4. واجهة المستخدم للشاشة الرئيسية 
@Composable
fun MainDashboardScreen(summary: PayslipSummary) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        // البطاقة البارزة للرصيد المتبقي
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)) // أزرق داكن للخلفية
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "الرصيد المتبقي (Remaining)", 
                    color = Color.White, 
                    fontSize = 18.sp
                )
                Text(
                    text = "${summary.remainingBalance} يوم", 
                    color = Color(0xFF10B981), // لون أخضر زاهي للرصيد
                    fontSize = 38.sp, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // أزرار الإدخال الهجينة (يدوي + ماسح ضوئي)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { /* فتح فورم الإدخال اليدوي للأيام المتغيرة 0.5, 1.0 الخ */ }, 
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("إدخال إجازة يدوياً")
            }
            Button(
                onClick = { /* فتح الكاميرا للمسح الضوئي */ }, 
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
            ) {
                Text("مسح طلب ورقي (OCR)")
            }
        }
    }
}
