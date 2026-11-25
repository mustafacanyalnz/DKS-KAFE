package com.example

import com.example.data.CafeTable
import com.example.data.OrderLineItem
import com.example.data.PaymentMethod
import com.example.data.StaffRole
import com.example.security.PasswordHasher
import com.example.util.Formatters
import com.example.util.WhatsAppShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeLogicTest {

    @Test
    fun initialStaffCredentials_containRequestedUsersAndRoles() {
        val users = PasswordHasher.INITIAL_STAFF_CREDENTIALS.associateBy { it.username }
        assertEquals("ADMIN", users["ErdiBasaran"]?.role)
        assertEquals("ADMIN", users["MustafacanYalnz"]?.role)
        assertEquals("WAITER", users["Garson"]?.role)
        assertEquals(3, users.size)
    }

    @Test
    fun enums_deserializeSafelyWithFallbacks() {
        assertEquals(StaffRole.ADMIN, StaffRole.fromRaw("ADMIN"))
        assertEquals(StaffRole.WAITER, StaffRole.fromRaw("UNKNOWN_ROLE"))
        assertEquals(PaymentMethod.KART, PaymentMethod.fromRaw("KART"))
        assertEquals(PaymentMethod.NAKIT, PaymentMethod.fromRaw("INVALID"))
    }

    @Test
    fun whatsAppReceipt_formatsDksSahneKafeAndItems() {
        val table = CafeTable(
            id = "table_4",
            tableNumber = 4L,
            name = "Masa 4",
            occupied = true,
            waiterName = "Garson",
            items = listOf(
                OrderLineItem(
                    productId = "prod_cay",
                    productName = "Çay",
                    unitPrice = 25.0,
                    quantity = 2L,
                    lineTotal = 50.0
                )
            ),
            totalAmount = 50.0,
            openedAtMillis = 1_700_000_000_000L
        )
        val receipt = WhatsAppShareHelper.buildOpenTableReceipt(table, "Garson")
        assertTrue(receipt.contains("DKS SAHNE KAFE"))
        assertTrue(receipt.contains("Masa 4"))
        assertTrue(receipt.contains("2x Çay"))
        assertTrue(receipt.contains("50,00"))
    }

    @Test
    fun formatters_formatTurkishCurrency() {
        assertEquals("25,00 ₺", Formatters.formatCurrency(25.0))
        assertEquals("25 ₺", Formatters.formatCompactCurrency(25.0))
    }
}
