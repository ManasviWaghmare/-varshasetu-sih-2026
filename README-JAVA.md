# VarshaSetu — Java Spring Boot Backend

Java port of the Flask API in `SIH Prototype v2/app.py`. Serves the same
dashboard (`src/main/resources/static/`, copied from `templates/` + `static/`)
on port 5000 with identical snake_case JSON keys, so `dashboard.js` works untouched.

## Run

```bash
mvn spring-boot:run
```

Build:

```bash
mvn package -DskipTests
java -jar target/varshasetu-1.0.0.jar
```

Docker:

```bash
docker build -t varshasetu-java .
docker run -p 5000:5000 varshasetu-java
```

Optional live weather: set `OPENWEATHERMAP_API_KEY` env var; otherwise
responses are simulated (same ranges as Flask).

## Endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/` | Dashboard (static `index.html`) |
| GET | `/health` | `{status, cities, model_trained}` |
| GET | `/api/architecture` | Architecture Overview flow |
| GET | `/api/stations` | All cities |
| GET | `/api/weather/{city}` | Live (or simulated) weather; 404 `{status:error}` if unknown |
| GET | `/api/forecast/{city}` | 5-day rainfall forecast |
| GET | `/api/inundation/{city}` | Flood depth / zones / risk |
| GET | `/api/alerts` | Optional `?level=RED` filter |
| GET | `/api/alerts/generate` | Generate over first 15 cities |
| GET | `/api/historical/{city}` | CSV rows for city (max 100) |
| GET | `/api/district/{state}/{district}` | CSV rows for district (max 100) |
| GET | `/api/model/stats` | Model accuracy + engine info |
| GET | `/api/radar/{city}` | 10x10 dBZ grid |
| GET | `/api/satellite/{city}` | Cloud fields |
| GET | `/api/data-sources` | Source statuses |

## ML note

The Python backend trains a `RandomForestClassifier` (sklearn) on the IMD
district CSV. This Java port uses a `JavaRuleEngine` (`ForecastService` +
`InundationService`) implementing the same category/mm-range logic, baseline
lookup, monsoon heuristic, and inundation formula for demo parity — no
sklearn dependency. `/api/model/stats` reports accuracy `87.4` with
`engine: JavaRuleEngine`.
