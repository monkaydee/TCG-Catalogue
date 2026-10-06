package com.monkaydee.tcgcatalogue.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R
import com.monkaydee.tcgcatalogue.data.AppSettings
import com.monkaydee.tcgcatalogue.data.BinderPage
import com.monkaydee.tcgcatalogue.data.Look
import com.monkaydee.tcgcatalogue.data.Palette
import com.monkaydee.tcgcatalogue.data.ThemeMode
import com.monkaydee.tcgcatalogue.data.db.Game
import com.monkaydee.tcgcatalogue.data.db.OwnedCard
import com.monkaydee.tcgcatalogue.ui.OnboardingScreen
import com.monkaydee.tcgcatalogue.ui.components.EmptyIllustration
import com.monkaydee.tcgcatalogue.ui.components.EmptyKind
import com.monkaydee.tcgcatalogue.ui.components.PageTurnState
import com.monkaydee.tcgcatalogue.ui.components.PageTurner
import com.monkaydee.tcgcatalogue.ui.components.appBarColors
import com.monkaydee.tcgcatalogue.ui.screens.BinderSheet
import com.monkaydee.tcgcatalogue.ui.theme.TcgTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens on the JVM and saves them to app/build/screenshots, so visual changes can be
 * reviewed as images (CI uploads them). Card images aren't loaded (no network), so their
 * placeholders show instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class Screenshots {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun save(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun cards(n: Int, offset: Int = 0) = (1..n).map { i ->
        OwnedCard(
            id = (i + offset).toLong(), game = Game.POKEMON, cardId = "c$i", variant = "normal", variantLabel = "Normal", name = "Card $i",
            number = "$i/165", setId = "s", setName = "151", price = (i + offset) * 1.5, priceCurrency = "EUR", quantity = if (i % 4 == 0) 2 else 1,
            grader = if (i % 5 == 0) "PSA" else null, grade = if (i % 5 == 0) "10" else null,
        )
    }

    @Test fun manualCenteringGuidesApplyWithoutCreatingAnOverallGrade() {
        val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            drawColor(android.graphics.Color.LTGRAY)
            drawRect(30f, 42f, 570f, 798f, android.graphics.Paint().apply { color = android.graphics.Color.BLUE })
        }
        var side by mutableStateOf(com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap, emptyList(), null,
            com.monkaydee.tcgcatalogue.grade.Wear.Result(emptyMap()), outlineConfirmed = true))
        var applied: com.monkaydee.tcgcatalogue.grade.Centering.Result? = null
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    com.monkaydee.tcgcatalogue.ui.screens.ManualCenteringPanel(side) { applied = it; side = side.copy(centering = it, manualCentering = true) }
                }
            }
        }
        rule.onNodeWithText("Adjust centering guides").performClick()
        save("manual_centering_guides")
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        assertNotNull(applied)
        assertFalse(side.usableForGrade)
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.1f) }
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNodeWithText("Outer edge").performScrollTo().performClick()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.02f) }
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        assertEquals(12.0, requireNotNull(applied).cuts[0], 1e-5)
        assertEquals(48.0, requireNotNull(applied).left, 1e-5)
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNodeWithTag("center_main_canvas").performScrollTo().performTouchInput {
            swipe(Offset(width * 0.02f, height / 2f), Offset(width * 0.06f, height / 2f), durationMillis = 600)
        }
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        assertTrue("Dragging the outer handle must move its cut", requireNotNull(applied).cuts[0] > 12.0)
        val beforeDetail = requireNotNull(applied).cuts[0]
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNodeWithText("Outer edge").performScrollTo().performClick()
        rule.onNodeWithText("20×").performScrollTo().performClick()
        rule.onNodeWithTag("center_detail_canvas").performScrollTo().performTouchInput {
            swipe(Offset(width / 2f, height / 2f), Offset(width * 0.6f, height / 2f), durationMillis = 600)
        }
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        assertTrue("Dragging in zoom detail must move the selected outer handle", requireNotNull(applied).cuts[0] > beforeDetail)
        val savedCuts = requireNotNull(applied).cuts
        val savedLeft = requireNotNull(applied).left
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        rule.onNodeWithText("Cancel").performScrollTo().performClick()
        rule.onNodeWithText("Adjust centering guides").performClick()
        rule.onNodeWithText("Use these guides").performScrollTo().performClick()
        assertEquals(savedLeft, requireNotNull(applied).left, 1e-6)
        assertEquals(savedCuts, requireNotNull(applied).cuts)
    }

    @Test fun fullscreenCenteringShowsBothGuideSelectorsWithoutScrolling() {
        val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val side = com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap, emptyList(), null,
            com.monkaydee.tcgcatalogue.grade.Wear.Result(emptyMap()), outlineConfirmed = true)
        var applied: com.monkaydee.tcgcatalogue.grade.Centering.Result? = null
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                com.monkaydee.tcgcatalogue.ui.screens.ManualCenteringPanel(side, fullscreen = true, onApply = { applied = it })
                }
            }
        }
        rule.onNodeWithText("Outer edge").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").assertIsDisplayed().performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").performClick()
        save("fullscreen_centering_controls")
        rule.onNodeWithText("Use these guides").assertIsDisplayed().performClick()
        assertEquals(1.0, requireNotNull(applied).cuts[0], 1e-6)
        assertEquals(30.0, requireNotNull(applied).left, 1e-6)
    }

    @Test fun onePieceResultsRecoverMissingFrontAndAllowManualCenteringPotential() {
        val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val wear = com.monkaydee.tcgcatalogue.grade.Wear.Result((com.monkaydee.tcgcatalogue.grade.Wear.EDGES + com.monkaydee.tcgcatalogue.grade.Wear.CORNERS)
            .associateWith { com.monkaydee.tcgcatalogue.grade.Wear.Zone(0.0, 0.0, 0.0) })
        var front by mutableStateOf(com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap, emptyList(), null, wear, outlineConfirmed = true))
        val back = front.copy(centering = com.monkaydee.tcgcatalogue.grade.Centering.Result(50.0, 50.0, 50.0, 50.0))
        var editing by mutableStateOf(false)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                if (editing) com.monkaydee.tcgcatalogue.ui.screens.ManualCenteringPanel(front, fullscreen = true, onApply = {
                    front = front.copy(centering = it, manualCentering = true); editing = false
                }) else com.monkaydee.tcgcatalogue.ui.screens.GradeResult(front, back, Game.ONE_PIECE, {}, {}, {
                    editing = it == com.monkaydee.tcgcatalogue.ui.screens.Step.FRONT
                })
                }
            }
        }
        rule.onNodeWithText("Complete both sides for centering potential").assertIsDisplayed()
        rule.onNodeWithText("Edit front centering").performScrollTo().performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed()
        rule.onNodeWithText("Use these guides").performClick()
        rule.onNodeWithText("Within PSA 10 centering limits").assertIsDisplayed()
        assertTrue(front.usableForCentering)
        assertFalse(front.usableForGrade)
        save("onepiece_centering_potential")
    }

    @Test fun badQualityCannotShowCleanWearOrCenteringPotentialAndOffersRetake() {
        val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888)
        val side = com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap,
            listOf(com.monkaydee.tcgcatalogue.grade.PhotoCheck.Problem.BLURRY),
            com.monkaydee.tcgcatalogue.grade.Centering.Result(50.0,50.0,50.0,50.0),
            com.monkaydee.tcgcatalogue.grade.Wear.Result(emptyMap()), outlineConfirmed = true)
        var retaken: com.monkaydee.tcgcatalogue.ui.screens.Step? = null
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    com.monkaydee.tcgcatalogue.ui.screens.GradeResult(side, side, Game.ONE_PIECE, {}, {}, {}, { retaken = it })
                }
            }
        }
        rule.onNodeWithText("Within PSA 10 centering limits").assertDoesNotExist()
        rule.onNodeWithText("no visible wear detected").assertDoesNotExist()
        rule.onNodeWithText("Retake front photo").performScrollTo().performClick()
        assertEquals(com.monkaydee.tcgcatalogue.ui.screens.Step.FRONT, retaken)
    }

    @Test fun individualWearInspectionRecordsDamageAndBlocksOverallGrade() {
        val bitmap = Bitmap.createBitmap(945, 1320, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val zones = (com.monkaydee.tcgcatalogue.grade.Wear.EDGES + com.monkaydee.tcgcatalogue.grade.Wear.CORNERS)
            .associateWith { com.monkaydee.tcgcatalogue.grade.Wear.Zone(0.0, 0.0, 0.0) }
        var side by mutableStateOf(com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap, emptyList(),
            com.monkaydee.tcgcatalogue.grade.Centering.Result(50.0, 50.0, 50.0, 50.0),
            com.monkaydee.tcgcatalogue.grade.Wear.Result(zones), outlineConfirmed = true))
        assertTrue(side.usableForGrade)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        com.monkaydee.tcgcatalogue.ui.screens.WearInspectionPanel(side, "Front") { name, finding ->
                            side = side.copy(wearFindings = side.wearFindings + (name to finding))
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Inspect all four corners and four edges").performClick()
        rule.onNodeWithText("Top-left corner").performScrollTo().performClick()
        rule.onNodeWithText("Whitening / scuffing visible").performScrollTo().performClick()
        assertEquals(com.monkaydee.tcgcatalogue.grade.Wear.Finding.WHITENING, side.wearFindings["TL"])
        assertTrue(side.reportedWear)
        assertFalse(side.usableForGrade)
        rule.onNodeWithText("Done").performScrollTo().performClick()
        rule.onNodeWithText("Top edge").performScrollTo().performClick()
        rule.onNodeWithText("Chip / tear visible").performScrollTo().performClick()
        assertEquals(com.monkaydee.tcgcatalogue.grade.Wear.Finding.CHIP_OR_TEAR, side.wearFindings["T"])
        rule.onNodeWithText("Done").performScrollTo().performClick()
        save("wear_region_review")
        // Replacing or rectifying a photo must not reuse observations from the previous image.
        assertTrue(side.copy(wearFindings = emptyMap()).usableForGrade)
        assertFalse(side.copy(problems = listOf(com.monkaydee.tcgcatalogue.grade.PhotoCheck.Problem.BLURRY)).usableForWear)
    }

    private fun handoffSide(): com.monkaydee.tcgcatalogue.grade.PreGrader.Side {
        val bitmap = Bitmap.createBitmap(600, 840, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        return com.monkaydee.tcgcatalogue.grade.PreGrader.Side(bitmap, emptyList(), null,
            com.monkaydee.tcgcatalogue.grade.Wear.Result(emptyMap()), photo = bitmap,
            quad = com.monkaydee.tcgcatalogue.grade.Quad(
                com.monkaydee.tcgcatalogue.grade.Pt(1.0, 1.0), com.monkaydee.tcgcatalogue.grade.Pt(598.0, 1.0),
                com.monkaydee.tcgcatalogue.grade.Pt(598.0, 838.0), com.monkaydee.tcgcatalogue.grade.Pt(1.0, 838.0)))
    }

    @Test fun correctedFrontOutlineOpensInnerOuterEditorDirectly() {
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                com.monkaydee.tcgcatalogue.ui.screens.PreGradeFlow(null, {}, Game.ONE_PIECE, initialFront = handoffSide())
            }
        }
        rule.onNodeWithText("Adjust the four card corners").assertIsDisplayed().performClick()
        rule.onNodeWithText("Card outline · four corners").assertIsDisplayed()
        rule.onNodeWithText("Continue to centering").assertIsDisplayed().performClick()
        rule.waitUntil(15000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Inner frame")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Centering · eight guides").assertIsDisplayed()
        rule.onNodeWithText("Outer edge").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").performClick()
        rule.onNodeWithText("1× ▾").performClick()
        rule.onNodeWithText("5×").performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed()
        rule.onNodeWithTag("center_detail_canvas").performTouchInput {
            val scale = minOf(width / 189f, height / 264f)
            val offset = (width - 189 * scale) / 2
            val inner = offset + 48.25f * scale
            swipe(Offset(inner, height / 2f), Offset(inner + 8 * scale, height / 2f), 300)
        }
        rule.onNodeWithText("↔ 53.9/46.1 · ↕ 50.0/50.0").assertIsDisplayed()
        save("outline_to_inner_outer_centering")
        rule.onNodeWithText("Use these guides").assertIsDisplayed().performClick()
        rule.onNodeWithText("Next: back").performScrollTo().performClick()
        rule.onNodeWithText("Confirm outline and measure centering").assertDoesNotExist()
    }

    @Test fun confirmingBackOutlineOpensInnerOuterEditorDirectly() {
        val side = handoffSide()
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                com.monkaydee.tcgcatalogue.ui.screens.PreGradeFlow(null, {}, Game.ONE_PIECE,
                    initialFront = side.copy(outlineConfirmed = true), initialBack = side,
                    initialStep = com.monkaydee.tcgcatalogue.ui.screens.Step.BACK)
            }
        }
        rule.onNodeWithText("Confirm outline and measure centering").assertIsDisplayed().performClick()
        rule.onNodeWithText("Centering · eight guides").assertIsDisplayed()
        rule.onNodeWithText("Outer edge").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("+1 image pixel").performClick()
        rule.onNodeWithText("Use these guides").assertIsDisplayed().performClick()
        rule.onNodeWithText("Show result").performScrollTo().performClick()
        rule.onNodeWithText("Edit back centering").performScrollTo().assertIsDisplayed()
    }

    private fun checkCenteringReviewKeepsCalibrationOnlyWhenUnchanged(changeInner: Boolean) {
        val source = handoffSide()
        val zones = (com.monkaydee.tcgcatalogue.grade.Wear.EDGES + com.monkaydee.tcgcatalogue.grade.Wear.CORNERS)
            .associateWith { com.monkaydee.tcgcatalogue.grade.Wear.Zone(0.0, 0.0, 0.0) }
        val side = source.copy(centering = com.monkaydee.tcgcatalogue.grade.Centering.Result(30.0, 30.0, 42.0, 42.0),
            wear = com.monkaydee.tcgcatalogue.grade.Wear.Result(zones))
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                com.monkaydee.tcgcatalogue.ui.screens.PreGradeFlow(null, {}, Game.POKEMON,
                    initialFront = side, initialBack = side.copy(outlineConfirmed = true))
            }
        }
        rule.onNodeWithText("Confirm outline and measure centering").performClick()
        rule.onNodeWithText("Inner frame").assertIsDisplayed()
        if (changeInner) rule.onNodeWithContentDescription("+1 image pixel").performClick()
        rule.onNodeWithText("Use these guides").performClick()
        rule.onNodeWithText("Next: back").performScrollTo().performClick()
        rule.onNodeWithText("Show result").performScrollTo().performClick()
        if (changeInner) rule.onNodeWithText("Experimental Pokémon photo estimate").assertDoesNotExist()
        else rule.onNodeWithText("Experimental Pokémon photo estimate").performScrollTo().assertIsDisplayed()
    }

    @Test fun unchangedGuideReviewPreservesAutomaticModelInputs() = checkCenteringReviewKeepsCalibrationOnlyWhenUnchanged(false)
    @Test fun movedInnerGuideRemainsOutsideCalibratedModelInputs() = checkCenteringReviewKeepsCalibrationOnlyWhenUnchanged(true)

    @Test fun binderPages() {
        var grid by mutableStateOf(3)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(8.dp)) {
                    BinderSheet(BinderPage(cards(grid * grid - 2), "Scarlet & Violet 151"), grid, AppSettings(), {})
                }
            }
        }
        save("binder_3x3")
        rule.runOnUiThread { grid = 9 }
        save("binder_9x9")
    }

    @Test fun gradingCompanySlabs() {
        var spec by mutableStateOf(Triple("PSA", "10", null as String?))
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${spec.first} ${spec.second} ${spec.third.orEmpty()}", color = Color.White)
                    com.monkaydee.tcgcatalogue.ui.components.GradedSlab(
                        null, spec.first, spec.second, spec.third, "Flareon · English", "Jungle #3/64", "74004211",
                        Modifier.fillMaxWidth(0.75f),
                    )
                    com.monkaydee.tcgcatalogue.ui.components.GradedSlab(
                        null, spec.first, spec.second, spec.third, "Flareon", "Jungle #3", "74004211",
                        Modifier.size(90.dp, 160.dp), thumb = true,
                    )
                }
            }
        }
        for (s in listOf(Triple("PSA", "10", null), Triple("BGS", "9.5", null), Triple("BGS", "10", null),
            Triple("BGS", "10", "Black Label"), Triple("CGC", "10", null), Triple("CGC", "10", "Pristine"), Triple("SGC", "10", null))) {
            rule.runOnUiThread { spec = s }
            save("slab_${s.first}_${s.second}_${s.third ?: "standard"}")
        }
    }

    @Test fun binderPageTurn() {
        val state = PageTurnState(1)
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = 32.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
                    PageTurner(state, 3, Modifier.fillMaxSize(), pageBack = { BinderSheet(null, 3, AppSettings(), {}) }) { i ->
                        BinderSheet(BinderPage(cards(9 - i * 3, i * 10), "Page ${i + 1}"), 3, AppSettings(), {})
                    }
                }
            }
        }
        for (t in listOf(0.25f, 0.45f)) {
            rule.runOnUiThread { state.progress = t }
            save("binder_turn_${(t * 100).toInt()}")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun themes() {
        var look by mutableStateOf(Look())
        rule.setContent { TcgTheme(look) { Sample() } }
        val cases = listOf(
            "theme_indigo_light" to Look(ThemeMode.LIGHT, Palette.INDIGO),
            "theme_indigo_dark" to Look(ThemeMode.DARK, Palette.INDIGO),
            "theme_forest_light" to Look(ThemeMode.LIGHT, Palette.FOREST),
            "theme_sunset_dark" to Look(ThemeMode.DARK, Palette.SUNSET),
        )
        for ((name, l) in cases) {
            rule.runOnUiThread { look = l }
            save(name)
        }
    }

    @Test fun onboarding() {
        rule.setContent { TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) { OnboardingScreen {} } }
        save("onboarding")
    }

    @Test fun emptyCollection() {
        rule.setContent {
            TcgTheme(Look(ThemeMode.LIGHT, Palette.INDIGO)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    EmptyIllustration(EmptyKind.COLLECTION, "Your collection is empty", "Scan a card to start.")
                }
            }
        }
        save("empty_collection")
    }

    @Test fun cardPageParts() {
        val today = java.time.LocalDate.now().toEpochDay()
        val history = (0 until 30).map { d ->
            com.monkaydee.tcgcatalogue.data.db.PriceHistory(1, today - 29 + d, 40.0 + 8 * kotlin.math.sin(d / 4.0) + d * 0.6, "EUR")
        }
        rule.setContent {
            TcgTheme(Look(ThemeMode.DARK, Palette.INDIGO)) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val tilt = androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.geometry.Offset(0.4f, -0.3f)) }
                    com.monkaydee.tcgcatalogue.ui.components.HoloCard(tilt, Modifier.size(180.dp, 251.dp)) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF3949AB)))
                    }
                    com.monkaydee.tcgcatalogue.ui.components.PriceHistoryCard(history, AppSettings(currency = "EUR"), Modifier.fillMaxWidth())
                }
            }
        }
        save("card_page_parts")
    }

    @Test fun launcherIcon() {
        rule.setContent {
            Box(Modifier.size(192.dp).clip(RoundedCornerShape(48.dp)).background(colorResource(R.color.cardnavo_navy))) {
                Image(painterResource(R.drawable.cardnavo_foreground), null, Modifier.fillMaxSize())
            }
        }
        save("launcher_icon")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sample() {
        Scaffold(topBar = { TopAppBar(title = { Text("TCG Catalogue") }, colors = appBarColors()) }) { p ->
            Column(Modifier.padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Portfolio value", style = MaterialTheme.typography.labelLarge)
                        Text("€1,234.56", style = MaterialTheme.typography.displaySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(true, {}, { Text("All") })
                    FilterChip(false, {}, { Text("Pokémon") })
                    FilterChip(false, {}, { Text("One Piece") })
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Scarlet & Violet 151", style = MaterialTheme.typography.titleSmall)
                        Text("Pokémon · 42/165 cards", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { 0.3f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({}) { Text("Scan a card") }
                    OutlinedButton({}) { Text("Import") }
                }
            }
        }
    }
}
