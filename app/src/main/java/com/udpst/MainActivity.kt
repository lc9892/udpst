/*
 * Copyright (c) 2026, Len Ciavattone
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * UDP Speed Test for Android (Derived from OB-UDPST)
 *
 * Author                   Date            Comments
 * --------------------     ----------      ----------------------------------
 * Len Ciavattone           01/01/2026      Created
 *
 */

package com.udpst

import android.annotation.SuppressLint
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.udpst.ui.theme.UDPSTTheme
import kotlinx.coroutines.launch
import android.content.ClipboardManager
import android.content.ClipData

data class ResultRow(
    val col1: String,
    val col2: String,
    val col3: String,
    val col4: String,
    val col5: String,
    val col6: String,
    val isSummary: Boolean = false
) {
    fun getAsList(): List<String> {
        return listOf(col1, col2, col3, col4, col5, col6)
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: SpeedTestViewModel by viewModels { SpeedTestViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UDPSTTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeedTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

class SpeedTestViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeedTestViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeedTestViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val versionName = BuildConfig.VERSION_NAME
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "UDPST ($versionName), PVer: $PROTOCOL_VER",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    Text(
                        text = "UDP Speed Test for Android is compatible with the official Broadband Forum Open Broadband Linux application OB-UDPST"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Based on the BBF TR-471 specification and compatible with IETF RFC 9097"
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Note: Max bandwidth and authentication key are only required if configured on the server."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Measurement Key:")
                            val indentStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 15.sp))
                            withStyle(style = indentStyle) {
                                append("Sub-Interval = 1-second test intervals")
                            }
                            withStyle(style = indentStyle) {
                                append("OoO = Out-Of-Order datagrams")
                            }
                            withStyle(style = indentStyle) {
                                append("Dup = Duplicate datagrams")
                            }
                            withStyle(style = indentStyle) {
                                append("OWDVar = One-Way Delay Variation*")
                            }
                            val nestedIndentStyle = ParagraphStyle(textIndent = TextIndent(firstLine = 100.sp))
                            withStyle(style = nestedIndentStyle) {
                                append("...across all datagrams")
                            }
                            withStyle(style = indentStyle) {
                                append("RTTVar = Round-Trip Time Variation*")
                            }
                            withStyle(style = nestedIndentStyle) {
                                append("...sampled every $DEF_TRIAL_INT ms")
                            }
                            withStyle(style = indentStyle) {
                                append("*Delays above the minimum")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Copyright (c) 2026, Len Ciavattone"
                    )
                }
            }
        }
    }
}

//
// This composable only contains BoxWithConstraints to calculate height
// and delegates the actual layout to another composable.
// This separation avoids a confusing lint warning.
//
@Composable
fun SpeedTestScreen(
    modifier: Modifier = Modifier,
    viewModel: SpeedTestViewModel
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val lazyColumnHeight = this.maxHeight * 0.40f

        SpeedTestLayout(
            viewModel = viewModel,
            lazyColumnHeight = lazyColumnHeight
        )
    }
}

//
// This function contains the actual UI elements.
// It's no longer nested directly inside BoxWithConstraints, which resolves a warning.
//
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun SpeedTestLayout(
    viewModel: SpeedTestViewModel,
    lazyColumnHeight: Dp // Receive the calculated height as a parameter
) {
    val ip by viewModel.ip.collectAsState()
    val recentIps by viewModel.recentIps.collectAsState()
    val ipDropdownExpanded by viewModel.ipDropdownExpanded.collectAsState()
    val port by viewModel.port.collectAsState()
    val bandwidth by viewModel.bandwidth.collectAsState()
    val keystring by viewModel.keystring.collectAsState()
    val keyid by viewModel.keyid.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val status by viewModel.status.collectAsState()
    val expanded by viewModel.expanded.collectAsState()
    val selectedRateText by viewModel.selectedRateText.collectAsState()
    val metricsPoints = viewModel.metricsPoints
    val showHelpDialog by viewModel.showHelpDialog.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val header1 = listOf("Sub-", "Delivered", "Impairments", "OWDVar(ms)", "RTTVar(ms)", "L3/IP")
    val header2 = listOf("Interval", "(%)", "Loss / OoO / Dup", "Min / Avg / Max", "Min / Avg / Max", "(Mbps)")
    val colWidths = listOf(8, 12, 18, 20, 20, 6) // First and last width should match max header text

    if (showHelpDialog) {
        HelpDialog(onDismiss = viewModel::onDismissHelpDialog)
    }

    val listState = rememberLazyListState()
    val columnScrollState = rememberScrollState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(metricsPoints.size) {
        if (metricsPoints.isNotEmpty()) {
            listState.animateScrollToItem(metricsPoints.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(columnScrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UDP Speed Test (UDPST) for Android",
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary)
            )
            IconButton(onClick = { viewModel.onShowHelpDialog() }) {
                Icon(Icons.Filled.Info, contentDescription = "Help")
            }
        }

        ExposedDropdownMenuBox(
            expanded = ipDropdownExpanded,
            onExpandedChange = { viewModel.onIpDropdownExpandedChange(!ipDropdownExpanded) },
        ) {
            OutlinedTextField(
                value = ip,
                onValueChange = viewModel::onIpChange,
                label = { Text("Server Hostname or IP Address") },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = !isTesting)
                    .fillMaxWidth(),
                singleLine = true,
                enabled = !isTesting,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = ipDropdownExpanded
                    )
                }
            )
            if (recentIps.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = ipDropdownExpanded,
                    onDismissRequest = { viewModel.onIpDropdownExpandedChange(false) },
                ) {
                    recentIps.forEach { ipAddress ->
                        DropdownMenuItem(
                            text = { Text(ipAddress) },
                            onClick = {
                                viewModel.onIpChange(ipAddress)
                                viewModel.onIpDropdownExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = port,
                onValueChange = viewModel::onPortChange,
                label = { Text("Control Port") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isTesting
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = {
                    if (!isTesting) {
                        viewModel.onExpandedChange(!expanded)
                    }
                },
                modifier = Modifier.weight(2f)
            ) {
                OutlinedTextField(
                    value = selectedRateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sending Rate (Mbps)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !isTesting)
                        .fillMaxWidth(),
                    enabled = !isTesting
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { viewModel.onExpandedChange(false) }
                ) {
                    viewModel.rates.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text(rate) },
                            onClick = {
                                viewModel.onSelectedRateTextChange(rate)
                                viewModel.onSelectedRateChange(if (rate == "Auto") -1 else rate.toInt())
                                viewModel.onExpandedChange(false)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy((0).dp)) {
                val textColor = if (isTesting) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    Color.Unspecified // Use the default color
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = direction == CHTA_CREQ_TESTACTUS,
                        onClick = { viewModel.onDirectionChange(CHTA_CREQ_TESTACTUS) },
                        modifier = Modifier.size(32.dp),
                        enabled = !isTesting
                    )
                    Text("Upstream Test", style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = direction == CHTA_CREQ_TESTACTDS,
                        onClick = { viewModel.onDirectionChange(CHTA_CREQ_TESTACTDS) },
                        modifier = Modifier.size(32.dp),
                        enabled = !isTesting
                    )
                    Text("Downstream Test", style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            OutlinedTextField(
                value = bandwidth,
                onValueChange = viewModel::onBandwidthChange,
                label = { Text("Max Bandwidth (Mbps)") },
                modifier = Modifier.weight(4f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isTesting
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = keystring,
                onValueChange = viewModel::onKeystringChange,
                label = { Text("Authentication Key") },
                modifier = Modifier.weight(3.5f),
                singleLine = true,
                enabled = !isTesting
            )
            OutlinedTextField(
                value = keyid,
                onValueChange = viewModel::onKeyidChange,
                label = { Text("Key ID") },
                modifier = Modifier.weight(1.5f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isTesting
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (isTesting) {
                        viewModel.stopTest()
                    } else {
                        viewModel.startTest()
                    }
                },
                modifier = Modifier.weight(3f),
                enabled = !viewModel.isSetupPending()
            ) {
                Text(if (isTesting) "Stop / Reset" else "Start Test")
            }
            Button(
                onClick = { viewModel.clearMetrics() },
                modifier = Modifier.weight(1.5f)
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        var text = ""
                        // Loop through the list of headers directly
                        val headers = listOf(header1, header2)
                        headers.forEach { hdr ->
                            // Use mapIndexed to transform each column text into a padded string
                            val formattedRow = hdr.mapIndexed { j, columnText ->
                                val width = colWidths[j]
                                val filler = width - columnText.length
                                val suffix = filler / 2
                                val prefix = filler - suffix
                                " ".repeat(prefix) + columnText + " ".repeat(suffix)
                            }.joinToString("") // Join the formatted columns into a single line

                            text += formattedRow + "\n"
                        }
                        val metricsText = viewModel.getMetricsAsText(colWidths)
                        val textToCopy = text + metricsText
                        val clip = ClipData.newPlainText("UDPST Metrics", textToCopy)
                        clipboardManager.setPrimaryClip(clip)
                    }
                },
                modifier = Modifier.weight(1.5f)
            ) {
                Text("Copy")
            }
        }

        Text(text = status, style = MaterialTheme.typography.bodyMedium)

        val col1Weight = if (isLandscape) 1f else 1f
        val col2Weight = if (isLandscape) 1.5f else 1.5f
        val col3Weight = if (isLandscape) 2.5f else 0f
        val col4Weight = if (isLandscape) 2.5f else 0f
        val col5Weight = if (isLandscape) 2.5f else 2.5f
        val col6Weight = if (isLandscape) 1f else 1f

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val headerStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                header1[0] + "\n" + header2[0],
                modifier = Modifier.weight(col1Weight),
                style = headerStyle,
                textAlign = TextAlign.Center
            )
            Text(
                header1[1] + "\n" + header2[1],
                modifier = Modifier.weight(col2Weight),
                style = headerStyle,
                textAlign = TextAlign.Center
            )
            if (isLandscape) {
                Text(
                    header1[2] + "\n" + header2[2],
                    modifier = Modifier.weight(col3Weight),
                    style = headerStyle,
                    textAlign = TextAlign.Center
                )
                Text(
                    header1[3] + "\n" + header2[3],
                    modifier = Modifier.weight(col4Weight),
                    style = headerStyle,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                header1[4] + "\n" + header2[4],
                modifier = Modifier.weight(col5Weight),
                style = headerStyle,
                textAlign = TextAlign.Center
            )
            Text(
                header1[5] + "\n" + header2[5],
                modifier = Modifier.weight(col6Weight),
                style = headerStyle,
                textAlign = TextAlign.Center
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 1.dp),
            thickness = 1.dp,
            color = Color.Gray
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(lazyColumnHeight)
                .padding(vertical = 0.dp)
        ) {
            items(metricsPoints) { row ->
                if (row.isSummary) {
                    Text(
                        text = row.col1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Start
                    )
                } else {
                    val delivered = row.col2.toDoubleOrNull() ?: 0.0
                    val rowColor = if (delivered < 50.0) {
                        Color.Red
                    } else if (delivered < 75.0) {
                        Color(0xFFFF4500)
                    } else {
                        Color.Unspecified
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val textStyle =
                            MaterialTheme.typography.bodyMedium.copy(color = rowColor)
                        Text(
                            row.col1,
                            modifier = Modifier.weight(col1Weight),
                            style = textStyle,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            row.col2,
                            modifier = Modifier.weight(col2Weight),
                            style = textStyle,
                            textAlign = TextAlign.Center
                        )
                        if (isLandscape) {
                            Text(
                                row.col3,
                                modifier = Modifier.weight(col3Weight),
                                style = textStyle,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                row.col4,
                                modifier = Modifier.weight(col4Weight),
                                style = textStyle,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            row.col5,
                            modifier = Modifier.weight(col5Weight),
                            style = textStyle,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            row.col6,
                            modifier = Modifier.weight(col6Weight),
                            style = textStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
