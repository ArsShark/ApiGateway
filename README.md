# API Gateway

Part of the e-commerce microservice application (Innowise Java Advanced course).
The Gateway is the only public entry point of the system. It routes requests to the business
services and checks the JWT of the caller.

[![CI](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml/badge.svg)](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml)

**Status:** the task JACAG-19 is complete: Spring Cloud Gateway on WebFlux, the JWT check, the
routes, the Dockerfile, the CI pipeline and a Postman collection that checks the whole system. The
Docker Compose of the whole system lives in the
[Infrastructure](https://github.com/ArsShark/Infrastructure) repository. The registration flow runs
in the Authentication Service; the Gateway only forwards it.

## Requirements

- JDK 21
- Docker (image build)
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
| `POST /api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/validate` | Authentication Service |

- The routes only decide where a path goes; the security rules decide who may call it. A path
  without a route is a 404. `/internal/**` and the key set of the Authentication Service are not
  routed.
- The Gateway waits 2 seconds to connect to a service and 10 seconds for its answer. The answer of
  a service, its errors included, is passed on unchanged.
- When a service cannot be reached the caller gets an RFC 9457 problem with a constant detail: 502
  `Bad Gateway` (refused, dropped, unknown host), 504 `Gateway Timeout` (no answer in time).
  The internal address of the service is never in the answer.
- The headers `X-Internal-Key`, `Forwarded` and `X-Forwarded-*` of a request from outside are
  removed. The Gateway sends its own `X-Forwarded-Host`, `-Port`, `-Proto` and `-For` to the
  services, so the links that a service builds (the `Location` of a created resource) point to the
  Gateway, and a client cannot make them point to a host of its own.
- In Spring Cloud Gateway 5 the property `spring.cloud.gateway.server.webflux.trusted-proxies` is
  what switches the `X-Forwarded-*` headers on: without it the Gateway sends none and the
  services answer with their internal address (`http://user-service:8082/...`). It is set in
  `application.yml`, and a test pins it. Behind an Ingress (Kubernetes) the address of the Ingress
  belongs in this property.

## Registration

`POST /api/auth/register` is forwarded to the Authentication Service, which runs the whole flow:
it creates the profile in the User Service (`POST /internal/users` with the internal key), saves
the account with the email of the profile as the login, and deletes the profile again if the
account cannot be saved. The body, the answers (201, 400, 409, 502, 504) and the known limits are
described in the README of the Authentication Service. The path is public: the `Authorization`
header is not looked at, so a stale token cannot break a registration.

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

## Run the whole system

The Docker Compose of the whole system (the Gateway, the Authentication Service, the User Service,
their databases and Redis, later the Order and Payment services) lives in the
[Infrastructure](https://github.com/ArsShark/Infrastructure) repository, together with the
instructions: the `.env` file, the keys, the images and the networks.

Compose starts the Gateway from the image that the CI pipeline of this repository publishes
(`ghcr.io/arsshark/apigateway`). To run a Gateway that you built yourself, build the image here and
set `API_GATEWAY_IMAGE` in the `.env` file of the Infrastructure repository:

```powershell
docker build -t api-gateway:dev .
```

```
API_GATEWAY_IMAGE=api-gateway:dev
```

## Postman

`postman/ApiGateway_postman_collection.json` checks the running system through the Gateway only
(`baseUrl` is `http://localhost:8080`): 45 requests and 101 checks.

1. Start the system with the Docker Compose of the Infrastructure repository and import the
   collection into Postman.
2. Set the CURRENT values of the collection variables `adminLogin`, `adminPassword` and
   `internalKey` (secrets are not stored in the file; never use the initial values).
3. Run the folders top to bottom in the Collection Runner.

| Folder | What it proves |
|---|---|
| 0. Setup | the admin logs in through the Gateway |
| 1. Registration, login and tokens | register, log in, own profile, card, refresh (also with a garbage `Authorization` header), validate, a used refresh token |
| 2. Registration errors | invalid body, malformed JSON, wrong content type, taken email, too long password |
| 3. Routing through the Gateway | methods, bodies, queries and errors of the User Service pass through; the forged `X-Forwarded-*` headers of a client never decide the `Location` |
| 4. Security at the edge | no token, bad tokens, `/internal` and the key set are closed even with the real internal key, a user cannot read or list other users |
| 5. Registration rollback | a registration that fails at the account leaves no profile behind (the undoing runs in the Authentication Service) |
| 6. Cleanup | the profiles of the run are deleted |

Every run uses unique data and deletes its profiles. The accounts of the registered users stay in
the Authentication Service: nothing deletes an account by its login, on purpose. The access token
of the admin lives 15 minutes: run the collection again if it expires.

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

- The limits of the registration (a crash between its two steps, an undoing that fails) belong to
  the Authentication Service: see its README.
- An access token cannot be revoked: it is valid until it expires (15 minutes). A user that is
  deactivated or deleted keeps a working token until then.
- The Gateway does not retry and has no circuit breaker: a failed call is a 502 or 504 at once.
- There is no limit for the number of login attempts. A rate limit needs a shared store (Redis),
  which the Gateway does not have in the architecture.
- At the log level `DEBUG` or `TRACE` the HTTP client of Spring and Reactor Netty write the
  internal address of the key set. The application runs at `INFO`.

## Troubleshooting

| Symptom | Usual cause |
|---|---|
| every token is 401 and the log of the Gateway or the User Service has `A token could not be verified` | the key set of the Authentication Service cannot be read: the address `AUTH_JWKS_URI` is wrong, the Authentication Service is not running, or (outside Compose) the Windows firewall blocks Java |
| the whole system does not start (`.env`, images, keys, passwords of the databases) | see the README of the Infrastructure repository |
| `curl` cannot connect to `127.0.0.1` on a proxy port | an `http_proxy` variable is set: use `curl.exe --noproxy "*"` |

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
