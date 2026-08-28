"use strict";

const HistoryMinuteMs = 60_000;
const HistoryDayMs = 24 * 60 * HistoryMinuteMs;

function finiteNumber(value) {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function historyMinuteBucket(timestampMs) {
  return Math.floor(timestampMs / HistoryMinuteMs) * HistoryMinuteMs;
}

function historyDayStart(timestampMs) {
  return Math.floor(timestampMs / HistoryDayMs) * HistoryDayMs;
}

function sourceTimestamp(report) {
  return finiteNumber(report.lastSeenAtMs) || finiteNumber(report.lastPublishedAtMs);
}

function publicationTimestamp(report, sourceAtMs) {
  return finiteNumber(report.lastPublishedAtMs) || sourceAtMs;
}

function gatewayLabel(report) {
  return typeof report.gatewayLabel === "string" && report.gatewayLabel.trim()
    ? report.gatewayLabel.trim().slice(0, 80)
    : "WILD reporting station";
}

/**
 * Converts one station report to the compact schema already read by the
 * dashboard. Undefined values are deliberately omitted: a sparse packet must
 * never erase the last complete values from a different station.
 */
function serverHistorySample(report) {
  const sourceAtMs = sourceTimestamp(report);
  if (sourceAtMs === null) return null;
  const publishedAtMs = publicationTimestamp(report, sourceAtMs);

  const rssiDbm = finiteNumber(report.rssiDbm);
  const batteryVolts = finiteNumber(report.batteryVolts);
  const storageUsedPercent = finiteNumber(report.storageUsedPercent);
  const recordingSeconds = finiteNumber(report.recordingSeconds);

  // Connected state alone is useful for the current fleet table, but is not a
  // health sample. Persist only observations that can contribute to history.
  if (rssiDbm === null && batteryVolts === null && storageUsedPercent === null && recordingSeconds === null) {
    return null;
  }

  const sample = {
    // History follows the low-rate gateway heartbeat. `a` preserves the time
    // of the advertisement that supplied the health fields, letting a station
    // carry a complete packet forward without pretending it was newly seen.
    t: historyMinuteBucket(publishedAtMs),
    a: sourceAtMs,
    c: report.connected === true,
    g: report.recording === true,
  };
  if (rssiDbm !== null) sample.r = Math.round(rssiDbm);
  if (batteryVolts !== null) sample.v = Math.round(batteryVolts * 100);
  if (storageUsedPercent !== null) sample.p = Math.round(storageUsedPercent);
  if (recordingSeconds !== null) sample.d = Math.max(0, Math.round(recordingSeconds));
  return sample;
}

/**
 * The trigger runs for every status write. Limit its *additional* Firestore
 * write to one per station and minute; the clients keep their existing slower
 * five-/fifteen-minute publish policy.
 */
function shouldWriteServerHistory(beforeReport, afterSample) {
  if (!afterSample) return false;
  const previousSample = beforeReport ? serverHistorySample(beforeReport) : null;
  return previousSample === null || previousSample.t !== afterSample.t;
}

function serverSampleMapKey(timestampMs) {
  return `m${Math.floor(timestampMs / HistoryMinuteMs)}`;
}

function historyDocumentId(stationDocumentId, timestampMs) {
  return `${stationDocumentId}_${historyDayStart(timestampMs)}`;
}

module.exports = {
  HistoryDayMs,
  gatewayLabel,
  historyDayStart,
  historyDocumentId,
  publicationTimestamp,
  serverHistorySample,
  serverSampleMapKey,
  shouldWriteServerHistory,
};
