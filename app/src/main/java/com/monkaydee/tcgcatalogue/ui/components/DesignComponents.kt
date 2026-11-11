package com.monkaydee.tcgcatalogue.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkaydee.tcgcatalogue.R

enum class ActionStyle { PRIMARY, SECONDARY, TONAL }

/** Existing screens share the same geometry while keeping their content and callbacks. */
@Composable
fun StandardButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp), colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

@Composable
fun StandardOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp), colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

@Composable
fun StandardTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp), colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}

/** Shared action geometry, with one clear primary action per task. */
@Composable
fun AppButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, style: ActionStyle = ActionStyle.PRIMARY, enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
    val shape = RoundedCornerShape(12.dp)
    val padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val bounds = modifier.heightIn(min = 48.dp)
    when (style) {
        ActionStyle.PRIMARY -> Button(onClick, bounds, enabled, shape = shape, contentPadding = padding, content = content)
        ActionStyle.SECONDARY -> OutlinedButton(onClick, bounds, enabled, shape = shape, contentPadding = padding, content = content)
        ActionStyle.TONAL -> FilledTonalButton(onClick, bounds, enabled, shape = shape, contentPadding = padding, content = content)
    }
}

data class SelectorOption<T>(val value: T, val label: String, val enabled: Boolean = true)

/** Bounded selector: long labels never force adjacent controls off the screen. */
@Composable
fun <T> AppSelector(
    label: String, selected: T, options: List<SelectorOption<T>>, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, icon: ImageVector? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selected }?.label ?: stringResource(R.string.design_select)
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            icon?.let { Icon(it, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(selectedLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; onSelect(option.value) }, enabled = option.enabled,
                    trailingIcon = if (option.value == selected) ({ Icon(Icons.Outlined.Check, null, Modifier.size(20.dp)) }) else null,
                )
            }
        }
    }
}

/** Compact metadata; meaning always has a text label, independent of colour. */
@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(6.dp),
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Detailed evidence stays reachable without dominating the main price or result. */
@Composable
fun ExpandablePanel(title: String, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val state = stringResource(if (expanded) R.string.design_expanded else R.string.design_collapsed)
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column {
            TextButton(onClick = { expanded = !expanded; onExpandedChange(expanded) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .semantics { stateDescription = state },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}

/** Actual capture/alignment/review state; the labels remain readable on small phones. */
@Composable
fun InspectionStages(active: Int, modifier: Modifier = Modifier) {
    val labels = listOf(R.string.design_capture, R.string.design_alignment, R.string.design_inspection, R.string.design_result).map { stringResource(it) }
    val stage = active.coerceIn(labels.indices)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.design_stage, stage + 1, labels.size, labels[stage]), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, _ ->
                LinearProgressIndicator(progress = { if (index <= stage) 1f else 0f }, modifier = Modifier.weight(1f).height(4.dp),
                    color = if (index < stage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
        }
    }
}
