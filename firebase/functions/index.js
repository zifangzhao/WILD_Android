"use strict";

const { initializeApp } = require("firebase-admin/app");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { logger } = require("firebase-functions");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const {
  gatewayLabel,
  historyDayStart,
  historyDocumentId,
  serverHistorySample,
  serverSampleMapKey,
  shouldWriteServerHistory,
} = require("./fleetHistory");

initializeApp();

const firestore = getFirestore();

/**
 * Captures every station's normal device-status heartbeat on the server.
 *
 * The status documents remain the per-station source of truth. This function
 * writes a compact minute sample into the corresponding daily history document
 * so PC and Android reporters are treated identically. The dashboard groups
 * those station traces by device name; it never needs to choose a single
 * station or let an incomplete report overwrite a richer one.
 */
exports.captureFleetHistory = onDocumentWritten(
  {
    document: "fleets/{ownerUid}/devices/{stationDocumentId}",
    region: "us-central1",
    maxInstances: 2,
  },
  async (event) => {
    const afterSnapshot = event.data?.after;
    if (!afterSnapshot?.exists) return;

    const afterReport = afterSnapshot.data();
    const beforeSnapshot = event.data?.before;
    const beforeReport = beforeSnapshot?.exists ? beforeSnapshot.data() : null;
    const sample = serverHistorySample(afterReport);
    if (!shouldWriteServerHistory(beforeReport, sample)) return;

    const ownerUid = event.params.ownerUid;
    const stationDocumentId = event.params.stationDocumentId;
    const dayStartMs = historyDayStart(sample.t);
    const historyReference = firestore
      .collection("fleets")
      .doc(ownerUid)
      .collection("history")
      .doc(historyDocumentId(stationDocumentId, sample.t));

    // serverSamples is a map rather than the phone writer's array. A map key
    // makes a retry idempotent and avoids a read-modify-write transaction.
    // It is a separate field, so it safely coexists with historical Android
    // array samples in the same document.
    await historyReference.set({
      deviceDocumentId: stationDocumentId,
      gatewayId: typeof afterReport.gatewayId === "string" ? afterReport.gatewayId : stationDocumentId,
      gatewayLabel: gatewayLabel(afterReport),
      displayName: typeof afterReport.displayName === "string" ? afterReport.displayName.slice(0, 80) : "WILD device",
      dayStartMs,
      lastServerSampleAtMs: sample.t,
      updatedAt: FieldValue.serverTimestamp(),
      serverSamples: {
        [serverSampleMapKey(sample.t)]: sample,
      },
    }, { merge: true });

    logger.debug("Captured fleet history sample", {
      ownerUid,
      stationDocumentId,
      timestampMs: sample.t,
    });
  },
);
