# BetterLaundry

CS-UY 3913 Applied Java Semester Project

Sufyan Waryah, Spring 2026

BetterLaundry is an accessible web application that any device with internet access can use at any time to see real-time laundry cycle status, progress, and AI-generated feedback tailored to the behavior of my family's two Samsung appliances, without requiring any app installation. The overall objective is to create a personalized, enhanced laundry experience for my family.

---
## Cloud Deployment

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
## Testing 

> [!NOTE]
> Ensure you've exported all of environment variables through your terminal to ensure that the app will function properly. 
> Without setting the environment variables, the app won't work (why else would you be using this app if you don't own the specific washer and dryer??)

1. Run tests

```bash
mvn clean test
```
2. If you ran the command above, then `target/` will have had been created, so you can run the generated JAR file:

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
