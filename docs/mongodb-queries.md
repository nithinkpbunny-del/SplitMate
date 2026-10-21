# MongoDB queries for the live demo

Copy-paste commands to show, in front of the examiner, that everything SplitMate does is stored in MongoDB.
Each block is self-contained, so you can paste any of them on its own. All of them were run against the demo
data (`--spring.profiles.active=demo`).

## Setup

```bash
mongosh splitmate
```

Or open **MongoDB Compass**, connect to `mongodb://localhost:27017` and pick the `splitmate` database. If you
started the optional `docker compose up -d`, mongo-express is at http://localhost:8081 (login `admin` / `splitmate`).

Money is stored as integer **minor units** (cents): `amountMinor: 8640` is €86.40. Dates are real BSON dates.
Ids are `ObjectId`s.

---

## 1. What collections exist, and how much is in each

*Proves: the app persists everything in eight collections plus GridFS (`fs.files`, `fs.chunks`).*

```js
db.getCollectionNames().sort().forEach(c => print(c.padEnd(14), db.getCollection(c).countDocuments()))
```

With the seeded data this prints 12 categories, 43 expenses, 61 activity entries, 4 users, 2 splits, 3 settlement
snapshots, 4 payments, 2 reports and 2 GridFS files.

## 2. A user, without the password

*Proves: accounts are stored, and the password is never readable (a projection hides it, and it is a BCrypt hash anyway).*

```js
db.users.find({ email: "aarav@demo.com" }, { passwordHash: 0 })
```

```js
db.users.aggregate([
  { $project: { _id: 0, email: 1, name: 1, hashAlgorithm: { $substrBytes: ["$passwordHash", 0, 4] }, hashLength: { $strLenBytes: "$passwordHash" } } }
])
```

The second query shows only the start of the hash: `$2a$` means BCrypt, 60 characters. The real password
(`Demo@1234`) is nowhere in the database.

## 3. All splits a user belongs to

*Proves: membership is embedded in the split document (`members.userId`), and the lookup uses an index.*

```js
const isha = db.users.findOne({ email: "isha@demo.com" })._id
db.splits.find(
  { members: { $elemMatch: { userId: isha, active: true } } },
  { name: 1, status: 1, currency: 1, period: 1, "members.name": 1, "members.role": 1 }
)
```

```js
const isha = db.users.findOne({ email: "isha@demo.com" })._id
const plan = db.splits.find({ "members.userId": isha }).explain("queryPlanner").queryPlanner.winningPlan
print(JSON.stringify(plan, null, 1).includes("IXSCAN") ? "uses an index (IXSCAN)" : "collection scan")
```

## 4. The expenses of a split, newest first

*Proves: expenses are separate documents tied to a split, with the payer, the participants and the date.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.expenses.find(
  { splitId: split._id, deleted: false },
  { _id: 0, date: 1, description: 1, amountMinor: 1, paidBy: 1, participants: 1 }
).sort({ date: -1 }).limit(6)
```

## 5. Each person's exact share of one expense

*Proves: shares are computed on the server and add up to the cent. €68.50 among 3 people is 2284 + 2283 + 2283: the extra cent goes to the first participant (sorted by id).*

```js
const split = db.splits.findOne({ name: "October month split" })
const e = db.expenses.findOne({ splitId: split._id, description: "Dinner at Monsieur Vuong" })
printjson({ amountMinor: e.amountMinor, participants: e.participants.length, shares: e.shares.map(s => s.amountMinor) })
print("shares add up to the amount:", e.shares.reduce((sum, s) => sum + Number(s.amountMinor), 0) === Number(e.amountMinor))
```

## 6. Spend by category (aggregation with `$lookup`)

*Proves: MongoDB does the analysis. `$group` totals each category, `$lookup` joins the category names.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.expenses.aggregate([
  { $match: { splitId: split._id, deleted: false } },
  { $group: { _id: "$categoryId", totalMinor: { $sum: "$amountMinor" }, expenses: { $sum: 1 } } },
  { $lookup: { from: "categories", localField: "_id", foreignField: "_id", as: "category" } },
  { $unwind: "$category" },
  { $project: { _id: 0, category: "$category.name", totalMinor: 1, expenses: 1 } },
  { $sort: { totalMinor: -1 } }
])
```

Rent is 160000 (€1,600.00), more than half of the €3,057.06 total.

## 7. What each person paid (aggregation with `$lookup` to users)

*Proves: "Paid" in the app is a sum over the payer field.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.expenses.aggregate([
  { $match: { splitId: split._id, deleted: false } },
  { $group: { _id: "$paidBy", paidMinor: { $sum: "$amountMinor" }, expenses: { $sum: 1 } } },
  { $lookup: { from: "users", localField: "_id", foreignField: "_id", as: "user" } },
  { $unwind: "$user" },
  { $project: { _id: 0, person: "$user.name", paidMinor: 1, expenses: 1 } },
  { $sort: { paidMinor: -1 } }
])
```

## 8. What each person actually consumed ("Their share")

*Proves: "Their share" is a sum over the `shares` array (`$unwind` then `$group`). Paid minus share is the balance.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.expenses.aggregate([
  { $match: { splitId: split._id, deleted: false } },
  { $unwind: "$shares" },
  { $group: { _id: "$shares.userId", shareMinor: { $sum: "$shares.amountMinor" } } },
  { $lookup: { from: "users", localField: "_id", foreignField: "_id", as: "user" } },
  { $unwind: "$user" },
  { $project: { _id: 0, person: "$user.name", shareMinor: 1 } },
  { $sort: { shareMinor: -1 } }
])
```

## 9. The latest calculation snapshot

*Proves: every "Calculate split" stores an immutable, versioned snapshot with each person's Paid / Share / Net and the
transfers.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.settlements.find(
  { splitId: split._id },
  { _id: 0, version: 1, calculatedAt: 1, totalSpendMinor: 1, expenseCount: 1, "memberSummaries.name": 1, "memberSummaries.paidMinor": 1, "memberSummaries.shareMinor": 1, "memberSummaries.netMinor": 1, transfers: 1 }
).sort({ version: -1 }).limit(1)
```

## 10. The money adds up: all net balances sum to zero

*Proves: the core invariant of the calculation. What some people are owed is exactly what others owe.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.settlements.aggregate([
  { $match: { splitId: split._id } },
  { $sort: { version: -1 } },
  { $limit: 1 },
  { $unwind: "$memberSummaries" },
  { $group: { _id: "$version", sumOfNetBalances: { $sum: "$memberSummaries.netMinor" }, transfers: { $first: { $size: "$transfers" } } } }
])
```

`sumOfNetBalances` is `0`, and 4 people need at most 3 transfers.

## 11. Calculation history of a split

*Proves: snapshots are kept per version (the unique index `{splitId, version}` guarantees one document per version).*

```js
const split = db.splits.findOne({ name: "September month split" })
db.settlements.find({ splitId: split._id }, { _id: 0, version: 1, calculatedAt: 1, totalSpendMinor: 1 }).sort({ version: 1 })
db.settlements.find({ splitId: split._id, version: 2 }, { _id: 0, version: 1, transfers: 1 })
```

September version 1 had transfers; after everyone paid, version 2 has an empty `transfers` array (everyone is square).

## 12. Real-world payments

*Proves: "I paid you back" is its own collection and shifts the balances on the next calculation.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.payments.aggregate([
  { $match: { splitId: split._id } },
  { $lookup: { from: "users", localField: "fromUserId", foreignField: "_id", as: "from" } },
  { $lookup: { from: "users", localField: "toUserId", foreignField: "_id", as: "to" } },
  { $project: { _id: 0, from: { $first: "$from.name" }, to: { $first: "$to.name" }, amountMinor: 1, note: 1, createdAt: 1 } }
])
```

## 13. The activity timeline

*Proves: every action (create, join, add expense, calculate, pay, report) is logged with who did it and when.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.activity_logs.find({ splitId: split._id }, { _id: 0, createdAt: 1, type: 1, message: 1 }).sort({ createdAt: -1 }).limit(8)
```

```js
const split = db.splits.findOne({ name: "October month split" })
db.activity_logs.aggregate([
  { $match: { splitId: split._id } },
  { $group: { _id: "$type", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
])
```

## 14. Invite links

*Proves: each split has a unique, unguessable 12-character code, enforced by a unique index.*

```js
db.splits.find({}, { _id: 0, name: 1, inviteCode: 1, inviteEnabled: 1 })
db.splits.getIndexes().filter(i => i.name === "inviteCode").forEach(i => printjson(i))
```

## 15. Soft delete

*Proves: deleting an expense in the app keeps the document (`deleted: true`) but removes it from all maths.*

```js
db.expenses.aggregate([{ $group: { _id: "$deleted", count: { $sum: 1 } } }])
db.expenses.find({ deleted: true }, { _id: 0, description: 1, amountMinor: 1, deleted: 1, updatedAt: 1 })
```

Right after seeding nothing is deleted. During the demo, delete an expense in the app, rerun this query, and it
appears here with `deleted: true`.

## 16. Reports and the PDF stored in GridFS

*Proves: the report is a document with computed statistics and insights, and the PDF itself lives in GridFS.*

```js
const split = db.splits.findOne({ name: "October month split" })
db.reports.find(
  { splitId: split._id },
  { _id: 0, generatedAt: 1, periodStart: 1, periodEnd: 1, "stats.totalSpendMinor": 1, "stats.expenseCount": 1, "stats.avgPerDayMinor": 1, insights: 1, pdfFileName: 1 }
)
```

```js
db.fs.files.find({}, { filename: 1, length: 1, uploadDate: 1, "metadata.reportId": 1, "metadata.splitId": 1 })
db.fs.chunks.aggregate([
  { $group: { _id: "$files_id", chunks: { $sum: 1 }, bytes: { $sum: { $binarySize: "$data" } } } }
])
```

`fs.files` is the file's metadata; `fs.chunks` holds the bytes (a PDF this small is one chunk). The metadata links
each file back to its report and split.

## 17. Indexes

*Proves: indexes are created by the application (`@Indexed`, `@CompoundIndex`), not by hand.*

```js
["users", "splits", "expenses", "categories", "settlements", "payments", "reports", "activity_logs"].forEach(c => {
  print("== " + c)
  db.getCollection(c).getIndexes().forEach(i => print("   ", i.name.padEnd(28), JSON.stringify(i.key), i.unique ? " UNIQUE" : ""))
})
```

Notable: `email` is unique on `users`, `{splitId, version}` is unique on `settlements`, and `{splitId, createdAt}` backs
the activity timeline.

## 18. One query across collections: who owes what, per person

*Proves: the data model supports questions the UI never asks directly. Here, each person's balance recomputed from
raw expenses and payments, independently of the stored snapshot.*

```js
const split = db.splits.findOne({ name: "October month split" })
const net = {}
const add = (id, minor) => { const k = id.toHexString(); net[k] = (net[k] || 0) + Number(minor) }
db.expenses.find({ splitId: split._id, deleted: false }).forEach(e => {
  add(e.paidBy, e.amountMinor)
  e.shares.forEach(s => add(s.userId, -Number(s.amountMinor)))
})
db.payments.find({ splitId: split._id }).forEach(p => { add(p.fromUserId, p.amountMinor); add(p.toUserId, -Number(p.amountMinor)) })
Object.entries(net).forEach(([id, minor]) => print(db.users.findOne({ _id: ObjectId(id) }).name.padEnd(14), minor))
print("sum:", Object.values(net).reduce((a, b) => a + b, 0))
```

This is `net = paid − share + payments sent − payments received`, the same formula as the app. Expected: Aarav
71596, Isha −32108, Rohan −39488, Sneha 0, and the sum is `0`. Aarav's 71596 is the app's "+€715.96", which already
includes Sneha's €564.83 payment recorded after the last calculation.

---

## Watching the data change live

Keep one of these running in a second terminal while you use the app. Rerun it after each action to see the new
documents appear:

```js
db.activity_logs.find({}, { _id: 0, createdAt: 1, type: 1, message: 1 }).sort({ createdAt: -1 }).limit(5)
```
