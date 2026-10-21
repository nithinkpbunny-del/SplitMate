# API reference

Every endpoint of the SplitMate backend. **All examples on this page are real responses**, recorded from a freshly seeded backend (`--spring.profiles.active=demo`) by a script that calls the API, so the field names and values are what the code returns. Ids and timestamps change on every run. Lists are shortened to two items.

An interactive version is available at `/swagger-ui.html` when the backend runs with the `dev` or `demo` profile.

## Conventions

| Topic | Rule |
|---|---|
| Base URL | `http://localhost:8090` (set `PORT`). The frontend dev server proxies `/api` to it. All paths below start with `/api`. |
| Format | JSON in, JSON out (`Content-Type: application/json`). The PDF and CSV endpoints return files. |
| Authentication | A JWT in the **httpOnly cookie `SM_TOKEN`**, set by `register` and `login` (7 days, `SameSite=Lax`, `Secure` in production). Alternatively send `Authorization: Bearer <token>` (handy for curl and Postman). Endpoints marked *public* need no login. |
| CSRF | Writes (`POST`, `PUT`, `PATCH`, `DELETE`) that rely on the cookie must echo the `XSRF-TOKEN` cookie in an `X-XSRF-TOKEN` header. `GET /api/auth/csrf` makes sure the cookie exists. Requests authenticated with a `Bearer` header skip this check. A missing token gives `403`. |
| Ids | MongoDB ObjectIds as 24-character hex strings. A malformed id in a URL gives `404`, never `400`. |
| Money | **Integer minor units (cents)** in every response (`amountMinor: 8640` is 86.40). Requests send amounts as **strings** (`"86,40"`, `"86.40"`, `"1.234,50"` are all accepted; more than 2 decimals, zero, negative or above 100,000.00 are rejected). |
| Dates | `LocalDate` as `yyyy-MM-dd`; timestamps are ISO-8601 UTC. |
| Not a member | Any split-scoped call by someone who is not an active member answers **404**, exactly as if the split did not exist. Owner-only actions answer **403** to other members. |

### Errors

Every error has the same shape:

```json
{
  "timestamp": "2026-10-21T13:40:51.209Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Enter a valid email address",
  "path": "/api/auth/register",
  "fieldErrors": [
    {
      "field": "email",
      "message": "Enter a valid email address"
    }
  ]
}
```

| Status | Meaning |
|---|---|
| `400` | Validation failed (`fieldErrors` lists each field) or the request is not allowed as sent |
| `401` | Not logged in, or the session has expired (the cookie is cleared) |
| `403` | Logged in but not allowed (owner-only action), or the CSRF token is missing |
| `404` | Not found, **or you are not a member** of that split |
| `409` | A rule blocks the action (duplicate email, split not open, balances not zero, ...), the message says why |
| `429` | Too many failed logins; see the `Retry-After` header |
| `500` | Unexpected; a generic message is returned and the details are only logged |

### Try it with curl

```bash
# 1. log in (stores the cookie jar), 2. send the CSRF header on writes
curl -s -c jar.txt http://localhost:8090/api/auth/csrf > /dev/null
XSRF=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -s -b jar.txt -c jar.txt -X POST http://localhost:8090/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"email":"aarav@demo.com","password":"Demo@1234"}'
curl -s -b jar.txt http://localhost:8090/api/splits
```

To use `Authorization: Bearer` instead, copy the value of the `SM_TOKEN` cookie from the login response (`curl -i`) and send it as the bearer token; no CSRF header is needed then.

---

## Health

#### `GET /api/health`

Liveness and readiness of the application (Spring Boot Actuator; only `health` is exposed).

*Who:* *public*

**Request**
```http
GET /api/health
```
**Response** `200 OK`
```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

## Authentication

#### `GET /api/auth/csrf`

Makes sure the `XSRF-TOKEN` cookie exists and returns the token. The frontend calls this once on startup.

*Who:* *public*

**Request**
```http
GET /api/auth/csrf
```
**Response** `200 OK`
```json
{
  "token": "33KPGFo-OecNnXF6Pg70ZxU7odcCcjjF1YLvyYlCSpK5l9vKukPpL2gKANEg-0QcCCPAVSNdjLVnFA3o5Lvf_7pwKKvf9O2v",
  "headerName": "X-XSRF-TOKEN"
}
```
_The token value is random per browser session._

#### `POST /api/auth/register`

Creates an account and logs in. The email is trimmed and lowercased and must be unique. Password: at least 8 characters with a letter and a number. An avatar colour is assigned from a fixed palette.

*Who:* *public*

**Request**
```http
POST /api/auth/register
```
```json
{
  "name": "Priya Sharma",
  "email": "priya.712038@example.com",
  "password": "Demo@1234"
}
```
**Response** `201 Created`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c233",
  "name": "Priya Sharma",
  "email": "priya.712038@example.com",
  "avatarColor": "#FB923C",
  "createdAt": "2026-10-21T13:38:03.505592Z"
}
```
Response header:

```http
Set-Cookie: SM_TOKEN=<jwt>; Path=/; Max-Age=604800; Expires=Wed, 28 Oct 2026 13:38:03 GMT; HttpOnly; SameSite=Lax
```

Errors: `400` validation (`fieldErrors`), `409` email already registered.

#### `POST /api/auth/login`

Logs in and sets the `SM_TOKEN` cookie. A wrong email or password gives the same `401` message either way. After 10 failed attempts within 5 minutes for the same IP and email the answer is `429` with a `Retry-After` header.

*Who:* *public*

**Request**
```http
POST /api/auth/login
```
```json
{
  "email": "aarav@demo.com",
  "password": "Demo@1234"
}
```
**Response** `200 OK`
```json
{
  "id": "6ad8bfc2c4db23378748315f",
  "name": "Aarav Mehta",
  "email": "aarav@demo.com",
  "avatarColor": "#F59E0B",
  "createdAt": "2026-10-21T13:36:02.158Z"
}
```

#### `GET /api/auth/me`

The logged-in user. `401` when not logged in.

*Who:* any logged-in user

**Request**
```http
GET /api/auth/me
```
**Response** `200 OK`
```json
{
  "id": "6ad8bfc2c4db23378748315f",
  "name": "Aarav Mehta",
  "email": "aarav@demo.com",
  "avatarColor": "#F59E0B",
  "createdAt": "2026-10-21T13:36:02.158Z"
}
```

#### `POST /api/auth/logout`

Clears the cookie.

*Who:* *public* (harmless without a session)

**Request**
```http
POST /api/auth/logout
```
**Response** `204 No Content`
_(no body)_
Response header: `Set-Cookie: SM_TOKEN=; Max-Age=0; HttpOnly; SameSite=Lax`

## Users

#### `GET /api/users/avatar-colors`

The fixed palette of avatar colours.

*Who:* any logged-in user

**Request**
```http
GET /api/users/avatar-colors
```
**Response** `200 OK`
```json
[
  "#F59E0B",
  "#34D399"
]
```
_Lists are shortened to two items here._

#### `PATCH /api/users/me`

Changes your name and/or avatar colour (both optional). The new name and colour are copied into every split you belong to.

*Who:* the user

**Request**
```http
PATCH /api/users/me
```
```json
{
  "name": "Priya S. Sharma",
  "avatarColor": "#34D399"
}
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c233",
  "name": "Priya S. Sharma",
  "email": "priya.712038@example.com",
  "avatarColor": "#34D399",
  "createdAt": "2026-10-21T13:38:03.505Z"
}
```
Errors: `400` for a name outside 2 to 50 characters or a colour outside the palette.

#### `PUT /api/users/me/password`

Changes the password. The current password must be correct and the new one must differ and follow the password rules.

*Who:* the user

**Request**
```http
PUT /api/users/me/password
```
```json
{
  "currentPassword": "Demo@1234",
  "newPassword": "Demo@5678"
}
```
**Response** `204 No Content`
_(no body)_
Errors: `400` if the current password is wrong.

## Categories

#### `GET /api/categories`

The 12 default categories (Rent, Groceries, Utilities, Dining Out, Transport, Household, Entertainment, Travel, Shopping, Health, Subscriptions, Other), plus the split's own when `splitId` is given. Their `icon` is a lucide icon name.

*Who:* any logged-in user; with `splitId`, members only

**Request**
```http
GET /api/categories
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8bfc2c4db233787483153",
    "key": "rent",
    "name": "Rent",
    "icon": "home",
    "color": "#F59E0B",
    "splitId": null,
    "isDefault": true,
    "sortOrder": 1
  },
  {
    "id": "6ad8bfc2c4db233787483154",
    "key": "groceries",
    "name": "Groceries",
    "icon": "shopping-cart",
    "color": "#22C55E",
    "splitId": null,
    "isDefault": true,
    "sortOrder": 2
  }
]
```
_Lists are shortened to two items here._

## Splits

#### `GET /api/splits`

Your splits (where you are an active member), newest first, each with a summary: member count, total spend, **your net balance**, whether the calculation is stale and the last activity.

*Who:* any logged-in user

**Request**
```http
GET /api/splits
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8bfc2c4db233787483191",
    "name": "October month split",
    "description": "Flat 4B, Berlin",
    "emoji": "🏠",
    "currency": "EUR",
    "status": "OPEN",
    "period": {
      "start": "2026-10-01",
      "end": "2026-10-31"
    },
    "myRole": "OWNER",
    "members": [
      {
        "userId": "6ad8bfc2c4db23378748315f",
        "name": "Aarav Mehta",
        "email": "aarav@demo.com",
        "avatarColor": "#F59E0B",
        "role": "OWNER",
        "joinedAt": "2026-10-21T13:36:02.767Z",
        "active": true
      },
      {
        "userId": "6ad8bfc2c4db233787483160",
        "name": "Isha Rao",
        "email": "isha@demo.com",
        "avatarColor": "#F472B6",
        "role": "MEMBER",
        "joinedAt": "2026-10-21T13:36:02.772Z",
        "active": true
      }
    ],
    "memberCount": 4,
    "totalSpendMinor": 305706,
    "expenseCount": 30,
    "myNetMinor": 71596,
    "calculationStale": false,
    "lastActivityAt": "2026-10-21T13:37:44.151Z",
    "createdAt": "2026-10-21T13:36:02.768Z"
  },
  {
    "id": "6ad8bfc2c4db233787483163",
    "name": "September month split",
    "description": "Flat 4B, Berlin",
    "emoji": "🏠",
    "currency": "EUR",
    "status": "SETTLED",
    "period": {
      "start": "2026-09-01",
      "end": "2026-09-30"
    },
    "myRole": "OWNER",
    "members": [
      {
        "userId": "6ad8bfc2c4db23378748315f",
        "name": "Aarav Mehta",
        "email": "aarav@demo.com",
        "avatarColor": "#F59E0B",
        "role": "OWNER",
        "joinedAt": "2026-10-21T13:36:02.166Z",
        "active": true
      },
      {
        "userId": "6ad8bfc2c4db233787483160",
        "name": "Isha Rao",
        "email": "isha@demo.com",
        "avatarColor": "#F472B6",
        "role": "MEMBER",
        "joinedAt": "2026-10-21T13:36:02.186Z",
        "active": true
      }
    ],
    "memberCount": 4,
    "totalSpendMinor": 226528,
    "expenseCount": 13,
    "myNetMinor": 0,
    "calculationStale": false,
    "lastActivityAt": "2026-09-30T20:00:00.488Z",
    "createdAt": "2026-10-21T13:36:02.167Z"
  }
]
```
_Lists are shortened to two items here._
`myNetMinor` is the live balance: positive means the group owes you.

#### `POST /api/splits`

Creates a split; you become its OWNER and an invite code is generated. `name` is 3 to 60 characters; `currency` defaults to `EUR`; `period` is optional (at most one year).

*Who:* any logged-in user

**Request**
```http
POST /api/splits
```
```json
{
  "name": "November month split",
  "description": "Flat 4B",
  "emoji": "🏠",
  "currency": "EUR",
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  }
}
```
**Response** `201 Created`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November month split",
  "description": "Flat 4B",
  "emoji": "🏠",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "OWNER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695803Z",
      "active": true
    }
  ],
  "inviteCode": "Xm4DJoOuYV4d",
  "inviteUrl": "http://localhost:5173/join/Xm4DJoOuYV4d",
  "inviteEnabled": true,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695941Z",
  "updatedAt": "2026-10-21T13:38:03.695941Z"
}
```
`inviteUrl` is `APP_BASE_URL/join/{inviteCode}`. `currencyLocked` becomes true after the first expense.

#### `GET /api/splits/{splitId}`

The full split: members, invite link, totals, your net balance, `calculationStale` and `settlementVersion`.

*Who:* members only (404 otherwise)

**Request**
```http
GET /api/splits/6ad8c03b0fcfebfca5a8c234
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November month split",
  "description": "Flat 4B",
  "emoji": "🏠",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "OWNER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695Z",
      "active": true
    }
  ],
  "inviteCode": "Xm4DJoOuYV4d",
  "inviteUrl": "http://localhost:5173/join/Xm4DJoOuYV4d",
  "inviteEnabled": true,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695Z",
  "updatedAt": "2026-10-21T13:38:03.695Z"
}
```

#### `PATCH /api/splits/{splitId}`

Partial update: `name`, `description`, `emoji`, `currency` (only before the first expense), `period` or `clearPeriod: true`, and `status` (`ARCHIVED` or `OPEN` to reopen). Unset fields are unchanged.

*Who:* owner only (`403` for other members)

**Request**
```http
PATCH /api/splits/6ad8c03b0fcfebfca5a8c234
```
```json
{
  "name": "November flat split",
  "emoji": "🍕"
}
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November flat split",
  "description": "Flat 4B",
  "emoji": "🍕",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "OWNER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695Z",
      "active": true
    }
  ],
  "inviteCode": "Xm4DJoOuYV4d",
  "inviteUrl": "http://localhost:5173/join/Xm4DJoOuYV4d",
  "inviteEnabled": true,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695Z",
  "updatedAt": "2026-10-21T13:38:03.705499Z"
}
```

#### `PATCH /api/splits/{splitId}/invite`

Turns the invite link on or off.

*Who:* owner only

**Request**
```http
PATCH /api/splits/6ad8c03b0fcfebfca5a8c234/invite
```
```json
{
  "enabled": false
}
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November flat split",
  "description": "Flat 4B",
  "emoji": "🍕",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "OWNER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695Z",
      "active": true
    }
  ],
  "inviteCode": "Xm4DJoOuYV4d",
  "inviteUrl": "http://localhost:5173/join/Xm4DJoOuYV4d",
  "inviteEnabled": false,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695Z",
  "updatedAt": "2026-10-21T13:38:03.710644Z"
}
```
The full split is returned; `inviteEnabled` is now `false`.

#### `POST /api/splits/{splitId}/invite/regenerate`

Creates a new invite code. The old link stops working immediately (`GET /api/invites/{old}` answers `404`).

*Who:* owner only

**Request**
```http
POST /api/splits/6ad8c03b0fcfebfca5a8c234/invite/regenerate
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November flat split",
  "description": "Flat 4B",
  "emoji": "🍕",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "OWNER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695Z",
      "active": true
    }
  ],
  "inviteCode": "h6dUhNz9AB9A",
  "inviteUrl": "http://localhost:5173/join/h6dUhNz9AB9A",
  "inviteEnabled": true,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695Z",
  "updatedAt": "2026-10-21T13:38:03.720974Z"
}
```

### Invites

#### `GET /api/invites/{code}`

**Public** preview of the split behind an invite code: name, emoji, owner, member count and first names only (no emails, no money). `404` if the code is unknown, regenerated or disabled.

*Who:* *public*

**Request**
```http
GET /api/invites/h6dUhNz9AB9A
```
**Response** `200 OK`
```json
{
  "code": "h6dUhNz9AB9A",
  "splitName": "November flat split",
  "emoji": "🍕",
  "description": "Flat 4B",
  "ownerName": "Priya S. Sharma",
  "memberCount": 1,
  "members": [
    {
      "firstName": "Priya",
      "avatarColor": "#34D399"
    }
  ],
  "status": "OPEN",
  "joinable": true
}
```

The code before regenerating now fails:

**Request**
```http
GET /api/invites/Xm4DJoOuYV4d
```
**Response** `404 Not Found`
```json
{
  "timestamp": "2026-10-21T13:38:03.727450Z",
  "status": 404,
  "error": "Not Found",
  "message": "This invite link is invalid or has been turned off",
  "path": "/api/invites/Xm4DJoOuYV4d",
  "fieldErrors": []
}
```

#### `POST /api/invites/{code}/join`

Joins the split as a MEMBER. **Idempotent**: joining twice changes nothing and returns the split. The member is added with one guarded update, so concurrent joins cannot duplicate. A former member who rejoins is reactivated. `409` if the split is not open.

*Who:* any logged-in user

**Request**
```http
POST /api/invites/h6dUhNz9AB9A/join
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c234",
  "name": "November flat split",
  "description": "Flat 4B",
  "emoji": "🍕",
  "currency": "EUR",
  "status": "OPEN",
  "ownerId": "6ad8c03b0fcfebfca5a8c233",
  "myRole": "MEMBER",
  "members": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "email": "priya.712038@example.com",
      "avatarColor": "#34D399",
      "role": "OWNER",
      "joinedAt": "2026-10-21T13:38:03.695Z",
      "active": true
    },
    {
      "userId": "6ad8c03b0fcfebfca5a8c23a",
      "name": "Dev Patel",
      "email": "dev.712038@example.com",
      "avatarColor": "#F87171",
      "role": "MEMBER",
      "joinedAt": "2026-10-21T13:38:03.789Z",
      "active": true
    }
  ],
  "inviteCode": "h6dUhNz9AB9A",
  "inviteUrl": "http://localhost:5173/join/h6dUhNz9AB9A",
  "inviteEnabled": true,
  "period": {
    "start": "2026-11-01",
    "end": "2026-11-30"
  },
  "totalSpendMinor": 0,
  "expenseCount": 0,
  "myNetMinor": 0,
  "currencyLocked": false,
  "lastCalculatedAt": null,
  "calculationStale": false,
  "settlementVersion": 0,
  "createdAt": "2026-10-21T13:38:03.695Z",
  "updatedAt": "2026-10-21T13:38:03.720Z"
}
```

## Expenses

#### `POST /api/splits/{splitId}/expenses`

Adds an expense. `amount` is a string; `paidBy` defaults to you; `participants` defaults to every active member. **Shares are computed on the server** (equal split, the remainder cents go to the first participants by id, the sum is exact) and stored. Rules: description 1 to 80 characters, payer and participants must be active members, date at most one day in the future, the split must be `OPEN` (`409` otherwise).

*Who:* members only

**Request**
```http
POST /api/splits/6ad8bfc2c4db233787483191/expenses
```
```json
{
  "description": "Lidl weekly groceries",
  "amount": "86,40",
  "categoryId": "6ad8bfc2c4db233787483154",
  "paidBy": "6ad8bfc2c4db23378748315f",
  "participants": [
    "6ad8bfc2c4db23378748315f",
    "6ad8bfc2c4db233787483160",
    "6ad8bfc2c4db233787483161"
  ],
  "date": "2026-10-20",
  "notes": "Weekly shop"
}
```
**Response** `201 Created`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c23c",
  "splitId": "6ad8bfc2c4db233787483191",
  "description": "Lidl weekly groceries",
  "amountMinor": 8640,
  "currency": "EUR",
  "categoryId": "6ad8bfc2c4db233787483154",
  "paidBy": "6ad8bfc2c4db23378748315f",
  "splitType": "EQUAL",
  "participants": [
    "6ad8bfc2c4db23378748315f",
    "6ad8bfc2c4db233787483160"
  ],
  "shares": [
    {
      "userId": "6ad8bfc2c4db23378748315f",
      "amountMinor": 2880
    },
    {
      "userId": "6ad8bfc2c4db233787483160",
      "amountMinor": 2880
    }
  ],
  "date": "2026-10-20",
  "notes": "Weekly shop",
  "createdBy": "6ad8bfc2c4db23378748315f",
  "canEdit": true,
  "createdAt": "2026-10-21T13:38:03.801990Z",
  "updatedAt": "2026-10-21T13:38:03.801990Z"
}
```
_Lists are shortened to two items here._
86.40 among 3 people is 2880 each. `canEdit` tells the UI whether you may change it.

#### `GET /api/splits/{splitId}/expenses`

Live (not deleted) expenses with filters, sorting and paging. Query parameters: `category`, `paidBy`, `participant` (ids), `from`, `to` (dates), `q` (case-insensitive text search in the description), `sort` (`date_desc` default, `date_asc`, `amount_desc`, `amount_asc`), `page` (from 0) and `size` (default 50, max 200). The response also carries the total of everything matching the filters.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191/expenses?category=6ad8bfc2c4db233787483154&sort=amount_desc&size=2
```
**Response** `200 OK`
```json
{
  "items": [
    {
      "id": "6ad8c03b0fcfebfca5a8c23c",
      "splitId": "6ad8bfc2c4db233787483191",
      "description": "Lidl weekly groceries",
      "amountMinor": 8640,
      "currency": "EUR",
      "categoryId": "6ad8bfc2c4db233787483154",
      "paidBy": "6ad8bfc2c4db23378748315f",
      "splitType": "EQUAL",
      "participants": [
        "6ad8bfc2c4db23378748315f",
        "6ad8bfc2c4db233787483160"
      ],
      "shares": [
        {
          "userId": "6ad8bfc2c4db23378748315f",
          "amountMinor": 2880
        },
        {
          "userId": "6ad8bfc2c4db233787483160",
          "amountMinor": 2880
        }
      ],
      "date": "2026-10-20",
      "notes": "Weekly shop",
      "createdBy": "6ad8bfc2c4db23378748315f",
      "canEdit": true,
      "createdAt": "2026-10-21T13:38:03.801Z",
      "updatedAt": "2026-10-21T13:38:03.801Z"
    },
    {
      "id": "6ad8bfc2c4db2337874831a2",
      "splitId": "6ad8bfc2c4db233787483191",
      "description": "Lidl weekly groceries",
      "amountMinor": 8640,
      "currency": "EUR",
      "categoryId": "6ad8bfc2c4db233787483154",
      "paidBy": "6ad8bfc2c4db233787483160",
      "splitType": "EQUAL",
      "participants": [
        "6ad8bfc2c4db23378748315f",
        "6ad8bfc2c4db233787483160"
      ],
      "shares": [
        {
          "userId": "6ad8bfc2c4db23378748315f",
          "amountMinor": 2160
        },
        {
          "userId": "6ad8bfc2c4db233787483160",
          "amountMinor": 2160
        }
      ],
      "date": "2026-10-06",
      "notes": null,
      "createdBy": "6ad8bfc2c4db233787483160",
      "canEdit": true,
      "createdAt": "2026-10-06T18:30:00Z",
      "updatedAt": "2026-10-06T18:30:00Z"
    }
  ],
  "page": 0,
  "size": 2,
  "totalItems": 8,
  "totalPages": 4,
  "totalAmountMinor": 49235
}
```
_Lists are shortened to two items here._

#### `GET /api/splits/{splitId}/expenses/{expenseId}`

One expense.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191/expenses/6ad8c03b0fcfebfca5a8c23c
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c23c",
  "splitId": "6ad8bfc2c4db233787483191",
  "description": "Lidl weekly groceries",
  "amountMinor": 8640,
  "currency": "EUR",
  "categoryId": "6ad8bfc2c4db233787483154",
  "paidBy": "6ad8bfc2c4db23378748315f",
  "splitType": "EQUAL",
  "participants": [
    "6ad8bfc2c4db23378748315f",
    "6ad8bfc2c4db233787483160"
  ],
  "shares": [
    {
      "userId": "6ad8bfc2c4db23378748315f",
      "amountMinor": 2880
    },
    {
      "userId": "6ad8bfc2c4db233787483160",
      "amountMinor": 2880
    }
  ],
  "date": "2026-10-20",
  "notes": "Weekly shop",
  "createdBy": "6ad8bfc2c4db23378748315f",
  "canEdit": true,
  "createdAt": "2026-10-21T13:38:03.801Z",
  "updatedAt": "2026-10-21T13:38:03.801Z"
}
```
_Lists are shortened to two items here._

#### `PUT /api/splits/{splitId}/expenses/{expenseId}`

Replaces the expense (same body as create); shares are recomputed. Marks the calculation stale.

*Who:* the creator, the payer or the split owner; split must be `OPEN`

**Request**
```http
PUT /api/splits/6ad8bfc2c4db233787483191/expenses/6ad8c03b0fcfebfca5a8c23c
```
```json
{
  "description": "Lidl weekly groceries (updated)",
  "amount": "90.00",
  "categoryId": "6ad8bfc2c4db233787483154",
  "paidBy": "6ad8bfc2c4db23378748315f",
  "participants": [
    "6ad8bfc2c4db23378748315f",
    "6ad8bfc2c4db233787483160",
    "6ad8bfc2c4db233787483161"
  ],
  "date": "2026-10-20",
  "notes": "Weekly shop"
}
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c23c",
  "splitId": "6ad8bfc2c4db233787483191",
  "description": "Lidl weekly groceries (updated)",
  "amountMinor": 9000,
  "currency": "EUR",
  "categoryId": "6ad8bfc2c4db233787483154",
  "paidBy": "6ad8bfc2c4db23378748315f",
  "splitType": "EQUAL",
  "participants": [
    "6ad8bfc2c4db23378748315f",
    "6ad8bfc2c4db233787483160"
  ],
  "shares": [
    {
      "userId": "6ad8bfc2c4db23378748315f",
      "amountMinor": 3000
    },
    {
      "userId": "6ad8bfc2c4db233787483160",
      "amountMinor": 3000
    }
  ],
  "date": "2026-10-20",
  "notes": "Weekly shop",
  "createdBy": "6ad8bfc2c4db23378748315f",
  "canEdit": true,
  "createdAt": "2026-10-21T13:38:03.801Z",
  "updatedAt": "2026-10-21T13:38:03.817860Z"
}
```
_Lists are shortened to two items here._

#### `DELETE /api/splits/{splitId}/expenses/{expenseId}`

**Soft delete**: the document stays in the database with `deleted: true` but is excluded from every calculation and list.

*Who:* the creator, the payer or the split owner; split must be `OPEN`

**Request**
```http
DELETE /api/splits/6ad8bfc2c4db233787483191/expenses/6ad8c03b0fcfebfca5a8c23c
```
**Response** `204 No Content`
_(no body)_

## Calculation (settlements)

#### `POST /api/splits/{splitId}/calculate`

Runs the calculation engine on the current expenses and payments and stores a new immutable **snapshot** (the version counter is incremented atomically). For each person: `paidMinor`, `shareMinor`, `paymentsSentMinor`, `paymentsReceivedMinor` and `netMinor` (= paid − share + payments sent − payments received; positive means they get money back). `transfers` is the smallest set of payments that settles everyone (at most n − 1). The nets always add up to 0.

*Who:* members only

**Request**
```http
POST /api/splits/6ad8c03b0fcfebfca5a8c234/calculate
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c242",
  "splitId": "6ad8c03b0fcfebfca5a8c234",
  "version": 1,
  "calculatedBy": "6ad8c03b0fcfebfca5a8c233",
  "calculatedByName": "Priya S. Sharma",
  "calculatedAt": "2026-10-21T13:38:03.842377Z",
  "currency": "EUR",
  "totalSpendMinor": 120000,
  "expenseCount": 1,
  "expenseIds": [
    "6ad8c03b0fcfebfca5a8c240"
  ],
  "memberSummaries": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "avatarColor": "#34D399",
      "paidMinor": 120000,
      "shareMinor": 60000,
      "paymentsSentMinor": 0,
      "paymentsReceivedMinor": 0,
      "netMinor": 60000
    },
    {
      "userId": "6ad8c03b0fcfebfca5a8c23a",
      "name": "Dev Patel",
      "avatarColor": "#F87171",
      "paidMinor": 0,
      "shareMinor": 60000,
      "paymentsSentMinor": 0,
      "paymentsReceivedMinor": 0,
      "netMinor": -60000
    }
  ],
  "transfers": [
    {
      "fromUserId": "6ad8c03b0fcfebfca5a8c23a",
      "fromName": "Dev Patel",
      "toUserId": "6ad8c03b0fcfebfca5a8c233",
      "toName": "Priya S. Sharma",
      "amountMinor": 60000
    }
  ],
  "allSettled": false
}
```
Here Priya paid the whole 1,200.00 rent shared with Dev, so Dev owes her 600.00.

#### `GET /api/splits/{splitId}/settlements/latest`

The newest snapshot, or `204` with no body if the split has never been calculated.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8c03b0fcfebfca5a8c234/settlements/latest
```
**Response** `200 OK`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c242",
  "splitId": "6ad8c03b0fcfebfca5a8c234",
  "version": 1,
  "calculatedBy": "6ad8c03b0fcfebfca5a8c233",
  "calculatedByName": "Priya S. Sharma",
  "calculatedAt": "2026-10-21T13:38:03.842Z",
  "currency": "EUR",
  "totalSpendMinor": 120000,
  "expenseCount": 1,
  "expenseIds": [
    "6ad8c03b0fcfebfca5a8c240"
  ],
  "memberSummaries": [
    {
      "userId": "6ad8c03b0fcfebfca5a8c233",
      "name": "Priya S. Sharma",
      "avatarColor": "#34D399",
      "paidMinor": 120000,
      "shareMinor": 60000,
      "paymentsSentMinor": 0,
      "paymentsReceivedMinor": 0,
      "netMinor": 60000
    },
    {
      "userId": "6ad8c03b0fcfebfca5a8c23a",
      "name": "Dev Patel",
      "avatarColor": "#F87171",
      "paidMinor": 0,
      "shareMinor": 60000,
      "paymentsSentMinor": 0,
      "paymentsReceivedMinor": 0,
      "netMinor": -60000
    }
  ],
  "transfers": [
    {
      "fromUserId": "6ad8c03b0fcfebfca5a8c23a",
      "fromName": "Dev Patel",
      "toUserId": "6ad8c03b0fcfebfca5a8c233",
      "toName": "Priya S. Sharma",
      "amountMinor": 60000
    }
  ],
  "allSettled": false
}
```
The same body as `calculate`.

#### `GET /api/splits/{splitId}/settlements`

The calculation history, newest first.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8c03b0fcfebfca5a8c234/settlements
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8c03b0fcfebfca5a8c242",
    "version": 1,
    "calculatedAt": "2026-10-21T13:38:03.842Z",
    "calculatedByName": "Priya S. Sharma",
    "totalSpendMinor": 120000,
    "expenseCount": 1,
    "transferCount": 1
  }
]
```

#### `GET /api/splits/{splitId}/settlements/{settlementId}`

One snapshot by id.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8c03b0fcfebfca5a8c234/settlements/6ad8c03b0fcfebfca5a8c242
```
**Response** `200 OK`: the same body as `calculate` above for that version.

## Payments

#### `POST /api/splits/{splitId}/payments`

Records a real-world "I paid you back". Both people must be active members. The amount may exceed what the latest calculation says is owed: it is then still recorded, with a human-readable `warning`. Balances change at the next calculation.

*Who:* the payer, the person paid, or the split owner; split must be `OPEN`

**Request**
```http
POST /api/splits/6ad8c03b0fcfebfca5a8c234/payments
```
```json
{
  "fromUserId": "6ad8c03b0fcfebfca5a8c23a",
  "toUserId": "6ad8c03b0fcfebfca5a8c233",
  "amount": "700,00",
  "note": "PayPal"
}
```
**Response** `201 Created`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c244",
  "splitId": "6ad8c03b0fcfebfca5a8c234",
  "fromUserId": "6ad8c03b0fcfebfca5a8c23a",
  "toUserId": "6ad8c03b0fcfebfca5a8c233",
  "amountMinor": 70000,
  "note": "PayPal",
  "settlementId": "6ad8c03b0fcfebfca5a8c242",
  "recordedBy": "6ad8c03b0fcfebfca5a8c23a",
  "createdAt": "2026-10-21T13:38:03.860021Z",
  "warning": "Dev Patel only owed Priya S. Sharma €600.00 in the latest calculation. The extra €100.00 is recorded too and will show up after recalculating."
}
```
`warning` is `null` when the amount is within what is owed.

#### `GET /api/splits/{splitId}/payments`

All payments of the split, newest first.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8c03b0fcfebfca5a8c234/payments
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8c03b0fcfebfca5a8c244",
    "splitId": "6ad8c03b0fcfebfca5a8c234",
    "fromUserId": "6ad8c03b0fcfebfca5a8c23a",
    "toUserId": "6ad8c03b0fcfebfca5a8c233",
    "amountMinor": 70000,
    "note": "PayPal",
    "settlementId": "6ad8c03b0fcfebfca5a8c242",
    "recordedBy": "6ad8c03b0fcfebfca5a8c23a",
    "createdAt": "2026-10-21T13:38:03.860Z",
    "warning": null
  }
]
```

#### `DELETE /api/splits/{splitId}/payments/{paymentId}`

Deletes a payment (balances change at the next calculation).

*Who:* who recorded it, or the split owner; split must be `OPEN`

**Request**
```http
DELETE /api/splits/6ad8c03b0fcfebfca5a8c234/payments/6ad8c03b0fcfebfca5a8c244
```
**Response** `204 No Content`
_(no body)_

#### `POST /api/splits/{splitId}/settle`

Marks the split **SETTLED**. Only allowed when the latest calculation is up to date and every balance is exactly zero; otherwise `409` with the reason. A settled split is read-only until the owner reopens it.

*Who:* members only; split must be `OPEN`

Not yet possible on the October demo split (balances are not zero):

**Request**
```http
POST /api/splits/6ad8bfc2c4db233787483191/settle
```
**Response** `409 Conflict`
```json
{
  "timestamp": "2026-10-21T13:38:03.891213Z",
  "status": 409,
  "error": "Conflict",
  "message": "Some balances are not zero yet. Record the remaining payments first.",
  "path": "/api/splits/6ad8bfc2c4db233787483191/settle",
  "fieldErrors": []
}
```

After everyone paid and the split was recalculated, it works:

**Request**
```http
POST /api/splits/6ad8c03b0fcfebfca5a8c234/settle
```
**Response** `204 No Content`
_(no body)_

## Reports

#### `POST /api/splits/{splitId}/reports`

Builds a spending report: statistics from MongoDB **aggregation pipelines** (one `$facet` over the expenses), rule-based insights, and a PDF stored in **GridFS**. The body is optional: `periodStart` / `periodEnd` default to the split's period, otherwise the first and last expense dates (at most one year). `409` if the split was never calculated or the period has no expenses.

*Who:* members only

**Request**
```http
POST /api/splits/6ad8bfc2c4db233787483191/reports
```
```json
{}
```
**Response** `201 Created`
```json
{
  "id": "6ad8c03b0fcfebfca5a8c250",
  "splitId": "6ad8bfc2c4db233787483191",
  "settlementId": "6ad8c03b0fcfebfca5a8c24e",
  "generatedBy": "6ad8bfc2c4db23378748315f",
  "generatedByName": "Aarav Mehta",
  "generatedAt": "2026-10-21T13:38:03.918594Z",
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "currency": "EUR",
  "stats": {
    "totalSpendMinor": 305706,
    "expenseCount": 30,
    "avgPerExpenseMinor": 10190,
    "avgPerDayMinor": 9861,
    "avgPerPersonMinor": 76427,
    "days": 31,
    "byCategory": [
      {
        "categoryId": "6ad8bfc2c4db233787483153",
        "key": "rent",
        "name": "Rent",
        "color": "#F59E0B",
        "totalMinor": 160000,
        "count": 1,
        "percent": 52.3
      },
      {
        "categoryId": "6ad8bfc2c4db233787483154",
        "key": "groceries",
        "name": "Groceries",
        "color": "#22C55E",
        "totalMinor": 40595,
        "count": 7,
        "percent": 13.3
      }
    ],
    "byMember": [
      {
        "userId": "6ad8bfc2c4db23378748315f",
        "name": "Aarav Mehta",
        "avatarColor": "#F59E0B",
        "paidMinor": 205234,
        "shareMinor": 77155,
        "netMinor": 128079,
        "percentOfPaid": 67.1
      },
      {
        "userId": "6ad8bfc2c4db233787483160",
        "name": "Isha Rao",
        "avatarColor": "#F472B6",
        "paidMinor": 31213,
        "shareMinor": 63321,
        "netMinor": -32108,
        "percentOfPaid": 10.2
      }
    ],
    "dailyTrend": [
      {
        "date": "2026-10-01",
        "totalMinor": 160000
      },
      {
        "date": "2026-10-02",
        "totalMinor": 3999
      }
    ],
    "weekdayPattern": [
      {
        "weekday": "MONDAY",
        "totalMinor": 12913
      },
      {
        "weekday": "TUESDAY",
        "totalMinor": 27335
      }
    ],
    "topExpenses": [
      {
        "expenseId": "6ad8bfc2c4db233787483196",
        "description": "Rent October",
        "amountMinor": 160000,
        "paidByName": "Aarav Mehta",
        "date": "2026-10-01",
        "categoryName": "Rent"
      },
      {
        "expenseId": "6ad8bfc2c4db2337874831be",
        "description": "Airbnb Prague (2 nights)",
        "amountMinor": 21000,
        "paidByName": "Aarav Mehta",
        "date": "2026-10-17",
        "categoryName": "Travel"
      }
    ],
    "memberCategoryMatrix": [
      {
        "userId": "6ad8bfc2c4db233787483161",
        "categoryKey": "utilities",
        "shareMinor": 3060
      },
      {
        "userId": "6ad8bfc2c4db233787483162",
        "categoryKey": "shopping",
        "shareMinor": 749
      }
    ]
  },
  "insights": [
    "Rent made up 52% of all spending.",
    "The most expensive day was Thursday 1 October (€1,600.00)."
  ],
  "pdfFileId": "6ad8c03b0fcfebfca5a8c251",
  "pdfFileName": "SplitMate-October-month-split-2026-10.pdf"
}
```
_Lists are shortened to two items here._
`stats.byMember`, `dailyTrend`, `weekdayPattern` and the matrix are shortened here. `pdfFileId` is the GridFS file id.

#### `GET /api/splits/{splitId}/reports`

The split's reports, newest first, as short summaries.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191/reports
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8c03b0fcfebfca5a8c250",
    "generatedAt": "2026-10-21T13:38:03.918Z",
    "generatedByName": "Aarav Mehta",
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "totalSpendMinor": 305706,
    "expenseCount": 30,
    "topInsight": "Rent made up 52% of all spending."
  },
  {
    "id": "6ad8c0270fcfebfca5a8c22a",
    "generatedAt": "2026-10-21T13:37:43.728Z",
    "generatedByName": "Aarav Mehta",
    "periodStart": "2026-10-01",
    "periodEnd": "2026-10-31",
    "totalSpendMinor": 305706,
    "expenseCount": 30,
    "topInsight": "Rent made up 52% of all spending."
  }
]
```
_Lists are shortened to two items here._

#### `GET /api/reports/{reportId}`

One full report (statistics and insights).

*Who:* members of the report's split (`404` otherwise)

**Request**
```http
GET /api/reports/6ad8c03b0fcfebfca5a8c250
```
**Response** `200 OK`: the same body as `POST .../reports` above.

#### `GET /api/reports/{reportId}/pdf`

Streams the report PDF from GridFS as an attachment.

*Who:* members of the report's split

**Request**
```http
GET /api/reports/6ad8c03b0fcfebfca5a8c250/pdf
```
**Response** `200 OK`
```http
Content-Disposition: attachment; filename="SplitMate-October-month-split-2026-10.pdf"
Content-Type: application/pdf
Content-Length: 14158

%PDF-1.6 ... (14158 bytes)
```

#### `GET /api/splits/{splitId}/export.csv`

All live expenses as CSV (UTF-8 with a byte order mark for Excel, RFC 4180 quoting; cells starting with `=`, `+`, `-` or `@` get a leading apostrophe so a spreadsheet cannot run them as formulas).

*Who:* members only

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191/export.csv
```
**Response** `200 OK`
```text
Content-Disposition: attachment; filename="SplitMate-October-month-split-expenses.csv"
Content-Type: text/csv;charset=UTF-8

Date,Description,Category,Amount,Currency,Paid by,Shared by,Notes
2026-10-01,Rent October,Rent,1600.00,EUR,Aarav Mehta,Aarav Mehta; Isha Rao; Rohan Nair; Sneha Iyer,Bank transfer to the landlord
2026-10-02,Vodafone internet,Utilities,39.99,EUR,Isha Rao,Aarav Mehta; Isha Rao; Rohan Nair; Sneha Iyer,
... (30 rows)
```

## Activity

#### `GET /api/splits/{splitId}/activity`

The split's timeline, newest first. `limit` defaults to 50 (max 200). Types: `SPLIT_CREATED`, `SPLIT_UPDATED`, `SPLIT_ARCHIVED`, `SPLIT_REOPENED`, `MEMBER_JOINED`, `MEMBER_LEFT`, `MEMBER_REMOVED`, `INVITE_REGENERATED`, `INVITE_ENABLED`, `INVITE_DISABLED`, `EXPENSE_ADDED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, `SPLIT_CALCULATED`, `PAYMENT_RECORDED`, `PAYMENT_DELETED`, `REPORT_GENERATED`, `SPLIT_SETTLED`.

*Who:* members only

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191/activity?limit=3
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8c03b0fcfebfca5a8c253",
    "splitId": "6ad8bfc2c4db233787483191",
    "actorId": "6ad8bfc2c4db23378748315f",
    "actorName": "Aarav Mehta",
    "type": "REPORT_GENERATED",
    "message": "Aarav Mehta generated a spending report for 1 Oct - 31 Oct 2026",
    "metadata": {
      "reportId": "6ad8c03b0fcfebfca5a8c250"
    },
    "createdAt": "2026-10-21T13:38:03.975Z"
  },
  {
    "id": "6ad8c03b0fcfebfca5a8c24f",
    "splitId": "6ad8bfc2c4db233787483191",
    "actorId": "6ad8bfc2c4db23378748315f",
    "actorName": "Aarav Mehta",
    "type": "SPLIT_CALCULATED",
    "message": "Aarav Mehta calculated the split (€3,057.06): 2 transfers settle everyone",
    "metadata": {
      "version": 5,
      "settlementId": "6ad8c03b0fcfebfca5a8c24e"
    },
    "createdAt": "2026-10-21T13:38:03.904Z"
  }
]
```
_Lists are shortened to two items here._

#### `GET /api/activity`

Recent activity across all your splits (the dashboard feed). `limit` defaults to 12.

*Who:* any logged-in user

**Request**
```http
GET /api/activity?limit=2
```
**Response** `200 OK`
```json
[
  {
    "id": "6ad8c03b0fcfebfca5a8c253",
    "splitId": "6ad8bfc2c4db233787483191",
    "actorId": "6ad8bfc2c4db23378748315f",
    "actorName": "Aarav Mehta",
    "type": "REPORT_GENERATED",
    "message": "Aarav Mehta generated a spending report for 1 Oct - 31 Oct 2026",
    "metadata": {
      "reportId": "6ad8c03b0fcfebfca5a8c250"
    },
    "createdAt": "2026-10-21T13:38:03.975Z"
  },
  {
    "id": "6ad8c03b0fcfebfca5a8c24f",
    "splitId": "6ad8bfc2c4db233787483191",
    "actorId": "6ad8bfc2c4db23378748315f",
    "actorName": "Aarav Mehta",
    "type": "SPLIT_CALCULATED",
    "message": "Aarav Mehta calculated the split (€3,057.06): 2 transfers settle everyone",
    "metadata": {
      "version": 5,
      "settlementId": "6ad8c03b0fcfebfca5a8c24e"
    },
    "createdAt": "2026-10-21T13:38:03.904Z"
  }
]
```

## Members, delete and archive

#### `DELETE /api/splits/{splitId}/members/{userId}`

The owner removes a member, or a member removes themself (leaves). The person stays in the data (`active: false`) because old expenses refer to them. **Blocked with `409`** while they take part in any live expense or have a balance other than zero, so nobody's share changes; the owner cannot leave their own split. They can rejoin with the invite link.

*Who:* the owner, or the member themself

Dev is on a live expense in the settled split, so this is refused:

**Request**
```http
DELETE /api/splits/6ad8c03b0fcfebfca5a8c234/members/6ad8c03b0fcfebfca5a8c23a
```
**Response** `409 Conflict`
```json
{
  "timestamp": "2026-10-21T13:38:04.005122Z",
  "status": 409,
  "error": "Conflict",
  "message": "Dev Patel is part of expenses in this split. Delete or edit those expenses first, so nobody's share changes.",
  "path": "/api/splits/6ad8c03b0fcfebfca5a8c234/members/6ad8c03b0fcfebfca5a8c23a",
  "fieldErrors": []
}
```

A member with no expenses and no balance can leave:

**Request**
```http
DELETE /api/splits/6ad8c03c0fcfebfca5a8c254/members/6ad8c03b0fcfebfca5a8c23a
```
**Response** `204 No Content`
_(no body)_

#### `DELETE /api/splits/{splitId}`

Deletes a split **only if it has no expenses** (also removing its activity, payments and reports). A split with expenses is **archived** instead so its history is kept.

*Who:* owner only

An empty split is deleted:

**Request**
```http
DELETE /api/splits/6ad8c03c0fcfebfca5a8c254
```
**Response** `200 OK`
```json
{
  "deleted": true,
  "archived": false,
  "message": "Split deleted"
}
```

A split with expenses is archived:

**Request**
```http
DELETE /api/splits/6ad8c03b0fcfebfca5a8c234
```
**Response** `200 OK`
```json
{
  "deleted": false,
  "archived": true,
  "message": "This split has expenses, so it was archived instead of deleted. Its history is kept."
}
```

## Error examples

**Validation (`400`)**

**Request**
```http
POST /api/auth/register
```
```json
{
  "name": "I",
  "email": "nope",
  "password": "short"
}
```
**Response** `400 Bad Request`
```json
{
  "timestamp": "2026-10-21T13:38:04.034506Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Enter a valid email address",
  "path": "/api/auth/register",
  "fieldErrors": [
    {
      "field": "email",
      "message": "Enter a valid email address"
    },
    {
      "field": "name",
      "message": "Name must be 2 to 50 characters"
    }
  ]
}
```
_Lists are shortened to two items here._

**Not logged in (`401`)**

**Request**
```http
GET /api/splits
```
**Response** `401 Unauthorized`
```json
{
  "timestamp": "2026-10-21T13:38:04.035981Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Please log in to continue.",
  "path": "/api/splits",
  "fieldErrors": []
}
```

**Wrong password (`401`)**, the same message as for an unknown email

**Request**
```http
POST /api/auth/login
```
```json
{
  "email": "aarav@demo.com",
  "password": "wrong-password1"
}
```
**Response** `401 Unauthorized`
```json
{
  "timestamp": "2026-10-21T13:38:04.091938Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Incorrect email or password",
  "path": "/api/auth/login",
  "fieldErrors": []
}
```

**Owner-only action by a member (`403`)**

**Request**
```http
PATCH /api/splits/6ad8bfc2c4db233787483191
```
```json
{
  "name": "Hijacked"
}
```
**Response** `403 Forbidden`
```json
{
  "timestamp": "2026-10-21T13:38:04.159786Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Only the owner of this split can do that",
  "path": "/api/splits/6ad8bfc2c4db233787483191",
  "fieldErrors": []
}
```

**Not a member (`404`)**: the split exists, but Dev is not in it

**Request**
```http
GET /api/splits/6ad8bfc2c4db233787483191
```
**Response** `404 Not Found`
```json
{
  "timestamp": "2026-10-21T13:38:04.162227Z",
  "status": 404,
  "error": "Not Found",
  "message": "Split not found",
  "path": "/api/splits/6ad8bfc2c4db233787483191",
  "fieldErrors": []
}
```

**Rule violation (`409`)**

**Request**
```http
POST /api/auth/register
```
```json
{
  "name": "Priya Again",
  "email": "priya.712038@example.com",
  "password": "Demo@1234"
}
```
**Response** `409 Conflict`
```json
{
  "timestamp": "2026-10-21T13:38:04.164338Z",
  "status": 409,
  "error": "Conflict",
  "message": "An account with this email already exists. Try logging in.",
  "path": "/api/auth/register",
  "fieldErrors": []
}
```

**Too many login attempts (`429`)** after 10 failures for the same IP and email:

**Request**
```http
POST /api/auth/login
```
```json
{
  "email": "nobody.712038@example.com",
  "password": "wrong-password1"
}
```
**Response** `429 Too Many Requests`
```json
{
  "timestamp": "2026-10-21T13:38:04.739139Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many login attempts. Try again in 5 minutes.",
  "path": "/api/auth/login",
  "fieldErrors": []
}
```
Response header: `Retry-After: 299` (seconds).

---

_40 endpoints documented._
