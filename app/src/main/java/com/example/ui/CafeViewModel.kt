package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CafeRepository
import com.example.data.CafeTable
import com.example.data.CompletedOrder
import com.example.data.DailyReportSummary
import com.example.data.LiveOccupancySummary
import com.example.data.MenuProduct
import com.example.data.OrderLineItem
import com.example.data.PaymentMethod
import com.example.data.ProductSalesRow
import com.example.data.StaffAccount
import com.example.data.StaffSalesRow
import com.example.data.UiState
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CafeViewModel(
    private val repository: CafeRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _activeStaff = MutableStateFlow<StaffAccount?>(null)
    val activeStaff: StateFlow<StaffAccount?> = _activeStaff.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _selectedTableId = MutableStateFlow<String?>(null)
    val selectedTableId: StateFlow<String?> = _selectedTableId.asStateFlow()

    private val _selectedReportDate = MutableStateFlow(Formatters.todayBusinessDate())
    val selectedReportDate: StateFlow<String> = _selectedReportDate.asStateFlow()

    private val _seedReady = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val result = repository.ensureSeedData(currentUserId)
            if (result.isFailure) {
                _loginError.value = result.exceptionOrNull()?.localizedMessage
                    ?: "Başlangıç verileri yüklenemedi."
            }
            _seedReady.value = true
        }
    }

    val tablesState: StateFlow<UiState<List<CafeTable>>> =
        repository.observeTables(currentUserId)
            .toUiStateFlow("Masalar yüklenemedi")

    val productsState: StateFlow<UiState<List<MenuProduct>>> =
        repository.observeProducts(currentUserId)
            .toUiStateFlow("Ürün listesi yüklenemedi")

    val ordersState: StateFlow<UiState<List<CompletedOrder>>> =
        repository.observeOrders(currentUserId)
            .toUiStateFlow("Sipariş geçmişi yüklenemedi")

    val staffState: StateFlow<UiState<List<StaffAccount>>> =
        repository.observeStaff(currentUserId)
            .toUiStateFlow("Personel listesi yüklenemedi")

    val selectedTable: StateFlow<CafeTable?> =
        combine(tablesState, _selectedTableId) { state, tableId ->
            if (tableId == null) null
            else (state as? UiState.Success)?.data?.firstOrNull { it.id == tableId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val liveOccupancySummary: StateFlow<LiveOccupancySummary> =
        tablesState.map { state ->
            val tables = (state as? UiState.Success)?.data.orEmpty()
            val total = if (tables.isNotEmpty()) tables.size else 20
            val occupied = tables.filter { it.occupied }
            val percent = if (total > 0) ((occupied.size * 100f) / total).toInt() else 0
            LiveOccupancySummary(
                occupiedCount = occupied.size,
                totalTables = total,
                occupancyPercent = percent,
                openRevenue = occupied.sumOf { it.totalAmount },
                occupiedTables = occupied.sortedBy { it.openedAtMillis }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LiveOccupancySummary(0, 20, 0, 0.0, emptyList())
        )

    val availableReportDates: StateFlow<List<String>> =
        ordersState.map { state ->
            val today = Formatters.todayBusinessDate()
            val yesterday = Formatters.yesterdayBusinessDate()
            val orderDates = (state as? UiState.Success)?.data
                ?.map { it.businessDate }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            (listOf(today, yesterday) + orderDates + listOf(ALL_DATES_KEY)).distinct()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = listOf(Formatters.todayBusinessDate(), Formatters.yesterdayBusinessDate(), ALL_DATES_KEY)
        )

    val dailyReportSummary: StateFlow<DailyReportSummary> =
        combine(ordersState, _selectedReportDate) { state, selectedDate ->
            val allOrders = (state as? UiState.Success)?.data.orEmpty()
            val filtered = if (selectedDate == ALL_DATES_KEY) {
                allOrders
            } else {
                allOrders.filter { it.businessDate == selectedDate }
            }

            val totalRevenue = filtered.sumOf { it.totalAmount }
            val orderCount = filtered.size
            val totalItemsSold = filtered.sumOf { it.itemCount.toInt() }
            val averageOrderValue = if (orderCount > 0) totalRevenue / orderCount else 0.0

            val cashTotal = filtered.filter { it.paymentMethod == PaymentMethod.NAKIT.wireValue }.sumOf { it.totalAmount }
            val cardTotal = filtered.filter { it.paymentMethod == PaymentMethod.KART.wireValue }.sumOf { it.totalAmount }
            val transferTotal = filtered.filter { it.paymentMethod == PaymentMethod.HAVALE.wireValue }.sumOf { it.totalAmount }

            val productMap = linkedMapOf<String, Pair<Int, Double>>()
            filtered.forEach { order ->
                order.items.forEach { item ->
                    val prev = productMap[item.productName] ?: (0 to 0.0)
                    productMap[item.productName] =
                        (prev.first + item.quantity.toInt()) to (prev.second + item.lineTotal)
                }
            }
            val productBreakdowns = productMap.entries
                .map { ProductSalesRow(it.key, it.value.first, it.value.second) }
                .sortedByDescending { it.totalRevenue }

            val staffMap = linkedMapOf<String, Pair<Int, Double>>()
            filtered.forEach { order ->
                val actor = order.waiterUsername.ifBlank { order.closedByUsername }
                val prev = staffMap[actor] ?: (0 to 0.0)
                staffMap[actor] = (prev.first + 1) to (prev.second + order.totalAmount)
            }
            val staffBreakdowns = staffMap.entries
                .map { StaffSalesRow(it.key, it.value.first, it.value.second) }
                .sortedByDescending { it.totalRevenue }

            val dateLabel = if (selectedDate == ALL_DATES_KEY) {
                "Tüm Günler"
            } else {
                Formatters.formatBusinessDateLabel(selectedDate)
            }

            DailyReportSummary(
                dateLabel = dateLabel,
                totalRevenue = totalRevenue,
                orderCount = orderCount,
                totalItemsSold = totalItemsSold,
                averageOrderValue = averageOrderValue,
                cashTotal = cashTotal,
                cardTotal = cardTotal,
                transferTotal = transferTotal,
                productBreakdowns = productBreakdowns,
                staffBreakdowns = staffBreakdowns,
                orders = filtered
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DailyReportSummary(
                dateLabel = Formatters.formatBusinessDateLabel(Formatters.todayBusinessDate()),
                totalRevenue = 0.0,
                orderCount = 0,
                totalItemsSold = 0,
                averageOrderValue = 0.0,
                cashTotal = 0.0,
                cardTotal = 0.0,
                transferTotal = 0.0,
                productBreakdowns = emptyList(),
                staffBreakdowns = emptyList(),
                orders = emptyList()
            )
        )

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): StateFlow<UiState<T>> {
        return this
            .map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: fallbackError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun selectTable(tableId: String?) {
        _selectedTableId.value = tableId
    }

    fun selectReportDate(date: String) {
        _selectedReportDate.value = date
    }

    fun loginStaff(username: String, password: String) {
        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            if (!_seedReady.value) {
                repository.ensureSeedData(currentUserId)
                _seedReady.value = true
            }
            val result = repository.authenticateStaff(currentUserId, username, password)
            _loginLoading.value = false
            result.fold(
                onSuccess = { staff ->
                    _activeStaff.value = staff
                    _selectedTableId.value = null
                },
                onFailure = { err ->
                    _loginError.value = err.localizedMessage ?: "Giriş başarısız."
                }
            )
        }
    }

    fun logoutStaff() {
        _selectedTableId.value = null
        _activeStaff.value = null
        _loginError.value = null
    }

    fun saveTableOrder(
        table: CafeTable,
        items: List<OrderLineItem>,
        customerNote: String
    ) {
        val actor = _activeStaff.value ?: return
        viewModelScope.launch {
            val waiter = table.waiterName.ifBlank { actor.username }
            val result = repository.updateTableOrder(
                userId = currentUserId,
                table = table,
                items = items,
                waiterName = waiter,
                customerNote = customerNote
            )
            result.fold(
                onSuccess = {
                    _statusBanner.value = if (items.isEmpty()) {
                        "${table.name} boşaltıldı."
                    } else {
                        "${table.name} adisyonu güncellendi."
                    }
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Adisyon kaydedilemedi."
                }
            )
        }
    }

    fun closeTableOrder(
        table: CafeTable,
        paymentMethod: PaymentMethod,
        onClosed: (CompletedOrder) -> Unit = {}
    ) {
        val actor = _activeStaff.value ?: return
        viewModelScope.launch {
            val result = repository.closeTableAndRecordOrder(
                userId = currentUserId,
                table = table,
                paymentMethod = paymentMethod,
                closedByUsername = actor.username
            )
            result.fold(
                onSuccess = { completed ->
                    _selectedTableId.value = null
                    _statusBanner.value = "${table.name} kapatıldı (${Formatters.formatCurrency(completed.totalAmount)})."
                    onClosed(completed)
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Hesap kapatılamadı."
                }
            )
        }
    }

    fun addProduct(name: String, category: String, price: Double) {
        val actor = _activeStaff.value
        if (actor == null || !actor.isAdmin) {
            _statusBanner.value = "Ürün ekleme yetkisi yalnızca yöneticilere aittir."
            return
        }
        viewModelScope.launch {
            val currentCount = (productsState.value as? UiState.Success)?.data?.size?.toLong() ?: 0L
            val result = repository.addProduct(
                userId = currentUserId,
                name = name,
                category = category,
                price = price,
                sortOrder = currentCount + 1L,
                updatedBy = actor.username
            )
            _statusBanner.value = result.fold(
                onSuccess = { "$name menüye eklendi." },
                onFailure = { it.localizedMessage ?: "Ürün eklenemedi." }
            )
        }
    }

    fun updateProduct(
        productId: String,
        name: String,
        category: String,
        price: Double,
        available: Boolean
    ) {
        val actor = _activeStaff.value
        if (actor == null || !actor.isAdmin) {
            _statusBanner.value = "Fiyat düzenleme yetkisi yalnızca ErdiBasaran ve MustafacanYalnz içindir."
            return
        }
        viewModelScope.launch {
            val result = repository.updateProduct(
                userId = currentUserId,
                productId = productId,
                name = name,
                category = category,
                price = price,
                available = available,
                updatedBy = actor.username
            )
            _statusBanner.value = result.fold(
                onSuccess = { "$name güncellendi." },
                onFailure = { it.localizedMessage ?: "Ürün güncellenemedi." }
            )
        }
    }

    fun deleteProduct(productId: String, productName: String) {
        val actor = _activeStaff.value
        if (actor == null || !actor.isAdmin) {
            _statusBanner.value = "Ürün silme yetkisi yalnızca yöneticilere aittir."
            return
        }
        viewModelScope.launch {
            val result = repository.deleteProduct(currentUserId, productId)
            _statusBanner.value = result.fold(
                onSuccess = { "$productName silindi." },
                onFailure = { it.localizedMessage ?: "Ürün silinemedi." }
            )
        }
    }

    fun updateStaffPassword(targetUsername: String, newPassword: String) {
        val actor = _activeStaff.value
        if (actor == null || !actor.isAdmin) {
            _statusBanner.value = "Şifre değiştirme yetkisi yalnızca yöneticilere aittir."
            return
        }
        viewModelScope.launch {
            val result = repository.updateStaffPassword(currentUserId, targetUsername, newPassword)
            _statusBanner.value = result.fold(
                onSuccess = { "$targetUsername şifresi güncellendi." },
                onFailure = { it.localizedMessage ?: "Şifre güncellenemedi." }
            )
        }
    }

    companion object {
        private const val TAG = "CafeVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
        const val ALL_DATES_KEY = "ALL"
    }
}
