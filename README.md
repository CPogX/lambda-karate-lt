# lambda-karate-lt

Lean LambdaTest-only Karate project.

Includes:
- prebuilt LambdaTest runtime config (`classpath:lambdatest/runtime.js`)
- prebuilt remote WebDriver helper (`LambdaWebDriverInterop`) for:
  - `lt:intercept:response`, `lt:intercept:redirect`, `lt:intercept:error`
  - remote file upload via `/se/file`
  - Lambda uploaded-file path helper (`lambda:userFiles`)
  - CDP command execution via `POST /goog/cdp/execute`
- smoke feature with visible assertions for intercept + upload
- plain JUnit runner and `@SpringBootTest` runner

## 1) Minimal Setup

Requirements:
- Java 17
- internet access to LambdaTest
- LambdaTest credentials

Set credentials and run smoke:

```powershell
$env:LT_USERNAME='your-user'
$env:LT_ACCESS_KEY='your-key'
./gradlew test --tests "io.cpogx.lambdatest.LambdaSmokeTest"
```

Report:
- `build/karate-reports/lambdatest-smoke/karate-summary.html`

## 2) Spring Boot mode (existing `application.yml`)

Put values in your existing yaml (no separate sauce/lambda yaml required):

```yaml
cpogx:
  browser:
    name: "Chrome"
    version: "latest"
    platform-name: "win11"
  lambdatest:
    grid-url: "https://hub.lambdatest.com/wd/hub"
    username: "${LT_USERNAME:}"
    access-key: "${LT_ACCESS_KEY:}"
    build: "my-build"
    tags: "smoke,lambda"
    tunnel:
      name: "${LT_TUNNEL_NAME:}"
```

Run:

```powershell
./gradlew test --tests "io.cpogx.lambdatest.LambdaSpringBootSmokeTest"
```

## 3) Run a specific tag

```powershell
./gradlew test --tests "io.cpogx.lambdatest.LambdaSmokeTest" -Dkarate.tags="@upload"
```

Tag expressions are supported (`@smoke and not @intercept`).

## 4) Intercept rule format

Use from Karate:

```karate
* def Interop = Java.type('io.cpogx.lambdatest.interop.LambdaWebDriverInterop')
```

Response override:

```karate
* def rule =
"""
{
  url: 'https://host/path.js',
  method: 'GET',
  response: {
    status: 200,
    headers: { 'Content-Type': 'application/javascript' },
    body: 'console.log("mocked")'
  }
}
"""
* Interop.intercept(driver, rule)
```

Redirect:

```karate
* Interop.intercept(driver, { url: 'https://old', redirectUrl: 'https://new' })
```

Force network error:

```karate
* Interop.intercept(driver, { url: 'https://api/down', errorCode: 'Failed' })
```

## 5) File upload patterns

Standard remote upload (`/se/file`):

```karate
* def FileUtil = Java.type('io.cpogx.lambdatest.support.FileUtil')
* def path = FileUtil.writeString('build/upload/demo.txt', 'hello')
* Interop.inputFile(driver, "input[type='file']", path)
```

Lambda pre-uploaded files (`lambda:userFiles`):

```karate
* def caps = { 'LT:Options': { user: '#(runtime.username)', accessKey: '#(runtime.accessKey)' } }
* def caps = Interop.withLambdaUserFiles(caps, ['demo.txt'])
* configure driver = runtime.buildDriver({ capabilities: caps })
* Interop.inputLambdaUploadedFile(driver, "input[type='file']", 'demo.txt')
```

## 6) CDP on LambdaTest

Use:
- HTTP endpoint: `/goog/cdp/execute`
- helper: `LambdaWebDriverInterop.cdpExecute(driver, cmd, params)`

Example:

```karate
* def result = Interop.cdpExecute(driver, 'Target.getTargets', {})
* match result.targetInfos != null
```

## 7) Tunnel and proxy notes

Tunnel:
- set `cpogx.lambdatest.tunnel.name` (or `-Dlt.tunnel.name=...`)
- runtime sets `LT:Options.tunnel=true` + `LT:Options.tunnelName`

Proxy:
- for Java/Gradle websocket/cdp issues in corporate networks, pass JVM proxy flags (`http.proxyHost`, `http.proxyPort`, `https.proxyHost`, `https.proxyPort`) in `JAVA_TOOL_OPTIONS`.
