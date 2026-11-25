package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.CafeTable
import com.example.data.CompletedOrder
import com.example.data.DailyReportSummary

object WhatsAppShareHelper {

    fun buildOpenTableReceipt(table: CafeTable, activeStaffName: String): String {
        val sb = StringBuilder()
        sb.appendLine("☕ *DKS SAHNE KAFE — E-ADİSYON*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🪑 Masa: *${table.name}*")
        val waiter = table.waiterName.ifBlank { activeStaffName }
        sb.appendLine("👤 Garson: $waiter")
        val dateText = if (table.openedAtMillis > 0L) {
            Formatters.formatDateTime(table.openedAtMillis)
        } else {
            Formatters.formatDateTime(System.currentTimeMillis())
        }
        sb.appendLine("📅 Açılış: $dateText")
        if (table.customerNote.isNotBlank()) {
            sb.appendLine("📝 Not: ${table.customerNote}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        if (table.items.isEmpty()) {
            sb.appendLine("_Henüz ürün eklenmedi._")
        } else {
            table.items.forEach { item ->
                sb.appendLine(
                    "• ${item.quantity}x ${item.productName} (${Formatters.formatCompactCurrency(item.unitPrice)}) = *${Formatters.formatCurrency(item.lineTotal)}*"
                )
            }
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 *TOPLAM: ${Formatters.formatCurrency(table.totalAmount)}*")
        sb.appendLine("📌 Durum: Açık Adisyon")
        sb.append("DKS Sahne Kafe'yi tercih ettiğiniz için teşekkürler!")
        return sb.toString()
    }

    fun buildCompletedOrderReceipt(order: CompletedOrder): String {
        val sb = StringBuilder()
        sb.appendLine("☕ *DKS SAHNE KAFE — ÖDEME FİŞİ*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🪑 Masa: *${order.tableName}*")
        sb.appendLine("👤 Garson: ${order.waiterUsername}")
        sb.appendLine("✅ Kapatan: ${order.closedByUsername}")
        sb.appendLine("📅 Tarih: ${Formatters.formatDateTime(order.closedAtMillis)}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        order.items.forEach { item ->
            sb.appendLine(
                "• ${item.quantity}x ${item.productName} (${Formatters.formatCompactCurrency(item.unitPrice)}) = *${Formatters.formatCurrency(item.lineTotal)}*"
            )
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💳 Ödeme Yöntemi: *${order.paymentMethod}*")
        sb.appendLine("💰 *TOPLAM: ${Formatters.formatCurrency(order.totalAmount)}*")
        sb.append("Teşekkür ederiz, yine bekleriz!")
        return sb.toString()
    }

    fun buildDailyReportText(summary: DailyReportSummary, actorUsername: String): String {
        val sb = StringBuilder()
        sb.appendLine("📊 *DKS SAHNE KAFE — GÜN SONU RAPORU*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📅 Dönem: ${summary.dateLabel}")
        sb.appendLine("👤 Raporu Alan: $actorUsername")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 *Toplam Ciro:* ${Formatters.formatCurrency(summary.totalRevenue)}")
        sb.appendLine("🧾 *Adisyon Sayısı:* ${summary.orderCount}")
        sb.appendLine("📦 *Satılan Ürün Adedi:* ${summary.totalItemsSold}")
        sb.appendLine("📈 *Ortalama Adisyon:* ${Formatters.formatCurrency(summary.averageOrderValue)}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("*Ödeme Dağılımı:*")
        sb.appendLine("• Nakit: ${Formatters.formatCurrency(summary.cashTotal)}")
        sb.appendLine("• Kart: ${Formatters.formatCurrency(summary.cardTotal)}")
        sb.appendLine("• Havale: ${Formatters.formatCurrency(summary.transferTotal)}")
        if (summary.productBreakdowns.isNotEmpty()) {
            sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
            sb.appendLine("*En Çok Satan Ürünler:*")
            summary.productBreakdowns.take(8).forEach { prod ->
                sb.appendLine("• ${prod.productName}: ${prod.quantitySold} adet (${Formatters.formatCurrency(prod.totalRevenue)})")
            }
        }
        return sb.toString()
    }

    fun shareTextViaWhatsApp(context: Context, message: String): Boolean {
        val packageManager = context.packageManager
        val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        val targetPackage = whatsAppPackages.firstOrNull { pkg ->
            isPackageInstalled(packageManager, pkg)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            if (targetPackage != null) {
                context.startActivity(sendIntent)
                true
            } else {
                val chooser = Intent.createChooser(sendIntent, "Adisyonu Paylaş").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                false
            }
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(fallback, "Adisyonu Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            false
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
