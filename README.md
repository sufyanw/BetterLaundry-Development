# BetterLaundry



BetterLaundry is an accessible web application that any device with internet access can use at any time to see real-time laundry cycle status, progress, and AI-generated feedback tailored to the behavior of my family's two Samsung appliances, without requiring any app installation. The overall objective is to create a personalized, enhanced laundry experience for my family.

---
## App Deployment

BetterLaundry is hosted on Heroku. I have previous experience with Heroku from CS-UY 4513 Software Engineering, so deploying for this project was very easy. All I had to do was create the app, add and commit my changes, and run `git push heroku main`. Then I set up a PostgreSQL Essential 0 database, and I was good to go. Note that I did all of this using the Heroku CLI.

https://betterlaundry-322c320b9860.herokuapp.com/index.html

---
## Environment Variables

In the event where you would like to host your own BetterLaundry, here's the environment variables you will need:

```
SMARTTHINGS_TOKEN=
WASHER_DEVICE_ID=
DRYER_DEVICE_ID=
GEMINI_API_KEY=
DATABASE_URL=
```
---
## System Design

The entry point of the app is `betterlaundry.Main`, which loads `AppConfig` from environment variables, constructs a `DatabaseManager`, `SmartThingsClient`, `DeviceStatusParser`, a fixed list of `LaundryDevice` instances (which is `WasherDevice` and `DryerDevice`), starts `DevicePollingService` to pull live data from the SmartThingsAPI, then starts `DashboardServer` with a `GeminiAnalysisService`. Note that port comes from the `PORT` environment variable when set (e.g. Heroku), otherwise it's from `AppConfig`.

---
### Packages

- `betterlaundry.config`

AppConfig: Loads & validates all environment variables at startup. If anything required is missing, the app throws a ConfigurationException.

DeviceConstants: Where the SmartThings capability and attribute key strings live (e.g. "washerOperatingState").

- `betterlaundry.client` 

SmartThingsClient: The only class in the app that talks directly to the SmartThingsAPI. It handles authentication with the PAT, makes HTTP GET requests for device status, and maps non-2xx responses to a SmartThingsAPIException. Nothing else in the app knows about the HTTP mechanics.

- `betterlaundry.parser` 

DeviceStatusParser: Takes the raw JSON that SmartThingsClient returns and maps it into typed Java objects. If a field is missing, it defaults to a safe value (e.g. MachineState.UNKNOWN, 0.0 for power) rather than throwing an error and crashing the app.

- `betterlaundry.model`

LaundryDevice: Abstract base class for a laundry appliance which defines the shared methods both washer and dryer implement.

WasherDevice: Subclass for the Samsung WF50BG8300AE washer.

DryerDevice: Subclass for the Samsung DVG50BG8300E gas dryer.

WasherStatus: Immutable snapshot of the washer's state at a single point in time

DryerStatus: Immutable snapshot of the dryer's state at a single point in time

CycleRecord: Represents one completed laundry cycle. This is an extremely important class because this is where we store the information about the cycle (e.g. start/end time, temperature level, etc.). It's constructed by DevicePollingService when a cycle-end transition is detected, then handed off to DatabaseManager for persistence.

MachineState: Enum for the possible machine states (RUNNING, PAUSED, STOP, FINISHED, UNKNOWN). Uses EnumMaps to map each state to a display label and a CSS class which is used in the front-end to display the current state.

- `betterlaundry.polling`

DevicePollingService: Runs on a background daemon thread and polls SmartThings every 10 seconds. On each tick, it fetches status for both devices, stores the latest snapshot in concurrent in-memory maps, and compares the new state against the previous one to detect when a cycle goes from running to finished (this is important because the SmartThingsAPI gives us live information, so once the cycle is finished, we need to store it immediately before the data is lost). Like mentioned earlier, it builds a CycleRecord and persists it to the database. It also maintains a bounded queue of SSE event messages that DashboardServer drains to push notifications to connected browsers.

- `betterlaundry.db`

DatabaseManager: Handles all PostgreSQL interactions. It creates a new table on startup if there isn't an existing one, just so that the schema is always up to date. This also implements the Persistable interface for reading and writing cycle history.

DbConfig: Used for getting the Java Database Connectivity (JDBC) URL. Heroku has its database URL as postgres://, so it gets changed here to jdbc:postgresql:// in order for us to actually persist data.

- `betterlaundry.server`

DashboardServer: Sets up Javalin app and registers all routes. This includes the front-end (`index.html`, is mentioned in the next section), /api/status (live device snapshot as JSON), /api/history (recent cycle records), /api/ai (AI Insights), and /api/events (SSE stream for cycle-completion push notifications). JSON responses are assembled with Jackson. A background broadcaster thread drains the SSE queue from DevicePollingService and sends events to all connected clients.

- `betterlaundry.ai`

GeminiAnalysisService: The only class that knows about the Gemini API, implements the AIAnalyzable interface. It takes a list of CycleRecord objects, builds a structured prompt that includes the cycle history, fixed specifications from DeviceConstants, and calls the Gemini Flash 2.5 model (up to 5 requests per minute on the free tier, which is more than enough for the scope of the project). The response is validated for required section headers (Energy Summary, Usage Observations, and Recommendations) before being returned. If they're missing, a fallback message is shown instead. Similar to DashboardServer, JSON responses are assembled with Jackson here, but we use it for communication with the Gemini API.

- `betterlaundry.exception` 

SmartThingsAPIException: Checked exception that wraps HTTP errors from SmartThings with the status code attached, so callers can handle a 429 rate limit differently from a 401 unauthorized.

ConfigurationException: Unchecked exception thrown at startup when required environment variable(s) are missing.

- `betterlaundry.interfaces`

AIAnalyzable: Defines generateSummary(List<CycleRecord>), implemented by GeminiAnalysisService.

Persistable: Defines save(CycleRecord) and load(int limit), implemented by DatabaseManager.

Pollable: Defines poll(), implemented by DevicePollingService.

----
**Front End**

I didn't make anything beautiful for the front-end, given the rubric for the project mentioning that we wouldn't be graded on it. With this in mind, I made a short `index.html` file to display the following:

- Last updated time. This is essentially telling the user the exact time we last polled the SmartThingsAPI. The cadence for when this happens is every 10 seconds though.
- 2 cards (box sections) for the Washer and Dryer, respectively.
- 1 table for the past 10 completed cycle records (recent cycles).
- 1 card at the bottom of the page with a button for the user to generate AI Insights from Gemini's Flash 2.5 model.

---
**Request & Data Flow**

1. A daemon thread in **`DevicePollingService`** polls SmartThings on a fixed interval from **`AppConfig`**.  
2. For each device, **`SmartThingsClient`** fetches status JSON; **`DeviceStatusParser`** produces typed washer/dryer status objects stored in concurrent maps.  
3. On transitions between active and inactive machine states, the poller builds a **`CycleRecord`** and calls **`DatabaseManager.save`**. It also enqueues **`DeviceEvent`** messages for SSE consumers (with a bounded queue; oldest dropped when full).  
4. **`DashboardServer`** serves the dashboard and JSON APIs from the poller’s in-memory snapshots and the database. **`/api/ai`** loads recent records via **`DatabaseManager.load`** and returns text from **`GeminiAnalysisService.generateSummary`**. A background thread drains SSE events and broadcasts to connected clients.
---
## Testing 

> [!NOTE]
> Ensure you've exported all of environment variables through your terminal to ensure that the app will function properly. 
> Without setting the environment variables, the app won't work (why else would you be using this app if you don't own the specific washer and dryer??)

> [!IMPORTANT]
> The SmartThings Personal Access Token (PAT) needs to be refreshed every 24 hours.

1. Run tests

```bash
mvn package
mvn clean test
```
2. If you ran `mvn package` above, then `target/` will have had been created, so you can run the generated JAR file:

```bash
java -jar target/betterlaundry-1.0-SNAPSHOT.jar
```
3. Be sure to check where the port is running. You'll get a log message that will tell you where it is. For example:
```bash
23:52:40.120 [betterlaundry.Main.main()] INFO  betterlaundry.Main - Dashboard live on port 3000.
```

4. Then you can just open up localhost:3000 on your browser.

--- 

## External Libraries & AI

- Javalin
- JUnit5
- SmartThingsAPI
- Gemini 
- Heroku
- PostgreSQL
- SLF4J & Logback -> both used in labs
- Jackson -> used to parse JSON returned by SmartThingsAPI and Gemini API. I also used Jackson to build JSON request bodies and to assemble JSON for `/api/status`, `/api/history`, etc. in [DashboardServer.Java](src/main/java/betterlaundry/server/DashboardServer.java)
---
## SmartThings API

Here is where I refered to in order to get live information for the washer and dryer:

- [washerOperatingState](https://developer.smartthings.com/docs/devices/capabilities/capabilities-reference#washerOperatingState) 

- [dryerOperatingState](https://developer.smartthings.com/docs/devices/capabilities/capabilities-reference#dryerOperatingState)

I was also able to cross-reference by curling into the current status of the Washer and Dryer:

```bash
curl -sS -H "Authorization: Bearer ${SMARTTHINGS_TOKEN}" "https://api.smartthings.com/v1/devices/${WASHER_DEVICE_ID}/status"
```

This provided a very useful JSON response which I could refer to:

```bash
{
    "components": {
      "hca.main": {
        "hca.washerMode": {
          "mode": {
            "value": "normal",
            "timestamp": "2026-05-02T00:47:44.878Z"
          },
          "supportedModes": {
            "value": [
              "normal",
              "quickWash"
            ],
            "timestamp": "2026-05-01T23:04:10.533Z"
          }
        }
      },
      "main": {
        "washerOperatingState": {
          "completionTime": {
            "value": "2026-05-02T01:32:44Z",
            "timestamp": "2026-05-02T00:47:44.910Z"
          },
          "machineState": {
            "value": "stop",
            "timestamp": "2026-05-02T00:47:44.910Z"
          },
          "washerJobState": {
            "value": "none",
            "timestamp": "2026-05-02T00:47:44.910Z"
          }
        },
        "samsungce.washerOperatingState": {
          "operatingState": {
            "value": "ready",
            "timestamp": "2026-05-02T00:47:44.910Z"
          },
          "progress": {
            "value": 1,
            "unit": "%",
            "timestamp": "2026-05-02T00:47:44.910Z"
          },
          "remainingTime": {
            "value": 45,
            "unit": "min",
            "timestamp": "2026-05-02T00:47:44.910Z"
          }
        },
        "samsungce.washerCycle": {
          "washerCycle": {
            "value": "Table_02_Course_01",
            "timestamp": "2026-05-02T00:47:44.878Z"
          },
          "cycleType": {
            "value": "washingOnly",
            "timestamp": "2026-05-02T00:20:43.819Z"
          }
        },
        "custom.washerWaterTemperature": {
          "washerWaterTemperature": {
            "value": "warm",
            "timestamp": "2026-05-01T23:15:18.950Z"
          }
        },
        "custom.washerSoilLevel": {
          "washerSoilLevel": {
            "value": "normal",
            "timestamp": "2026-05-02T00:47:44.796Z"
          }
        },
        "custom.washerSpinLevel": {
          "washerSpinLevel": {
            "value": "high",
            "timestamp": "2026-05-02T00:47:44.797Z"
          }
        },
        "custom.washerRinseCycles": {
          "washerRinseCycles": {
            "value": "2",
            "timestamp": "2026-05-02T00:20:46.569Z"
          }
        },
        "switch": {
          "switch": {
            "value": "off",
            "timestamp": "2026-05-02T00:47:44.704Z"
          }
        }
      }
    }
  }
```
The same applies for the dryer:

```bash
curl -sS -H "Authorization: Bearer ${SMARTTHINGS_TOKEN}" "https://api.smartthings.com/v1/devices/${DRYER_DEVICE_ID}/status"
```
This provided another very useful JSON response which I could refer to:
```bash
{
    "components": {
      "hca.main": {
        "hca.dryerMode": {
          "mode": {
            "value": "quickDry",
            "timestamp": "2026-05-02T01:27:32.275Z"
          },
          "supportedModes": {
            "value": [
              "normal",
              "timeDry",
              "quickDry"
            ],
            "timestamp": "2026-05-02T01:27:18.486Z"
          }
        }
      },
      "main": {
        "custom.dryerWrinklePrevent": {
          "operatingState": {
            "value": "ready",
            "timestamp": "2026-05-01T18:38:12.300Z"
          },
          "dryerWrinklePrevent": {
            "value": "off",
            "timestamp": "2026-05-01T18:38:12.300Z"
          }
        },
        "samsungce.dryerDryingTemperature": {
          "dryingTemperature": {
            "value": "high",
            "timestamp": "2026-05-02T01:27:32.170Z"
          },
          "supportedDryingTemperature": {
            "value": [
              "none",
              "extraLow",
              "low",
              "mediumLow",
              "medium",
              "high"
            ],
            "timestamp": "2026-05-01T18:38:12.300Z"
          }
        },
        "switch": {
          "switch": {
            "value": "on",
            "timestamp": "2026-05-02T01:27:29.104Z"
          }
        },
        "custom.dryerDryLevel": {
          "dryerDryLevel": {
            "value": "none",
            "timestamp": "2026-05-02T01:27:32.171Z"
          },
          "supportedDryerDryLevel": {
            "value": [
              "none",
              "damp",
              "less",
              "normal",
              "more",
              "very"
            ],
            "timestamp": "2026-04-05T04:32:06.481Z"
          }
        },
        "samsungce.dryerCycle": {
          "dryerCycle": {
            "value": "Table_03_Course_44",
            "timestamp": "2026-05-02T01:27:32.275Z"
          }
        },
        "dryerOperatingState": {
          "completionTime": {
            "value": "2026-05-02T02:07:37Z",
            "timestamp": "2026-05-02T01:27:37.860Z"
          },
          "machineState": {
            "value": "run",
            "timestamp": "2026-05-02T01:27:49.022Z"
          },
          "dryerJobState": {
            "value": "drying",
            "timestamp": "2026-05-02T01:27:49.022Z"
          }
        },
        "samsungce.dryerOperatingState": {
          "operatingState": {
            "value": "running",
            "timestamp": "2026-05-02T01:27:49.022Z"
          },
          "progress": {
            "value": 77,
            "unit": "%",
            "timestamp": "2026-05-02T01:58:22.174Z"
          },
          "remainingTime": {
            "value": 10,
            "unit": "min",
            "timestamp": "2026-05-02T01:57:51.058Z"
          },
          "dryerJobState": {
            "value": "drying",
            "timestamp": "2026-05-02T01:27:49.022Z"
          }
        }
      }
    }
  }
```
This was a huge part of the learning process for me, because in my design proposal, I initally thought I would be able to poll the live energy for both machines, but this didn't work. Each Samsung machine works differently, and my household appliances are no exception. Despite the inital fallback, I was able to work around this and still get useful energy insights. For example, if the Temperature Level or number of rinses is too high, then that indicates a high energy use. It's not quite the same as getting a specific wattage number, but it's still insightful nonetheless. Overall, had it not been me using a combination of the SmartThingsAPI documentation and curling into my washer and dryer, this project would have had been much more difficult to implement.