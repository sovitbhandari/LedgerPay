# LedgerPay

Educational double-entry ledger for synthetic funds. It is an internal practice service, not a payment processor, not a bank, and not connected to any real money network.

Customers hold accounts. A transfer debits one customer account and credits another in the same database transaction. Synthetic deposits credit a customer account and debit a per-currency system funding account, which is allowed to go negative so the books stay balanced. Every persisted movement is one `DEBIT` and one `CREDIT` of the same minor-unit amount.

## Stack

- Java 21, Maven, Spring Boot 4.1.2
- Spring MVC, Spring Data JPA, Spring Security, Jakarta Bean Validation
- PostgreSQL 16, Flyway
- Tests use the JUnit Jupiter API. Spring Boot 4.1.2 manages JUnit 6 (the current Jupiter generation), Mockito 5, and Testcontainers 2
- Docker Compose and GitHub Actions

Jackson 3 is the JSON library brought in by Spring Boot 4. Authentication tokens are HMAC-SHA256 JWTs signed with the JDK, not an external JWT library.

## Money

Amounts are integers in minor units. The API accepts a decimal string and stores the minor-unit value. Floating-point amounts are rejected.

| Currency | Scale | Example | Stored |
| --- | --- | --- | --- |
| USD, EUR, GBP | 2 | `10.50` | `1050` |
| JPY | 0 | `500` | `500` |

`10`, `10.5`, and `10.50` are the same USD amount. `10.501` is rejected for USD. `500.0` is rejected for JPY. The largest single movement is `1000000000000` minor units. Zero and negative amounts are rejected.

Customer balances cannot be negative. That rule is checked in the posting service and by `accounts_customer_balance_non_negative`. The stored `balance_minor` column is a projection updated in the same transaction as the ledger lines. The ledger lines are the audit trail.

## Transaction boundary

`LedgerPostingService.post` is the only method that changes balances and ledger lines. It runs in one `READ COMMITTED` transaction with a 15 second transaction timeout:

1. Insert the caller's idempotency claim and flush it. A second request with the same user and key waits on the unique index `uq_idempotency_requester_key`.
2. Lock the two account rows with `SELECT … FOR UPDATE` in ascending account-id order. The database `lock_timeout` for that transaction is 5 seconds. Sorting the ids is what keeps opposing transfers from deadlocking each other.
3. Re-check ownership, status, currency, and available funds on the locked rows.
4. Insert the transfer and exactly two ledger lines, then apply the debit and the credit to the locked balances.
5. Attach the transfer id to the idempotency claim.
6. Commit. Deferred constraints reject a commit whose transfer is not a balanced pair, and reject an idempotency claim that never received a transfer id.

A runtime failure rolls the claim, the transfer, both lines, and both balance updates back together. Business failures such as insufficient funds do not keep the idempotency key, so the caller can retry that key after the account has funds. A committed key is kept. The same key and the same minor-unit payload returns the original transfer. The same key with a different payload returns `409 IDEMPOTENCY_KEY_REUSED`.

Synthetic deposits use the same boundary. The source is the system funding account for that currency, not an account id supplied by the client.

## Security

Passwords are hashed with BCrypt strength 12. Login and a missing user return the same `401` message. The password check still runs against a dummy hash when the email is unknown.

Routes other than register, login, and the actuator health and info endpoints require `Authorization: Bearer <jwt>`. The filter loads the user from the database and rejects the system user. Account reads and debits require ownership of that customer account. A missing account is `404`. An account owned by someone else is `403 OWNERSHIP_VIOLATION`.

The JWT secret in `application.yml` is a local default. Set `LEDGERPAY_JWT_SECRET` to at least 32 bytes outside a personal machine. Tokens expire after `ledgerpay.jwt.ttl` (default 1 hour). There is no refresh token and no revocation list.

## API

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | no | Creates a user and returns a bearer token |
| POST | `/api/v1/auth/login` | no | Returns a bearer token |
| GET | `/api/v1/accounts?page&size` | yes | The caller's customer accounts |
| POST | `/api/v1/accounts` | yes | Opens an account at balance 0 |
| GET | `/api/v1/accounts/{id}/balance` | yes | Owner only |
| GET | `/api/v1/accounts/{id}/ledger?page&size` | yes | Owner only, newest first |
| POST | `/api/v1/accounts/{id}/status` | yes | `ACTIVE`, `FROZEN`, or `CLOSED` |
| POST | `/api/v1/accounts/{id}/deposits` | yes | Synthetic funds. Requires `Idempotency-Key` |
| POST | `/api/v1/transfers` | yes | Requires `Idempotency-Key` |
| GET | `/api/v1/transfers/{id}` | yes | Requester or an account owner on the transfer |
| GET | `/actuator/health` | no | Includes readiness when the database is up |
| GET | `/actuator/health/liveness` | no | Process liveness |
| GET | `/actuator/health/readiness` | no | Database readiness |

`Idempotency-Key` is 8 to 200 characters from `A-Z a-z 0-9 . _ ~ -`. Page size is 1 to 100.

Errors are JSON:

```json
{
  "timestamp": "2026-11-17T00:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_FUNDS",
  "message": "Account balance is insufficient for this transfer.",
  "path": "/api/v1/transfers"
}
```

Validation errors add `fieldErrors`. Status `422` is used for currency mismatch, inactive accounts, same-account transfers, and illegal status changes.

Account status moves `ACTIVE ↔ FROZEN`, and either of those can become `CLOSED`. `CLOSED` cannot be reopened. Frozen and closed accounts cannot send or receive.

## Local PostgreSQL

Docker Desktop or another engine must be running.

```bash
docker compose up -d postgres
```

That publishes `localhost:5432` with database, user, and password `ledgerpay`. If a PostgreSQL server is already listening on `127.0.0.1:5432`, publish a different host port:

```bash
LEDGERPAY_POSTGRES_PORT=5433 docker compose up -d postgres
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/ledgerpay
```

Then:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./mvnw spring-boot:run
```

If `JAVA_HOME` is not set through `java_home`, point it at a JDK 21 install. On this machine that path is `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

The app listens on port 8080. Flyway applies `src/main/resources/db/migration/V1__init_ledger.sql` on startup. Hibernate validates the schema and does not create tables.

## Tests

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./mvnw verify
```

`mvn verify` is the same command when Maven is already installed. Integration tests start their own PostgreSQL 16 container through Testcontainers. They do not use the Compose database. Docker must be running. The suite is not parallel: the concurrency tests share one migrated database and create their own users.

GitHub Actions workflow `.github/workflows/verify.yml` runs `./mvnw -B verify` on Ubuntu with Temurin 21.

## Docker Compose application

```bash
docker compose up --build
```

The image is a multi-stage build: Maven and Temurin 21 JDK compile the jar, then a Temurin 21 JRE runs it as the `ledger` user. Compose waits for Postgres health, then starts the app with `LEDGERPAY_JWT_SECRET` set to a local-only value. The image health check calls `/actuator/health`.

```bash
curl -sS http://localhost:8080/actuator/health
```

## Example flow

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"Password12345"}' | jq -r .accessToken)

ACCOUNT=$(curl -sS -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spending","currency":"USD"}' | jq -r .id)

curl -sS -X POST "http://localhost:8080/api/v1/accounts/$ACCOUNT/deposits" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: deposit-demo-001' \
  -H 'Content-Type: application/json' \
  -d '{"amount":"25.00"}'
```

Create a second user and account, then post a transfer with `Idempotency-Key`. Repeating that request returns the same transfer id.

## Limitations

- Synthetic deposits are unlimited aside from the per-request maximum. Any authenticated user can fund their own account. That is intentional for this exercise and unsafe for anything else.
- The system funding accounts absorb the other side of those deposits and may be negative.
- One PostgreSQL database. No multi-region story, no idempotency TTL, and no outbox.
- JWTs are symmetric, local, and not revocable. There is no MFA, lockout, or rate limit.
- Balance reads return the last committed projection. They are not a serializable snapshot across accounts.
- Ledger lines do not store a running balance. Reconstruction is the sum of lines.
- Supported currencies are the four codes in `LedgerCurrency`.
- No PCI scope, no KYC, no external rails, and no production deployment was performed.
- Security coverage is the automated API tests in this repo. No scanner or penetration test was run.

## Verification log

Observed on this machine on 17 November 2026. These are the commands that were actually run. Nothing below is a forecast.

Runtime: Homebrew OpenJDK `21.0.13.0.3`. `./mvnw -v` reported Apache Maven `3.9.9`. Docker `28.1.1` and Compose `v5.5.1`. PostgreSQL image `postgres:16-alpine`, server version `16.15` in the container logs.

```text
./mvnw -B verify
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
Total time: 10.734 s
```

That run is the one that matches the current source, including the exclusion of Spring Boot's generated in-memory user. Class counts from the same run:

| Test class | Result |
| --- | --- |
| `SecurityAccessTest` | 4 passed |
| `AuthServiceTest` | 2 passed |
| `ConcurrentTransferTest` | 3 passed |
| `PostgresTransactionRollbackTest` | 3 passed |
| `RequestFingerprintTest` | 1 passed |
| `IdempotencyServiceTest` | 3 passed |
| `MoneyTest` | 2 passed |
| `LedgerApiTest` | 4 passed |
| `AccountStatusTest` | 1 passed |

`ConcurrentTransferTest` ran 16 simultaneous `$1.00` spends against a `$5.00` balance, 20 opposing transfers, and 8 duplicate idempotency keys. The duplicate-key case logged PostgreSQL `uq_idempotency_requester_key` conflicts and the test still passed, with one persisted transfer. No throughput or latency number was measured.

A local PostgreSQL was already listening on `127.0.0.1:5432`, so Compose was published on host port `5433`. The jar was started with Java 21:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/ledgerpay java -jar target/ledgerpay.jar
```

Flyway logged `Successfully applied 1 migration to schema "public", now at version v1`. The process logged `Started LedgerPayApplication in 2.5 seconds`. Against that process:

- `GET /actuator/health` returned `200` and `{"status":"UP"}`.
- `GET /actuator/health/readiness` returned `200` and `{"status":"UP"}`.
- Register, create two USD accounts, deposit `25.00`, and transfer `10.50` all returned `201`.
- Repeating the transfer with the same `Idempotency-Key` and amount `10.5` returned the same transfer id `732c917e-1e88-48e3-8543-1b7b70353c0c`.
- Balances were `14.50` (`1450`) and `10.50` (`1050`).
- `GET /api/v1/accounts` without a token returned `401` `UNAUTHENTICATED`.
- In Postgres, that transfer had one `DEBIT` and one `CREDIT` of `1050` USD. `select coalesce(sum(balance_minor),0) from accounts` returned `0`. One idempotency row pointed at the transfer.

`docker compose build` produced `ledgerpay-app`. The first image start logged Spring Boot's generated in-memory user. `UserDetailsServiceAutoConfiguration` was then excluded. `./mvnw -B verify` was run again (the 23 tests above). The image was rebuilt and started with `LEDGERPAY_POSTGRES_PORT=5433 docker compose up -d --build app`. Health and readiness were `200` `UP`. The new container log did not contain a generated security password. `Started LedgerPayApplication in 2.413 seconds`. Compose was stopped afterward. The image was not pushed anywhere, and this was not a production deploy.

GitHub Actions was not executed from this machine. The workflow file is present and was not run.
