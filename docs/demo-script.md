# Demo script (about 7 minutes)

The story: a new user starts a split and invites a flatmate with a link, then the group's October split goes through
the whole loop (add an expense, calculate, pay someone back, recalculate, report), and every step is shown in MongoDB.

Query numbers like "Q9" refer to sections in [mongodb-queries.md](mongodb-queries.md).

## Before you start (not part of the 7 minutes)

1. MongoDB is running on `localhost:27017`.
2. Start from a clean demo database, so the numbers below match:
   ```bash
   mongosh splitmate --eval 'db.dropDatabase()'
   ```
3. Terminal 1, the backend (it fills the database with demo data on start):
   ```bash
   cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   Wait for `Demo data ready` in the log. It listens on **http://localhost:8090**.
4. Terminal 2, the frontend (**http://localhost:5173**):
   ```bash
   cd frontend && npm run dev
   ```
5. Windows:
   - **Window A**: a normal browser window at http://localhost:5173.
   - **Window B**: an incognito/private window, not open yet.
   - **Window C**: a terminal running `mongosh splitmate` (or MongoDB Compass connected to
     `mongodb://localhost:27017`, database `splitmate`). Put it beside the browser so the examiner sees both.
6. Nothing needs the internet. Everything runs on this machine.

Demo accounts (password `Demo@1234`): `aarav@demo.com` (owner of both splits), `isha@demo.com`, `rohan@demo.com`,
`sneha@demo.com`. The October split is open, has 30 expenses and a payment, and is out of date on purpose.

---

## Part A: a new person, a new split, an invite link (0:00 to 2:45)

### 1. Register (0:00)

**Window A.** Open the landing page and click **Get started**. Register as *Priya Sharma*, `priya@example.com`,
password `Demo@1234`. Point at the password strength meter while typing.

> Say: "Accounts are real. The password is hashed with BCrypt, and the login is a signed token in an httpOnly cookie."

**Show in MongoDB:** Q2 (user without the password, and only the start of the hash: `$2a$`, 60 characters).

You land on an empty dashboard with a friendly empty state.

### 2. Create a split (0:45)

Click **New split**. Name it `November month split`, pick the **November 2026** month, keep EUR, click **Create split**.

The share screen appears: the invite link in a mono field, **Copy** (watch the checkmark), Share, WhatsApp and a QR code.
Click **Copy**.

> Say: "Creating a split generates an unguessable 12-character invite code. Anyone with the link can join."

**Show in MongoDB:** Q14 (the code on the split, and the unique index on it) and Q13 (first entries in the timeline).

### 3. A second person joins by the link (1:45)

**Window B (incognito).** Paste the invite link. You are **logged out**, and still see the preview: "Priya invited you
to November month split", the members, the member count.

Click **Create an account to join**, register as *Dev Patel*, `dev@example.com`, `Demo@1234`.
After registering you are **returned to the join page automatically**. Click **Join split**.

> Say: "The preview is public but shows nothing private, only first names. After signing up the app remembers
> where you were going."

Back in **Window A**, click **Go to split** and refresh the page: the split now has 2 members.

**Show in MongoDB:** Q3 with `dev@example.com` instead of `isha@demo.com` (the split Dev belongs to, with the members
embedded). Joining twice cannot add Dev twice: the join is a single guarded update.

---

## Part B: the October split, end to end (2:45 to 6:15)

### 4. Switch to the shared flat (2:45)

**Window A.** Open the profile menu (top right), **Log out**, then log in as `aarav@demo.com` / `Demo@1234`.
Open **October month split**.

Point out: 30 expenses, total **€3,057.06**, the yellow "Expenses changed since the last calculation" banner, the
expense list styled like a receipt and grouped by day.

> Say: "Four flatmates, one month. Rent, groceries, a Prague weekend for three, Netflix for two."

### 5. Add an expense for only some people (3:15)

Press **N** (or click **Add expense**). Type the amount the European way: `24,50`. Description `Taxi back from Prague`,
category **Transport**. Set **Paid by** to *Rohan*. Under **Split with** click **Only me + …**, then switch **Rohan** on.

The live preview shows **€12.25 each × 2**.

> Say: "Shares are always computed on the server and add up to the cent. The preview uses the same rule."

Press **Ctrl/Cmd+Enter** (or click **Add expense**) to save. The expense appears in the list immediately and the total becomes
**€3,081.56**. (Plain Enter also saves while you are typing in a text field.)

**Show in MongoDB:** Q4 (the new expense: 2 participants, `amountMinor: 2450`) and Q5 (how a share is split, including the
odd cent).

### 6. Calculate the split (4:00)

Press **C** (or **Calculate split**). The "crunching numbers" animation plays for about a second, then the results appear:

| Person | Paid | Their share | Net |
|---|---|---|---|
| You (Aarav) | €2,052.34 | €783.80 | **+€703.71** (includes the €564.83 Sneha already paid back) |
| Sneha | €251.69 | €816.52 | **€0.00** |
| Isha | €312.13 | €633.21 | **−€321.08** |
| Rohan | €465.40 | €848.03 | **−€382.63** |

The graph shows two arrows into Aarav: **Rohan → Aarav €382.63** and **Isha → Aarav €321.08**.

> Say: "Paid is what someone actually put out. Their share is what they used. The difference is the balance, and the app
> finds the fewest transfers that settle everyone."

**Show in MongoDB:** Q9 (the new snapshot, version 2, with every person's Paid / Share / Net and the transfers) and
Q10 (the net balances add up to exactly `0`).

### 7. Record a payment and recalculate (5:00)

Click **Record payments**. You land on the **Balances** tab. Under the graph click **Record a payment** (it switches to the
list), then on the **Isha pays you €321.08** row click **Record payment**. The amount is prefilled; click the **PayPal**
chip, then **Record payment**.

The stale banner returns. Click **Recalculate**: now only **Rohan → Aarav €382.63** remains, and Isha shows "settled".

> Say: "Each calculation is a new, versioned snapshot, so there is a history of what was true when."

**Show in MongoDB:** Q12 (payments) and Q11 with `"October month split"` (three versions: 3 transfers, then 2, then 1).

### 8. The spending report (5:45)

Click **Generate spending report** on the results (or open the **Report** tab and click **Generate report**).

The report page shows the total, the average per day and per person, insights ("Rent made up 52% of all spending",
"Spending is up 36% compared with September month split"), the category donut, paid vs share, the day-by-day chart and
the who-used-what heat-map.

Click **Download PDF** and open it: cover, KPIs, categories, per-person table, who pays whom, biggest expenses, insights and
the full expense list, with page numbers.

**Show in MongoDB:** Q16 (the report document with its computed statistics, and the PDF as a GridFS file: `fs.files`
for the metadata and `fs.chunks` for the bytes).

---

## Part C: everything is in the database (6:15 to 7:00)

Run these three, one after another, while pointing at the screen:

1. **Q13**: the activity timeline. Every step you just did is there, with who did it: expense added, split calculated,
   payment recorded, report generated.
2. **Q1**: the collections and their counts (users, splits, expenses, categories, settlements, payments, reports,
   activity_logs, and the two GridFS collections).
3. **Q17**: the indexes, created by the application itself.

Optional, if there is time: delete an expense in the app, then run **Q15**. It is still in the database with
`deleted: true`, but no longer in anyone's balance.

> Say: "Java 21 and Spring Boot on the backend, MongoDB for all data with aggregation pipelines for the analysis,
> GridFS for the PDFs, React on the front. Money is stored as whole cents, never floating point."

---

## Cheat sheet: numbers to expect

| Moment | Value |
|---|---|
| Seeded October total / expenses | €3,057.06 / 30 |
| After adding the taxi | €3,081.56 / 31 |
| Taxi shares | €12.25 + €12.25 |
| Calculation version after first calculate / recalculate | v2 / v3 |
| Transfers after the first calculate | Rohan → Aarav €382.63, Isha → Aarav €321.08 |
| Transfers after Isha's payment | Rohan → Aarav €382.63 only |
| Biggest expense / its share of the total | Rent, €1,600.00 / 52% |
| September total (the comparison) | €2,265.28 |

## If something goes wrong

- **Numbers differ from this script**: the database has been used since it was seeded. Drop it and restart the backend
  (step 2 and 3 of "Before you start").
- **Login says "Too many login attempts"**: wait five minutes or restart the backend (the limit is kept in memory).
- **Page shows an error after the backend restarted**: log out and in again. If `JWT_SECRET` is not set, every backend
  restart invalidates existing logins.
- **Port 8090 in use**: set `PORT` and `BACKEND_URL` in `.env` (see `.env.example`).
