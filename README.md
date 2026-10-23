# MonitorX / InspectorX

MonitorX is an Android diagnostics toolkit. InspectorX is its platform-independent business-flow
rule engine. This directory is a self-contained Gradle project and can be moved to a separate Git
repository without Gemgala sources or build logic.

## Modules

| Artifact | Responsibility | Android dependency |
|---|---|---|
| `monitor-core` | InspectorX events, rules, sessions and JSON rule packs | No |
| `monitor-source-okhttp` | Convert HTTP snapshots into InspectorX events | No |
| `monitor-okhttp` | Capture, Room storage, mock/weak-network and anomaly bridge | Yes |
| `monitor-runtime-android` | Page lifecycle, foreground Activity and performance collectors | Yes |
| `monitor-tools` | PC console, API debugger, IM inspector and local service | Yes |
| `monitor-ui` | Optional activities, fragments, adapters and resources | Yes |
| `monitorx-gradle-plugin` | OkHttp builder bytecode instrumentation | Build-time only |

```text
monitor-ui -> monitor-tools -> monitor-runtime-android
           -> monitor-okhttp -> monitor-source-okhttp -> monitor-core
```

## Build independently

```bash
cd monitorX
./gradlew test assemble
./gradlew publishToMavenLocal
./gradlew -p gradle-plugin build publishToMavenLocal
```

Override the version with `-Pmonitorx.version=1.0.0`. Plugin remote publishing accepts
`MONITORX_REPO_URL`, `MONITORX_REPO_USERNAME` and `MONITORX_REPO_PASSWORD`; credentials are never
stored in source control.

For a local AAR integration test, publish the complete dependency graph to Maven Local and let the
host consume the generated AARs through their Maven metadata:

```bash
./gradlew :monitorX:core:publishToMavenLocal \
  :monitorX:source-okhttp:publishToMavenLocal \
  :monitorX:okhttp:publishToMavenLocal \
  :monitorX:runtime-android:publishToMavenLocal \
  :monitorX:tools:publishToMavenLocal \
  :monitorX:ui:publishToMavenLocal
./gradlew :app:compileDemoGoogleDebugKotlin -Pmonitorx.usePublished=true
```

Do not commit AAR files to Git history. For shared builds, publish the same Maven artifacts to
GitHub Packages with `MONITORX_REPO_URL=https://maven.pkg.github.com/OWNER/REPOSITORY`, the GitHub
user as `MONITORX_REPO_USERNAME`, and a token with package write access as
`MONITORX_REPO_PASSWORD`. GitHub Releases is suitable only when an AAR is provided as a manual
download; Maven metadata is preferred because MonitorX contains multiple transitive artifacts.

## Consumer integration

```kotlin
plugins { id("com.hkjustar.monitorx") version "1.0.0" }

dependencies {
    debugImplementation("com.hkjustar.monitorx:monitor-ui:1.0.0")
}

MonitorX {
    enabledVariants = arrayOf(".*Debug")
    disabledVariants = arrayOf(".*Release")
}
```

Choose a smaller artifact when UI or tooling is unnecessary. AndroidX Startup initializes only
the layers present in the application.

## InspectorX API

InspectorX can run in a JVM service or test without Android:

```kotlin
InspectorX.installRulePack(json)
InspectorX.emit("LOGIN_COMPLETED")
val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
InspectorX.resetSession(userId)
```

Use `InspectorX.addReporter` to forward violations to logs, analytics, CI or a custom UI. Android
hosts can use `InspectorXAndroid` to load rule assets explicitly or validate configuration.

## Portable rules

Put rule packs under `src/main/assets/monitorx/*.json`. Assets from applications and feature AARs
are merged, discovered in stable filename order and installed automatically. Copying the JSON file
to another package migrates the rule without business-code registration.

Supported types are `POINT`, `DEPENDENCY`, `FORBIDDEN_BEFORE`, `RATE` and `FUNNEL`. Matchers support
event type/name, name regex, HTTP path regex and exact attributes. Point conditions support blank,
equality, prefix, regex and set membership with `ALL`/`ANY` composition. The schema is strict:
unknown fields, unsupported versions, duplicate rule IDs and missing required fields fail during
installation.

See [`samples/rules/checkout-rules.json`](samples/rules/checkout-rules.json).

For host-app rule placement, new-user context, payment notification monitoring and history UI,
see [`BUSINESS_MONITORING.md`](BUSINESS_MONITORING.md).

## Extraction checklist

1. Move the entire `monitorX` directory into the new repository root.
2. Keep the wrapper, root Gradle files, `gradle/` and `gradle-plugin/`.
3. Run `./gradlew test assemble`.
4. Configure Maven credentials outside source control.
5. Publish libraries and the Gradle plugin.
6. Replace Gemgala project dependencies with Maven coordinates.

Packages under `com.hkjustar.monitor.okhttp` remain temporarily stable for binary compatibility.
New public APIs use `com.hkjustar.monitorx`.
