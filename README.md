# API Gateway

Part of the e-commerce microservice application (Innowise Java Advanced course).
The Gateway is the only public entry point of the system. It routes requests to the business
services, checks the JWT of the caller and runs the user registration flow.

**Status:** project skeleton only. Security, routes and registration arrive in the next pull
requests (JACAG-19).

## Requirements

- JDK 21
- Docker (later: Docker Compose of the whole system)

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

## Versions

Spring Boot 4.0.x with Spring Cloud 2025.1.x (Spring Cloud Gateway 5.0.x, WebFlux). The other
services use Spring Boot 4.1.x: the Spring Cloud release train for Boot 4.1 is not released yet,
and the Gateway needs a supported pair.

## Tests

```powershell
.\mvnw.cmd -ntp clean verify
```

The JaCoCo report is written to `target/site/jacoco/`.
