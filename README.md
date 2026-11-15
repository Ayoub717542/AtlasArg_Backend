# AtlasArg Climate Data API

A Spring Boot service that exposes a small HTTP API for retrieving climate and soil-moisture data from NASA services. AtlasArg is a separate backend repository; it is **not currently connected** to the AtlasAgri frontend.

[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-wrapper-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

## Current scope

- NASA POWER daily point data for temperature and precipitation.
- GPM IMERG Final Daily V07 precipitation for a grid cell.
- NASA SMAP L4 surface and root-zone soil moisture through AppEEARS.
- Three read-only `GET` endpoints; no database or persistence layer is configured.

This API is an integration starting point, not yet a drop-in replacement for the AtlasAgri demo's mock API. The frontend reads local JSON and expects monthly records; AtlasArg currently returns NASA POWER's response or daily GPM/SMAP response data. An adapter and aggregation step are still needed.

## Requirements and credentials

- JDK 25
- Internet access to the NASA services used by the API
- An Earthdata bearer token for GPM IMERG
- Earthdata Login credentials for the SMAP/AppEEARS endpoint

Set credentials in the process environment; do not put real credentials in source control. `EARTHDATA_TOKEN` is required when the application starts because the GPM service validates it during initialization. `EARTHDATA_USERNAME` and `EARTHDATA_PASSWORD` are required when calling the SMAP endpoint.

### PowerShell

```powershell
$env:EARTHDATA_TOKEN = "<your-earthdata-token>"
$env:EARTHDATA_USERNAME = "<your-earthdata-username>"
$env:EARTHDATA_PASSWORD = "<your-earthdata-password>"
```

### macOS / Linux

```bash
export EARTHDATA_TOKEN="<your-earthdata-token>"
export EARTHDATA_USERNAME="<your-earthdata-username>"
export EARTHDATA_PASSWORD="<your-earthdata-password>"
```

## Run locally

The Maven project is in the `AtlasArg/` subdirectory of this repository.

```bash
git clone https://github.com/Ayoub717542/AtlasArg.git
cd AtlasArg/AtlasArg
```

Run the app with the Maven wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

The Spring Boot server uses port `8080` by default. To package and run the tests:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

## API

All endpoints accept decimal latitude/longitude and date strings in `yyyyMMdd` format. There is no application-level authentication configured for incoming requests.

| Method | Path | Query parameters | Response |
| --- | --- | --- | --- |
| `GET` | `/api/climate/nasa-power` | `latitude`, `longitude`, `startDate`, `endDate` | NASA POWER JSON response for `T2M`, `T2M_MAX`, `T2M_MIN`, and `PRECTOTCORR` |
| `GET` | `/api/climate/gpm-rainfall` | `latitude`, `longitude`, `startDate`, `endDate` | Source metadata, grid-cell center, `mm/day` units, and daily precipitation keyed by date |
| `GET` | `/api/climate/smap-soil-moisture` | `latitude`, `longitude`, `startDate`, `endDate`, optional `taskId` | Source metadata, AppEEARS task ID, `m3/m3` units, and daily surface/root-zone values |

### Examples

```bash
curl "http://localhost:8080/api/climate/nasa-power?latitude=32.34&longitude=-6.35&startDate=20240101&endDate=20240131"
```

```bash
curl "http://localhost:8080/api/climate/gpm-rainfall?latitude=32.34&longitude=-6.35&startDate=20240101&endDate=20240103"
```

```bash
curl "http://localhost:8080/api/climate/smap-soil-moisture?latitude=32.34&longitude=-6.35&startDate=20240101&endDate=20240131"
```

SMAP submits a point task to AppEEARS and waits synchronously for it to finish (up to approximately ten minutes). Passing the optional `taskId` reuses an existing task rather than submitting a new one.

## Repository layout

```text
AtlasArg/
  pom.xml
  src/main/java/com/example/AtlasArg/
    controller/  HTTP endpoint definitions
    service/     NASA POWER, GPM IMERG, and AppEEARS clients
  src/main/resources/application.properties
```

## Security and operational notes

- Protect Earthdata credentials and use environment variables or a deployment secret manager. Never commit tokens or passwords.
- Incoming endpoints do not currently implement authentication, authorization, rate limiting, or CORS configuration. Do not expose this service publicly without adding appropriate controls.
- NASA requests are made synchronously; there is no caching or background job API implemented.
- Dates and coordinates are passed through to the external services without comprehensive application-level validation.

## Related project

[AtlasAgri](https://github.com/Ayoub717542/AtlasAgri) is the separate React/Vite demonstration frontend. It currently uses local mock API functions and simulated datasets; no live connection to this backend has been configured.

## License

No license file is currently included. Reuse and redistribution terms have not been specified.
