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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MenuProduct
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.ui.theme.MonoFontFamily
import com.example.util.Formatters

@Composable
fun ProductManagementScreen(
    productsState: UiState<List<MenuProduct>>,
    activeStaff: StaffAccount,
    onAddProduct: (name: String, category: String, price: Double) -> Unit,
    onUpdateProduct: (productId: String, name: String, category: String, price: Double, available: Boolean) -> Unit,
    onDeleteProduct: (productId: String, productName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<MenuProduct?>(null) }
    var deletingProduct by remember { mutableStateOf<MenuProduct?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Yeni Ürün Ekle")
            }
        }
    ) { innerPadding ->
        when (productsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(productsState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                val products = productsState.data
                val categories = remember(products) {
                    listOf("Tümü") + products.map { it.category }.distinct()
                }
                val filtered = remember(products, searchQuery, selectedCategory) {
                    products.filter { prod ->
                        (selectedCategory == "Tümü" || prod.category == selectedCategory) &&
                            (searchQuery.isBlank() || prod.name.contains(searchQuery, ignoreCase = true))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Ürün & Fiyat Yönetimi",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Yetkili: ${activeStaff.username} • ${products.size} ürün",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Ürün ara...") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_search_input")
                            )

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
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { product ->
                            ProductManagementRow(
                                product = product,
                                onEdit = { editingProduct = product },
                                onToggleAvailable = { isAvailable ->
                                    onUpdateProduct(
                                        product.id,
                                        product.name,
                                        product.category,
                                        product.price,
                                        isAvailable
                                    )
                                },
                                onDelete = { deletingProduct = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProductEditorDialog(
            title = "Yeni Ürün Ekle",
            initialName = "",
            initialCategory = "Sıcak İçecekler",
            initialPrice = "",
            onDismiss = { showAddDialog = false },
            onSave = { name, category, price ->
                onAddProduct(name, category, price)
                showAddDialog = false
            }
        )
    }

    editingProduct?.let { product ->
        ProductEditorDialog(
            title = "${product.name} Düzenle",
            initialName = product.name,
            initialCategory = product.category,
            initialPrice = if (product.price % 1.0 == 0.0) {
                product.price.toInt().toString()
            } else {
                product.price.toString()
            },
            onDismiss = { editingProduct = null },
            onSave = { name, category, price ->
                onUpdateProduct(product.id, name, category, price, product.available)
                editingProduct = null
            }
        )
    }

    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Ürünü Sil") },
            text = { Text("${product.name} menüden kalıcı olarak silinsin mi?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product.id, product.name)
                        deletingProduct = null
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun ProductManagementRow(
    product: MenuProduct,
    onEdit: () -> Unit,
    onToggleAvailable: (Boolean) -> Unit,
    onDelete: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.category} • Güncelleyen: ${product.updatedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatCurrency(product.price),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = product.available,
                    onCheckedChange = onToggleAvailable
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_product_${product.id}")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Fiyat ve ürün düzenle")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_product_${product.id}")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Ürünü sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialPrice: String,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, price: Double) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var priceText by remember { mutableStateOf(initialPrice) }

    val presetCategories = remember {
        listOf("Sıcak İçecekler", "Soğuk İçecekler", "Tatlı & Atıştırmalık")
    }
    val parsedPrice = priceText.replace(',', '.').toDoubleOrNull()
    val isValid = name.trim().isNotEmpty() &&
        category.trim().isNotEmpty() &&
        parsedPrice != null &&
        parsedPrice in 0.0..100_000.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Ürün Adı") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_product_name")
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Fiyat (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_product_price")
                )
                Text(
                    text = "Kategori",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetCategories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { if (it.length <= 40) category = it },
                    label = { Text("Kategori Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedPrice != null) {
                        onSave(name.trim(), category.trim(), parsedPrice)
                    }
                },
                enabled = isValid,
                modifier = Modifier.testTag("dialog_product_save")
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
