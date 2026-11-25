package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CompletedOrder
import com.example.data.DailyReportSummary
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.ui.CafeViewModel
import com.example.ui.theme.AvailableSage
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OccupiedAmber
import com.example.ui.theme.StageGold
import com.example.ui.theme.WhatsAppGreen
import com.example.util.Formatters
import com.example.util.WhatsAppShareHelper

@Composable
fun EndOfDayReportScreen(
    ordersState: UiState<List<CompletedOrder>>,
    summary: DailyReportSummary,
    availableDates: List<String>,
    selectedDate: String,
    activeStaff: StaffAccount,
    onSelectDate: (String) -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    when (ordersState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(ordersState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is UiState.Success -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Assessment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Gün Sonu Raporları",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            Button(
                                onClick = {
                                    val reportText = WhatsAppShareHelper.buildDailyReportText(
                                        summary,
                                        activeStaff.username
                                    )
                                    val ok = WhatsAppShareHelper.shareTextViaWhatsApp(context, reportText)
                                    if (!ok) {
                                        onNotify("Rapor paylaşım menüsü açıldı.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppGreen,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("share_report_whatsapp_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rapor Paylaş")
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableDates) { date ->
                                val label = if (date == CafeViewModel.ALL_DATES_KEY) {
                                    "Tüm Geçmiş"
                                } else {
                                    Formatters.formatBusinessDateLabel(date)
                                }
                                FilterChip(
                                    selected = selectedDate == date,
                                    onClick = { onSelectDate(date) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // KPI Cards
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReportKpiCard(
                                label = "Toplam Ciro",
                                value = Formatters.formatCurrency(summary.totalRevenue),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            ReportKpiCard(
                                label = "Adisyon Sayısı",
                                value = "${summary.orderCount}",
                                accent = AvailableSage,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReportKpiCard(
                                label = "Ortalama Adisyon",
                                value = Formatters.formatCurrency(summary.averageOrderValue),
                                accent = OccupiedAmber,
                                modifier = Modifier.weight(1f)
                            )
                            ReportKpiCard(
                                label = "Satılan Ürün",
                                value = "${summary.totalItemsSold} adet",
                                accent = StageGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Payment Breakdown
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Ödeme Yöntemi Dağılımı", style = MaterialTheme.typography.titleMedium)
                            val total = summary.totalRevenue.coerceAtLeast(0.01)
                            PaymentProgressRow("Nakit", summary.cashTotal, total, AvailableSage)
                            PaymentProgressRow("Kart", summary.cardTotal, total, MaterialTheme.colorScheme.primary)
                            PaymentProgressRow("Havale / EFT", summary.transferTotal, total, StageGold)
                        }
                    }
                }

                // Product Sales Breakdown
                if (summary.productBreakdowns.isNotEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Ürün Bazlı Satışlar", style = MaterialTheme.typography.titleMedium)
                                summary.productBreakdowns.forEachIndexed { idx, row ->
                                    if (idx > 0) HorizontalDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(row.productName, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${row.quantitySold} adet satıldı",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = Formatters.formatCurrency(row.totalRevenue),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Staff Performance
                if (summary.staffBreakdowns.isNotEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Personel Performansı", style = MaterialTheme.typography.titleMedium)
                                summary.staffBreakdowns.forEach { staffRow ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${staffRow.username} (${staffRow.orderCount} adisyon)",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = Formatters.formatCurrency(staffRow.totalRevenue),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Order History
                item {
                    Text(
                        text = "Sipariş Geçmişi (${summary.orders.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (summary.orders.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(38.dp)
                                )
                                Text(
                                    text = "Seçilen dönemde kapatılmış sipariş bulunmuyor.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(summary.orders, key = { it.id }) { order ->
                        CompletedOrderHistoryCard(
                            order = order,
                            onShareWhatsApp = {
                                val text = WhatsAppShareHelper.buildCompletedOrderReceipt(order)
                                val ok = WhatsAppShareHelper.shareTextViaWhatsApp(context, text)
                                if (!ok) {
                                    onNotify("Paylaşım menüsü açıldı.")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaymentProgressRow(
    label: String,
    amount: Double,
    totalRevenue: Double,
    color: Color
) {
    val ratio = if (totalRevenue > 0.0) (amount / totalRevenue).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = Formatters.formatCurrency(amount),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.SemiBold
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun CompletedOrderHistoryCard(
    order: CompletedOrder,
    onShareWhatsApp: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.tableName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = order.paymentMethod,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = order.itemsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatDateTime(order.closedAtMillis)} • Garson: ${order.waiterUsername}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.formatCurrency(order.totalAmount),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onShareWhatsApp) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Fişi WhatsApp ile paylaş",
                        tint = WhatsAppGreen
                    )
                }
            }
        }
    }
}
