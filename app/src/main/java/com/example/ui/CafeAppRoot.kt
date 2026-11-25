package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.CafeRepository
import com.example.data.CompletedOrder
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.StaffLoginScreen
import com.example.ui.auth.signOutGoogle
import com.example.ui.screens.AdminOccupancyScreen
import com.example.ui.screens.EndOfDayReportScreen
import com.example.ui.screens.ProductManagementScreen
import com.example.ui.screens.TableOrderScreen
import com.example.ui.screens.TablesScreen
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.WhatsAppGreen
import com.example.util.Formatters
import com.example.util.WhatsAppShareHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class CafeTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val adminOnly: Boolean,
    val tag: String
) {
    TABLES(R.string.nav_tables, Icons.Rounded.TableRestaurant, Icons.Outlined.TableRestaurant, false, "tab_tables"),
    OCCUPANCY(R.string.nav_occupancy, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, true, "tab_occupancy"),
    PRODUCTS(R.string.nav_products, Icons.Rounded.Coffee, Icons.Outlined.Coffee, true, "tab_products"),
    REPORTS(R.string.nav_reports, Icons.Rounded.Assessment, Icons.Outlined.Assessment, true, "tab_reports")
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: CafeViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        CafeViewModel(CafeRepository(db), uid)
                    }
                }
            )
            AuthenticatedCafeSession(
                viewModel = viewModel,
                googleEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedCafeSession(
    viewModel: CafeViewModel,
    googleEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val activeStaff by viewModel.activeStaff.collectAsStateWithLifecycle()
    val loginLoading by viewModel.loginLoading.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    val staff = activeStaff
    if (staff == null) {
        StaffLoginScreen(
            isLoading = loginLoading,
            errorMessage = loginError,
            googleEmail = googleEmail,
            onLoginSubmit = { username, password -> viewModel.loginStaff(username, password) },
            onClearError = { viewModel.clearLoginError() },
            onSignOutGoogle = {
                signOutGoogle(credentialManager = credentialManager, scope = scope)
            }
        )
    } else {
        CafeWorkspace(
            viewModel = viewModel,
            activeStaff = staff,
            onSwitchStaff = { viewModel.logoutStaff() },
            onSignOutGoogle = {
                viewModel.logoutStaff()
                signOutGoogle(credentialManager = credentialManager, scope = scope)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CafeWorkspace(
    viewModel: CafeViewModel,
    activeStaff: StaffAccount,
    onSwitchStaff: () -> Unit,
    onSignOutGoogle: () -> Unit
) {
    val context = LocalContext.current
    val tablesState by viewModel.tablesState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val staffState by viewModel.staffState.collectAsStateWithLifecycle()
    val selectedTable by viewModel.selectedTable.collectAsStateWithLifecycle()
    val occupancy by viewModel.liveOccupancySummary.collectAsStateWithLifecycle()
    val reportSummary by viewModel.dailyReportSummary.collectAsStateWithLifecycle()
    val availableDates by viewModel.availableReportDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedReportDate.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember(activeStaff.username) { mutableStateOf(CafeTab.TABLES) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var justClosedOrder by remember { mutableStateOf<CompletedOrder?>(null) }

    val visibleTabs = remember(activeStaff.isAdmin) {
        CafeTab.entries.filter { !it.adminOnly || activeStaff.isAdmin }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    val currentTable = selectedTable
    if (currentTable != null) {
        val productList = (productsState as? UiState.Success)?.data.orEmpty()
        TableOrderScreen(
            table = currentTable,
            products = productList,
            activeStaff = activeStaff,
            snackbarHostState = snackbarHostState,
            onBack = { viewModel.selectTable(null) },
            onSaveOrder = { items, note ->
                viewModel.saveTableOrder(currentTable, items, note)
            },
            onCloseTable = { tableWithItems, paymentMethod, _ ->
                viewModel.closeTableOrder(tableWithItems, paymentMethod) { completed ->
                    justClosedOrder = completed
                }
            },
            onNotify = viewModel::showBanner
        )
    } else {
        if (currentTab != CafeTab.TABLES) {
            BackHandler { currentTab = CafeTab.TABLES }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.brand_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeStaff.displayName} • ${activeStaff.staffRole.labelTr}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = if (activeStaff.isAdmin) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeStaff.isAdmin) {
                                        Icons.Rounded.AdminPanelSettings
                                    } else {
                                        Icons.Rounded.Badge
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = activeStaff.username,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAccountMenu = true },
                            modifier = Modifier.testTag("account_menu_button")
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = "Kullanıcı / Çıkış")
                        }

                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.switch_staff)) },
                                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showAccountMenu = false
                                    onSwitchStaff()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out_google)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                                },
                                onClick = {
                                    showAccountMenu = false
                                    onSignOutGoogle()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (visibleTabs.size > 1) {
                    // Bottom bar on compact width; Rail handled inside BoxWithConstraints on wider screens
                    BoxWithConstraints {
                        if (maxWidth < 600.dp) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                visibleTabs.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag(tab.tag)
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
                val useRail = visibleTabs.size > 1 && maxWidth >= 600.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            visibleTabs.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    when (currentTab) {
                        CafeTab.TABLES -> {
                            TablesScreen(
                                tablesState = tablesState,
                                onTableClick = { table -> viewModel.selectTable(table.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        CafeTab.OCCUPANCY -> {
                            if (activeStaff.isAdmin) {
                                AdminOccupancyScreen(
                                    occupancy = occupancy,
                                    activeStaff = activeStaff,
                                    staffList = (staffState as? UiState.Success)?.data.orEmpty(),
                                    onOpenTable = { table -> viewModel.selectTable(table.id) },
                                    onUpdateStaffPassword = { username, newPass ->
                                        viewModel.updateStaffPassword(username, newPass)
                                    },
                                    onNotify = viewModel::showBanner,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                TablesScreen(
                                    tablesState = tablesState,
                                    onTableClick = { table -> viewModel.selectTable(table.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        CafeTab.PRODUCTS -> {
                            if (activeStaff.isAdmin) {
                                ProductManagementScreen(
                                    productsState = productsState,
                                    activeStaff = activeStaff,
                                    onAddProduct = { name, cat, price ->
                                        viewModel.addProduct(name, cat, price)
                                    },
                                    onUpdateProduct = { id, name, cat, price, available ->
                                        viewModel.updateProduct(id, name, cat, price, available)
                                    },
                                    onDeleteProduct = { id, name ->
                                        viewModel.deleteProduct(id, name)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                TablesScreen(
                                    tablesState = tablesState,
                                    onTableClick = { table -> viewModel.selectTable(table.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        CafeTab.REPORTS -> {
                            if (activeStaff.isAdmin) {
                                EndOfDayReportScreen(
                                    ordersState = ordersState,
                                    summary = reportSummary,
                                    availableDates = availableDates,
                                    selectedDate = selectedDate,
                                    activeStaff = activeStaff,
                                    onSelectDate = { date -> viewModel.selectReportDate(date) },
                                    onNotify = viewModel::showBanner,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                TablesScreen(
                                    tablesState = tablesState,
                                    onTableClick = { table -> viewModel.selectTable(table.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    justClosedOrder?.let { closedOrder ->
        AlertDialog(
            onDismissRequest = { justClosedOrder = null },
            title = { Text("${closedOrder.tableName} Hesabı Kapatıldı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = Formatters.formatCurrency(closedOrder.totalAmount),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ödeme: ${closedOrder.paymentMethod} • Sipariş geçmişine kaydedildi.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val receipt = WhatsAppShareHelper.buildCompletedOrderReceipt(closedOrder)
                        WhatsAppShareHelper.shareTextViaWhatsApp(context, receipt)
                        justClosedOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("post_close_whatsapp_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fişi WhatsApp'tan Paylaş")
                }
            },
            dismissButton = {
                TextButton(onClick = { justClosedOrder = null }) {
                    Text("Tamam")
                }
            }
        )
    }
}
