# API Gateway

Part of the e-commerce microservice application (Innowise Java Advanced course).
The Gateway is the only public entry point of the system. It routes requests to the business
services, checks the JWT of the caller and runs the user registration flow.

[![CI](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml/badge.svg)](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml)

**Status:** project skeleton, Dockerfile, CI pipeline and the security filter (JWT check).
The routes and the registration flow arrive in the next pull requests (JACAG-19).

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

- An access token cannot be revoked: it is valid until it expires (15 minutes). A user that is
  deactivated or deleted keeps a working token until then.
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
