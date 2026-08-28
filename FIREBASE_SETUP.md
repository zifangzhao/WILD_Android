# WILD Firebase fleet setup

The Android app publishes a compact fleet status to Firestore. It does not upload waveform samples, recording files, raw BLE addresses, command bytes, or local file paths.

## One-time Firebase Console setup

1. Open **Build > Firestore Database** and create the database in **Production mode**.
2. In the **Rules** tab, paste the contents of [`firebase/firestore.rules`](firebase/firestore.rules), then publish the rules.
3. Open **Build > Authentication > Sign-in method** and enable **Anonymous** and
   **Email/Password**. Anonymous sign-in lets a gateway phone publish immediately during
   commissioning; use the shared email/password account when two phones need to see the
   same fleet.

The rules make each fleet private to one Firebase Auth user. They are intentionally not Test Mode rules.

## Use on two phones

1. On the gateway phone, open **Cloud fleet**, create a shared email/password account, and keep the app running while it is connected to WILD devices.
2. On the viewing phone, install this same WILD app, open **Cloud fleet**, and sign in with the same account.
3. The remote fleet view updates from Firestore. It is status-only in this release; no remote BLE command is sent.

## Firestore data layout

```
fleets/{firebase-auth-uid}
  gateways/{gateway-id}
  devices/{gateway-id}_{hashed-device-key}
```

Each device document contains only display name, connection/recording/live state, RSSI, battery/storage values when supplied by the device, firmware labels, and last-seen/published times.
