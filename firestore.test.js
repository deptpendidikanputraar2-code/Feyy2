const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write notifications or sync_profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notifications").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "Pesan Baru",
      body: "Tes",
      category: "chat",
      read: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sync_profiles").get());
});

test("Authenticated owner can create, list, mark read, and delete notifications (Kotlin-mirrored)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").set({
      userId: ALICE_UID,
      title: "Chat Majelis Umum",
      body: "Assalamu'alaikum Ustadz",
      category: "chat",
      channelId: "CHN-UMUM",
      read: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      read: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_2").set({
      id: "notif_2",
      userId: ALICE_UID,
      title: "Ghost",
      body: "Should fail",
      category: "chat",
      read: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_future").set({
      userId: ALICE_UID,
      title: "Future",
      body: "Tampered time",
      category: "chat",
      read: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notifications").doc("notif_1").delete()
  );
});

test("Authenticated owner can create and update sync_profile; cross-user access is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync_profiles").doc("main").set({
      userId: ALICE_UID,
      displayName: "Ust. Ahmad",
      mmqaRole: "dewan_harian",
      mmqaUsername: "admin",
      notificationsEnabled: true,
      lastSyncedSource: "Supabase + Firestore",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sync_profiles").doc("main").update({
      lastSyncedSource: "Supabase Cloud",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or write Alice's notifications or sync_profiles
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sync_profiles").doc("main").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sync_profiles").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("hijack").set({
      userId: ALICE_UID,
      title: "Hijack",
      body: "No",
      category: "system",
      read: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});
