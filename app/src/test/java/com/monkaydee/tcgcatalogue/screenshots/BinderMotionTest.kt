package com.monkaydee.tcgcatalogue.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.ui.components.*
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w360dp-h640dp-xhdpi",application=Application::class)
class BinderMotionTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private val state=PageTurnState(0)
    private lateinit var scope: CoroutineScope
    private var count by mutableIntStateOf(3)
    private var show by mutableStateOf(true)
    private fun setup() {
        rule.mainClock.autoAdvance=false
        rule.setContent { TcgTheme {
            scope=rememberCoroutineScope()
            if (show) PageTurner(state,count,Modifier.fillMaxSize().testTag("binder_turn")) { i ->
                Column(Modifier.fillMaxSize().background(if(i%2==0)Color(0xFF33443A) else Color(0xFF263B46)).padding(24.dp)) {
                    Text("Page ${i+1}")
                    repeat(3) { r -> Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        repeat(3) { c -> Box(Modifier.weight(1f).fillMaxHeight().padding(vertical=6.dp)
                            .background(Color(0xFF82ADA4))) { Text("${r*3+c+1}",Modifier.padding(12.dp)) }
                    } } }
                }
            }
        } }
    }
    private fun save(name:String) {
        rule.waitForIdle(); val view=rule.activity.window.decorView
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun shortDragReturnsAndPageButtonsStayWithinBounds() {
        setup()
        rule.onNodeWithTag("binder_turn").performTouchInput { swipe(center,center.copy(x=center.x-30),600) }
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(0,state.page); assertEquals(0f,state.progress,0f)
        rule.runOnIdle { state.next(scope); state.next(scope) }
        rule.mainClock.advanceTimeBy(280); save("binder_curved_page")
        assertTrue(state.progress>0f && state.progress<1f)
        rule.mainClock.advanceTimeBy(1800); assertEquals(1,state.page)
        rule.runOnIdle { state.previous(scope) }; rule.mainClock.advanceTimeBy(1800)
        assertEquals(0,state.page)
        rule.runOnIdle { state.previous(scope) }; rule.mainClock.advanceTimeBy(1800)
        assertEquals(0,state.page)
    }
    @Test fun jumpOrFilterCancelsPendingTurnWithoutSkippingPages() {
        setup()
        rule.runOnIdle { state.next(scope) }; rule.mainClock.advanceTimeBy(200)
        rule.runOnIdle { state.jump(0) }; rule.mainClock.advanceTimeBy(1500)
        assertEquals(0,state.page); assertEquals(0f,state.progress,0f)
        rule.runOnIdle { state.next(scope) }; rule.mainClock.advanceTimeBy(200)
        rule.runOnIdle { count=1 }; rule.mainClock.advanceTimeBy(1500)
        assertEquals(0,state.page); assertEquals(0f,state.progress,0f)
    }
    @Test fun leavingBinderDuringTurnDoesNotLeaveItStuckOnReturn() {
        setup()
        rule.runOnIdle { state.next(scope) }; rule.mainClock.advanceTimeBy(200)
        assertTrue(state.progress > 0f)
        rule.runOnIdle { show = false }; rule.mainClock.advanceTimeBy(100)
        assertEquals(0f, state.progress, 0f)
        rule.runOnIdle { show = true }; rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { state.next(scope) }; rule.mainClock.advanceTimeBy(1800)
        assertEquals(1, state.page)
    }

}
