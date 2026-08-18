package sui.k.als.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import sui.k.als.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ALSScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(
                title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }, navigationIcon = {
                onBack?.let {
                    IconButton(onClick = it) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            stringResource(R.string.action_back),
                            Modifier.size(24.dp)
                        )
                    }
                }
            }, actions = actions, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
            )
        }, floatingActionButton = floatingAction, content = content
    )
}

@Composable
fun ALSSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 3.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ALSTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    supporting: String? = null,
    placeholder: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ALSSwitchRow(
    label: String, supporting: String? = null, checked: Boolean, onChange: (Boolean) -> Unit
) {
    Row(Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 54.dp)
        .clickable { onChange(!checked) }
        .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            supporting?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ALSChoiceField(
    label: String, value: String, options: List<String>, onSelected: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(
            painterResource(R.drawable.expand_more),
            stringResource(R.string.action_expand),
            Modifier.size(24.dp)
        )
    }
    if (open) {
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            Column {
                options.forEachIndexed { index, option ->
                    Text(
                        option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                open = false
                            }
                            .padding(vertical = 9.dp),
                        color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    if (index != options.lastIndex) HorizontalDivider()
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                open = false
            }) { Text(stringResource(R.string.action_close)) }
        })
    }
}

@Composable
fun ALSPathField(
    label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit
) {
    ALSTextField(
        label = label,
        value = value,
        modifier = modifier,
        placeholder = stringResource(R.string.qemu_absolute_path),
        onValueChange = onValueChange
    )
}

@Composable
fun ALSIconAction(
    icon: Int,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), description, Modifier.size(24.dp), tint = tint)
    }
}