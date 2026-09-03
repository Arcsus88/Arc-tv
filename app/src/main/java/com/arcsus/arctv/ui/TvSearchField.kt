package com.arcsus.arctv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.arcsus.arctv.ui.theme.ArcBlue

/**
 * TV-friendly editable field. The BasicTextField stays mounted permanently and
 * flips readOnly instead of being swapped in and out — replacing the focused
 * node hands D-pad focus back to the tab row, whose tabs switch on focus and
 * yank the user to the Browse tab mid-edit. D-pad focus lands on the field
 * without opening the keyboard (a readOnly field doesn't summon the IME);
 * pressing OK unlocks it and shows the keyboard; Done/Search or focusing away
 * locks it again.
 */
@Composable
fun TvEditField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    /** Called when editing ends for any reason (Done, Search, or focus loss). */
    onDeactivate: () -> Unit = {},
) {
    var active by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun deactivate() {
        if (!active) return
        active = false
        keyboard?.hide()
        onDeactivate()
    }

    val borderColor = when {
        active -> ArcBlue
        focused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(2.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = !active,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(ArcBlue),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction(); deactivate() },
                    onSearch = { onImeAction(); deactivate() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        if (!state.isFocused) deactivate()
                    }
                    .onPreviewKeyEvent { event ->
                        val isSelect = event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        // A text field swallows the arrow keys to move its
                        // cursor -- even read-only -- so D-pad focus could land
                        // on a field and never leave it. While locked, arrows
                        // are navigation: hand them to the focus system.
                        val arrow = when (event.key) {
                            Key.DirectionUp -> FocusDirection.Up
                            Key.DirectionDown -> FocusDirection.Down
                            Key.DirectionLeft -> FocusDirection.Left
                            Key.DirectionRight -> FocusDirection.Right
                            else -> null
                        }
                        when {
                            !active && arrow != null -> {
                                if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(arrow)
                                true
                            }
                            !active && isSelect -> {
                                // Activate on KeyUp so the press that opened editing
                                // can't leak into the now-editable field as an Enter.
                                if (event.type == KeyEventType.KeyUp) {
                                    active = true
                                    keyboard?.show()
                                }
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}

/** The search box used across the app — a [TvEditField] with a search icon. */
@Composable
fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
) {
    TvEditField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = RoundedCornerShape(24.dp),
        imeAction = ImeAction.Search,
        onImeAction = onSearch,
    )
}
