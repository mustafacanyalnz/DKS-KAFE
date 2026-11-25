package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CafeTable
import com.example.data.CompletedOrder
import com.example.data.MenuProduct
import com.example.data.OrderLineItem
import com.example.data.PaymentMethod
import com.example.data.StaffAccount
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.WhatsAppGreen
import com.example.util.Formatters
import com.example.util.WhatsAppShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableOrderScreen(
    table: CafeTable,
    products: List<MenuProduct>,
    activeStaff: StaffAccount,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSaveOrder: (items: List<OrderLineItem>, note: String) -> Unit,
    onCloseTable: (tableWithLatestItems: CafeTable, paymentMethod: PaymentMethod, onClosed: (CompletedOrder) -> Unit) -> Unit,
    onNotify: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val draftItems = remember(table.id, table.items) {
        mutableStateListOf<OrderLineItem>().apply { addAll(table.items) }
    }
    var note by remember(table.id, table.customerNote) { mutableStateOf(table.customerNote) }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.NAKIT) }

    val categories = remember(products) {
        listOf("Tümü") + products.map { it.category }.distinct()
    }
    val visibleProducts = remember(products, selectedCategory) {
        products.filter { it.available && (selectedCategory == "Tümü" || it.category == selectedCategory) }
    }
    val draftTotal = draftItems.sumOf { it.lineTotal }
    val draftTableSnapshot = remember(table, draftItems.toList(), note, draftTotal) {
        table.copy(
            occupied = draftItems.isNotEmpty(),
            waiterName = table.waiterName.ifBlank { activeStaff.username },
            customerNote = note,
            items = draftItems.toList(),
            totalAmount = draftTotal
        )
    }

    fun addProduct(product: MenuProduct) {
        val index = draftItems.indexOfFirst { it.productId == product.id }
        if (index >= 0) {
            val current = draftItems[index]
            val newQty = (current.quantity + 1L).coerceAtMost(200L)
            draftItems[index] = current.copy(
                quantity = newQty,
                lineTotal = newQty * current.unitPrice
            )
        } else if (draftItems.size < 50) {
            draftItems.add(
                OrderLineItem(
                    productId = product.id,
                    productName = product.name,
                    unitPrice = product.price,
                    quantity = 1L,
                    lineTotal = product.price
                )
            )
        } else {
            onNotify("Bir adisyonda en fazla 50 kalem bulunabilir.")
        }
    }

    fun decrementItem(item: OrderLineItem) {
        val index = draftItems.indexOfFirst { it.productId == item.productId }
        if (index < 0) return
        if (item.quantity <= 1L) {
            draftItems.removeAt(index)
        } else {
            val newQty = item.quantity - 1L
            draftItems[index] = item.copy(
                quantity = newQty,
                lineTotal = newQty * item.unitPrice
            )
        }
    }

    fun shareCurrentViaWhatsApp() {
        val text = WhatsAppShareHelper.buildOpenTableReceipt(draftTableSnapshot, activeStaff.username)
        val launchedWhatsApp = WhatsAppShareHelper.shareTextViaWhatsApp(context, text)
        if (!launchedWhatsApp) {
            onNotify("Paylaşım menüsü açıldı (WhatsApp kurulu değilse diğer uygulamalar seçilebilir).")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${table.name} • Adisyon",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Personel: ${table.waiterName.ifBlank { activeStaff.username }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("table_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = WhatsAppShareHelper.buildOpenTableReceipt(
                                draftTableSnapshot,
                                activeStaff.username
                            )
                            WhatsAppShareHelper.copyToClipboard(context, "Adisyon", text)
                            onNotify("Adisyon metni panoya kopyalandı.")
                        },
                        modifier = Modifier.testTag("copy_receipt_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Adisyonu Kopyala")
                    }
                    IconButton(
                        onClick = { shareCurrentViaWhatsApp() },
                        modifier = Modifier.testTag("topbar_whatsapp_button")
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "WhatsApp ile Paylaş",
                            tint = WhatsAppGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
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
                            text = "Toplam (${draftItems.sumOf { it.quantity }} ürün)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = Formatters.formatCurrency(draftTotal),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSaveOrder(draftItems.toList(), note)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_table_order_button")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_order), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { shareCurrentViaWhatsApp() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("whatsapp_share_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", maxLines = 1)
                        }

                        Button(
                            onClick = { showPaymentDialog = true },
                            enabled = draftItems.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("close_bill_button")
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Öde", maxLines = 1)
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
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ProductSelectorPane(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        products = visibleProducts,
                        onAddProduct = ::addProduct,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    )
                    CartSummaryPane(
                        items = draftItems,
                        note = note,
                        onNoteChange = { note = it },
                        onAddOne = { item ->
                            products.firstOrNull { it.id == item.productId }?.let(::addProduct)
                        },
                        onDecrement = ::decrementItem,
                        onClearAll = { draftItems.clear() },
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Menüden Ürün Ekle",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(categories) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category) }
                                )
                            }
                        }
                    }

                    items(visibleProducts.chunked(2)) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { product ->
                                ProductQuickTapCard(
                                    product = product,
                                    onClick = { addProduct(product) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Adisyon Kalemleri (${draftItems.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (draftItems.isNotEmpty()) {
                                TextButton(onClick = { draftItems.clear() }) {
                                    Text("Temizle", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (draftItems.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Henüz ürün eklenmedi. Yukarıdaki ürünlere dokunarak adisyona ekleyin.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(draftItems, key = { it.productId }) { item ->
                            OrderLineRow(
                                item = item,
                                onIncrement = {
                                    val match = products.firstOrNull { it.id == item.productId }
                                        ?: MenuProduct(
                                            id = item.productId,
                                            name = item.productName,
                                            price = item.unitPrice
                                        )
                                    addProduct(match)
                                },
                                onDecrement = { decrementItem(item) }
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { if (it.length <= 200) note = it },
                            label = { Text("Masa Notu (opsiyonel)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("table_note_input")
                        )
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("${table.name} Hesabı Kapat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tahsil edilecek tutar: ${Formatters.formatCurrency(draftTotal)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ödeme yöntemini seçin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedPayment == method,
                            onClick = { selectedPayment = method },
                            label = { Text(method.labelTr) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_method_${method.name}")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPaymentDialog = false
                        onCloseTable(draftTableSnapshot, selectedPayment) { completed ->
                            // Closed successfully; ViewModel clears selected table and sets status banner
                        }
                    },
                    modifier = Modifier.testTag("confirm_close_bill_button")
                ) {
                    Text("Ödemeyi Al & Kapat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
private fun ProductSelectorPane(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    products: List<MenuProduct>,
    onAddProduct: (MenuProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Menü", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }
        }
        items(products.chunked(2)) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { product ->
                    ProductQuickTapCard(
                        product = product,
                        onClick = { onAddProduct(product) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartSummaryPane(
    items: List<OrderLineItem>,
    note: String,
    onNoteChange: (String) -> Unit,
    onAddOne: (OrderLineItem) -> Unit,
    onDecrement: (OrderLineItem) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(end = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adisyon (${items.size})", style = MaterialTheme.typography.titleMedium)
                if (items.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Temizle", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        items(items, key = { it.productId }) { item ->
            OrderLineRow(
                item = item,
                onIncrement = { onAddOne(item) },
                onDecrement = { onDecrement(item) }
            )
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) onNoteChange(it) },
                label = { Text("Masa Notu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProductQuickTapCard(
    product: MenuProduct,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .height(82.dp)
            .testTag("add_product_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatCompactCurrency(product.price),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "${product.name} ekle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderLineRow(
    item: OrderLineItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Formatters.formatCompactCurrency(item.unitPrice)} × ${item.quantity} = ${Formatters.formatCurrency(item.lineTotal)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement) {
                    Icon(
                        imageVector = if (item.quantity <= 1L) Icons.Rounded.DeleteOutline else Icons.Rounded.Remove,
                        contentDescription = "Azalt",
                        tint = if (item.quantity <= 1L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = onIncrement) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Artır",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
