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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_ips")

class RecentIpAddressesRepository(context: Context) {

    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val RECENT_IPS = stringPreferencesKey("recent_ips")
    }

    val recentIpsFlow: Flow<List<String>> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.RECENT_IPS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        }

    suspend fun addIp(ip: String) {
        dataStore.edit { preferences ->
            val currentIps = preferences[PreferencesKeys.RECENT_IPS]?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
            if (currentIps.contains(ip)) {
                currentIps.remove(ip)
            }
            currentIps.add(0, ip)
            val newIps = currentIps.take(5).joinToString(",")
            preferences[PreferencesKeys.RECENT_IPS] = newIps
        }
    }
}
