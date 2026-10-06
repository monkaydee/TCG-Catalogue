package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monkaydee.tcgcatalogue.data.db.OwnedCard

/** Colours of a grading company's label. */
data class SlabStyle(val label: Brush, val text: Color, val accent: Color, val logo: String)

private fun solid(c: Color) = Brush.verticalGradient(listOf(c, c))

/** Company label palettes. CGC uses the current black/silver label, gold for Pristine. */
fun slabStyle(grader: String?, grade: String?, qualifier: String?): SlabStyle = when (grader?.trim()?.uppercase()) {
    "PSA" -> SlabStyle(solid(Color(0xFFF7F7F2)), Color(0xFF1A1A1A), Color(0xFFD6262E), "PSA")
    "BGS" -> when {
        qualifier == "Black Label" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF050505))), Color(0xFFF5F5F5), Color(0xFFE2C46F), "BECKETT")
        grade == "10" || grade == "9.5" ->
            SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF3DC8A), Color(0xFFC9A23A))), Color(0xFF2B2108), Color(0xFF7A5C12), "BECKETT")
        else -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFFE9ECEF), Color(0xFFAEB4BA))), Color(0xFF1C1F22), Color(0xFF4A5056), "BECKETT")
    }
    "CGC" -> if (qualifier == "Perfect")
        SlabStyle(Brush.verticalGradient(listOf(Color(0xFFD9EAF8), Color(0xFF9FBEDA))), Color(0xFF142231), Color(0xFF152E4E), "CGC")
    else if (qualifier == "Pristine")
        SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF4DEA0), Color(0xFFCFAE60))), Color(0xFF171717), Color.Black, "CGC")
    else SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF5F5F3), Color(0xFFE1E3E4))), Color(0xFF171717), Color.Black, "CGC")
    "SGC" -> SlabStyle(solid(Color(0xFFF8F8F5)), Color(0xFF171717), Color(0xFF24683A), "SGC")
    "TAG" -> SlabStyle(Brush.linearGradient(listOf(Color(0xCCCFDBE2), Color(0x99E7EDF1))), Color(0xFF2F3C43), Color(0xFF526976), "TAG")
    "ACE" -> SlabStyle(solid(Color(0xFFF9F9F8)), Color(0xFF171717), Color(0xFFDD2839), "ACE")
    "AOG" -> SlabStyle(solid(Color(0xFFF1F2F3)), Color(0xFF1B2025), Color(0xFF292E35), "AOG")
    "GSG" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF2DFA7), Color(0xFFD0B66D))), Color(0xFF2B2108), Color(0xFF6D4C00), "GSG")
    "PI" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF00695C), Color(0xFF003D33))), Color.White, Color(0xFF80CBC4), "PI")
    else -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF616161), Color(0xFF353535))), Color.White, Color(0xFFBDBDBD), grader?.takeUnless { it == "Other" } ?: "GRADED")
}

/** The words printed next to the grade, in each company's own terms. */
fun gradeWords(grader: String?, grade: String?, qualifier: String?): String {
    val g = grade?.toDoubleOrNull() ?: return ""
    if (qualifier == "Black Label") return "PRISTINE"
    if (qualifier == "Pristine") return "PRISTINE"
    if (qualifier == "Perfect") return "PERFECT"
    return when (grader) {
        "PSA" -> when {
            g >= 10 -> "GEM MT"
            g >= 9 -> "MINT"
            g >= 8 -> if (g > 8) "NM-MT+" else "NM-MT"
            g >= 7 -> if (g > 7) "NM+" else "NM"
            g >= 6 -> "EX-MT"
            g >= 5 -> "EX"
            g >= 4 -> "VG-EX"
            g >= 3 -> "VG"
            g >= 2 -> "GOOD"
            else -> "PR"
        }
        "BGS" -> when {
            g >= 10 -> "PRISTINE"
            g >= 9.5 -> "GEM MINT"
            g >= 9 -> "MINT"
            g >= 8 -> if (g > 8) "NM-MT+" else "NM-MT"
            g >= 7 -> if (g > 7) "NM+" else "NM"
            else -> "EX"
        }
        else -> when {
            g >= 10 -> "GEM MINT"
            g >= 9.5 -> "MINT+"
            g >= 9 -> "MINT"
            g >= 8 -> if (g > 8) "NM/MINT+" else "NM/MINT"
            g >= 7 -> "NEAR MINT"
            g >= 6 -> "EX/NM"
            g >= 5 -> "EXCELLENT"
            else -> "VG"
        }
    }
}

/** A graded card drawn inside a slab, or the plain card when [card] isn't graded. */
@Composable
fun CardOrSlab(card: OwnedCard, modifier: Modifier = Modifier, thumb: Boolean = false) {
    if (card.graded) {
        GradedSlab(
            imageUrl = card.imageUrl,
            grader = card.grader,
            grade = card.grade,
            qualifier = card.gradeQualifier,
            title = card.name,
            subtitle = "${card.setName} #${card.number.substringBefore('/')}",
            cert = card.certNumber,
            modifier = modifier,
            thumb = thumb,
        )
    } else {
        CardImage(card.imageUrl, modifier, thumb = thumb)
    }
}

/** Shared case proportions for binder pockets, thumbnails, card pages and add/import previews. */
const val SLAB_ASPECT_RATIO = 0.64f

/** A display of the user's recorded grade. No certificate, security mark or subgrade is invented. */
@Composable
fun GradedSlab(
    imageUrl: String?, grader: String?, grade: String?, qualifier: String?, title: String,
    subtitle: String, cert: String?, modifier: Modifier = Modifier, thumb: Boolean = false,
) {
    val company = canonicalGrader(grader)
    val style = slabStyle(company, grade, qualifier)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = if (maxHeight.value.isFinite()) minOf(maxWidth, maxHeight * SLAB_ASPECT_RATIO) else maxWidth
        val radius = w * 0.075f
        val caseShape = RoundedCornerShape(radius)
        Column(
            Modifier.width(w).aspectRatio(SLAB_ASPECT_RATIO)
                .semantics { contentDescription = listOfNotNull(title, company, grade, qualifier).joinToString(" · ") }
                .clip(caseShape)
                .background(Brush.linearGradient(listOf(Color(0x997C919E), Color(0x66EAF6FC), Color(0x55828E97), Color(0x88D7E4ED))))
                .border((w * 0.018f).coerceAtLeast(0.7.dp), Color(0x99DEEAF0), caseShape)
                .drawWithCache {
                    val seam = size.width * 0.031f
                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(Color(0x99FFFFFF), topLeft = Offset(seam, seam),
                            size = Size(size.width - seam * 2, size.height - seam * 2),
                            cornerRadius = CornerRadius(size.width * 0.045f), style = Stroke(size.width * 0.005f))
                        drawLine(Color(0x66FFFFFF), Offset(size.width * 0.10f, size.height * 0.988f),
                            Offset(size.width * 0.90f, size.height * 0.988f), size.width * 0.005f)
                    }
                }
                .padding(horizontal = w * 0.047f, vertical = w * 0.049f),
            verticalArrangement = Arrangement.spacedBy(w * 0.034f),
        ) {
            SlabLabel(style, company, grade, qualifier, title, subtitle, cert, w)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(w * 0.033f))
                    .background(if (company == "SGC") Color(0xFF08090A) else Color(0x332A3740))
                    .border((w * 0.009f).coerceAtLeast(0.4.dp), Color(0x668A9CA9), RoundedCornerShape(w * 0.033f))
                    .padding(w * if (company == "SGC") 0.045f else 0.022f),
                contentAlignment = Alignment.Center,
            ) {
                CardImage(imageUrl, Modifier.fillMaxSize(), thumb = thumb)
            }
        }
    }
}

private fun canonicalGrader(grader: String?): String = when (val code = grader?.trim()?.uppercase()) {
    "BECKETT" -> "BGS"
    null, "", "OTHER" -> "GRADED"
    else -> code
}

@Composable
private fun LabelText(value: String, color: Color, size: androidx.compose.ui.unit.TextUnit,
                      modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Medium,
                      family: FontFamily = FontFamily.SansSerif) {
    Text(value, modifier, color = color,
        style = TextStyle(fontFamily = family, fontSize = size, fontWeight = weight, lineHeight = size * 1.1f),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SlabLabel(style: SlabStyle, grader: String, grade: String?, qualifier: String?,
                      title: String, subtitle: String, cert: String?, w: Dp) {
    val small = w < 90.dp
    val fs = { fraction: Float -> (w.value * fraction).sp }
    val labelShape = RoundedCornerShape(w * 0.007f)
    Column(
        Modifier.fillMaxWidth().height(w * 0.26f).clip(labelShape).background(style.label)
            .border((w * if (grader == "PSA") 0.014f else 0.004f).coerceAtLeast(0.3.dp), style.accent, labelShape),
    ) {
        if (grader in setOf("CGC", "ACE", "AOG")) {
            Row(Modifier.fillMaxWidth().height(w * 0.055f)
                .background(if (grader == "CGC") Color.Black else style.accent)
                .padding(horizontal = w * 0.018f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LabelText(grader, Color.White, fs(0.036f), weight = FontWeight.Black)
                if (!small) LabelText(when (grader) {"CGC" -> "TRADING CARDS"; "AOG" -> "ABSOLUTE OBJECTIVE"; else -> "GRADING"},
                    Color.White, fs(0.023f), weight = FontWeight.Bold)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = w * 0.022f, vertical = w * 0.012f),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (grader !in setOf("CGC", "ACE", "AOG")) {
                    LabelText(if (small && grader == "BGS") "BGS" else style.logo, if (grader == "PSA") Color(0xFF184A80) else style.accent,
                        fs(if (small) 0.095f else 0.064f), weight = FontWeight.Black,
                        family = if (grader == "BGS") FontFamily.Serif else FontFamily.SansSerif)
                }
                if (!small) {
                    LabelText(title.uppercase(), style.text, fs(0.040f), weight = FontWeight.Bold)
                    LabelText(subtitle.uppercase(), style.text.copy(alpha = 0.85f), fs(0.033f))
                    cert?.takeIf { it.isNotBlank() }?.let {
                        LabelText(it, style.text.copy(alpha = 0.75f), fs(0.029f), family = FontFamily.Monospace)
                    }
                } else if (grader in setOf("CGC", "ACE", "AOG")) {
                    LabelText(qualifier ?: gradeWords(grader, grade, qualifier), style.text, fs(0.041f), weight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(w * 0.012f))
            Column(Modifier.width(w * 0.24f)
                .then(if (grader in setOf("CGC", "ACE")) Modifier.background(if (grader == "CGC") Color.Black else style.accent, RoundedCornerShape(w * 0.009f)) else Modifier)
                .padding(vertical = w * 0.005f), horizontalAlignment = Alignment.CenterHorizontally) {
                val ink = if (grader in setOf("CGC", "ACE")) Color.White else style.text
                if (!small) LabelText(gradeWords(grader, grade, qualifier), ink, fs(0.025f), weight = FontWeight.Bold)
                LabelText(grade?.takeIf { it.isNotBlank() } ?: "—", ink,
                    fs(if ((grade?.length ?: 0) > 4) 0.065f else if ((grade?.length ?: 0) > 2) 0.115f else 0.15f), weight = FontWeight.Black)
            }
        }
    }
}
