package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActiveOrderItemEntity
import com.example.data.MenuItemEntity
import com.example.data.OrderHistoryWithItems
import com.example.data.PaymentMethod
import com.example.data.TableWithItems
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.TableAvailableGreen
import com.example.ui.theme.TableOccupiedAmber
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppOnGreen
import com.example.util.ReceiptFormatter
import com.example.util.WhatsAppShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(
    tableWithItems: TableWithItems,
    allTables: List<TableWithItems>,
    menuItems: List<MenuItemEntity>,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onAddProduct: (MenuItemEntity) -> Unit,
    onChangeQuantity: (itemId: Long, delta: Int) -> Unit,
    onSaveNote: (String) -> Unit,
    onTransferTable: (Int) -> Unit,
    onCancelOrder: () -> Unit,
    onCloseBill: (PaymentMethod, (OrderHistoryWithItems) -> Unit) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("Tümü") }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var closedReceiptForShare by remember { mutableStateOf<OrderHistoryWithItems?>(null) }

    val categories = remember(menuItems) {
        listOf("Tümü") + menuItems.map { it.category }.distinct()
    }
    val filteredMenu = remember(menuItems, searchQuery, selectedCategory) {
        menuItems.filter { item ->
            val matchCat = selectedCategory == "Tümü" || item.category == selectedCategory
            val matchQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery.trim(), ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val table = tableWithItems.table
    val hasItems = tableWithItems.items.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Masa ${table.tableNumber}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (table.isOccupied) {
                                "Dolu • ${table.openedByUsername ?: ""}"
                            } else {
                                "Müsait Masa"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (table.isOccupied) TableOccupiedAmber else TableAvailableGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("table_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Masalara dön")
                    }
                },
                actions = {
                    if (hasItems) {
                        IconButton(
                            onClick = { showNoteDialog = true },
                            modifier = Modifier.testTag("table_note_button")
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = "Masa notu")
                        }
                        IconButton(
                            onClick = { showTransferDialog = true },
                            modifier = Modifier.testTag("table_transfer_button")
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Masayı taşı")
                        }
                        if (isAdmin) {
                            IconButton(
                                onClick = { showCancelConfirm = true },
                                modifier = Modifier.testTag("table_cancel_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Adisyonu iptal et",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (hasItems) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Adisyon Toplamı (${tableWithItems.totalItemCount} ürün)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ReceiptFormatter.formatTlStrict(tableWithItems.totalAmountTl),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = ReceiptFormatter.buildActiveTableReceipt(tableWithItems)
                                    WhatsAppShareHelper.shareTextViaWhatsApp(context, msg)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppGreen,
                                    contentColor = WhatsAppOnGreen
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_whatsapp_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "WhatsApp",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { showCloseDialog = true },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("close_bill_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hesabı Kapat",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 720.dp
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActiveOrderPanel(
                        tableWithItems = tableWithItems,
                        onChangeQuantity = onChangeQuantity,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    MenuPickerPanel(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        menuItems = filteredMenu,
                        onAddProduct = onAddProduct,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    ActiveOrderCompactSection(
                        tableWithItems = tableWithItems,
                        onChangeQuantity = onChangeQuantity
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MenuPickerPanel(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        menuItems = filteredMenu,
                        onAddProduct = onAddProduct,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        var noteText by remember { mutableStateOf(table.note) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Masa ${table.tableNumber} Notu") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Sipariş / müşteri notu") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSaveNote(noteText)
                    showNoteDialog = false
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Vazgeç") }
            }
        )
    }

    if (showTransferDialog) {
        val emptyTables = allTables.filter { !it.table.isOccupied && it.table.tableNumber != table.tableNumber }
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Masa ${table.tableNumber} Adisyonunu Taşı") },
            text = {
                if (emptyTables.isEmpty()) {
                    Text("Şu an boş masa bulunmuyor.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(220.dp)
                    ) {
                        items(emptyTables, key = { it.table.tableNumber }) { target ->
                            FilledTonalButton(
                                onClick = {
                                    onTransferTable(target.table.tableNumber)
                                    showTransferDialog = false
                                    onBack()
                                },
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("M${target.table.tableNumber}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) { Text("Kapat") }
            }
        )
    }

    if (showCloseDialog) {
        var selectedMethod by remember { mutableStateOf(PaymentMethod.NAKIT) }
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Hesabı Kapat • Masa ${table.tableNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Toplam Tutar: ${ReceiptFormatter.formatTlStrict(tableWithItems.totalAmountTl)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ödeme yöntemini seçiniz:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaymentMethod.entries.forEach { method ->
                        val isSelected = selectedMethod == method
                        val icon = when (method) {
                            PaymentMethod.NAKIT -> Icons.Filled.Payments
                            PaymentMethod.KART -> Icons.Filled.CreditCard
                            PaymentMethod.HAVALE -> Icons.Filled.QrCode2
                        }
                        Surface(
                            onClick = { selectedMethod = method },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_${method.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = method.labelTr,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseDialog = false
                        onCloseBill(selectedMethod) { closedOrder ->
                            closedReceiptForShare = closedOrder
                        }
                    },
                    modifier = Modifier.testTag("confirm_close_bill")
                ) {
                    Text("Ödemeyi Al & Kapat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) { Text("Vazgeç") }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Adisyon İptali") },
            text = { Text("Masa ${table.tableNumber} üzerindeki tüm kalemler silinsin mi? Bu işlem güvenlik kaydına işlenir.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancelOrder()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("İptal Et") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Vazgeç") }
            }
        )
    }

    closedReceiptForShare?.let { closedOrder ->
        AlertDialog(
            onDismissRequest = {
                closedReceiptForShare = null
                onBack()
            },
            title = { Text("Hesap Kapatıldı!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fiş No: ${closedOrder.order.orderCode}")
                    Text(
                        text = "Tahsil Edilen: ${ReceiptFormatter.formatTlStrict(closedOrder.order.totalAmountTl)} (${closedOrder.order.paymentMethodEnum.labelTr})",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Fişi müşteriye veya işletme grubuna WhatsApp ile göndermek ister misiniz?")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = ReceiptFormatter.buildClosedOrderReceipt(closedOrder)
                        closedReceiptForShare = null
                        WhatsAppShareHelper.shareTextViaWhatsApp(context, msg)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = WhatsAppOnGreen
                    )
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp ile Paylaş")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    closedReceiptForShare = null
                    onBack()
                }) { Text("Masalara Dön") }
            }
        )
    }
}

@Composable
private fun ActiveOrderCompactSection(
    tableWithItems: TableWithItems,
    onChangeQuantity: (Long, Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aktif Adisyon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (tableWithItems.table.note.isNotBlank()) {
                    Text(
                        text = "Not: ${tableWithItems.table.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (tableWithItems.items.isEmpty()) {
                Text(
                    text = "Henüz ürün eklenmedi. Aşağıdaki menüden dokunarak ekleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((tableWithItems.items.size.coerceAtMost(3) * 52 + 8).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tableWithItems.items, key = { it.id }) { item ->
                        ActiveOrderItemRow(
                            item = item,
                            onMinus = { onChangeQuantity(item.id, -1) },
                            onPlus = { onChangeQuantity(item.id, +1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderPanel(
    tableWithItems: TableWithItems,
    onChangeQuantity: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Aktif Adisyon",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (tableWithItems.table.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Not: ${tableWithItems.table.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            if (tableWithItems.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sağdaki menüden ürün ekleyerek adisyonu başlatın.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tableWithItems.items, key = { it.id }) { item ->
                        ActiveOrderItemRow(
                            item = item,
                            onMinus = { onChangeQuantity(item.id, -1) },
                            onPlus = { onChangeQuantity(item.id, +1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderItemRow(
    item: ActiveOrderItemEntity,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${ReceiptFormatter.formatTl(item.unitPriceTl)} • Toplam: ${ReceiptFormatter.formatTl(item.lineTotalTl)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onMinus,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("order_minus_${item.id}")
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Azalt")
            }
            Text(
                text = "${item.quantity}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = onPlus,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("order_plus_${item.id}")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Artır")
            }
        }
    }
}

@Composable
private fun MenuPickerPanel(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    menuItems: List<MenuItemEntity>,
    onAddProduct: (MenuItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Ürün ara (örn. Çay, Kahve, Tost)...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("menu_search_input")
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(menuItems, key = { it.id }) { item ->
                Card(
                    onClick = { if (item.isAvailable) onAddProduct(item) },
                    enabled = item.isAvailable,
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isAvailable) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .testTag("menu_pick_${item.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ReceiptFormatter.formatTl(item.priceTl),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = if (item.isAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (item.isAvailable) "+ Ekle" else "Tükendi",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isAvailable) TableAvailableGreen else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
