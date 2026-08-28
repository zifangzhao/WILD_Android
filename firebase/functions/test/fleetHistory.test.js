"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  historyDayStart,
  historyDocumentId,
  serverHistorySample,
  serverSampleMapKey,
  shouldWriteServerHistory,
} = require("../fleetHistory");

test("converts a station health report to a minute-bucketed compact sample", () => {
  const report = {
    lastSeenAtMs: 1_725_000_061_234,
    lastPublishedAtMs: 1_725_000_122_000,
    rssiDbm: -67.4,
    batteryVolts: 3.812,
    storageUsedPercent: 42.4,
    recordingSeconds: 135.8,
    connected: true,
    recording: true,
  };

  assert.deepEqual(serverHistorySample(report), {
    t: 1_725_000_120_000,
    a: 1_725_000_061_234,
    r: -67,
    v: 381,
    p: 42,
    d: 136,
    c: true,
    g: true,
  });
});

test("skips reports with no time or no historic measurement", () => {
  assert.equal(serverHistorySample({ rssiDbm: -70 }), null);
  assert.equal(serverHistorySample({ lastPublishedAtMs: 1_725_000_000_000, connected: true }), null);
});

test("writes one server sample per station heartbeat minute", () => {
  const first = serverHistorySample({ lastPublishedAtMs: 1_725_000_001_000, rssiDbm: -70 });
  const sameMinute = serverHistorySample({ lastPublishedAtMs: 1_725_000_059_000, rssiDbm: -65 });
  const nextMinute = serverHistorySample({ lastPublishedAtMs: 1_725_000_061_000, rssiDbm: -63 });

  assert.equal(shouldWriteServerHistory(null, first), true);
  assert.equal(shouldWriteServerHistory({ lastPublishedAtMs: 1_725_000_001_000, rssiDbm: -70 }, sameMinute), false);
  assert.equal(shouldWriteServerHistory({ lastPublishedAtMs: 1_725_000_001_000, rssiDbm: -70 }, nextMinute), true);
});

test("keeps a complete advertisement on later station heartbeats", () => {
  const previous = { lastSeenAtMs: 1_725_000_001_000, lastPublishedAtMs: 1_725_000_001_000, batteryVolts: 3.8 };
  const later = { lastSeenAtMs: 1_725_000_001_000, lastPublishedAtMs: 1_725_000_301_000, batteryVolts: 3.8 };
  const sample = serverHistorySample(later);

  assert.equal(sample.a, previous.lastSeenAtMs);
  assert.equal(sample.t, 1_725_000_300_000);
  assert.equal(shouldWriteServerHistory(previous, sample), true);
});

test("uses a stable daily document and minute map key", () => {
  const timestampMs = 1_725_000_060_000;
  const dayStartMs = historyDayStart(timestampMs);
  assert.equal(historyDocumentId("station_device", timestampMs), `station_device_${dayStartMs}`);
  assert.equal(serverSampleMapKey(timestampMs), `m${Math.floor(timestampMs / 60_000)}`);
});
