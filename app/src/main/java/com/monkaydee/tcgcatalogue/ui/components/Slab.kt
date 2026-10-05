package com.monkaydee.tcgcatalogue.ui.components

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
fun slabStyle(grader: String?, grade: String?, qualifier: String?): SlabStyle = when (grader) {
    "PSA" -> SlabStyle(solid(Color(0xFFF7F7F2)), Color(0xFF1A1A1A), Color(0xFFD6262E), "PSA")
    "BGS" -> when {
        qualifier == "Black Label" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF050505))), Color(0xFFE8C766), Color(0xFFD4AF37), "BECKETT")
        grade == "10" || grade == "9.5" ->
            SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF3DC8A), Color(0xFFC9A23A))), Color(0xFF2B2108), Color(0xFF7A5C12), "BECKETT")
        else -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFFE9ECEF), Color(0xFFAEB4BA))), Color(0xFF1C1F22), Color(0xFF4A5056), "BECKETT")
    }
    "CGC" -> if (qualifier == "Pristine")
        SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF4DEA0), Color(0xFFCFAE60))), Color(0xFF171717), Color.Black, "CGC")
    else SlabStyle(Brush.verticalGradient(listOf(Color(0xFFF1F2F2), Color(0xFFCBCCCE))), Color(0xFF171717), Color.Black, "CGC")
    "SGC" -> SlabStyle(solid(Color(0xFF111111)), Color.White, Color(0xFF3FB34F), "SGC")
    "TAG" -> SlabStyle(solid(Color(0xFF0D0D0D)), Color.White, Color(0xFFBDBDBD), "TAG")
    "ACE" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0E1830))), Color(0xFFF2D27A), Color(0xFFF2D27A), "ACE")
    "AOG" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFF4A148C), Color(0xFF2A0B52))), Color.White, Color(0xFFCE93D8), "AOG")
    "GSG" -> SlabStyle(Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFC79A2B))), Color(0xFF2B2108), Color(0xFF6D4C00), "GSG")
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

/**
 * A virtual slab: clear plastic case with the grading company's label on top. Small sizes show
 * only the company and grade; larger ones also the card, grade words and cert number.
 */
@Composable
fun GradedSlab(
    imageUrl: String?,
    grader: String?,
    grade: String?,
    qualifier: String?,
    title: String,
    subtitle: String,
    cert: String?,
    modifier: Modifier = Modifier,
    thumb: Boolean = false,
) {
    val style = slabStyle(grader, grade, qualifier)
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val compact = w < 140.dp
        val corner = w * 0.06f
        val plastic = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x14B0BEC5), Color(0x33FFFFFF)))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corner))
                .background(Color(0xFFDDE3E8))
                .background(plastic)
                .border(width = (w * 0.018f).coerceAtLeast(1.dp), color = Color(0xFFB8C2CA), shape = RoundedCornerShape(corner))
                .padding(w * 0.045f),
            verticalArrangement = Arrangement.spacedBy(w * 0.04f),
        ) {
            SlabLabel(style, grader, grade, qualifier, title, subtitle, cert, w, compact)
            // The card sits in its own well inside the case.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner * 0.6f))
                    .background(if (grader == "SGC") Color(0xFF101010) else Color(0x22000000))
                    .padding(w * 0.03f),
            ) {
                CardImage(imageUrl, Modifier.fillMaxWidth(), thumb = thumb)
            }
        }
    }
}

@Composable
private fun SlabLabel(
    style: SlabStyle,
    grader: String?,
    grade: String?,
    qualifier: String?,
    title: String,
    subtitle: String,
    cert: String?,
    w: Dp,
    compact: Boolean,
) {
    val fs = { fraction: Float -> (w.value * fraction).sp }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(w * 0.012f))
            .background(style.label)
            .border((w * if (grader == "PSA") 0.022f else 0.008f).coerceAtLeast(1.dp), style.accent, RoundedCornerShape(w * 0.012f))
            .padding(horizontal = w * 0.04f, vertical = w * 0.025f),
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                // Small slabs: the company code ("BGS", not "BECKETT") so the grade always fits.
                val code = grader?.takeUnless { it == "Other" } ?: "GRD"
                Text(code, color = style.accent, style = TextStyle(fontSize = fs(0.14f), fontWeight = FontWeight.Black), maxLines = 1)
                Text(
                    grade.orEmpty(),
                    color = style.text,
                    style = TextStyle(fontSize = fs(if ((grade?.length ?: 0) > 2) 0.16f else 0.2f), fontWeight = FontWeight.Black),
                    maxLines = 1,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(w * 0.016f)) {
            if (grader == "CGC") {
                Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = w * 0.015f, vertical = w * 0.008f),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CGC", color = Color.White, fontSize = fs(0.065f), fontWeight = FontWeight.Bold)
                    Text("CERTIFIED GUARANTY COMPANY", color = Color.White, fontSize = fs(0.025f), maxLines = 1)
                }
            } else if (grader == "BGS") {
                Text("BECKETT", Modifier.align(Alignment.CenterHorizontally), color = style.text,
                    style = TextStyle(fontSize = fs(0.063f), fontWeight = FontWeight.Black))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = style.text, style = TextStyle(fontSize = fs(0.052f), fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = style.text.copy(alpha = 0.8f), style = TextStyle(fontSize = fs(0.04f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (grader != "CGC" && grader != "BGS") {
                        Text(style.logo, color = style.accent, style = TextStyle(fontSize = fs(0.065f), fontWeight = FontWeight.Black), maxLines = 1)
                    }
                    cert?.let { Text(it, color = style.text.copy(alpha = 0.75f), style = TextStyle(fontSize = fs(0.038f)), maxLines = 1) }
                }
                Spacer(Modifier.width(w * 0.02f))
                Column(Modifier.width(w * 0.25f), horizontalAlignment = Alignment.End) {
                    Text(
                        gradeWords(grader, grade, qualifier),
                        color = style.text,
                        style = TextStyle(fontSize = fs(0.034f), fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(grade.orEmpty(), color = style.text, style = TextStyle(fontSize = fs(0.16f), fontWeight = FontWeight.Black, lineHeight = fs(0.17f)))
                }
            }
            }
        }
    }
}
