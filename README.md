# API Gateway

Part of the e-commerce microservice application (Innowise Java Advanced course).
The Gateway is the only public entry point of the system. It routes requests to the business
services, checks the JWT of the caller and runs the user registration flow.

[![CI](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml/badge.svg)](https://github.com/ArsShark/ApiGateway/actions/workflows/ci.yml)

**Status:** project skeleton, Dockerfile and CI pipeline. Security, routes and registration
arrive in the next pull requests (JACAG-19).

## Requirements

- JDK 21
- Docker (image build, later Docker Compose of the whole system)

## Configuration

All settings are environment variables.

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `LOG_DIR` | `logs` | Directory of the log files |

## Run locally

```powershell
.\mvnw.cmd spring-boot:run
```

The log is written to the console and to `logs/api-gateway.log`.

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

The JaCoCo report is written to `target/site/jacoco/`.

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
