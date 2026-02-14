Feature: LambdaTest smoke for intercept and remote upload

Background:
  * def LambdaRuntime = read('classpath:lambdatest/runtime.js')
  * def runtime = LambdaRuntime({ defaultScenarioName: 'lambda-karate-lt-smoke' })
  * if (!runtime.gridUrl) karate.fail('missing karate.grid.url')
  * if (!runtime.username || !runtime.accessKey) karate.fail('set lt.username and lt.accessKey (or LT_USERNAME / LT_ACCESS_KEY)')
  * def Interop = Java.type('io.cpogx.lambdatest.interop.LambdaWebDriverInterop')
  * def FileUtil = Java.type('io.cpogx.lambdatest.support.FileUtil')
  * configure driver = runtime.buildDriver({ capabilities: { 'LT:Options': { network: true, console: true, visual: true } } })
  * configure afterScenario = runtime.createAfterScenario()

@smoke @intercept
Scenario: Intercept pre-existing script request and verify built-in page behavior changes
  * driver 'about:blank'
  * def rule =
  """
  {
    url: 'https://www.selenium.dev/selenium/web/devtools_request_interception_test/one.js',
    redirectUrl: 'https://www.selenium.dev/selenium/web/devtools_request_interception_test/two.js'
  }
  """
  * def interceptAck = Interop.intercept(driver, rule)
  * match interceptAck != null
  * driver 'https://www.selenium.dev/selenium/web/devToolsRequestInterceptionTest.html'
  * waitFor('button')
  * screenshot()
  * click('button')
  * waitForText('#result', 'two')
  * match text('#result') == 'two'
  * screenshot()

@smoke @upload
Scenario: Upload local file on remote LambdaTest session
  * def content = 'lambda smoke upload content'
  * def uploadPath = FileUtil.writeString('build/lambda-upload/input.txt', content)
  * driver 'https://www.selenium.dev/selenium/web/web-form.html'
  * waitFor("input[name='my-file']")
  * Interop.inputFile(driver, "input[name='my-file']", uploadPath)
  * screenshot()
  * click("button[type='submit']")
  * waitForText('#message', 'Received!')
  * def submittedUrl = script('window.location.href')
  * match submittedUrl contains 'my-file=input.txt'
  * screenshot()
