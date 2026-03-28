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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class UdpManager {
    //
    // Global variables
    //
    val setupPending = AtomicBoolean(false)
    val isRunning = AtomicBoolean(false)
    private var systemClock = 0L
    private val debugLogging = false

    //
    // Data repository
    //
    private data class Repository(
        var callback: UdpManagerCallback? = null,
        var channel: DatagramChannel? = null,
        var isIpv6: Boolean = false,
        var serverHost: String? = null,
        var serverSrcIp: String? = null,
        var controlPort: Int = 0,
        var testDirection: Int = 0,
        var randPayload: Boolean = false,
        var testOptions: String = "",
        var maxBandwidth: UInt = 0u,
        var selectedRate: UInt = 0u,
        var testPort: Int = 0,
        var testAction: UInt = 0u,
        var lpduSeqNo: UInt = 0u,
        var spduSeqNo: UInt = 0u,
        var spduTimeSec: UInt = 0u,
        var spduTimeNsec: UInt = 0u,
        var pduRxTime: Long = 0L,
        var sendTimer1: Long = 0L,
        var sendTimer2: Long = 0L,
        var subIntSeqNo: UInt = 0u,
        var clockDeltaMin: Long = 0L,
        var delayVarMin: UInt = 0u,
        var delayVarMax: UInt = 0u,
        var delayVarSum: UInt = 0u,
        var delayVarCnt: UInt = 0u,
        var tiDeltaTime: UInt = 0u,
        var tiRxDatagrams: UInt = 0u,
        var tiRxBytes: UInt = 0u,
        var lpduHistBuf: MutableList<UInt> = MutableList(LPDU_HISTORY_SIZE) { 0u },
        var lpduHistIdx: Int = 0,
        var seqErrLoss: UInt = 0u,
        var seqErrOoo: UInt = 0u,
        var seqErrDup: UInt = 0u,
        var trialIntClock: Long = 0L,
        var subIntClock: Long = 0L,
        var accumTime: UInt = 0u,
        var spduSeqErr: UInt = 0u,
        var rttMinimum: UInt = STATUS_NODEL,
        var rttVarSample: UInt = 0u,
        var delayMinUpd: Boolean = false,
        var rttVarSum: UInt = 0u,
        var rttVarCnt: UInt = 0u,
        var rxMbpsMax: Double = 0.0,
        var tsRxDatagrams: UInt = 0u,
        var tsSeqErrLoss: UInt = 0u,
        var keyString: String = "",
        var keyId: Int = 0,
        var clientKey: List<Byte> = emptyList(),
        var serverKey: List<Byte> = emptyList()
    )
    private lateinit var r: Repository

    //
    // Buffer variables
    //
    private val maxDatagramSize = 1500 - (L3DG_OVERHEAD) // MTU - IPv4 header - UDP header
    private val receiveBuffer = ByteBuffer.allocateDirect(maxDatagramSize)
    private val sendBuffer = ByteBuffer.allocateDirect(maxDatagramSize)
    private val sendingRateBuf = ByteBuffer.allocate(SendingRate.TOTAL_SIZE)
    private val sisActBuf = ByteBuffer.allocate(SubIntStats.TOTAL_SIZE)
    private val sisSavBuf = ByteBuffer.allocate(SubIntStats.TOTAL_SIZE)
    private val zeroDirectForStatus = ByteBuffer.allocateDirect(StatusHdr.TOTAL_SIZE)
    private val zeroHeapForSis = ByteBuffer.allocate(SubIntStats.TOTAL_SIZE)

    //
    // Object variables
    //
    private val lHdr = LoadHdr(ByteBuffer.allocateDirect(0))
    private val sHdr = StatusHdr(ByteBuffer.allocateDirect(0))
    private val srStruct = SendingRate(sendingRateBuf)
    private val sisAct = SubIntStats(sisActBuf)
    private val sisSav = SubIntStats(sisSavBuf)

    //
    // Interface definitions
    //
    interface UdpManagerCallback {
        fun onDataExchanged(s1: String, s2: String, s3: String, s4: String, s5: String, s6: String)
        fun onSummaryExchanged(s1: String)
        fun onSetTesting(state: Boolean)
        fun onStatus(message: String)
        fun onStoreIp()
    }

    //
    // Entry from UI
    //
    suspend fun start(ip: String, port: Int, direction: Int, bandwidth: Int, selectedRate: Int, keystring: String, keyid: Int, callback: UdpManagerCallback) = withContext(Dispatchers.IO) {
        r = Repository() // (Re)initialize repository

        //
        // Check parameters and set repository values AFTER verification
        //
        if (ip.isEmpty()) {
            callback.onStatus("Server Hostname/IP Address required")
            return@withContext
        }
        if (port !in 1..UShort.MAX_VALUE.toInt()) {
            callback.onStatus("Port out of range (1 - ${UShort.MAX_VALUE})")
            return@withContext
        }
        if (bandwidth !in 0..MAX_CLIENT_BW) {
            callback.onStatus("Bandwidth out of range (0 - $MAX_CLIENT_BW)")
            return@withContext
        }
        if (keyid !in 0..MAX_KEY_ID.toInt()) {
            callback.onStatus("Key ID out of range (0 - $MAX_KEY_ID)")
            return@withContext
        }
        if (keyid != 0 && keystring.isEmpty()) {
            callback.onStatus("Authentication key required")
            return@withContext
        }
        r.callback = callback
        r.serverHost = ip
        r.controlPort = port
        r.testDirection = direction
        r.maxBandwidth = bandwidth.toUInt()
        if (selectedRate < 0) // Auto comes in as -1
            r.selectedRate = CHTA_SRIDX_DEF
        else
            r.selectedRate = selectedRate.toUInt()
        if (keystring.isNotEmpty()) {
            r.keyString = keystring
            r.keyId = keyid
        }

        //
        // Initiate control handshake and data exchange
        //
        try {
            r.channel = DatagramChannel.open(StandardProtocolFamily.INET6).apply {
                configureBlocking(false)
            }
            r.channel?.bind(null)

            //
            // Increase socket buffer size
            //
            val desiredBufferSize = 256 * 1024
            var actualBufferSize: Int?
            if (r.testDirection == CHTA_CREQ_TESTACTUS) {
                r.channel?.setOption(StandardSocketOptions.SO_SNDBUF, desiredBufferSize)
                actualBufferSize = r.channel?.getOption(StandardSocketOptions.SO_SNDBUF)
            } else {
                r.channel?.setOption(StandardSocketOptions.SO_RCVBUF, desiredBufferSize)
                actualBufferSize = r.channel?.getOption(StandardSocketOptions.SO_RCVBUF)
            }
            Log.d("UdpManager", "Requested buffer size: $desiredBufferSize, Actual size: $actualBufferSize")

            //
            // Process hostname/IP address
            //
            systemClock = System.nanoTime()
            val address = InetSocketAddress(ip, port)
            if (address.isUnresolved) {
                callback.onStatus("Unable to resolve $ip")
                return@withContext
            }
            if (address.address is Inet6Address)
                r.isIpv6 = true

            //
            // Process Setup
            //
            setupPending.set(true)
            val payload = createControlHdrSR()
            if (!controlHandshake(address, payload)) {
                setupPending.set(false)
                return@withContext
            }

            //
            // Process Test Activation and initiate test
            //
            if (r.serverSrcIp != null && r.testPort != 0) {
                systemClock = System.nanoTime()
                val testAddress = InetSocketAddress(r.serverSrcIp, r.testPort)
                val payload = createControlHdrTA()
                if (!controlHandshake(testAddress, payload)) {
                    setupPending.set(false)
                    return@withContext
                }
                r.channel?.connect(testAddress) // Connect socket for data exchange

                //
                // Set initial send timers
                //
                if (r.testDirection == CHTA_CREQ_TESTACTUS) {
                    r.sendTimer1 = resetTimer(srStruct.txInterval1, r.sendTimer1, true)
                    r.sendTimer2 = resetTimer(srStruct.txInterval2, r.sendTimer2, true)
                } else {
                    r.sendTimer1 = systemClock + (DEF_TRIAL_INT * NSECINMSEC) - SENDTIMER_ADJ
                    r.sendTimer2 = 0L
                }

                //
                // Begin testing
                //
                setupPending.set(false)
                isRunning.set(true)
                r.callback?.onStoreIp()
                r.callback?.onStatus("Test in progress...")
                dataExchange()
            }
        } catch (e: Exception) {
            r.callback?.onStatus("Test Exception")
            r.callback?.onSummaryExchanged("Error: ${e.message ?: e.toString().substringAfterLast(".")}")
        } finally {
            stop()
        }
    }

    //
    // Key Derivation Function (KDF) in Counter Mode using HMAC-SHA256
    // Output individual authentication keys of length SHA256_KEY_LEN from derived key material
    //
    private fun kdfHmacSha256(kin: String, unixtime: UInt) {
        val hmac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(kin.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(keySpec)

        val keys = 2
        val label = "UDPSTP".toByteArray(Charsets.UTF_8)
        val context = unixtime.toString().toByteArray(Charsets.UTF_8)
        val lBits = SHA256_KEY_LEN * keys * 8 // Total bits produced

        val result = ByteArray(2 * SHA256_KEY_LEN)
        for (i in 1..keys) {
            // NIST SP 800-108: [i]2 || Label || 0x00 || Context || [L]2
            // Using 4 bytes for counter i and 4 bytes for length L
            val buffer = ByteBuffer.allocate(4 + label.size + 1 + context.size + 4)
            buffer.putInt(i) // Counter i (32 bits big-endian)
            buffer.put(label)
            buffer.put(0.toByte())
            buffer.put(context)
            buffer.putInt(lBits) // Length L (32 bits big-endian)
            
            val segment = hmac.doFinal(buffer.array())
            System.arraycopy(segment, 0, result, (i - 1) * SHA256_KEY_LEN, SHA256_KEY_LEN)
        }

        r.clientKey = result.take(SHA256_KEY_LEN)
        r.serverKey = result.drop(SHA256_KEY_LEN).take(SHA256_KEY_LEN)
        return
    }

    //
    // Build Setup Request for control handshake
    //
    private fun createControlHdrSR(): ByteArray {
        //
        // Create Setup Request
        //
        val cHdrSR = ControlHdrSR.createHeap().apply {
            pduId = CHSR_ID.toUShort()
            protocolVer = PROTOCOL_VER.toUShort()
            mcIndex = 0u
            mcCount = 1u
            mcIdent = Random.nextInt(1, 65536).toUShort() // Non-zero
            cmdRequest = 1u
            cmdResponse = 0u
            //
            maxBandwidth = r.maxBandwidth.toUShort()
            if (r.maxBandwidth > 0u && r.testDirection == CHTA_CREQ_TESTACTUS)
                maxBandwidth = r.maxBandwidth.or(CHSR_USDIR_BIT).toUShort()
            //
            testPort = 0u
            modifierBitmap = 0u // No jumbo sizes or traditional MTU packets
            authMode = AUTHMODE_0.toUByte()
            authUnixTime = 0u
            authDigest = ByteArray(AUTH_DIGEST_LENGTH)
            keyId = 0u
            reservedAuth1 = 0u
            checkSum = 0u
        }
        val buffer = cHdrSR.buffer

        //
        // Include authetication if key string was provided
        //
        if (r.keyString.isNotEmpty()) {
            val unixtime = (System.currentTimeMillis() / MSECINSEC).toUInt()
            cHdrSR.authMode = AUTHMODE_1.toUByte()
            cHdrSR.authUnixTime = unixtime
            cHdrSR.keyId = r.keyId.toUByte()

            val keySpec: SecretKeySpec
            val hmac = Mac.getInstance("HmacSHA256")
            if (CLIENT_VER_IS_LATEST) {
                //
                // Create and use KDF keys
                //
                kdfHmacSha256(r.keyString, unixtime)
                keySpec = SecretKeySpec(r.clientKey.toByteArray(), "HmacSHA256")
            } else {
                //
                // Use key string directly
                //
                keySpec = SecretKeySpec(r.keyString.toByteArray(), "HmacSHA256")
            }

            //
            // Insert digest as last step
            //
            hmac.init(keySpec)
            val digest = hmac.doFinal(buffer.array())
            cHdrSR.authDigest = digest
        }

        return buffer.array()
    }

    //
    // Build Test Activation Request for control handshake
    //
    private fun createControlHdrTA(): ByteArray {
        //
        // Special options available via unique max bandwidth bit settings
        //
        val minMaxBWValue = 30000u // Threshold for special options
        val optAlgoCBitMap = 0x01 // Use algorithm C for rate adjustment
        val optRandPBitMap = 0x02 // Randomize payload data

        //
        // Create Test Activation Request
        //
        val cHdrTA = ControlHdrTA.createHeap().apply {
            pduId = CHTA_ID.toUShort()
            protocolVer = PROTOCOL_VER.toUShort()
            cmdRequest = r.testDirection.toUByte()
            cmdResponse = 0u
            lowThresh = 30u
            upperThresh = 90u
            trialInt = DEF_TRIAL_INT.toUShort()
            testIntTime = DEF_TESTINT_TIME.toUShort()
            reserved1 = if (CLIENT_VER_IS_LATEST) 0u else (DEF_SUBINT_PERIOD / 1000).toUByte()
            dscpEcn = 0u
            srIndexConf = r.selectedRate.toUShort()
            useOwDelVar = 0u
            highSpeedDelta = 10u
            slowAdjThresh = 3u
            seqErrThresh = 10u
            ignoreOooDup = 1u
            modifierBitmap = 0u
            rateAdjAlgo = CHTA_RA_ALGO_B.toUByte()
            //
            // Check for special options
            //
            if (r.maxBandwidth > minMaxBWValue) {
                r.testOptions += ", Options:"
                if ((r.maxBandwidth and optAlgoCBitMap.toUInt()) != 0u) {
                    rateAdjAlgo = CHTA_RA_ALGO_C.toUByte()
                    r.testOptions += " +AlgoC"
                }
                if ((r.maxBandwidth and optRandPBitMap.toUInt()) != 0u) {
                    r.randPayload = true
                    modifierBitmap = modifierBitmap.or(CHTA_RAND_PAYLOAD.toUByte())
                    r.testOptions += " +Rand"
                }
            }
            reserved2 = 0u
            srStruct = ByteArray(SendingRate.TOTAL_SIZE)
            subIntPeriod = if (CLIENT_VER_IS_LATEST) DEF_SUBINT_PERIOD.toUShort() else 0u
            reserved3 = 0u
            if (CLIENT_VER_IS_LATEST) {
                reserved4 = 0u
                reserved5 = 0u
                authMode = 0u
                authUnixTime = 0u
                authDigest = ByteArray(AUTH_DIGEST_LENGTH)
                keyId = 0u
                reservedAuth1 = 0u
                checkSum = 0u
            }
        }
        val buffer = cHdrTA.buffer

        //
        // Include authetication if key string was provided
        //
        if (CLIENT_VER_IS_LATEST and r.keyString.isNotEmpty()) {
            val unixtime = (System.currentTimeMillis() / MSECINSEC).toUInt()
            cHdrTA.authMode = AUTHMODE_1.toUByte()
            cHdrTA.authUnixTime = unixtime
            cHdrTA.keyId = r.keyId.toUByte()

            val hmac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(r.clientKey.toByteArray(), "HmacSHA256")

            //
            // Insert digest as last step
            //
            hmac.init(keySpec)
            val digest = hmac.doFinal(buffer.array())
            cHdrTA.authDigest = digest
        }

        return buffer.array()
    }

    //
    // Execute control handshake with server
    //
    enum class ControlType {
        Unknown,
        Setup,
        TestActivation
    }
    private suspend fun controlHandshake(address: InetSocketAddress, payload: ByteArray): Boolean {
        val sizeofControlHdrSR = ControlHdrSR.TOTAL_SIZE
        val sizeofControlHdrTA = ControlHdrTA.TOTAL_SIZE

        //
        // Send control PDU request
        //
        var pdutype = ControlType.Unknown
        var pdutext = "Unknown PDU"
        val buf = ByteBuffer.wrap(payload)
        withContext(Dispatchers.IO) {
            r.channel?.send(buf, address)
        }
        if (buf.position() == sizeofControlHdrSR) {
            pdutype = ControlType.Setup
            pdutext = "Setup"
        } else if (buf.position() == sizeofControlHdrTA) {
            pdutype = ControlType.TestActivation
            pdutext = "Test Act."
        }
        r.callback?.onStatus("$pdutext Request sent to ${address.address.hostAddress}:${address.port}")

        //
        // Await control PDU response (including possible Null Request)
        //
        @Suppress("UNUSED_PARAMETER")
        for (i in 1..2) {
            //
            // Wrap non-blocking receive logic in a timeout
            //
            try {
                withTimeout(TIMEOUT_NOTRAFFIC) { // Throw a TimeoutCancellationException upon timeout
                    while (true) {
                        receiveBuffer.clear()
                        val sender = r.channel?.receive(receiveBuffer) as? InetSocketAddress
                        if (sender != null) {
                            if (r.serverSrcIp == null)
                                r.serverSrcIp = sender.address.hostAddress // Save initial server source IP
                            break
                        }
                        yield() // Let other coroutines continue as needed
                    }
                }
            } catch (e: TimeoutCancellationException) {
                r.callback?.onStatus("Timeout: No response from ${address.address.hostAddress}:${address.port}")
                return false
            }
            r.pduRxTime = systemClock
            val receiveSize = receiveBuffer.position()
            receiveBuffer.flip()

            //
            // If Null Request received from server, do another receive
            //
            if (receiveSize == ControlHdrNR.TOTAL_SIZE) {
                val resp = ControlHdrNR(receiveBuffer)
                if (resp.pduId == CHNR_ID.toUShort()) {
                    Log.d("UdpManager", "Null Request received")
                }
                continue
            }

            //
            // Continue based on expected control PDU
            //
            if (pdutype == ControlType.Unknown) {
                Log.d("UdpManager", "$pdutext Response received")
                return false
            } else if (pdutype == ControlType.Setup) {
                //
                // Setup Response expected
                //
                if (receiveSize == sizeofControlHdrSR) {
                    val resp = ControlHdrSR(receiveBuffer)
                    if (resp.pduId == CHSR_ID.toUShort()) {
                        when (val responseCode = resp.cmdResponse.toInt()) {
                            CHSR_CRSP_NONE -> r.callback?.onStatus("CRSP not provided in server response")
                            CHSR_CRSP_ACKOK -> {
                                r.testPort = resp.testPort.toInt()
                                Log.d("UdpManager", "$pdutext Response received: Server test port ${r.testPort}")
                                break // Exits the 'i' loop
                            }
                            CHSR_CRSP_BADVER -> r.callback?.onStatus("Protocol version not accepted by server")
                            CHSR_CRSP_BADJS -> r.callback?.onStatus("Server must disable jumbo datagram sizes")
                            CHSR_CRSP_AUTHNC -> r.callback?.onStatus("Authentication not configured on server")
                            CHSR_CRSP_AUTHREQ -> r.callback?.onStatus("Authentication required by server")
                            CHSR_CRSP_AUTHINV -> r.callback?.onStatus("Authentication method does not match server")
                            CHSR_CRSP_AUTHFAIL -> r.callback?.onStatus("Authentication verification failed at server")
                            CHSR_CRSP_AUTHTIME -> r.callback?.onStatus("Authentication time outside time window of server")
                            CHSR_CRSP_NOMAXBW -> r.callback?.onStatus("Max bandwidth option required by server")
                            CHSR_CRSP_CAPEXC -> r.callback?.onStatus("Max bandwidth exceeds server capacity")
                            CHSR_CRSP_BADTMTU -> r.callback?.onStatus("Server must disable Traditional MTU sizes")
                            CHSR_CRSP_MCINVPAR -> r.callback?.onStatus("Multi-conn. parameters rejected by server")
                            CHSR_CRSP_CONNFAIL -> r.callback?.onStatus("Connection allocation failure on server")
                            else -> r.callback?.onStatus("Unexpected CRSP ($responseCode) in server response")
                        }
                    }
                } else {
                    r.callback?.onStatus("Unexpected PDU size ($receiveSize) awaiting $pdutext Response")
                }
                return false
            } else {
                //
                // Test Activation Response expected
                //
                if (receiveSize == sizeofControlHdrTA) {
                    val resp = ControlHdrTA(receiveBuffer)
                    if (resp.pduId == CHTA_ID.toUShort()) {
                        when (val responseCode = resp.cmdResponse.toInt()) {
                            CHTA_CRSP_NONE -> r.callback?.onStatus("CRTA not provided in server response")
                            CHTA_CRSP_ACKOK -> {
                                if (r.randPayload && (resp.modifierBitmap and CHTA_RAND_PAYLOAD.toUByte()).toUInt() == 0u) {
                                    // Randomized payload request was denied by server
                                    r.callback?.onStatus("Server must enable randomized payload")
                                } else {
                                    // Save sending rate structure from server
                                    sendingRateBuf.clear()
                                    sendingRateBuf.put(resp.srStruct)
                                    sendingRateBuf.flip()
                                    break // Exits the 'i' loop
                                }
                            }
                            CHTA_CRSP_BADPARAM -> r.callback?.onStatus("Test parameters rejected by server")
                            else -> r.callback?.onStatus("Unexpected CRTA ($responseCode) in server response")
                        }
                    }
                } else {
                    r.callback?.onStatus("Unexpected PDU size ($receiveSize) awaiting $pdutext Response")
                }
                return false
            }
        }
        return true
    }

    //
    // Execute data exchange with server
    //
    private fun dataExchange() {
        val sizeofLoadHdr = LoadHdr.TOTAL_SIZE
        val sizeofStatusHdr = StatusHdr.TOTAL_SIZE

        while (isRunning.get()) {
            //
            // Receive all available PDUs
            //----------------------------------------------------------------------------
            //
            var updateLimiter = 0
            while (r.testAction == TEST_ACT_TEST) {
                receiveBuffer.clear()
                if (r.testDirection == CHTA_CREQ_TESTACTDS)
                    receiveBuffer.limit(sizeofLoadHdr) // Truncate to load PDU header
                val receiveSize = r.channel?.read(receiveBuffer)
                if (receiveSize == null || receiveSize <= 0) // No (additional) data to receive
                    break
                if ((updateLimiter++ % BURST_LIMIT) == 0) // Update clock initially and every N consecutive reads
                    systemClock = System.nanoTime()
                r.pduRxTime = systemClock
                receiveBuffer.flip()

                //
                // Process based on test direction
                //
                if (r.testDirection == CHTA_CREQ_TESTACTUS) {
                    if (receiveSize == sizeofStatusHdr) {
                        serviceStatusPdu(receiveBuffer)
                    } else {
                        Log.d("UdpManager", "Invalid Status Message size (${receiveSize})")
                    }
                } else {
                    if (receiveSize == sizeofLoadHdr) { // Expect truncation (actual size in header)
                        serviceLoadPdu(receiveBuffer)
                    } else {
                        Log.d("UdpManager", "Invalid Load PDU size (${receiveSize})")
                    }
                    if (systemClock >= r.sendTimer1) // Stop receive processing if status must be sent
                        break
                }
            }

            //
            // Process send timer 1
            //----------------------------------------------------------------------------
            //
            systemClock = System.nanoTime()
            if ((r.sendTimer1 > 0L) && (systemClock >= r.sendTimer1)) {
                if (r.testDirection == CHTA_CREQ_TESTACTUS) {
                    //
                    // Send load PDUs
                    //
                    sendLoadPdu(1, sizeofLoadHdr)
                    r.sendTimer1 = resetTimer(srStruct.txInterval1, r.sendTimer1, true)
                    r.sendTimer2 = resetTimer(srStruct.txInterval2, r.sendTimer2, false)
                } else {
                    //
                    // Send status message
                    //
                    if (r.lpduSeqNo > 0u) {
                        createStatusHdr(sendBuffer)
                        r.channel?.write(sendBuffer) ?: 0
                    } else {
                        Log.d("UdpManager", "Skipping status transmission, awaiting initial load PDUs...")
                    }
                    r.sendTimer1 = systemClock + (DEF_TRIAL_INT * NSECINMSEC) - SENDTIMER_ADJ
                }
                systemClock = System.nanoTime() // Update clock if data was sent
            }

            //
            // Process send timer 2
            //----------------------------------------------------------------------------
            //
            if ((r.sendTimer2 > 0L) && (systemClock >= r.sendTimer2)) {
                //
                // Send load PDUs
                //
                sendLoadPdu(2, sizeofLoadHdr)
                r.sendTimer1 = resetTimer(srStruct.txInterval1, r.sendTimer1, false)
                r.sendTimer2 = resetTimer(srStruct.txInterval2, r.sendTimer2, true)
            }

            //
            // Check watchdog and latest test action from server
            //----------------------------------------------------------------------------
            //
            val watchdog = (systemClock - r.pduRxTime) / NSECINMSEC
            if (watchdog >= TIMEOUT_NOTRAFFIC) {
                r.callback?.onStatus("Test Failure")
                r.callback?.onSummaryExchanged("Error: Incoming traffic has completely stopped")
                break // End data exchange

            } else if (r.testAction != TEST_ACT_TEST) {
                //
                // Output end-of-test stats
                //
                var delivered = 0.0
                val sent = r.tsRxDatagrams.toDouble() + r.tsSeqErrLoss.toDouble()
                if (sent > 0.0) {
                    delivered = (r.tsRxDatagrams.toDouble() * 100.0) / sent
                }
                var rttmin = 0u
                if (r.rttMinimum != STATUS_NODEL) {
                    rttmin = r.rttMinimum
                }
                var dir = "Upstream"
                if (r.testDirection == CHTA_CREQ_TESTACTDS) {
                    dir = "Downstream"
                }
                r.callback?.onStatus("Test Complete")
                r.callback?.onSummaryExchanged("%s %.2f%%".format("Summary Delivered:", delivered))
                r.callback?.onSummaryExchanged("%s %d ms".format("Minimum Round-Trip Time:", rttmin.toLong()))
                r.callback?.onSummaryExchanged("%s %.2f Mbps".format("Maximum $dir L3/IP:", r.rxMbpsMax))
                if (r.serverHost != r.serverSrcIp)
                    r.callback?.onSummaryExchanged("Server: ${r.serverHost}[${r.serverSrcIp}]:${r.controlPort}")
                else
                    r.callback?.onSummaryExchanged("Server: ${r.serverSrcIp}:${r.controlPort}")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val formattedDateTime = sdf.format(java.util.Date())
                r.callback?.onSummaryExchanged("Completion: $formattedDateTime${r.testOptions}")

                r.callback?.onSetTesting(false) // Set testing to false if test completed
                break // End data exchange
            }
        }
        return
    }

    //
    // Send load PDUs via periodic timers for transmitters 1 & 2
    //
    private fun sendLoadPdu(transmitter: Int, sizeofLoadHdr: Int) {
        //
        // Select transmitter specifics
        //
        var payload: Int
        var burstSize: Int
        var addon: Int
        var random = false
        if (transmitter == 1) {
            payload = srStruct.udpPayload1.toInt()
            burstSize = srStruct.burstSize1.toInt()
            addon = 0
        } else {
            payload = srStruct.udpPayload2.toInt()
            burstSize = srStruct.burstSize2.toInt()
            addon = (srStruct.udpAddon2 and SRATE_RAND_BIT.inv()).toInt()
            if ((srStruct.udpAddon2 and SRATE_RAND_BIT) != 0u)
                random = true
        }

        //
        // Adjust burst size if needed
        //
        if (r.testAction != TEST_ACT_TEST && burstSize > 1)
            burstSize = 1 // Handle test stop in progress
        if (burstSize > BURST_LIMIT)
            burstSize = BURST_LIMIT // Limit burst size for this client

        //
        // If IPv6 reduce payload to maintain L3 packet sizes
        //
        val minPayloadSize = sizeofLoadHdr + IPV6_ADDSIZE
        if (r.isIpv6) {
            if (payload >= minPayloadSize)
                payload -= IPV6_ADDSIZE
            if (addon >= minPayloadSize)
                addon -= IPV6_ADDSIZE
        }

        //
        // If designated as random, use stored size as max when calculating size
        //
        if (addon > 0 && random)
            addon = Random.nextInt(sizeofLoadHdr, addon + 1)

        //
        // Send burst of load PDUs (and addon if needed)
        //
        var init = true
        @Suppress("UNUSED_PARAMETER")
        for (i in 1..2) {
            for (j in 1..burstSize) {
                createLoadHdr(sendBuffer, sizeofLoadHdr, payload, init)
                init = false
                val bytesSent = r.channel?.write(sendBuffer) ?: 0
                if (bytesSent <= 0) // Check for backpressure
                    r.lpduSeqNo-- // Adjust sequence number to correct for datagram not accepted
            }
            if (addon > 0) {
                payload = addon
                burstSize = 1
            } else {
                break
            }
        }
        return
    }

    //
    // Reset send timer for next expiry
    //
    private fun resetTimer(txInterval: UInt, sendTimer: Long, currentTimer: Boolean): Long {
        if (currentTimer) {
            if (txInterval > 0u)
                return systemClock + (txInterval.toLong() * NSECINUSEC) - SENDTIMER_ADJ
            return 0L
        } else {
            if (sendTimer == 0L && txInterval > 0u)
                return systemClock + (txInterval.toLong() * NSECINUSEC) - SENDTIMER_ADJ
            else if (sendTimer > 0L && txInterval == 0u)
                return 0L
        }
        return sendTimer
    }

    //
    // Build Load PDU for data exchange
    //
    private fun createLoadHdr(buffer: ByteBuffer, hsize: Int, size: Int, init: Boolean) {
        buffer.limit(size)

        //
        // Clearing not necessary since all fields populated initially
        //
        buffer.position(0)
        lHdr.buffer = buffer
        if (init) {
            lHdr.pduId = LOAD_ID.toUShort()
            lHdr.testAction = r.testAction.toUByte()
            lHdr.rxStopped = 0u
        }
        lHdr.lpduSeqNo = ++r.lpduSeqNo
        lHdr.udpPayload = size.toUShort()
        if (init) {
            lHdr.spduSeqErr = r.spduSeqErr.toUShort()
            lHdr.spduTimeSec = r.spduTimeSec
            lHdr.spduTimeNsec = r.spduTimeNsec
            lHdr.lpduTimeSec = (systemClock / NSECINSEC).toUInt()
            lHdr.lpduTimeNsec = (systemClock % NSECINSEC).toUInt()
            lHdr.rttRespDelay = 0u
            if (r.pduRxTime > 0) {
                val rttRespDelay = systemClock - r.pduRxTime
                lHdr.rttRespDelay = ((rttRespDelay + NSECADJ_MSEC) / NSECINMSEC).toUShort()
            }
            lHdr.checkSum = 0u
        }

        //
        // Randomize payload if needed
        //
        if (r.randPayload) {
            buffer.position(hsize)
            randomizePayload(buffer)
            buffer.position(0)
        }
    }

    //
    // Randomizes the contents of the given ByteBuffer to introduce high entropy,
    // defeating pattern-based compression schemes in networking equipment.
    //
    fun randomizePayload(buffer: ByteBuffer) {
        if (buffer.remaining() == 0) return

        var state = Random.nextLong() // Single initial seed for minimal RNG overhead
        val size = buffer.remaining()
        val longCount = size / 8

        //
        // Process in 64-bit chunks
        //
        @Suppress("UNUSED_PARAMETER")
        for (i in 0 until longCount) {
            // Xorshift64* variant: fast, period 2^64-1, good statistical properties for non-crypto use
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            buffer.putLong(state)
        }

        //
        // Handle remaining bytes (0-7)
        //
        val remaining = size % 8
        if (remaining > 0) {
            // Generate one more long value
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)

            var temp = state
            @Suppress("UNUSED_PARAMETER")
            for (j in 0 until remaining) {
                buffer.put((temp and 0xFFL).toByte())
                temp = temp ushr 8
            }
        }
    }

    //
    // Process Load PDU for data exchange
    //
    private fun serviceLoadPdu(buffer: ByteBuffer) {
        lHdr.buffer = buffer

        if (lHdr.pduId != LOAD_ID.toUShort())
            return

        r.testAction = lHdr.testAction.toUInt()
        if (r.testAction != TEST_ACT_TEST)
            Log.d("UdpManager", "Test stop received")

        //
        // Update traffic stats (use size specified in PDU)
        //
        val payload = lHdr.udpPayload
        sisAct.rxDatagrams++
        sisAct.rxBytes += payload.toUInt()
        r.tiRxDatagrams++
        r.tiRxBytes += payload.toUInt()

        //
        // Check sequence number for loss and reordering
        //
        var firstpdu = false
        if (r.lpduSeqNo == 0u)
            firstpdu = true
        var hbstatus = 0 // Used below for history buffer processing
        val seqno = lHdr.lpduSeqNo
        if (seqno >= r.lpduSeqNo + 1u) {
            //
            // Sequence number greater than or equal to expected
            //
            if (seqno > r.lpduSeqNo + 1u) {
                val loss = seqno - r.lpduSeqNo - 1u
                sisAct.seqErrLoss += loss
                r.seqErrLoss += loss
            }
            r.lpduSeqNo = seqno
        } else {
            //
            // Sequence number less than expected, check history buffer
            //
            if (r.lpduHistBuf.contains(seqno)) {
                //
                // Sequence number in history buffer, increment duplicate count
                //
                r.seqErrDup++
                sisAct.seqErrDup++
                hbstatus = 2 // Skip history buffer insertion as well as subsequent processing
            } else {
                r.seqErrOoo++
                sisAct.seqErrOoo++
                hbstatus = 1 // Skip subsequent processing

                //
                // Correct previous loss count that resulted from this "late" datagram
                //
                if (r.seqErrLoss > 0u)
                    r.seqErrLoss--
                if (sisAct.seqErrLoss > 0u)
                    sisAct.seqErrLoss--
            }
        }
        if (hbstatus < 2) {
            r.lpduHistBuf[r.lpduHistIdx] = seqno                // Save sequence number in history buffer
            ++r.lpduHistIdx                                     // Advance history buffer index
            r.lpduHistIdx = r.lpduHistIdx and LPDU_HISTORY_MASK // Maintain index limit
        }
        if (hbstatus > 0)
            return // No further processing for non-increasing sequence numbers

        //
        // If an updated value is detected (because another status PDU was sent),
        // calculate round-trip time from the last status PDU sent until this load PDU
        //
        if (lHdr.spduTimeNsec != r.spduTimeNsec || lHdr.spduTimeSec != r.spduTimeSec) {
            var rtt = systemClock - ((lHdr.spduTimeSec.toLong() * NSECINSEC) + lHdr.spduTimeNsec.toLong())
            //
            // Adjust RTT based on delay between when status PDU was received and load PDU sent
            //
            val rttrd = lHdr.rttRespDelay.toLong() * NSECINMSEC
            if (rttrd <= rtt) {
                rtt -= rttrd
            }
            rtt = ((rtt + NSECADJ_MSEC) / NSECINMSEC) // Convert to ms
            //
            // Check for new minimum
            //
            if (rtt.toUInt() < r.rttMinimum) {
                r.rttMinimum = rtt.toUInt()
                r.delayMinUpd = true
            }
            //
            // Update RTT variation for trial interval and RTT variation range for sub-interval
            //
            r.rttVarSample = rtt.toUInt() - r.rttMinimum
            if (r.rttVarSample < sisAct.rttVarMinimum)
                sisAct.rttVarMinimum = r.rttVarSample
            if (r.rttVarSample > sisAct.rttVarMaximum)
                sisAct.rttVarMaximum = r.rttVarSample
            r.rttVarSum += r.rttVarSample
            r.rttVarCnt++
            //
            // Save to detect updated value
            //
            r.spduTimeSec = lHdr.spduTimeSec
            r.spduTimeNsec = lHdr.spduTimeNsec
        }

        //
        // Process one-way clock delta and delay variation for this load PDU
        //
        val lpduTime = (lHdr.lpduTimeSec.toLong() * NSECINSEC) + lHdr.lpduTimeNsec.toLong()
        val delta = systemClock - lpduTime
        if (firstpdu) {
            r.clockDeltaMin = delta
            r.delayMinUpd = true
        } else {
            //
            // Check for new minimum
            //
            if (delta < r.clockDeltaMin) {
                r.clockDeltaMin = delta
                r.delayMinUpd   = true
            }
            val delayvar = (((delta - r.clockDeltaMin) + NSECADJ_MSEC) / NSECINMSEC).toUInt()
            //
            // Update one-way delay variation stats for trial interval
            //
            if (delayvar < r.delayVarMin)
                r.delayVarMin = delayvar
            if (delayvar > r.delayVarMax)
                r.delayVarMax = delayvar
            r.delayVarSum += delayvar
            r.delayVarCnt++
            //
            // Update one-way delay variation stats for sub-interval
            //
            if (delayvar < sisAct.delayVarMin)
                sisAct.delayVarMin = delayvar
            if (delayvar > sisAct.delayVarMax)
                sisAct.delayVarMax = delayvar
            sisAct.delayVarSum += delayvar
            sisAct.delayVarCnt++
        }
    }

    //
    // Build Status Message PDU for data exchange
    //
    private fun createStatusHdr(buffer: ByteBuffer) {
        //
        // Clear needed buffer space using pre-zeroed buffer
        //
        zeroDirectForStatus.rewind()
        buffer.clear()
        buffer.put(zeroDirectForStatus)
        buffer.flip()

        //
        // Build status header
        //
        sHdr.buffer = buffer
        sHdr.apply {
            pduId = STATUS_ID.toUShort()
            testAction = r.testAction.toUByte()
            spduSeqNo = ++r.spduSeqNo

            //
            // Include last saved sub-interval statistics
            //
            subIntSeqNo = r.subIntSeqNo
            sisSav = sisSavBuf.array()

            //
            // Include sequence error loss and out-of-order
            //
            seqErrLoss = r.seqErrLoss
            seqErrOoo = r.seqErrOoo
            seqErrDup = r.seqErrDup

            //
            // Include delay info
            //
            clockDeltaMin = r.clockDeltaMin.toUInt()
            delayVarMin = r.delayVarMin
            delayVarMax = r.delayVarMax
            delayVarSum = r.delayVarSum
            delayVarCnt = r.delayVarCnt
            rttMinimum = r.rttMinimum
            rttVarSample = r.rttVarSample
            delayMinUpd = if (r.delayMinUpd) 1u else 0u

            //
            // Include trial interval info
            //
            val delta = systemClock - r.trialIntClock
            r.tiDeltaTime = ((delta + NSECADJ) / NSECINUSEC).toUInt()
            tiDeltaTime = r.tiDeltaTime
            tiRxDatagrams = r.tiRxDatagrams
            tiRxBytes = r.tiRxBytes

            //
            // Include time reference for this status PDU
            //
            spduTimeSec = (systemClock / NSECINSEC).toUInt()
            spduTimeNsec = (systemClock % NSECINSEC).toUInt()

            //
            // Maintain authentication mode setting
            //
            if (CLIENT_VER_IS_LATEST and r.keyString.isNotEmpty())
                authMode = AUTHMODE_1.toUByte()
        }

        //
        // Output verbose/debug messages if configured
        //
        if (r.delayMinUpd && r.rttMinimum != STATUS_NODEL) {
            outputMinimum()
        }
        if (debugLogging) {
            outputDebug(buffer)
        }

        //
        // Initialize values after copying to status message
        //
        r.seqErrLoss = 0u
        r.seqErrOoo = 0u
        r.seqErrDup  = 0u
        // Do not clear clock delta minimum
        r.delayVarMin = STATUS_NODEL
        r.delayVarMax = 0u
        r.delayVarSum = 0u
        r.delayVarCnt = 0u
        // Do not clear global RTT minimum
        r.rttVarSample = STATUS_NODEL
        r.delayMinUpd = false
        r.trialIntClock = systemClock
        r.tiDeltaTime = 0u
        r.tiRxDatagrams = 0u
        r.tiRxBytes = 0u

        //
        // Initialize or process sub-interval statistics. Because it is checked with each
        // status message, the sub-interval time has the granularity of the trial interval.
        //
        if (r.subIntClock == 0L) {
            procSubInterval(true)
        } else {
            var delta = systemClock - r.subIntClock
            delta = ((delta + NSECADJ_MSEC) / NSECINMSEC)
            if (delta > (DEF_SUBINT_PERIOD - (DEF_TRIAL_INT / 2))) {
                procSubInterval(false)
            }
        }
    }

    //
    // Process sub-interval expiry
    //
    private fun procSubInterval(initialize: Boolean) {
        //
        // If not doing initialization
        //
        if (!initialize) {
            //
            // Finalize active statistics for this sub-interval and save them
            //
            r.subIntSeqNo++
            val delta = systemClock - r.subIntClock
            sisAct.deltaTime = ((delta + NSECADJ) / NSECINUSEC).toUInt()
            r.accumTime += ((delta + NSECADJ_MSEC) / NSECINMSEC).toUInt()
            sisAct.accumTime = r.accumTime
            sisSavBuf.clear()
            sisSavBuf.put(sisActBuf)
            sisSavBuf.flip()

            outputCurRate()
        }

        //
        // (Re)initialize active sub-interval statistics after saving
        //
        zeroHeapForSis.rewind()
        sisActBuf.clear()
        sisActBuf.put(zeroHeapForSis)
        sisActBuf.flip()
        sisAct.delayVarMin = STATUS_NODEL
        sisAct.rttVarMinimum = STATUS_NODEL
        r.subIntClock = systemClock
        if (initialize)
            r.accumTime = 0u
    }

    //
    // Process Status Message PDU for data exchange
    //
    private fun serviceStatusPdu(buffer: ByteBuffer) {
        sHdr.buffer = buffer

        if (sHdr.pduId != STATUS_ID.toUShort())
            return

        r.testAction = sHdr.testAction.toUInt()
        if (r.testAction != TEST_ACT_TEST)
            Log.d("UdpManager", "Test stop received")

        //
        // Check for status message sequence errors
        //
        r.spduSeqErr = 0u
        val seqno = sHdr.spduSeqNo
        if (seqno >= r.spduSeqNo + 1u) {
            if (seqno > r.spduSeqNo + 1u) {
                r.spduSeqErr = seqno - r.spduSeqNo - 1u
            }
            r.spduSeqNo = seqno
        } else {
            r.spduSeqErr = UInt.MAX_VALUE // Signal reordered with special value
        }

        //
        // Save sequence error loss, out-of-order, and duplicate stats
        //
        r.seqErrLoss = sHdr.seqErrLoss
        r.seqErrOoo = sHdr.seqErrOoo
        r.seqErrDup = sHdr.seqErrDup

        //
        // Save delay info
        //
        r.clockDeltaMin = sHdr.clockDeltaMin.toLong()
        r.delayVarMin = sHdr.delayVarMin
        r.delayVarMax = sHdr.delayVarMax
        r.delayVarSum = sHdr.delayVarSum
        r.delayVarCnt = sHdr.delayVarCnt
        r.rttMinimum = sHdr.rttMinimum
        r.rttVarSample = sHdr.rttVarSample
        r.delayMinUpd = sHdr.delayMinUpd.toInt() != 0
        if (r.rttVarSample != STATUS_NODEL) {
            r.rttVarSum += r.rttVarSample
            r.rttVarCnt++
        }

        //
        // Save trial interval info
        //
        r.tiDeltaTime = sHdr.tiDeltaTime
        r.tiRxDatagrams = sHdr.tiRxDatagrams
        r.tiRxBytes = sHdr.tiRxBytes

        //
        // Save time reference for this status PDU
        //
        r.spduTimeSec = sHdr.spduTimeSec
        r.spduTimeNsec = sHdr.spduTimeNsec

        //
        // Copy sending rate parameters specified by server in this status message
        //
        sendingRateBuf.clear()
        sendingRateBuf.put(sHdr.srStruct)
        sendingRateBuf.flip()

        //
        // Output verbose/debug messages if configured
        //
        if (r.delayMinUpd && r.rttMinimum != STATUS_NODEL) {
            outputMinimum()
        }
        if (debugLogging) {
            outputDebug(buffer)
        }

        //
        // Copy last saved sub-interval statistics (as measured by peer receiver) if it is new, which
        // is detected by an updated sequence number
        //
        if (r.subIntSeqNo != sHdr.subIntSeqNo) {
            r.subIntSeqNo = sHdr.subIntSeqNo

            // Save received sub-interval statistics
            sisSavBuf.clear()
            sisSavBuf.put(sHdr.sisSav)
            sisSavBuf.flip()

            outputCurRate()
        }
    }

    //
    // Output current rate for sub-interval
    //
    private fun outputCurRate() {
        //
        // Do not allow sub-interval count to exceed expected maximum
        //
        if (r.subIntSeqNo > ((DEF_TESTINT_TIME * MSECINSEC) / MSECINSEC).toUInt())
            return

        //
        // Delivered percentage
        //
        var delivered = 0.0
        val sent = sisSav.rxDatagrams.toDouble() + sisSav.seqErrLoss.toDouble()
        if (sent > 0.0) {
            delivered = (sisSav.rxDatagrams.toDouble() * 100.0) / sent
        }

        //
        // One-way delay variation
        //
        var dvmin = 0u
        var dvavg = 0u
        if (sisSav.delayVarCnt > 0u) {
            dvmin = sisSav.delayVarMin
            dvavg = (((sisSav.delayVarSum * 10u) / sisSav.delayVarCnt) + 5u) / 10u
        }

        //
        // Round-trip time variation
        //
        var rttmin = 0u
        if (sisSav.rttVarMinimum != STATUS_NODEL)
            rttmin = sisSav.rttVarMinimum
        var rttavg = 0u
        if (r.rttVarCnt > 0u)
            rttavg = (((r.rttVarSum * 10u) / r.rttVarCnt) + 5u) / 10u

        //
        // L3/IP receive data rate
        //
        var overhead = L3DG_OVERHEAD
        if (r.isIpv6) {
            overhead += IPV6_ADDSIZE
        }
        var rxMbps = sisSav.rxDatagrams.toDouble()
        rxMbps *= overhead
        rxMbps += sisSav.rxBytes.toDouble()
        rxMbps *= 8
        rxMbps /= sisSav.deltaTime.toDouble()
        if (rxMbps > r.rxMbpsMax)
            r.rxMbpsMax = rxMbps // Update new maximum

        //
        // Output sub-interval text
        //
        r.callback?.onDataExchanged(
            r.subIntSeqNo.toString(),
            "%6.2f".format(delivered),
            "${sisSav.seqErrLoss} / ${sisSav.seqErrOoo} / ${sisSav.seqErrDup}",
            "$dvmin / $dvavg / ${sisSav.delayVarMax}",
            "$rttmin / $rttavg / ${sisSav.rttVarMaximum}",
            "%6.2f".format(rxMbps)
        )

        //
        // Accumulate overall test summary statistics
        //
        r.tsRxDatagrams += sisSav.rxDatagrams
        r.tsSeqErrLoss += sisSav.seqErrLoss

        //
        // Re-initialize local RTT variation sum and count
        //
        r.rttVarSum = 0u
        r.rttVarCnt = 0u
    }

    //
    // Output updated minimums
    //
    private fun outputMinimum() {
        Log.d("UdpManager", "Minimum Round-Trip Time(ms): ${r.rttMinimum}")
    }

    //
    // Output debug info
    //
    private fun outputDebug(buffer: ByteBuffer) {
        sHdr.buffer = buffer
        Log.d("UdpManager", "Status Message: testAction=${sHdr.testAction}, spduSeqNo=${sHdr.spduSeqNo}")
    }

    //
    // Process test stop
    //
    fun stop() {
        setupPending.set(false)
        isRunning.set(false)
        r.channel?.close()
        r.channel = null

        //
        // Clear buffers
        //
        receiveBuffer.clear()
        sendBuffer.clear()
        sendingRateBuf.clear()
        sisActBuf.clear()
        sisSavBuf.clear()
    }
}
