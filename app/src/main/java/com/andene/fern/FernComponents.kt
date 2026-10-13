package com.andene.fern

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize

/*
 * Fern's controls (roadmap item 336): Material's, with every size on the CLAUDE.md §7 scale.
 * Material's own sizes are off it - a text field 56 dp tall, a button 40, a slider thumb 20, a
 * switch 52 x 32 - and the theme can't change them, so UI code uses these instead of calling
 * Material's directly. Each keeps Material's parameters, order and semantics, so a call site
 * changes only by its name.
 */

/** Material's `TextButton`, 48 dp tall (Material's is 40) - the touch target, on the scale. */
@Composable
fun FernTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = FernSize.touchTarget), enabled = enabled, content = content)
}

/**
 * Material's `Slider` with a 16 dp thumb (Material's is 20, which §7.1 rounds to 16). The
 * slider's own height stays its 48 dp touch area, so the smaller thumb is no harder to grab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                thumbSize = DpSize(FernScale.s16, FernScale.s16),
            )
        },
    )
}

/**
 * A switch drawn on the scale: a 48 x 24 dp track and a 16 dp thumb (Material's is 52 x 32
 * with a 16-24 dp thumb), inside the 48 dp touch target. Announced as a switch with its state,
 * like Material's.
 */
@Composable
fun FernSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val disabledAlpha = if (enabled) 1f else DISABLED_ALPHA
    val track = (if (checked) colors.primary else colors.surfaceVariant).copy(alpha = disabledAlpha)
    val thumb = (if (checked) colors.onPrimary else colors.outline).copy(alpha = disabledAlpha)
    val thumbOffset by animateDpAsState(if (checked) FernScale.s24 + FernScale.s4 else FernScale.s4, label = "switch thumb")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier.minimumInteractiveComponentSize().then(toggle), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = FernScale.s48, height = FernScale.s24)
                .background(track, CircleShape)
                .border(FernSize.hairline, colors.outline.copy(alpha = disabledAlpha), CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.padding(start = thumbOffset).size(FernScale.s16).background(thumb, CircleShape))
        }
    }
}

/**
 * Material's `OutlinedTextField`, 48 dp tall for one line (Material's is 56): the same
 * decoration with 12 dp of vertical padding around a 24 sp line instead of 16.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val textColor = when {
        !enabled -> scheme.onSurface.copy(alpha = DISABLED_ALPHA)
        isError -> scheme.error
        else -> scheme.onSurface
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = FernSize.touchTarget),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                colors = colors,
                contentPadding = PaddingValues(horizontal = FernSpace.lg, vertical = FernSpace.md),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(enabled, isError, interactionSource, colors, shape)
                },
            )
        },
    )
}

/**
 * Material's `RangeSlider` with 16 dp thumbs, for the same reason as [FernSlider].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FernRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val startInteraction = remember { MutableInteractionSource() }
    val endInteraction = remember { MutableInteractionSource() }
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        startInteractionSource = startInteraction,
        endInteractionSource = endInteraction,
        startThumb = { SliderDefaults.Thumb(interactionSource = startInteraction, enabled = enabled, thumbSize = DpSize(FernScale.s16, FernScale.s16)) },
        endThumb = { SliderDefaults.Thumb(interactionSource = endInteraction, enabled = enabled, thumbSize = DpSize(FernScale.s16, FernScale.s16)) },
    )
}

/**
 * A menu row for Material's `DropdownMenu`, replacing `DropdownMenuItem`: 48 dp tall and at
 * least 128 dp wide (Material's minimum is 112, off the scale), 12 dp side padding, Material's
 * menu text style and colours. The leading and trailing slots hold 24 dp icons.
 */
@Composable
fun FernMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    Row(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .sizeIn(minWidth = FernScale.s128, minHeight = FernSize.touchTarget)
            .padding(horizontal = FernSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FernSpace.md),
    ) {
        CompositionLocalProvider(LocalContentColor provides color, LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            leadingIcon?.invoke()
            Box(Modifier.weight(1f, fill = false)) { text() }
            trailingIcon?.invoke()
        }
    }
}

/**
 * A list row, replacing Material's `ListItem`: 48 dp tall with one line, 64 with a supporting
 * line (Material's are 56 and 72, off the scale), 16 dp side and 8 dp vertical padding. Callers
 * add `clickable` through [modifier], as with `ListItem`.
 */
@Composable
fun FernListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supportingContent == null) FernSize.touchTarget else FernScale.s64)
            .padding(horizontal = FernSpace.lg, vertical = FernSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FernSpace.lg),
    ) {
        leadingContent?.invoke()
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                headlineContent()
            }
            if (supportingContent != null) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant, LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    supportingContent()
                }
            }
        }
        if (trailingContent != null) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant, LocalTextStyle provides MaterialTheme.typography.labelSmall) {
                trailingContent()
            }
        }
    }
}

/**
 * Material's `AlertDialog` on the scale: 272 to 544 dp wide (Material's 280 and 560 are off it;
 * §7.2 gives 256 + 16 or 256 + 32 for 280 and 512 + 32 or 512 + 64 for 560, ties broken toward
 * the narrower so a phone in portrait keeps its margins), 24 dp padding, 16 dp between title,
 * text and buttons. Same slots and order as Material's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FernAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        Surface(
            modifier = Modifier.widthIn(min = FernScale.s256 + FernScale.s16, max = FernScale.s512 + FernScale.s32),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = FernScale.s6,
        ) {
            Column(Modifier.padding(FernSpace.xl), verticalArrangement = Arrangement.spacedBy(FernSpace.lg)) {
                if (title != null) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.headlineSmall) { title() }
                }
                if (text != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) { text() }
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(FernSpace.sm)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/** Material's alpha for disabled content. */
private const val DISABLED_ALPHA = 0.38f
