package com.inkcast.android

import android.app.Application
import com.inkcast.android.ui.MainViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test

class MainViewModelCreationTest {

    @Test
    fun testMainViewModelHasApplicationConstructor() {
        val constructor = MainViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }
}
