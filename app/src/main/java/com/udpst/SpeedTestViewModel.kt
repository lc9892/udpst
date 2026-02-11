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

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val recentIpAddressesRepository = RecentIpAddressesRepository(application)
    private val udpManager = UdpManager()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private val _showHelpDialog = MutableStateFlow(false)
    val showHelpDialog = _showHelpDialog.asStateFlow()

    fun onShowHelpDialog() {
        _showHelpDialog.value = true
    }

    fun onDismissHelpDialog() {
        _showHelpDialog.value = false
    }

    private val _ip = MutableStateFlow("")
    val ip = _ip.asStateFlow()

    private val _recentIps = MutableStateFlow<List<String>>(emptyList())
    val recentIps = _recentIps.asStateFlow()

    private val _ipDropdownExpanded = MutableStateFlow(false)
    val ipDropdownExpanded = _ipDropdownExpanded.asStateFlow()

    fun onIpDropdownExpandedChange(expanded: Boolean) {
        _ipDropdownExpanded.value = expanded
    }

    private val _port = MutableStateFlow("")
    val port = _port.asStateFlow()

    private val _bandwidth = MutableStateFlow("")
    val bandwidth = _bandwidth.asStateFlow()

    private val _keystring = MutableStateFlow("")
    val keystring = _keystring.asStateFlow()

    private val _keyid = MutableStateFlow("")
    val keyid = _keyid.asStateFlow()

    private val _direction = MutableStateFlow(0)
    val direction = _direction.asStateFlow()

    private val _selectedRate = MutableStateFlow(-1)

    val rates = listOf("Auto", "0", "1", "10", "50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "1000")

    private val _selectedRateText = MutableStateFlow(rates[0])
    val selectedRateText = _selectedRateText.asStateFlow()

    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()

    private val _status = MutableStateFlow("Ready...")
    val status = _status.asStateFlow()

    val metricsPoints = mutableStateListOf<ResultRow>()

    fun clearMetrics() {
        metricsPoints.clear()
    }

    fun getMetricsAsText(colWidths: List<Int>): String {
        var text = ""
        for (row in metricsPoints) {
            if (row.isSummary) {
                text += row.col1
            } else {
                // Loop through the columns in each row
                row.getAsList().forEachIndexed { index, columnValue ->
                    val width = colWidths.getOrElse(index) { columnValue.length }
                    val filler = width - columnValue.length
                    val suffix = filler / 2
                    val prefix = filler - suffix
                    text += " ".repeat(prefix) + columnValue + " ".repeat(suffix)
                }
            }
            text += "\n"
        }
        return text
    }

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _ip.value = settings[PreferencesKeys.IP] ?: ""
            _port.value = settings[PreferencesKeys.PORT] ?: DEF_CONTROL_PORT.toString()
            _bandwidth.value = settings[PreferencesKeys.BANDWIDTH] ?: ""
            _keystring.value = settings[PreferencesKeys.KEYSTRING] ?: ""
            _keyid.value = settings[PreferencesKeys.KEYID] ?: "0"
            _direction.value = settings[PreferencesKeys.DIRECTION] ?: CHTA_CREQ_TESTACTDS
            _selectedRate.value = settings[PreferencesKeys.SELECTED_RATE] ?: -1
            _selectedRateText.value = settings[PreferencesKeys.SELECTED_RATE_TEXT] ?: rates[0]
        }
        recentIpAddressesRepository.recentIpsFlow
            .onEach { ips -> _recentIps.value = ips }
            .launchIn(viewModelScope)
    }

    private fun saveSettings() {
        viewModelScope.launch {
            settingsRepository.saveSettings(
                _ip.value,
                _port.value,
                _bandwidth.value,
                _keystring.value,
                _keyid.value,
                _direction.value,
                _selectedRate.value,
                _selectedRateText.value
            )
        }
    }

    fun onIpChange(value: String) {
        _ip.value = value
        saveSettings()
    }

    fun onPortChange(value: String) {
        if (value.all { it.isDigit() }) {
            _port.value = value
        }
        saveSettings()
    }

    fun onBandwidthChange(value: String) {
        if (value.all { it.isDigit() }) {
            _bandwidth.value = value
        }
        saveSettings()
    }

    fun onKeystringChange(value: String) {
        _keystring.value = value
        saveSettings()
    }

    fun onKeyidChange(value: String) {
        if (value.all { it.isDigit() }) {
            _keyid.value = value
        }
        saveSettings()
    }

    fun onDirectionChange(value: Int) {
        _direction.value = value
        saveSettings()
    }

    fun onExpandedChange(value: Boolean) {
        _expanded.value = value
    }

    fun onSelectedRateTextChange(value: String) {
        _selectedRateText.value = value
        saveSettings()
    }

    fun onSelectedRateChange(value: Int) {
        _selectedRate.value = value
        saveSettings()
    }

    fun startTest() {
        _ipDropdownExpanded.value = false
        _isTesting.value = true
        viewModelScope.launch {
            udpManager.start(
                _ip.value,
                _port.value.toIntOrNull() ?: 0,
                _direction.value,
                _bandwidth.value.toIntOrNull() ?: 0,
                _selectedRate.value,
                _keystring.value,
                _keyid.value.toIntOrNull() ?: 0,
                object : UdpManager.UdpManagerCallback {
                    override fun onDataExchanged(s1: String, s2: String, s3: String, s4: String, s5: String, s6: String) {
                        if (metricsPoints.size >= 150) {
                            metricsPoints.removeAt(0)
                        }
                        metricsPoints.add(ResultRow(s1, s2, s3, s4, s5, s6))
                    }

                    override fun onSummaryExchanged(s1: String) {
                        metricsPoints.add(ResultRow(s1, "", "", "", "", "", isSummary = true))
                    }

                    override fun onSetTesting(state: Boolean) {
                        _isTesting.value = state
                    }

                    override fun onStatus(message: String) {
                        _status.value = message
                    }

                    override fun onStoreIp() {
                        if (_ip.value.isNotBlank()) {
                            viewModelScope.launch {
                                recentIpAddressesRepository.addIp(_ip.value)
                            }
                        }
                    }
                })
        }
    }

    fun stopTest() {
        if (udpManager.isRunning.get()) {
            metricsPoints.add(ResultRow("Test Stopped", "", "", "", "", "", isSummary = true))
        }
        udpManager.stop()
        _isTesting.value = false
        _status.value = "Ready..."
    }

    fun isSetupPending(): Boolean {
        return udpManager.setupPending.get()
    }

    override fun onCleared() {
        super.onCleared()
        udpManager.stop()
    }
}