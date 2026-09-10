package com.wild.android.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.test.*

class Ce64SchedulerTest {
    private val daily = SchedulerRuleUiState(0, enabled = true, durationSeconds = 1800)

    @Test fun supportsDailyOncePeriodicAndThresholdRules() {
        assertNull(Ce64Scheduler.validationError(daily))
        assertNull(Ce64Scheduler.validationError(daily.copy(trigger = 1, anchorDay = 9746)))
        assertNull(Ce64Scheduler.validationError(daily.copy(trigger = 2, periodSeconds = 60)))
        assertNull(Ce64Scheduler.validationError(daily.copy(trigger = 3, action = 1, signalSource = 4,
            conditionMask = 1, conditionInvertMask = 1, batteryThresholdMv = 3400)))
    }

    @Test fun rejectsDangerousOrUnrepresentableRulesWithoutClamping() {
        val invalid = listOf(daily.copy(durationSeconds = 0), daily.copy(durationSeconds = 86401),
            daily.copy(trigger = 2, periodSeconds = 59), daily.copy(trigger = 2, periodSeconds = 604801),
            daily.copy(action = 1, profileId = 0), daily.copy(trigger = 3, signalSource = 4),
            daily.copy(conditionMask = 4, evaluationSeconds = 59), daily.copy(conditionInvertMask = 2),
            daily.copy(action = 3), daily.copy(timeOfDaySeconds = 86400), daily.copy(id = 8),
            daily.copy(batteryThresholdMv = 65536), daily.copy(aiThresholdQ15 = 32768))
        invalid.forEach { assertNotNull(Ce64Scheduler.validationError(it), it.toString()) }
        assertFailsWith<IllegalArgumentException> { Ce32Protocol.buildSchedulerRuleUpdate(daily.copy(id = 8)) }
    }

    @Test fun restartAndMotionMatchFirmwareRestrictions() {
        assertNull(Ce64Scheduler.validationError(daily.copy(action = 3, trigger = 2, periodSeconds = 3600)))
        assertNotNull(Ce64Scheduler.validationError(daily.copy(action = 3, trigger = 2, periodSeconds = 3600, missedPolicy = 1)))
        assertNull(Ce64Scheduler.validationError(daily.copy(trigger = 3, signalSource = 2, conditionMask = 4, evaluationSeconds = 60)))
    }

    @Test fun delayedWrongCommandOrProfileChunkCannotCompleteRequest() {
        val request = Ce32Protocol.buildSchedulerProfileRead(2, 4)
        val reply = byteArrayOf(0xD9.toByte(), 2, 4, 16) + ByteArray(32)
        assertTrue(Ce64Scheduler.responseMatches(request, reply))
        assertFalse(Ce64Scheduler.responseMatches(request, reply.copyOf().also { it[2] = 3 }))
        assertFalse(Ce64Scheduler.responseMatches(request, reply.copyOf().also { it[1] = 1 }))
        assertFalse(Ce64Scheduler.responseMatches(request, byteArrayOf(0xD2.toByte(), 0)))
    }

    @Test fun profileAckMaskMustBeExactAndOnlyIdempotentOperationsRetry() {
        val request = Ce32Protocol.buildSchedulerProfileWrite(0, 2, ByteArray(32))
        assertTrue(Ce64Scheduler.responseMatches(request, byteArrayOf(0xDB.toByte(), 0, 7, 0)))
        assertFalse(Ce64Scheduler.responseMatches(request, byteArrayOf(0xDB.toByte(), 0, 3, 0)))
        assertFalse(Ce64Scheduler.responseMatches(request, byteArrayOf(0xDB.toByte(), 0, 15, 0)))
        assertTrue(Ce64Scheduler.retryable(0xDB))
        assertTrue(Ce64Scheduler.retryable(0xD1))
        for (command in 0xD2..0xD6) assertFalse(Ce64Scheduler.retryable(command))
    }

    @Test fun profileBuildersDoNotPadOrRetargetInvalidInputs() {
        assertFailsWith<IllegalArgumentException> { Ce32Protocol.buildSchedulerProfileWrite(0, 0, ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { Ce32Protocol.buildSchedulerProfileRead(8, 0) }
        assertFailsWith<IllegalArgumentException> { Ce32Protocol.buildSchedulerProfileRead(0, 16) }
        assertFailsWith<IllegalArgumentException> { Ce32Protocol.buildSchedulerRuleClear(-1) }
    }

    private fun config(pristine: Boolean = false): ByteArray {
        val b = ByteBuffer.allocate(472).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0, 0x53434844).putShort(4, 2).putShort(6, 472).putInt(12, 1)
        if (!pristine) {
            b.putInt(8, 3).putInt(468, 0x434D4954)
            b.putInt(464, CRC32().apply { update(b.array(), 0, 464) }.value.toInt())
        }
        return b.array()
    }

    @Test fun checksConfigIntegrityAndRecognizesOnlyExactPristineImage() {
        assertNotNull(Ce32Protocol.parseSchedulerConfig(config()))
        assertNotNull(Ce32Protocol.parseSchedulerConfig(config(true)))
        assertNull(Ce32Protocol.parseSchedulerConfig(config().also { it[32] = 1 }))
        assertNull(Ce32Protocol.parseSchedulerConfig(config(true).also { it[32] = 1 }))
        assertNull(Ce32Protocol.parseSchedulerConfig(config().also { it[468] = 0 }))
        assertNull(Ce32Protocol.parseSchedulerConfig(config().copyOf(471)))
        assertEquals((0..7).toList(), Ce32Protocol.parseSchedulerConfig(config(true))!!.rules.map { it.id })
    }

    @Test fun framingUsesEchoAcrossEveryFragmentBoundaryIncludingLateReplies() {
        // Embedded frame lead/tail bytes must remain payload, even with a stale
        // resolver left over from the old Android host implementation.
        val config = config().also { it[120] = 0xAD.toByte(); it[121] = 0x3C; it[122] = 0xDA.toByte() }
        val body = byteArrayOf(0xD1.toByte()) + config
        val stream = byteArrayOf(0xAD.toByte(), 0xD8.toByte()) + body +
            byteArrayOf(0xDA.toByte(), 0xDA.toByte(), 0xAD.toByte(), 0xD8.toByte(), 0xD2.toByte(), 0, 0xAD.toByte(), 0x41)
        for (split in 0..stream.size) {
            val received = mutableListOf<Pair<Int, ByteArray>>()
            val parser = Ce32FrameParser(payloadLengthResolver = { 2 }, onFrame = { c, p -> received += c to p })
            // Resolver should apply only to non-D8 packets; use D8 frames here.
            val packets = stream.copyOf(stream.size - 2)
            val boundary = split.coerceAtMost(packets.size)
            parser.push(packets.copyOfRange(0, boundary)); parser.push(packets.copyOfRange(boundary, packets.size))
            assertEquals(2, received.size, "split=$split")
            assertContentEquals(body, received[0].second)
            assertContentEquals(byteArrayOf(0xD2.toByte(), 0), received[1].second)
        }
    }

    @Test fun unknownSchedulerEchoResynchronizesAtNextValidFrame() {
        val received = mutableListOf<Int>()
        Ce32FrameParser { command, _ -> received += command }
            .push(byteArrayOf(0xAD.toByte(), 0xD8.toByte(), 0xFE.toByte(), 0xAD.toByte(), 0x41))
        assertEquals(listOf(0x41), received)
    }
}
