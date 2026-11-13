# Migrating to Spring Boot 3.x (Jakarta EE 9+)

Spring Boot 2.7.x is EOL. This document is a concrete, scoped starting point for the
migration - not the migration itself. It was deliberately **not** attempted as part of
this security/architecture pass: it's a large, mechanical, high-blast-radius change
(every entity, every controller, security config, and the JWT library all shift at
once), and doing it without the ability to compile-check the result in this
environment would be irresponsible. Do this as its own reviewed change, with a real
build available.

## Why this matters

Spring Boot 2.7 reached end-of-life in November 2023 - no further security patches.
Staying on it indefinitely means accumulating unpatched CVEs in the framework itself,
independent of anything in this codebase's own code.

## Scope of the change

### 1. The `javax.*` → `jakarta.*` namespace switch

Jakarta EE 9 renamed every `javax.*` package this project uses to `jakarta.*`. Current
inventory (38 files affected):

| Current import | Becomes |
|---|---|
| `javax.persistence.*` | `jakarta.persistence.*` |
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` |

This is mechanical (a find-and-replace, essentially) but touches every entity
(`@Entity`, `@Column`, `@ManyToOne`, etc.), every DTO with `@NotBlank`/`@Email`/etc.,
`JwtAuthenticationFilter` and `RateLimitFilter` (`javax.servlet.*`), and `JwtService`
(`javax.annotation.PostConstruct`). Run a full project-wide search-and-replace, then
rely on the compiler to catch anything missed - this is exactly the kind of change
where "does it compile" is a genuinely sufficient correctness check.

### 2. `pom.xml`

- Bump `spring-boot-starter-parent` to a 3.x version (3.2.x or later recommended).
- Bump `<java.version>` to at least 17 (3.x requires it; this project is already on 17).
- MySQL connector: `mysql:mysql-connector-java` was renamed to
  `com.mysql:mysql-connector-j` starting with Boot 3's dependency management - update
  the groupId/artifactId, not just the version.
- Lombok: current versions support Boot 3 fine, no action needed beyond re-verifying
  annotation processing still works after the namespace switch.

### 3. Spring Security 6

This project's `SecurityConfig` already uses the modern lambda DSL
(`authorizeHttpRequests(auth -> ...)`), which carries forward to Security 6 mostly
unchanged - one deliberate exception:

- **`antMatchers()` is removed in Security 6** - replace every `.antMatchers(...)`
  with `.requestMatchers(...)` (same argument shape, different method name).
- `ResponseCookie`, `SessionCreationPolicy`, `@PreAuthorize` SpEL expressions
  (including the `@studentAccessService.isOwnStudent(...)` custom-bean pattern used
  throughout this project) are all unchanged in Security 6.

### 4. JWT library (`io.jsonwebtoken` / jjwt)

The jjwt API used in `JwtService` (`Jwts.builder()`, `Jwts.parserBuilder()`,
`Keys.hmacShaKeyFor()`) is stable across the versions compatible with both Boot 2.7
and Boot 3.x - no API changes needed here specifically for the Boot upgrade, but this
is a good moment to also bump jjwt to its latest 0.12.x release if not already there
(check `pom.xml`), since the builder API changed slightly between the 0.11.x and
0.12.x lines independent of Spring Boot.

### 5. Things that do NOT need to change

- `schema.sql` - pure SQL, no Spring/Jakarta dependency at all.
- The frontend (`static/**`) - no Java, unaffected.
- `application.properties` keys - unchanged between Boot 2.7 and 3.x for everything
  used in this project (datasource, JPA, mail, multipart, etc.).
- Business logic - the actual service/controller code doesn't change, only its
  imports and (in `SecurityConfig`) one method name.

## Recommended migration order

1. Create a branch. Don't do this on top of uncommitted work.
2. Bump `pom.xml` (parent version, MySQL connector coordinates).
3. Global find-and-replace `javax.` → `jakarta.` across `src/main/java` and
   `src/test/java` for the four packages above.
4. Fix `SecurityConfig`: `antMatchers` → `requestMatchers`.
5. Compile. Fix whatever the compiler flags (there will likely be a handful of
   stragglers this document's inventory didn't catch, e.g. inside test files).
6. Run the full test suite (`SisApplicationTests` plus anything added per the
   Testing section below) against the H2 test profile.
7. Manually verify against a real MySQL instance: login, the full student flow,
   the admin flow, file upload/download.
8. Only then merge.
