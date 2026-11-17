package app.nova.assistant

import app.nova.assistant.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {

    @Test fun riskyTapsNeedApproval() {
        assertTrue(Safety.isRiskyTap("Send"))
        assertTrue(Safety.isRiskyTap("Pay now"))
        assertTrue(Safety.isRiskyTap("Delete chat"))
        assertTrue(Safety.isRiskyTap("পাঠান"))
        assertTrue(Safety.isRiskyTap("bKash"))
    }

    @Test fun ordinaryTapsDoNotNeedApproval() {
        assertFalse(Safety.isRiskyTap("Search"))
        assertFalse(Safety.isRiskyTap("Chats"))
        assertFalse(Safety.isRiskyTap("lofi music"))
    }

    @Test fun whatsappNumbers() {
        assertEquals("8801712345678", Safety.whatsappNumber("01712-345678"))
        assertEquals("8801712345678", Safety.whatsappNumber("+880 1712 345678"))
        assertEquals("8801712345678", Safety.whatsappNumber("008801712345678"))
        assertEquals("447700900123", Safety.whatsappNumber("+44 7700 900123"))
    }

    @Test fun finalStatus() {
        assertEquals(TaskStatus.COMPLETED, Safety.finalStatus(cancelled = false, error = null, lastActionFailed = false))
        assertEquals(TaskStatus.FAILED, Safety.finalStatus(cancelled = false, error = "no connection", lastActionFailed = false))
        assertEquals(TaskStatus.FAILED, Safety.finalStatus(cancelled = false, error = "", lastActionFailed = true))
        assertEquals(TaskStatus.CANCELLED, Safety.finalStatus(cancelled = true, error = "x", lastActionFailed = true))
    }
}
