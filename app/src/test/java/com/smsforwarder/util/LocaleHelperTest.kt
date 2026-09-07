package com.smsforwarder.util

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleHelperTest {

    @Test
    fun `test Persian locale and RTL direction`() {
        val locale = LocaleHelper.getLocale("fa")
        assertEquals("fa", locale.language)

        val direction = LocaleHelper.getLayoutDirection("fa")
        assertEquals(LayoutDirection.Rtl, direction)
    }

    @Test
    fun `test English locale and LTR direction`() {
        val locale = LocaleHelper.getLocale("en")
        assertEquals("en", locale.language)

        val direction = LocaleHelper.getLayoutDirection("en")
        assertEquals(LayoutDirection.Ltr, direction)
    }

    @Test
    fun `test System default fallback`() {
        val locale = LocaleHelper.getLocale("system")
        assertEquals(Locale.getDefault().language, locale.language)
    }
}
