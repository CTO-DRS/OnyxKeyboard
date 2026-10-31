package com.onyx.keyboard

import com.onyx.keyboard.intelligence.OtpDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F14 (PRD) — رمز التحقق المكتشف: pure detector behaviour.
 */
class OtpDetectorTest {

    @Test
    fun `bare code alone is detected`() {
        assertEquals("4821", OtpDetector.detect("4821"))
        assertEquals("482199", OtpDetector.detect(" 482199 "))
    }

    @Test
    fun `split code with space or dash is joined`() {
        assertEquals("123456", OtpDetector.detect("123 456"))
        assertEquals("123456", OtpDetector.detect("123-456"))
        assertEquals("12345678", OtpDetector.detect("1234-5678"))
    }

    @Test
    fun `keyword message yields the code`() {
        assertEquals("552210", OtpDetector.detect("رمز التحقق الخاص بك هو 552210"))
        assertEquals("778899", OtpDetector.detect("Your verification code is 778899. Do not share it."))
        assertEquals("3155", OtpDetector.detect("كود التفعيل: 3155"))
    }

    @Test
    fun `arabic keyword detection is case and form tolerant`() {
        assertEquals("903411", OtpDetector.detect("G-5522 هو رمز التحقق 903411"))
        assertEquals("246810", OtpDetector.detect("CODE: 246810"))
    }

    @Test
    fun `long text without keyword never triggers`() {
        assertNull(
            OtpDetector.detect(
                "طلبت الطلب رقم 10293 من المتجر وسيصل خلال 2026 يوم عمل قادم شكرا لك"
            )
        )
    }

    @Test
    fun `no digits is null`() {
        assertNull(OtpDetector.detect("مرحبا بك من جديد"))
        assertNull(OtpDetector.detect(""))
    }

    @Test
    fun `too short digit runs are ignored`() {
        assertNull(OtpDetector.detect("عدد 123 من القطع"))
        assertNull(OtpDetector.detect("1234567890123"))
    }

    @Test
    fun `number glued to a longer run is not a code`() {
        assertNull(OtpDetector.detect("115524201"))
        assertNull(OtpDetector.detect("هاتف 0501234567 للتواصل"))
    }

    @Test
    fun `keyword on very long text still works but is bounded`() {
        val long = "رمز التحقق " + "كلمة ".repeat(60) + " 774411"
        assertNull(long.let(OtpDetector::detect)) // > 200 chars — suppressed
    }
}
