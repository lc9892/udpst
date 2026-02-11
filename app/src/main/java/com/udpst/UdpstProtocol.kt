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

import java.nio.ByteBuffer
import java.nio.ByteOrder

//
// Client will operate as latest version, else legacy version
//
const val CLIENT_VER_IS_LATEST = false

//
// ---- UDPST Protocol Constants ----
//
val PROTOCOL_VER = if (CLIENT_VER_IS_LATEST) 20 else 11
val DEF_CONTROL_PORT = if (CLIENT_VER_IS_LATEST) 24601 else 25000
const val SIZE_U8   = 1
const val SIZE_U16  = 2
const val SIZE_U32  = 4
const val SIZE_U64  = 8
const val MSECINSEC = 1000L
const val NSECINSEC = 1000000000L
const val NSECINMSEC = 1000000L
const val NSECINUSEC = 1000L
const val NSECADJ = (NSECINUSEC / 2)
const val NSECADJ_MSEC = (NSECINMSEC / 2)
const val SENDTIMER_ADJ = (NSECINUSEC * 10)
const val BURST_LIMIT = 10 // Burst limit for this client
const val TIMEOUT_NOTRAFFIC = 3000L
const val L3DG_OVERHEAD = (8 + 20)
const val IPV6_ADDSIZE = 20
const val DEF_TESTINT_TIME = 10
const val DEF_SUBINT_PERIOD = 1000
const val DEF_TRIAL_INT = 50
const val MAX_CLIENT_BW = Int.MAX_VALUE // (MSb for direction [CHSR_USDIR_BIT])
const val MAX_KEY_ID = UByte.MAX_VALUE
//-----------------------------------
const val CHSR_ID = 0xACE1
const val CHSR_CRSP_NONE = 0
const val CHSR_CRSP_ACKOK = 1
const val CHSR_CRSP_BADVER = 2
const val CHSR_CRSP_BADJS = 3
const val CHSR_CRSP_AUTHNC = 4
const val CHSR_CRSP_AUTHREQ = 5
const val CHSR_CRSP_AUTHINV = 6
const val CHSR_CRSP_AUTHFAIL = 7
const val CHSR_CRSP_AUTHTIME = 8
const val CHSR_CRSP_NOMAXBW = 9
const val CHSR_CRSP_CAPEXC = 10
const val CHSR_CRSP_BADTMTU = 11
const val CHSR_CRSP_MCINVPAR = 12
const val CHSR_CRSP_CONNFAIL = 13
const val CHSR_USDIR_BIT = 0x8000u
const val AUTHMODE_0 = 0.toByte() // Mode 0: Unauthenticated (unofficial)
const val AUTHMODE_1 = 1.toByte() // Mode 1: Authenticated Control
const val AUTH_DIGEST_LENGTH = 32
const val SHA256_KEY_LEN = 32
//-----------------------------------
const val CHNR_ID = 0xDEAD
//-----------------------------------
const val CHTA_ID = 0xACE2
const val CHTA_CREQ_TESTACTUS = 1
const val CHTA_CREQ_TESTACTDS = 2
const val CHTA_CRSP_NONE = 0
const val CHTA_CRSP_ACKOK = 1
const val CHTA_CRSP_BADPARAM = 2
const val SRATE_RAND_BIT = 0x80000000u
const val CHTA_SRIDX_DEF = UInt.MAX_VALUE
const val CHTA_RA_ALGO_B = 0u
const val CHTA_RA_ALGO_C = 1u
//-----------------------------------
const val LOAD_ID = 0xBEEF
const val TEST_ACT_TEST = 0u
const val LPDU_HISTORY_SIZE = 32 // Size must be power of 2
const val LPDU_HISTORY_MASK = (LPDU_HISTORY_SIZE - 1)
//-----------------------------------
const val STATUS_ID = 0xFEED
const val STATUS_NODEL = UInt.MAX_VALUE
//-----------------------------------

//
// Base class for various PDU types
//
open class DatagramPDU(open val buffer: ByteBuffer) {
    fun getUByte(offset: Int): UByte = buffer.get(offset).toUByte()
    fun putUByte(offset: Int, value: UByte) { buffer.put(offset, value.toByte()) }

    fun getUShort(offset: Int): UShort = buffer.getShort(offset).toUShort()
    fun putUShort(offset: Int, value: UShort) { buffer.putShort(offset, value.toShort()) }

    fun getUInt(offset: Int): UInt = buffer.getInt(offset).toUInt()
    fun putUInt(offset: Int, value: UInt) { buffer.putInt(offset, value.toInt()) }

    fun getULong(offset: Int): ULong = buffer.getLong(offset).toULong()
    fun putULong(offset: Int, value: ULong) { buffer.putLong(offset, value.toLong()) }

    //
    // Reads a sequence of bytes from the buffer at a specific offset
    // ...safely handles both direct and heap buffers
    //
    fun getBytes(offset: Int, length: Int): ByteArray {
        val dst = ByteArray(length)
        // Duplicate the buffer to avoid changing the original buffer's position
        val tempBuffer = buffer.duplicate()
        tempBuffer.position(offset)
        tempBuffer.get(dst, 0, length)
        return dst
    }

    //
    // Writes a source byte array into the buffer at a specific offset
    // ...safely handles both direct and heap buffers
    //
    fun putBytes(offset: Int, src: ByteArray) {
        // Duplicate the buffer to avoid changing the original buffer's position
        val tempBuffer = buffer.duplicate()
        tempBuffer.position(offset)
        tempBuffer.put(src)
    }
}

//
// ------- C Structure Wrappers (see udpst_protocol.h) -------
//
// Control header for UDP payload of Setup Request/Response PDUs
//
class ControlHdrSR(buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val PDUID_OFFSET: Int          = current.also { current += SIZE_U16 }
        val PROTOCOLVER_OFFSET: Int    = current.also { current += SIZE_U16 }
        val MCINDEX_OFFSET: Int        = current.also { current += SIZE_U8 }
        val MCCOUNT_OFFSET: Int        = current.also { current += SIZE_U8 }
        val MCIDENT_OFFSET: Int        = current.also { current += SIZE_U16 }
        val CMDREQUEST_OFFSET: Int     = current.also { current += SIZE_U8 }
        val CMDRESPONSE_OFFSET: Int    = current.also { current += SIZE_U8 }
        val MAXBANDWIDTH_OFFSET: Int   = current.also { current += SIZE_U16 }
        val TESTPORT_OFFSET: Int       = current.also { current += SIZE_U16 }
        val MODIFIERBITMAP_OFFSET: Int = current.also { current += SIZE_U8 }
        val AUTHMODE_OFFSET: Int       = current.also { current += SIZE_U8 }
        val AUTHUNIXTIME_OFFSET: Int   = current.also { current += SIZE_U32 }
        val AUTHDIGEST_OFFSET: Int     = current.also { current += AUTH_DIGEST_LENGTH }
        val KEYID_OFFSET: Int          = current.also { current += SIZE_U8 }
        val RESERVEDAUTH1_OFFSET: Int  = current.also { current += SIZE_U8 }
        val CHECKSUM_OFFSET: Int       = current.also { current += SIZE_U16 }
        val TOTAL_SIZE: Int = current

        //
        // Factory method for convenient allocation
        //
        fun createHeap(): ControlHdrSR {
            return ControlHdrSR(ByteBuffer.allocate(TOTAL_SIZE).order(ByteOrder.BIG_ENDIAN))
        }
    }

    var pduId: UShort
        get() = getUShort(PDUID_OFFSET)
        set(value) = putUShort(PDUID_OFFSET, value)
    var protocolVer: UShort
        get() = getUShort(PROTOCOLVER_OFFSET)
        set(value) = putUShort(PROTOCOLVER_OFFSET, value)
    var mcIndex: UByte
        get() = getUByte(MCINDEX_OFFSET)
        set(value) = putUByte(MCINDEX_OFFSET, value)
    var mcCount: UByte
        get() = getUByte(MCCOUNT_OFFSET)
        set(value) = putUByte(MCCOUNT_OFFSET, value)
    var mcIdent: UShort
        get() = getUShort(MCIDENT_OFFSET)
        set(value) = putUShort(MCIDENT_OFFSET, value)
    var cmdRequest: UByte
        get() = getUByte(CMDREQUEST_OFFSET)
        set(value) = putUByte(CMDREQUEST_OFFSET, value)
    var cmdResponse: UByte
        get() = getUByte(CMDRESPONSE_OFFSET)
        set(value) = putUByte(CMDRESPONSE_OFFSET, value)
    var maxBandwidth: UShort
        get() = getUShort(MAXBANDWIDTH_OFFSET)
        set(value) = putUShort(MAXBANDWIDTH_OFFSET, value)
    var testPort: UShort
        get() = getUShort(TESTPORT_OFFSET)
        set(value) = putUShort(TESTPORT_OFFSET, value)
    var modifierBitmap: UByte
        get() = getUByte(MODIFIERBITMAP_OFFSET)
        set(value) = putUByte(MODIFIERBITMAP_OFFSET, value)
    var authMode: UByte
        get() = getUByte(AUTHMODE_OFFSET)
        set(value) = putUByte(AUTHMODE_OFFSET, value)
    var authUnixTime: UInt
        get() = getUInt(AUTHUNIXTIME_OFFSET)
        set(value) = putUInt(AUTHUNIXTIME_OFFSET, value)
    var authDigest: ByteArray
        get() = getBytes(AUTHDIGEST_OFFSET, AUTH_DIGEST_LENGTH)
        set(value) = putBytes(AUTHDIGEST_OFFSET, value)
    var keyId: UByte
        get() = getUByte(KEYID_OFFSET)
        set(value) = putUByte(KEYID_OFFSET, value)
    var reservedAuth1: UByte
        get() = getUByte(RESERVEDAUTH1_OFFSET)
        set(value) = putUByte(RESERVEDAUTH1_OFFSET, value)
    var checkSum: UShort
        get() = getUShort(CHECKSUM_OFFSET)
        set(value) = putUShort(CHECKSUM_OFFSET, value)
}
//----------------------------------------------------------------------------
//
// Control header for UDP payload of Null Request PDU
//
class ControlHdrNR(buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val PDUID_OFFSET: Int         = current.also { current += SIZE_U16 }
        val PROTOCOLVER_OFFSET: Int   = current.also { current += SIZE_U16 }
        val CMDREQUEST_OFFSET: Int    = current.also { current += SIZE_U8 }
        val CMDRESPONSE_OFFSET: Int   = current.also { current += SIZE_U8 }
        val RESERVED1_OFFSET: Int     = current.also { current += SIZE_U8 }
        val AUTHMODE_OFFSET: Int      = current.also { current += SIZE_U8 }
        val AUTHUNIXTIME_OFFSET: Int  = current.also { current += SIZE_U32 }
        val AUTHDIGEST_OFFSET: Int    = current.also { current += AUTH_DIGEST_LENGTH }
        val KEYID_OFFSET: Int         = current.also { current += SIZE_U8 }
        val RESERVEDAUTH1_OFFSET: Int = current.also { current += SIZE_U8 }
        val CHECKSUM_OFFSET: Int      = current.also { current += SIZE_U16 }
        val TOTAL_SIZE: Int = current
    }

    var pduId: UShort
        get() = getUShort(PDUID_OFFSET)
        set(value) = putUShort(PDUID_OFFSET, value)
    var protocolVer: UShort
        get() = getUShort(PROTOCOLVER_OFFSET)
        set(value) = putUShort(PROTOCOLVER_OFFSET, value)
    var cmdRequest: UByte
        get() = getUByte(CMDREQUEST_OFFSET)
        set(value) = putUByte(CMDREQUEST_OFFSET, value)
    var cmdResponse: UByte
        get() = getUByte(CMDRESPONSE_OFFSET)
        set(value) = putUByte(CMDRESPONSE_OFFSET, value)
    var reserved1: UByte
        get() = getUByte(RESERVED1_OFFSET)
        set(value) = putUByte(RESERVED1_OFFSET, value)
    var authMode: UByte
        get() = getUByte(AUTHMODE_OFFSET)
        set(value) = putUByte(AUTHMODE_OFFSET, value)
    var authUnixTime: UInt
        get() = getUInt(AUTHUNIXTIME_OFFSET)
        set(value) = putUInt(AUTHUNIXTIME_OFFSET, value)
    var authDigest: ByteArray
        get() = getBytes(AUTHDIGEST_OFFSET, AUTH_DIGEST_LENGTH)
        set(value) = putBytes(AUTHDIGEST_OFFSET, value)
    var keyId: UByte
        get() = getUByte(KEYID_OFFSET)
        set(value) = putUByte(KEYID_OFFSET, value)
    var reservedAuth1: UByte
        get() = getUByte(RESERVEDAUTH1_OFFSET)
        set(value) = putUByte(RESERVEDAUTH1_OFFSET, value)
    var checkSum: UShort
        get() = getUShort(CHECKSUM_OFFSET)
        set(value) = putUShort(CHECKSUM_OFFSET, value)
}
//----------------------------------------------------------------------------
//
// Sending rate structure for a single row of transmission parameters
//
class SendingRate(buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val TXINTERVAL1_OFFSET: Int = current.also { current += SIZE_U32 }
        val UDPPAYLOAD1_OFFSET: Int = current.also { current += SIZE_U32 }
        val BURSTSIZE1_OFFSET: Int  = current.also { current += SIZE_U32 }
        val TXINTERVAL2_OFFSET: Int = current.also { current += SIZE_U32 }
        val UDPPAYLOAD2_OFFSET: Int = current.also { current += SIZE_U32 }
        val BURSTSIZE2_OFFSET: Int  = current.also { current += SIZE_U32 }
        val UDPADDON2_OFFSET: Int   = current.also { current += SIZE_U32 }
        val TOTAL_SIZE: Int = current
    }

    var txInterval1: UInt
        get() = getUInt(TXINTERVAL1_OFFSET)
        set(value) = putUInt(TXINTERVAL1_OFFSET, value)
    var udpPayload1: UInt
        get() = getUInt(UDPPAYLOAD1_OFFSET)
        set(value) = putUInt(UDPPAYLOAD1_OFFSET, value)
    var burstSize1: UInt
        get() = getUInt(BURSTSIZE1_OFFSET)
        set(value) = putUInt(BURSTSIZE1_OFFSET, value)
    var txInterval2: UInt
        get() = getUInt(TXINTERVAL2_OFFSET)
        set(value) = putUInt(TXINTERVAL2_OFFSET, value)
    var udpPayload2: UInt
        get() = getUInt(UDPPAYLOAD2_OFFSET)
        set(value) = putUInt(UDPPAYLOAD2_OFFSET, value)
    var burstSize2: UInt
        get() = getUInt(BURSTSIZE2_OFFSET)
        set(value) = putUInt(BURSTSIZE2_OFFSET, value)
    var udpAddon2: UInt
        get() = getUInt(UDPADDON2_OFFSET)
        set(value) = putUInt(UDPADDON2_OFFSET, value)
}
//
// Control header for UDP payload of Test Act. Request/Response PDUs
//
class ControlHdrTA(buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val PDUID_OFFSET: Int          = current.also { current += SIZE_U16 }
        val PROTOCOLVER_OFFSET: Int    = current.also { current += SIZE_U16 }
        val CMDREQUEST_OFFSET: Int     = current.also { current += SIZE_U8 }
        val CMDRESPONSE_OFFSET: Int    = current.also { current += SIZE_U8 }
        val LOWTHRESH_OFFSET: Int      = current.also { current += SIZE_U16 }
        val UPPERTHRESH_OFFSET: Int    = current.also { current += SIZE_U16 }
        val TRIALINT_OFFSET: Int       = current.also { current += SIZE_U16 }
        val TESTINTTIME_OFFSET: Int    = current.also { current += SIZE_U16 }
        val RESERVED1_OFFSET: Int      = current.also { current += SIZE_U8 }
        val DSCPECN_OFFSET: Int        = current.also { current += SIZE_U8 }
        val SRINDEXCONF_OFFSET: Int    = current.also { current += SIZE_U16 }
        val USEOWDELVAR_OFFSET: Int    = current.also { current += SIZE_U8 }
        val HIGHSPEEDDELTA_OFFSET: Int = current.also { current += SIZE_U8 }
        val SLOWADJTHRESH_OFFSET: Int  = current.also { current += SIZE_U16 }
        val SEQERRTHRESH_OFFSET: Int   = current.also { current += SIZE_U16 }
        val IGNOREOOODUP_OFFSET: Int   = current.also { current += SIZE_U8 }
        val MODIFIERBITMAP_OFFSET: Int = current.also { current += SIZE_U8 }
        val RATEADJALGO_OFFSET: Int    = current.also { current += SIZE_U8 }
        val RESERVED2_OFFSET: Int      = current.also { current += SIZE_U8 }
        val SRSTRUCT_OFFSET: Int       = current.also { current += SendingRate.TOTAL_SIZE }
        val SUBINTPERIOD_OFFSET: Int   = current.also { current += SIZE_U16 }
        val RESERVED3_OFFSET: Int      = current.also { current += SIZE_U16 }
        val LEGACY_SIZE: Int = current
        val RESERVED4_OFFSET: Int      = current.also { current += SIZE_U16 }
        val RESERVED5_OFFSET: Int      = current.also { current += SIZE_U8 }
        val AUTHMODE_OFFSET: Int       = current.also { current += SIZE_U8 }
        val AUTHUNIXTIME_OFFSET: Int   = current.also { current += SIZE_U32 }
        val AUTHDIGEST_OFFSET: Int     = current.also { current += AUTH_DIGEST_LENGTH }
        val KEYID_OFFSET: Int          = current.also { current += SIZE_U8 }
        val RESERVEDAUTH1_OFFSET: Int  = current.also { current += SIZE_U8 }
        val CHECKSUM_OFFSET: Int       = current.also { current += SIZE_U16 }
        val TOTAL_SIZE: Int = if (CLIENT_VER_IS_LATEST) current else LEGACY_SIZE

        //
        // Factory method for convenient allocation
        //
        fun createHeap(): ControlHdrTA {
            return ControlHdrTA(ByteBuffer.allocate(TOTAL_SIZE).order(ByteOrder.BIG_ENDIAN))
        }
    }

    var pduId: UShort
        get() = getUShort(PDUID_OFFSET)
        set(value) = putUShort(PDUID_OFFSET, value)
    var protocolVer: UShort
        get() = getUShort(PROTOCOLVER_OFFSET)
        set(value) = putUShort(PROTOCOLVER_OFFSET, value)
    var cmdRequest: UByte
        get() = getUByte(CMDREQUEST_OFFSET)
        set(value) = putUByte(CMDREQUEST_OFFSET, value)
    var cmdResponse: UByte
        get() = getUByte(CMDRESPONSE_OFFSET)
        set(value) = putUByte(CMDRESPONSE_OFFSET, value)
    var lowThresh: UShort
        get() = getUShort(LOWTHRESH_OFFSET)
        set(value) = putUShort(LOWTHRESH_OFFSET, value)
    var upperThresh: UShort
        get() = getUShort(UPPERTHRESH_OFFSET)
        set(value) = putUShort(UPPERTHRESH_OFFSET, value)
    var trialInt: UShort
        get() = getUShort(TRIALINT_OFFSET)
        set(value) = putUShort(TRIALINT_OFFSET, value)
    var testIntTime: UShort
        get() = getUShort(TESTINTTIME_OFFSET)
        set(value) = putUShort(TESTINTTIME_OFFSET, value)
    var reserved1: UByte
        get() = getUByte(RESERVED1_OFFSET)
        set(value) = putUByte(RESERVED1_OFFSET, value)
    var dscpEcn: UByte
        get() = getUByte(DSCPECN_OFFSET)
        set(value) = putUByte(DSCPECN_OFFSET, value)
    var srIndexConf: UShort
        get() = getUShort(SRINDEXCONF_OFFSET)
        set(value) = putUShort(SRINDEXCONF_OFFSET, value)
    var useOwDelVar: UByte
        get() = getUByte(USEOWDELVAR_OFFSET)
        set(value) = putUByte(USEOWDELVAR_OFFSET, value)
    var highSpeedDelta: UByte
        get() = getUByte(HIGHSPEEDDELTA_OFFSET)
        set(value) = putUByte(HIGHSPEEDDELTA_OFFSET, value)
    var slowAdjThresh: UShort
        get() = getUShort(SLOWADJTHRESH_OFFSET)
        set(value) = putUShort(SLOWADJTHRESH_OFFSET, value)
    var seqErrThresh: UShort
        get() = getUShort(SEQERRTHRESH_OFFSET)
        set(value) = putUShort(SEQERRTHRESH_OFFSET, value)
    var ignoreOooDup: UByte
        get() = getUByte(IGNOREOOODUP_OFFSET)
        set(value) = putUByte(IGNOREOOODUP_OFFSET, value)
    var modifierBitmap: UByte
        get() = getUByte(MODIFIERBITMAP_OFFSET)
        set(value) = putUByte(MODIFIERBITMAP_OFFSET, value)
    var rateAdjAlgo: UByte
        get() = getUByte(RATEADJALGO_OFFSET)
        set(value) = putUByte(RATEADJALGO_OFFSET, value)
    var reserved2: UByte
        get() = getUByte(RESERVED2_OFFSET)
        set(value) = putUByte(RESERVED2_OFFSET, value)
    var srStruct: ByteArray
        get() = getBytes(SRSTRUCT_OFFSET, SendingRate.TOTAL_SIZE)
        set(value) = putBytes(SRSTRUCT_OFFSET, value)
    var subIntPeriod: UShort
        get() = getUShort(SUBINTPERIOD_OFFSET)
        set(value) = putUShort(SUBINTPERIOD_OFFSET, value)
    var reserved3: UShort
        get() = getUShort(RESERVED3_OFFSET)
        set(value) = putUShort(RESERVED3_OFFSET, value)
    var reserved4: UShort
        get() = getUShort(RESERVED4_OFFSET)
        set(value) = putUShort(RESERVED4_OFFSET, value)
    var reserved5: UByte
        get() = getUByte(RESERVED5_OFFSET)
        set(value) = putUByte(RESERVED5_OFFSET, value)
    var authMode: UByte
        get() = getUByte(AUTHMODE_OFFSET)
        set(value) = putUByte(AUTHMODE_OFFSET, value)
    var authUnixTime: UInt
        get() = getUInt(AUTHUNIXTIME_OFFSET)
        set(value) = putUInt(AUTHUNIXTIME_OFFSET, value)
    var authDigest: ByteArray
        get() = getBytes(AUTHDIGEST_OFFSET, AUTH_DIGEST_LENGTH)
        set(value) = putBytes(AUTHDIGEST_OFFSET, value)
    var keyId: UByte
        get() = getUByte(KEYID_OFFSET)
        set(value) = putUByte(KEYID_OFFSET, value)
    var reservedAuth1: UByte
        get() = getUByte(RESERVEDAUTH1_OFFSET)
        set(value) = putUByte(RESERVEDAUTH1_OFFSET, value)
    var checkSum: UShort
        get() = getUShort(CHECKSUM_OFFSET)
        set(value) = putUShort(CHECKSUM_OFFSET, value)
}
//----------------------------------------------------------------------------
//
// Load header for UDP payload of Load PDUs
//
class LoadHdr(override var buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val PDUID_OFFSET: Int         = current.also { current += SIZE_U16 }
        val TESTACTION_OFFSET: Int    = current.also { current += SIZE_U8 }
        val RXSTOPPED_OFFSET: Int     = current.also { current += SIZE_U8 }
        val LPDUSEQNO_OFFSET: Int     = current.also { current += SIZE_U32 }
        val UDPPAYLOAD_OFFSET: Int    = current.also { current += SIZE_U16 }
        val SPDUSEQERR_OFFSET: Int    = current.also { current += SIZE_U16 }
        val SPDUTIME_SEC_OFFSET: Int  = current.also { current += SIZE_U32 }
        val SPDUTIME_NSEC_OFFSET: Int = current.also { current += SIZE_U32 }
        val LPDUTIME_SEC_OFFSET: Int  = current.also { current += SIZE_U32 }
        val LPDUTIME_NSEC_OFFSET: Int = current.also { current += SIZE_U32 }
        val RTTRESPDELAY_OFFSET: Int  = current.also { current += SIZE_U16 }
        val CHECKSUM_OFFSET: Int      = current.also { current += SIZE_U16 }
        val TOTAL_SIZE: Int = current
    }

    var pduId: UShort
        get() = getUShort(PDUID_OFFSET)
        set(value) = putUShort(PDUID_OFFSET, value)
    var testAction: UByte
        get() = getUByte(TESTACTION_OFFSET)
        set(value) = putUByte(TESTACTION_OFFSET, value)
    var rxStopped: UByte
        get() = getUByte(RXSTOPPED_OFFSET)
        set(value) = putUByte(RXSTOPPED_OFFSET, value)
    var lpduSeqNo: UInt
        get() = getUInt(LPDUSEQNO_OFFSET)
        set(value) = putUInt(LPDUSEQNO_OFFSET, value)
    var udpPayload: UShort
        get() = getUShort(UDPPAYLOAD_OFFSET)
        set(value) = putUShort(UDPPAYLOAD_OFFSET, value)
    var spduSeqErr: UShort
        get() = getUShort(SPDUSEQERR_OFFSET)
        set(value) = putUShort(SPDUSEQERR_OFFSET, value)
    var spduTimeSec: UInt
        get() = getUInt(SPDUTIME_SEC_OFFSET)
        set(value) = putUInt(SPDUTIME_SEC_OFFSET, value)
    var spduTimeNsec: UInt
        get() = getUInt(SPDUTIME_NSEC_OFFSET)
        set(value) = putUInt(SPDUTIME_NSEC_OFFSET, value)
    var lpduTimeSec: UInt
        get() = getUInt(LPDUTIME_SEC_OFFSET)
        set(value) = putUInt(LPDUTIME_SEC_OFFSET, value)
    var lpduTimeNsec: UInt
        get() = getUInt(LPDUTIME_NSEC_OFFSET)
        set(value) = putUInt(LPDUTIME_NSEC_OFFSET, value)
    var rttRespDelay: UShort
        get() = getUShort(RTTRESPDELAY_OFFSET)
        set(value) = putUShort(RTTRESPDELAY_OFFSET, value)
    var checkSum: UShort
        get() = getUShort(CHECKSUM_OFFSET)
        set(value) = putUShort(CHECKSUM_OFFSET, value)
}
//----------------------------------------------------------------------------
//
// Sub-interval statistics structure for received traffic information
//
class SubIntStats(buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val RXDATAGRAMS_OFFSET: Int = current.also { current += SIZE_U32 }
        // Conditional C compiler padding for legacy versions
        val PADDING1_OFFSET: Int    = if (CLIENT_VER_IS_LATEST) {0} else { current.also { current += SIZE_U32 } }
        val RXBYTES_OFFSET: Int     = current.also { current += SIZE_U64 }
        val DELTATIME_OFFSET: Int   = current.also { current += SIZE_U32 }
        val SEQERRLOSS_OFFSET: Int  = current.also { current += SIZE_U32 }
        val SEQERROOO_OFFSET: Int   = current.also { current += SIZE_U32 }
        val SEQERRDUP_OFFSET: Int   = current.also { current += SIZE_U32 }
        val DELAYVARMIN_OFFSET: Int = current.also { current += SIZE_U32 }
        val DELAYVARMAX_OFFSET: Int = current.also { current += SIZE_U32 }
        val DELAYVARSUM_OFFSET: Int = current.also { current += SIZE_U32 }
        val DELAYVARCNT_OFFSET: Int = current.also { current += SIZE_U32 }
        val RTTMINIMUM_OFFSET: Int  = current.also { current += SIZE_U32 }
        val RTTMAXIMUM_OFFSET: Int  = current.also { current += SIZE_U32 }
        val ACCUMTIME_OFFSET: Int   = current.also { current += SIZE_U32 }
        // Conditional C compiler padding for legacy versions
        val PADDING2_OFFSET: Int    = if (CLIENT_VER_IS_LATEST) {0} else { current.also { current += SIZE_U32 } }
        val TOTAL_SIZE: Int = current
    }

    var rxDatagrams: UInt
        get() = getUInt(RXDATAGRAMS_OFFSET)
        set(value) = putUInt(RXDATAGRAMS_OFFSET, value)
    var rxBytes: ULong
        get() = getULong(RXBYTES_OFFSET)
        set(value) = putULong(RXBYTES_OFFSET, value)
    var deltaTime: UInt
        get() = getUInt(DELTATIME_OFFSET)
        set(value) = putUInt(DELTATIME_OFFSET, value)
    var seqErrLoss: UInt
        get() = getUInt(SEQERRLOSS_OFFSET)
        set(value) = putUInt(SEQERRLOSS_OFFSET, value)
    var seqErrOoo: UInt
        get() = getUInt(SEQERROOO_OFFSET)
        set(value) = putUInt(SEQERROOO_OFFSET, value)
    var seqErrDup: UInt
        get() = getUInt(SEQERRDUP_OFFSET)
        set(value) = putUInt(SEQERRDUP_OFFSET, value)
    var delayVarMin: UInt
        get() = getUInt(DELAYVARMIN_OFFSET)
        set(value) = putUInt(DELAYVARMIN_OFFSET, value)
    var delayVarMax: UInt
        get() = getUInt(DELAYVARMAX_OFFSET)
        set(value) = putUInt(DELAYVARMAX_OFFSET, value)
    var delayVarSum: UInt
        get() = getUInt(DELAYVARSUM_OFFSET)
        set(value) = putUInt(DELAYVARSUM_OFFSET, value)
    var delayVarCnt: UInt
        get() = getUInt(DELAYVARCNT_OFFSET)
        set(value) = putUInt(DELAYVARCNT_OFFSET, value)
    var rttMinimum: UInt
        get() = getUInt(RTTMINIMUM_OFFSET)
        set(value) = putUInt(RTTMINIMUM_OFFSET, value)
    var rttMaximum: UInt
        get() = getUInt(RTTMAXIMUM_OFFSET)
        set(value) = putUInt(RTTMAXIMUM_OFFSET, value)
    var accumTime: UInt
        get() = getUInt(ACCUMTIME_OFFSET)
        set(value) = putUInt(ACCUMTIME_OFFSET, value)
}
//
// Status feedback header for UDP payload of status PDUs
//
class StatusHdr(override var buffer: ByteBuffer) : DatagramPDU(buffer) {
    companion object Offsets {
        private var current = 0
        val PDUID_OFFSET: Int         = current.also { current += SIZE_U16 }
        val TESTACTION_OFFSET: Int    = current.also { current += SIZE_U8 }
        val RXSTOPPED_OFFSET: Int     = current.also { current += SIZE_U8 }
        val SPDUSEQNO_OFFSET: Int     = current.also { current += SIZE_U32 }
        val SRSTRUCT_OFFSET: Int      = current.also { current += SendingRate.TOTAL_SIZE }
        val SUBINTSEQNO_OFFSET: Int   = current.also { current += SIZE_U32 }
        val SISSAV_OFFSET: Int        = current.also { current += SubIntStats.TOTAL_SIZE }
        val SEQERRLOSS_OFFSET: Int    = current.also { current += SIZE_U32 }
        val SEQERROOO_OFFSET: Int     = current.also { current += SIZE_U32 }
        val SEQERRDUP_OFFSET: Int     = current.also { current += SIZE_U32 }
        val CLOCKDELTAMIN_OFFSET: Int = current.also { current += SIZE_U32 }
        val DELAYVARMIN_OFFSET: Int   = current.also { current += SIZE_U32 }
        val DELAYVARMAX_OFFSET: Int   = current.also { current += SIZE_U32 }
        val DELAYVARSUM_OFFSET: Int   = current.also { current += SIZE_U32 }
        val DELAYVARCNT_OFFSET: Int   = current.also { current += SIZE_U32 }
        val RTTMINIMUM_OFFSET: Int    = current.also { current += SIZE_U32 }
        val RTTVARSAMPLE_OFFSET: Int  = current.also { current += SIZE_U32 }
        val DELAYMINUPD_OFFSET: Int   = current.also { current += SIZE_U8 }
        val RESERVED1_OFFSET: Int     = current.also { current += SIZE_U8 }
        val RESERVED2_OFFSET: Int     = current.also { current += SIZE_U16 }
        val TIDELTATIME_OFFSET: Int   = current.also { current += SIZE_U32 }
        val TIRXDATAGRAMS_OFFSET: Int = current.also { current += SIZE_U32 }
        val TIRXBYTES_OFFSET: Int     = current.also { current += SIZE_U32 }
        val SPDUTIME_SEC_OFFSET: Int  = current.also { current += SIZE_U32 }
        val SPDUTIME_NSEC_OFFSET: Int = current.also { current += SIZE_U32 }
        val LEGACY_SIZE: Int = current
        val RESERVED3_OFFSET: Int     = current.also { current += SIZE_U16 }
        val RESERVED4_OFFSET: Int     = current.also { current += SIZE_U8 }
        val AUTHMODE_OFFSET: Int      = current.also { current += SIZE_U8 }
        val AUTHUNIXTIME_OFFSET: Int  = current.also { current += SIZE_U32 }
        val AUTHDIGEST_OFFSET: Int    = current.also { current += AUTH_DIGEST_LENGTH }
        val KEYID_OFFSET: Int         = current.also { current += SIZE_U8 }
        val RESERVEDAUTH1_OFFSET: Int = current.also { current += SIZE_U8 }
        val CHECKSUM_OFFSET: Int      = current.also { current += SIZE_U16 }
        val TOTAL_SIZE: Int = if (CLIENT_VER_IS_LATEST) current else LEGACY_SIZE
    }

    var pduId: UShort
        get() = getUShort(PDUID_OFFSET)
        set(value) = putUShort(PDUID_OFFSET, value)
    var testAction: UByte
        get() = getUByte(TESTACTION_OFFSET)
        set(value) = putUByte(TESTACTION_OFFSET, value)
    var rxStopped: UByte
        get() = getUByte(RXSTOPPED_OFFSET)
        set(value) = putUByte(RXSTOPPED_OFFSET, value)
    var spduSeqNo: UInt
        get() = getUInt(SPDUSEQNO_OFFSET)
        set(value) = putUInt(SPDUSEQNO_OFFSET, value)
    var srStruct: ByteArray
        get() = getBytes(SRSTRUCT_OFFSET, SendingRate.TOTAL_SIZE)
        set(value) = putBytes(SRSTRUCT_OFFSET, value)
    var subIntSeqNo: UInt
        get() = getUInt(SUBINTSEQNO_OFFSET)
        set(value) = putUInt(SUBINTSEQNO_OFFSET, value)
    var sisSav: ByteArray
        get() = getBytes(SISSAV_OFFSET, SubIntStats.TOTAL_SIZE)
        set(value) = putBytes(SISSAV_OFFSET, value)
    var seqErrLoss: UInt
        get() = getUInt(SEQERRLOSS_OFFSET)
        set(value) = putUInt(SEQERRLOSS_OFFSET, value)
    var seqErrOoo: UInt
        get() = getUInt(SEQERROOO_OFFSET)
        set(value) = putUInt(SEQERROOO_OFFSET, value)
    var seqErrDup: UInt
        get() = getUInt(SEQERRDUP_OFFSET)
        set(value) = putUInt(SEQERRDUP_OFFSET, value)
    var clockDeltaMin: UInt
        get() = getUInt(CLOCKDELTAMIN_OFFSET)
        set(value) = putUInt(CLOCKDELTAMIN_OFFSET, value)
    var delayVarMin: UInt
        get() = getUInt(DELAYVARMIN_OFFSET)
        set(value) = putUInt(DELAYVARMIN_OFFSET, value)
    var delayVarMax: UInt
        get() = getUInt(DELAYVARMAX_OFFSET)
        set(value) = putUInt(DELAYVARMAX_OFFSET, value)
    var delayVarSum: UInt
        get() = getUInt(DELAYVARSUM_OFFSET)
        set(value) = putUInt(DELAYVARSUM_OFFSET, value)
    var delayVarCnt: UInt
        get() = getUInt(DELAYVARCNT_OFFSET)
        set(value) = putUInt(DELAYVARCNT_OFFSET, value)
    var rttMinimum: UInt
        get() = getUInt(RTTMINIMUM_OFFSET)
        set(value) = putUInt(RTTMINIMUM_OFFSET, value)
    var rttVarSample: UInt
        get() = getUInt(RTTVARSAMPLE_OFFSET)
        set(value) = putUInt(RTTVARSAMPLE_OFFSET, value)
    var delayMinUpd: UByte
        get() = getUByte(DELAYMINUPD_OFFSET)
        set(value) = putUByte(DELAYMINUPD_OFFSET, value)
    var reserved1: UByte
        get() = getUByte(RESERVED1_OFFSET)
        set(value) = putUByte(RESERVED1_OFFSET, value)
    var reserved2: UShort
        get() = getUShort(RESERVED2_OFFSET)
        set(value) = putUShort(RESERVED2_OFFSET, value)
    var tiDeltaTime: UInt
        get() = getUInt(TIDELTATIME_OFFSET)
        set(value) = putUInt(TIDELTATIME_OFFSET, value)
    var tiRxDatagrams: UInt
        get() = getUInt(TIRXDATAGRAMS_OFFSET)
        set(value) = putUInt(TIRXDATAGRAMS_OFFSET, value)
    var tiRxBytes: UInt
        get() = getUInt(TIRXBYTES_OFFSET)
        set(value) = putUInt(TIRXBYTES_OFFSET, value)
    var spduTimeSec: UInt
        get() = getUInt(SPDUTIME_SEC_OFFSET)
        set(value) = putUInt(SPDUTIME_SEC_OFFSET, value)
    var spduTimeNsec: UInt
        get() = getUInt(SPDUTIME_NSEC_OFFSET)
        set(value) = putUInt(SPDUTIME_NSEC_OFFSET, value)
    var reserved3: UShort
        get() = getUShort(RESERVED3_OFFSET)
        set(value) = putUShort(RESERVED3_OFFSET, value)
    var reserved4: UByte
        get() = getUByte(RESERVED4_OFFSET)
        set(value) = putUByte(RESERVED4_OFFSET, value)
    var authMode: UByte
        get() = getUByte(AUTHMODE_OFFSET)
        set(value) = putUByte(AUTHMODE_OFFSET, value)
    var authUnixTime: UInt
        get() = getUInt(AUTHUNIXTIME_OFFSET)
        set(value) = putUInt(AUTHUNIXTIME_OFFSET, value)
    var authDigest: ByteArray
        get() = getBytes(AUTHDIGEST_OFFSET, AUTH_DIGEST_LENGTH)
        set(value) = putBytes(AUTHDIGEST_OFFSET, value)
    var keyId: UByte
        get() = getUByte(KEYID_OFFSET)
        set(value) = putUByte(KEYID_OFFSET, value)
    var reservedAuth1: UByte
        get() = getUByte(RESERVEDAUTH1_OFFSET)
        set(value) = putUByte(RESERVEDAUTH1_OFFSET, value)
    var checkSum: UShort
        get() = getUShort(CHECKSUM_OFFSET)
        set(value) = putUShort(CHECKSUM_OFFSET, value)
}
//----------------------------------------------------------------------------
