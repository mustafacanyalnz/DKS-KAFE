const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cafe_owner";
const BOB_UID = "bob_other_owner";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tables`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/products`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/orders`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/staff`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/products/cay`).set({
      name: "Çay",
      category: "Sıcak İçecekler",
      price: 25.0,
      available: true,
      sortOrder: 1,
      updatedBy: "ErdiBasaran",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's cafe collections", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.doc(`users/${ALICE_UID}/products/cay`).set({
      name: "Çay",
      category: "Sıcak İçecekler",
      price: 25.0,
      available: true,
      sortOrder: 1,
      updatedBy: "ErdiBasaran",
      createdAt: now,
      updatedAt: now,
    });
    await db.doc(`users/${ALICE_UID}/tables/table_1`).set({
      tableNumber: 1,
      name: "Masa 1",
      occupied: false,
      waiterName: "",
      customerNote: "",
      items: [],
      totalAmount: 0.0,
      openedAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/products`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/products/cay`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/products/cay`).update({
      price: 1.0,
      updatedBy: "Bob",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/tables`).get());
});

test("Staff collection: valid create, password update, rejects role escalation & delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/staff/Garson`).set({
      username: "Garson",
      displayName: "Garson",
      role: "WAITER",
      passwordHash: "qTL2it8+hokKi3KWADc1vASl6nYxXvfBHJlmdsABHCU=",
      passwordSalt: "V+CdDwKZkK0wYPT0KY42Hg==",
      active: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject role escalation on update
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/staff/Garson`).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject delete
  await assertFails(aliceDb.doc(`users/${ALICE_UID}/staff/Garson`).delete());

  // Valid password update
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/staff/Garson`).update({
      passwordHash: "newValidHashBase64String1234567890==",
      passwordSalt: "newSaltBase641234==",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Products collection: valid create, price update, rejects ghost fields and negative price", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/products/cay`).set({
      name: "Çay",
      category: "Sıcak İçecekler",
      price: 25.0,
      available: true,
      sortOrder: 1,
      updatedBy: "ErdiBasaran",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid price update
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/products/cay`).update({
      price: 30.0,
      updatedBy: "MustafacanYalnz",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative price
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/products/cay`).update({
      price: -5.0,
      updatedBy: "ErdiBasaran",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' or ghost field
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/products/kahve`).set({
      id: "kahve",
      name: "Türk Kahvesi",
      category: "Sıcak İçecekler",
      price: 60.0,
      available: true,
      sortOrder: 2,
      updatedBy: "ErdiBasaran",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/products/future`).set({
      name: "Latte",
      category: "Sıcak İçecekler",
      price: 85.0,
      available: true,
      sortOrder: 3,
      updatedBy: "ErdiBasaran",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Tables & Orders: valid 20-table update and immutable completed order creation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/tables/table_1`).set({
      tableNumber: 1,
      name: "Masa 1",
      occupied: false,
      waiterName: "",
      customerNote: "",
      items: [],
      totalAmount: 0.0,
      openedAtMillis: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Open table with 2x Çay
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/tables/table_1`).update({
      occupied: true,
      waiterName: "Garson",
      customerNote: "Cam kenarı",
      items: [
        {
          productId: "cay",
          productName: "Çay",
          unitPrice: 25.0,
          quantity: 2,
          lineTotal: 50.0,
        },
      ],
      totalAmount: 50.0,
      openedAtMillis: Date.now() - 5000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject tableNumber > 20
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/tables/table_21`).set({
      tableNumber: 21,
      name: "Masa 21",
      occupied: false,
      waiterName: "",
      customerNote: "",
      items: [],
      totalAmount: 0.0,
      openedAtMillis: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create immutable completed order
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/orders/order_001`).set({
      tableNumber: 1,
      tableName: "Masa 1",
      waiterUsername: "Garson",
      closedByUsername: "ErdiBasaran",
      paymentMethod: "NAKİT",
      totalAmount: 50.0,
      itemCount: 2,
      itemsSummary: "2x Çay",
      items: [
        {
          productId: "cay",
          productName: "Çay",
          unitPrice: 25.0,
          quantity: 2,
          lineTotal: 50.0,
        },
      ],
      businessDate: "2026-11-25",
      closedAtMillis: Date.now() - 1000,
      status: "COMPLETED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Completed order cannot be modified or deleted (audit integrity)
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/orders/order_001`).update({
      totalAmount: 10.0,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/orders/order_001`).delete()
  );

  // List queries succeed on owner collections
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/tables`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/products`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/orders`).get());
});
