package net.number42.dutchtrains.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.ClickableText
import net.number42.dutchtrains.ui.theme.AppCardBackground
import net.number42.dutchtrains.ui.theme.AppScreenBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    var keyVisible by remember { mutableStateOf(false) }
    var displayWindowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppScreenBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppScreenBackground),
                title = { Text("Settings", color = Color(0xFF1F2638)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF5E6FA8),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = AppCardBackground,
            shadowElevation = 2.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))

                Text("NS API Key", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F2638))
                Spacer(Modifier.height(4.dp))
                val apiPortalText = buildAnnotatedString {
                    append("Obtain a key at ")
                    pushStringAnnotation(tag = "URL", annotation = "https://apiportal.ns.nl")
                    withStyle(
                        style = SpanStyle(
                            color = Color(0xFF4C5FD7),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append("apiportal.ns.nl")
                    }
                    pop()
                    append(". Subscribe to the NS App API.")
                }
                ClickableText(
                    text = apiPortalText,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF67718B)),
                    onClick = { offset ->
                        apiPortalText
                            .getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()
                            ?.let { uriHandler.openUri(it.item) }
                    },
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            viewModel.onSave()
                            onNavigateBack()
                        },
                        enabled = uiState.apiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5B63E6),
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Save")
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = viewModel::onTestConnection,
                        enabled = uiState.apiKey.isNotBlank() && !uiState.isValidating,
                    ) {
                        if (uiState.isValidating) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Text("Test connection")
                        }
                    }
                }

                uiState.validationResult?.let { result ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.startsWith("Connected")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    )
                }

                if (uiState.isSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("Trips shown ahead", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F2638))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Only show departures within this time window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF67718B),
                )
                Spacer(Modifier.height(10.dp))
                ExposedDropdownMenuBox(
                    expanded = displayWindowExpanded,
                    onExpandedChange = { displayWindowExpanded = !displayWindowExpanded },
                ) {
                    OutlinedTextField(
                        value = "${uiState.displayWindowMinutes} minutes",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Display window") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = displayWindowExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    )
                    ExposedDropdownMenu(
                        expanded = displayWindowExpanded,
                        onDismissRequest = { displayWindowExpanded = false },
                    ) {
                        (30..180 step 30).forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option minutes") },
                                onClick = {
                                    viewModel.onDisplayWindowMinutesChange(option)
                                    displayWindowExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Follow Notifications", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F2638))
                Spacer(Modifier.height(8.dp))

                NotificationToggleRow(
                    label = "Platform changes",
                    checked = uiState.notifyPlatformChanges,
                    onCheckedChange = viewModel::onNotifyPlatformChanges,
                )
                NotificationToggleRow(
                    label = "Departure time",
                    checked = uiState.notifyDepartureTime,
                    onCheckedChange = viewModel::onNotifyDepartureTime,
                )
                NotificationToggleRow(
                    label = "Arrival time",
                    checked = uiState.notifyArrivalTime,
                    onCheckedChange = viewModel::onNotifyArrivalTime,
                )
                NotificationToggleRow(
                    label = "Platform arrival changes",
                    checked = uiState.notifyPlatformArrivalChanges,
                    onCheckedChange = viewModel::onNotifyPlatformArrivalChanges,
                )
                NotificationToggleRow(
                    label = "Material changes",
                    checked = uiState.notifyMaterialChanges,
                    onCheckedChange = viewModel::onNotifyMaterialChanges,
                )
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF5B63E6),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFC7CFDD),
            ),
        )
    }
}
