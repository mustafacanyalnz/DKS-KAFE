package com.example.util

import com.example.data.OrderHistoryWithItems
import com.example.data.PaymentMethod
import com.example.data.TableWithItems
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProductSalesStat(
    val productName: String,
    val quantity: Int,
    val revenueTl: Double
)

data class StaffSalesStat(
    val username: String,
    val orderCount: Int,
    val revenueTl: Double
)

data class EndOfDayReport(
    val rangeLabel: String,
    val totalRevenueTl: Double,
    val orderCount: Int,
    val totalItemsSold: Int,
    val averageTicketTl: Double,
    val paymentTotals: Map<PaymentMethod, Double>,
    val paymentCounts: Map<PaymentMethod, Int>,
    val topProducts: List<ProductSalesStat>,
    val staffStats: List<StaffSalesStat>
)

object ReceiptFormatter {
    private val trLocale = Locale("tr", "TR")

    fun formatTl(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(trLocale).apply {
            minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            maximumFractionDigits = 2
        }
        return "${nf.format(amount)} TL"
    }

    fun formatTlStrict(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(trLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "${nf.format(amount)} TL"
    }

    fun formatDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", trLocale)
        return sdf.format(Date(millis))
    }

    fun formatTimeOnly(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", trLocale)
        return sdf.format(Date(millis))
    }

    fun formatElapsedMinutes(openedAt: Long?): String {
        if (openedAt == null) return ""
        val diffMin = ((System.currentTimeMillis() - openedAt) / 60_000L).coerceAtLeast(0L)
        return if (diffMin < 60) {
            "${diffMin} dk"
        } else {
            val h = diffMin / 60
            val m = diffMin % 60
            "${h} sa ${m} dk"
        }
    }

    fun buildActiveTableReceipt(tableWithItems: TableWithItems): String {
        val table = tableWithItems.table
        val sb = StringBuilder()
        sb.appendLine("*DKS SAHNE KAFE - E-ADİSYON*")
        sb.appendLine("Masa: Masa ${table.tableNumber}")
        sb.appendLine("Tarih: ${formatDateTime(table.openedAt ?: System.currentTimeMillis())}")
        table.openedByUsername?.let { sb.appendLine("Personel: $it") }
        if (table.note.isNotBlank()) {
            sb.appendLine("Not: ${table.note}")
        }
        sb.appendLine("------------------------")
        tableWithItems.items.forEach { item ->
            sb.appendLine("${item.quantity}x ${item.productName} (${formatTl(item.unitPriceTl)}) = ${formatTl(item.lineTotalTl)}")
        }
        sb.appendLine("------------------------")
        sb.appendLine("*TOPLAM: ${formatTlStrict(tableWithItems.totalAmountTl)}*")
        sb.append("Afiyet olsun! - DKS SAHNE KAFE")
        return sb.toString()
    }

    fun buildClosedOrderReceipt(orderWithItems: OrderHistoryWithItems): String {
        val o = orderWithItems.order
        val sb = StringBuilder()
        sb.appendLine("*DKS SAHNE KAFE - ÖDEME FİŞİ*")
        sb.appendLine("Fiş No: ${o.orderCode}")
        sb.appendLine("Masa: Masa ${o.tableNumber}")
        sb.appendLine("Kapanış: ${formatDateTime(o.closedAt)}")
        sb.appendLine("Ödeme: ${o.paymentMethodEnum.labelTr}")
        sb.appendLine("Personel: ${o.waiterUsername}")
        if (o.note.isNotBlank()) {
            sb.appendLine("Not: ${o.note}")
        }
        sb.appendLine("------------------------")
        orderWithItems.items.forEach { item ->
            sb.appendLine("${item.quantity}x ${item.productName} (${formatTl(item.unitPriceTl)}) = ${formatTl(item.lineTotalTl)}")
        }
        sb.appendLine("------------------------")
        sb.appendLine("*ÖDENEN TOPLAM: ${formatTlStrict(o.totalAmountTl)}*")
        sb.append("Bizi tercih ettiğiniz için teşekkürler!")
        return sb.toString()
    }

    fun buildEndOfDayReportText(
        report: EndOfDayReport,
        occupiedCount: Int,
        openTablesRevenue: Double
    ): String {
        val sb = StringBuilder()
        sb.appendLine("*DKS SAHNE KAFE - GÜN SONU RAPORU*")
        sb.appendLine("Dönem: ${report.rangeLabel}")
        sb.appendLine("Rapor Zamanı: ${formatDateTime(System.currentTimeMillis())}")
        sb.appendLine("------------------------")
        sb.appendLine("*Toplam Ciro:* ${formatTlStrict(report.totalRevenueTl)}")
        sb.appendLine("Kapanan Adisyon: ${report.orderCount}")
        sb.appendLine("Satılan Ürün: ${report.totalItemsSold} adet")
        sb.appendLine("Ortalama Adisyon: ${formatTlStrict(report.averageTicketTl)}")
        sb.appendLine("Anlık Doluluk: $occupiedCount / 20 Masa (${formatTl(openTablesRevenue)} açık)")
        sb.appendLine("------------------------")
        sb.appendLine("*Ödeme Dağılımı:*")
        PaymentMethod.entries.forEach { method ->
            val amt = report.paymentTotals[method] ?: 0.0
            val cnt = report.paymentCounts[method] ?: 0
            sb.appendLine("• ${method.labelTr}: ${formatTlStrict(amt)} ($cnt adisyon)")
        }
        if (report.topProducts.isNotEmpty()) {
            sb.appendLine("------------------------")
            sb.appendLine("*En Çok Satanlar:*")
            report.topProducts.take(5).forEachIndexed { idx, prod ->
                sb.appendLine("${idx + 1}. ${prod.productName} — ${prod.quantity} adet (${formatTl(prod.revenueTl)})")
            }
        }
        return sb.toString().trim()
    }
}
