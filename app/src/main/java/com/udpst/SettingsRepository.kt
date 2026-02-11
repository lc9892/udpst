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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

internal object PreferencesKeys {
    val IP = stringPreferencesKey("ip_address")
    val PORT = stringPreferencesKey("port")
    val BANDWIDTH = stringPreferencesKey("bandwidth")
    val KEYSTRING = stringPreferencesKey("keystring")
    val KEYID = stringPreferencesKey("keyid")
    val DIRECTION = intPreferencesKey("direction")
    val SELECTED_RATE = intPreferencesKey("selected_rate")
    val SELECTED_RATE_TEXT = stringPreferencesKey("selected_rate_text")
}

class SettingsRepository(private val context: Context) {

    suspend fun saveSettings(
        ip: String,
        port: String,
        bandwidth: String,
        keystring: String,
        keyid: String,
        direction: Int,
        selectedRate: Int,
        selectedRateText: String
    ) {
        context.dataStore.edit {
            it[PreferencesKeys.IP] = ip
            it[PreferencesKeys.PORT] = port
            it[PreferencesKeys.BANDWIDTH] = bandwidth
            it[PreferencesKeys.KEYSTRING] = keystring
            it[PreferencesKeys.KEYID] = keyid
            it[PreferencesKeys.DIRECTION] = direction
            it[PreferencesKeys.SELECTED_RATE] = selectedRate
            it[PreferencesKeys.SELECTED_RATE_TEXT] = selectedRateText
        }
    }

    suspend fun getSettings(): Preferences {
        return context.dataStore.data.first()
    }
}