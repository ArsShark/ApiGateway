# API Gateway

Part of the e-commerce microservice application (Innowise Java Advanced course).
The Gateway is the only public entry point of the system. It routes requests to the business
services, checks the JWT of the caller and runs the user registration flow.

[![CI](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml/badge.svg)](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml)

**Status:** project skeleton, Dockerfile, CI pipeline, the security filter (JWT check) and the
routes to the User Service and the Authentication Service and the registration flow. The Docker
Compose of the whole system arrives in the next pull request (JACAG-19).

## Requirements

- JDK 21
- Docker (image build, later Docker Compose of the whole system)
- The Authentication Service, which publishes the key set that verifies the tokens

## Configuration

All settings are environment variables. The profile `local` is the default
(`spring.profiles.default`); Docker Compose sets `SPRING_PROFILES_ACTIVE=docker`. The profile
files hold only the default address of the Authentication Service.

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `LOG_DIR` | `logs` | Directory of the log files |
| `USER_SERVICE_URI` | `local`: `http://localhost:8082`, `docker`: `http://user-service:8082` | Internal address of the User Service |
| `AUTH_SERVICE_URI` | `local`: `http://localhost:8081`, `docker`: `http://authentication-service:8081` | Internal address of the Authentication Service |
| `INTERNAL_API_KEY` | none, required | The shared key of the internal endpoints (header `X-Internal-Key`); the same value in every service. The Gateway does not start without it |
| `AUTH_JWKS_URI` | `local`: `http://localhost:8081/.well-known/jwks.json`, `docker`: `http://authentication-service:8081/.well-known/jwks.json` | Internal address of the key set of the Authentication Service. Never the address of the Gateway: it does not route the key set |
| `JWT_ISSUER` | `authentication-service` | The `iss` that a token must have; the same value as in the Authentication Service |

## Security

The Gateway is a resource server. The access tokens are signed by the Authentication Service
(RS256); the Gateway verifies them with the public key from its key set and never signs
anything. The chain is stateless and closed by default.

| Request | Who may call it |
|---|---|
| `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/register` | everybody (the `Authorization` header is not looked at) |
| `POST /api/auth/validate` | a caller with the role `ADMIN` or `USER` |
| `/api/users/**`, `/api/cards/**` | a caller with the role `ADMIN` or `USER`; who may do what is decided by the User Service, which checks the token again |
| everything else, the internal paths and the key set included | nobody: 401 for an anonymous caller, 403 for an authenticated one |

- A missing, wrong, expired or unverifiable token gets the same answer: 401 with the detail
  `Authentication is required` (`application/problem+json`). A caller without the right gets
  403 with `Access is denied`. The texts are constants.
- A token is checked for: the signature (RS256 only), the issuer, the expiry (no clock skew, a
  token without `exp` is refused) and `token_type = access`. A role other than exactly `ADMIN`
  or `USER` gives no authority.
- The key set is read without blocking: timeouts of 2 s, an answer limit of 64 KB, a cache of
  5 minutes. A token with an unknown key id causes a new call at most once per 30 seconds. If
  the key set cannot be read, the token is refused (401), never accepted and never a 500.
- The header `X-Internal-Key` of a request from outside is removed before anything else runs.

## Routes

| Public request | Goes to |
|---|---|
| `/api/users/**`, `/api/cards/**` (any method) | User Service |
| `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/validate` | Authentication Service |
| `POST /api/auth/register` | answered by the Gateway itself (next pull request), never proxied |

- The routes only decide where a path goes; the security rules decide who may call it. A path
  without a route is a 404. `/internal/**` and the key set of the Authentication Service are not
  routed.
- The Gateway waits 2 seconds to connect to a service and 10 seconds for its answer. The answer of
  a service, its errors included, is passed on unchanged.
- When a service cannot be reached the caller gets an RFC 9457 problem with a constant detail: 502
  `Bad Gateway` (refused, dropped, unknown host), 504 `Gateway Timeout` (no answer in time).
  The internal address of the service is never in the answer.
- The headers `X-Internal-Key`, `Forwarded` and `X-Forwarded-*` of a request from outside are
  removed. The Gateway adds its own `X-Forwarded-*` headers, so a client cannot make the services
  build links to a host of its own.

## Registration

`POST /api/auth/register` is answered by the Gateway itself. Body: `name`, `surname`, `birthDate`,
`email`, `password`. The rules are those of the User Service (profile) and of the Authentication
Service (password: 8 to 72 bytes, not blank); the email is the login.

1. The profile is created in the User Service (`POST /internal/users`).
2. The account is created in the Authentication Service (`POST /internal/credentials`), with the
   email of the profile as the login and the id of the profile.
3. The answer is 201 with the new profile (no token: the user logs in next).

Each service has its own database, so there is no transaction around the two steps. If step 2
fails, the Gateway undoes step 1 by compensation: it deletes the account by the id of the profile
(`DELETE /internal/credentials/by-user/{userId}`) and then the profile
(`DELETE /internal/users/{id}`). The account is deleted even when the Authentication Service did
not answer: the answer may only have been lost, and an account without a profile would keep the
email for ever. Both deletions accept "already gone" and are safe to repeat; a failed deletion is
logged (`ERROR`, with the id of the profile) and does not stop the other one. The flow does not
depend on the client: a client that disconnects in the middle cannot cancel it.

| Answer | When |
|---|---|
| 201 | registered |
| 400 | the body is invalid (the fields are listed, the values never) or unreadable |
| 409 | the email is taken (the profile exists, or an account with this login does) |
| 502 | a service answered with an error, or could not be reached |
| 504 | a service did not answer in time |

## Run locally

```powershell
.\mvnw.cmd spring-boot:run
```

The log is written to the console and to `logs/api-gateway.log`. Tokens, keys, passwords and
the address of the key set are never logged (a test checks it).

## Run with Docker

```powershell
docker build -t api-gateway:dev .
docker run --rm -p 8080:8080 api-gateway:dev
```

The image is built in two stages (JDK with the Maven Wrapper, then a JRE-only runtime). The
tests are not run in the image build: the CI pipeline runs them. The application runs as the
non-root user `app`, and the log files are written to `/app/logs`.

## Versions

Spring Boot 4.0.x with Spring Cloud 2025.1.x (Spring Cloud Gateway 5.0.x, WebFlux). The other
services use Spring Boot 4.1.x: the Spring Cloud release train for Boot 4.1 is not released yet,
and the Gateway needs a supported pair.

## Tests

```powershell
.\mvnw.cmd -ntp clean verify
```

The tests need no Docker and no other service: the key set is played by a small HTTP server of
the tests. The JaCoCo report is written to `target/site/jacoco/`.

## Known limits

- A crash of the Gateway between the two steps of the registration cannot be undone by the Gateway:
  it leaves a profile without an account, which keeps its email. The same happens when the creation
  of the profile itself gets no answer (the Gateway does not know the id). Both need a cleanup by
  hand. A repair job would fix them; it is not part of this task.
- An undoing whose deletions fail leaves an account or a profile behind; it is logged at `ERROR`.
- An access token cannot be revoked: it is valid until it expires (15 minutes). A user that is
  deactivated or deleted keeps a working token until then.
- The Gateway does not retry and has no circuit breaker: a failed call is a 502 or 504 at once.
- There is no limit for the number of login attempts. A rate limit needs a shared store (Redis),
  which the Gateway does not have in the architecture.
- At the log level `DEBUG` or `TRACE` the HTTP client of Spring and Reactor Netty write the
  internal address of the key set. The application runs at `INFO`.

## CI pipeline

The workflow `.github/workflows/ci.yml` runs on every push, on pull requests into `main` and
manually.

| Job | Steps |
|---|---|
| `build` | build, tests, SonarQube Cloud analysis (pull requests and `main` only), upload of the JUnit and JaCoCo reports |
| `docker` | builds the image on every run; pushes `ghcr.io/arsshark/apigateway` (tags `latest` and `sha-<short commit id>`) only on pushes to `main` or on a manual run with `publish` ticked |

The third-party actions are pinned to a commit SHA, because the `docker` job holds a token that
may write packages. The analysis needs the secret `SONAR_TOKEN` and the variables
`SONAR_ORGANIZATION` and `SONAR_PROJECT_KEY` in the repository settings. The quality gate does
not fail the build yet.
