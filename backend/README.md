# Smart Library – Backend (Java 17 + Spring Boot 3 + PostgreSQL)

## Run
```bash
# 1. database
docker compose -f ../docker-compose.yml up -d db      # or use your own PostgreSQL (db: smart_library)

# 2. backend
cd backend
mvn spring-boot:run                                    # http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
mvn test                                               # unit tests
```
Env vars: `DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET (>=32 chars), CORS_ORIGINS, SEED_ENABLED, ADMIN_EMAIL, ADMIN_PASSWORD`.

Demo accounts (seeded): `admin@library.com / Admin@12345`, `member@library.com / Member@12345`.

## Structure
```
com.smartlibrary
├── config/      AppProperties, LibraryProperties (business rules), SecurityConfig, CorsConfig, OpenApiConfig, DataSeeder
├── security/    JwtService, JwtAuthFilter, UserPrincipal, CustomUserDetailsService, 401/403 JSON handlers
├── controller/  Auth, Book, Category, Loan, User, AdminBook, AdminUser, AdminLoan, Report
├── dto/         request/response records (+ projection/ for JPQL constructor results)
├── entity/      User, Membership, Category, Book, BookCopy, Loan, AuditLog + enums
├── repository/  Spring Data JPA (locking queries live here)
├── service/     Auth, Book, Category, Loan, Membership, User, Report, Audit  <- ALL business rules
├── exception/   ApiException family + GlobalExceptionHandler (uniform error body)
└── scheduler/   nightly membership expiry
```

## Endpoints (/api/v1)
| Method | Path | Access |
|---|---|---|
| POST | /auth/register, /auth/login | public |
| GET | /books?q=&author=&category=&status=available&page=&size=&sort= , /books/{id}, /categories | public |
| POST | /loans {bookId} | MEMBER |
| POST | /loans/{id}/return {condition?} , /loans/{id}/renew | owner or ADMIN |
| GET | /users/me, /users/me/loans?status=, /users/me/membership | logged in |
| GET | /users/{id}/reading-history | ADMIN or same user |
| POST/PUT/DELETE | /admin/books, /admin/books/{id} (DELETE = archive) | ADMIN |
| GET/POST | /admin/books/{id}/copies ; PATCH /admin/copies/{id}/status | ADMIN |
| POST/PUT | /admin/categories | ADMIN |
| GET | /admin/users?q= , /admin/users/{id}, /admin/users/{id}/memberships | ADMIN |
| PATCH | /admin/users/{id}/status {status: ACTIVE\|SUSPENDED} | ADMIN |
| PUT | /admin/users/{id}/membership {planName,startDate,endDate,status?} | ADMIN |
| GET/POST | /admin/loans?userId=&status=&overdue= ; POST /admin/loans/issue {userId,bookId} | ADMIN |
| GET | /reports/dashboard, /most-read-categories, /borrowing-trends, /inventory, /overdue, /audit-logs | ADMIN |

## Design decisions
* **Concurrency**: borrow locks the member row, then claims a copy with `SELECT … FOR UPDATE SKIP LOCKED`; two users asking for the last copy -> exactly one succeeds. `BookCopy` also has `@Version`.
* **One transaction**: loan row + copy status change commit together (return likewise). Double return -> 409.
* **Membership** validity = `status ACTIVE` and `startDate <= today <= endDate` (date based, nightly job only tidies status). Each admin update adds a new period; history is kept.
* **Rules are config** (`library.*` in application.yml): loan days 14, max 3 active loans, 1 renewal (+7 days), block borrowing when overdue, 30-day trial membership on register. Demo values – confirm real policy.
* **Errors**: `{timestamp,status,code,message,path,fieldErrors}`; 401/403/404/409/422 are consistent. 422 = policy block, 409 = state conflict.
* **Security**: BCrypt, stateless JWT, user re-loaded per request (suspension takes effect instantly), role rules on URL + `@PreAuthorize`, register can only create MEMBER.
* **Not included on purpose**: fines (not specified), refresh tokens, Flyway. For production switch `DDL_AUTO=validate` and add Flyway migrations.

## Quick demo (curl)
```bash
T=$(curl -s -XPOST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
   -d '{"email":"member@library.com","password":"Member@12345"}' | jq -r .accessToken)
curl -s localhost:8080/api/v1/books?status=available
curl -s -XPOST localhost:8080/api/v1/loans -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"bookId":1}'
curl -s localhost:8080/api/v1/users/me/loans -H "Authorization: Bearer $T"
```
