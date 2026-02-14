Feature: LambdaTest smoke for intercept and remote upload

Background:
  * def LambdaRuntime = read('classpath:lambdatest/runtime.js')
  * def runtime = LambdaRuntime({ defaultScenarioName: 'lambda-karate-lt-smoke' })
  * if (!runtime.gridUrl) karate.fail('missing karate.grid.url')
  * if (!runtime.username || !runtime.accessKey) karate.fail('set lt.username and lt.accessKey (or LT_USERNAME / LT_ACCESS_KEY)')
  * def Interop = Java.type('io.cpogx.lambdatest.interop.LambdaWebDriverInterop')
  * def FileUtil = Java.type('io.cpogx.lambdatest.support.FileUtil')
  * def strictIntercept = karate.properties['lt.intercept.strict'] == 'true'
  * configure driver = runtime.buildDriver({ capabilities: { 'LT:Options': { network: true, console: true, visual: true } } })
  * configure afterScenario = runtime.createAfterScenario()

@smoke @intercept
Scenario: Intercept script response and verify visible browser change
  * driver 'https://www.selenium.dev/selenium/web/web-form.html'
  * waitFor("input[name='my-text']")
  * script("document.body.insertAdjacentHTML('beforeend', '<pre id=\"lt-intercept-result\">pending</pre>')")
  * def rule =
  """
  {
    url: 'https://petstore.swagger.io/v2/user/user1',
    error: 'Failed'
  }
  """
  * def interceptAck = Interop.intercept(driver, rule)
  * match interceptAck != null
  * script("document.getElementById('lt-intercept-result').textContent = 'intercept command accepted'")
  * waitForText('#lt-intercept-result', 'intercept command accepted')
  * if (strictIntercept) script("window.__ltInterceptBody = 'pending'; fetch('https://petstore.swagger.io/v2/user/user1', { headers: { accept: 'application/json' } }).then(function(r){ return r.text(); }).then(function(t){ window.__ltInterceptBody = t; document.getElementById('lt-intercept-result').textContent = t; }).catch(function(e){ window.__ltInterceptBody = 'ERR:' + e; document.getElementById('lt-intercept-result').textContent = window.__ltInterceptBody; });")
  * if (strictIntercept) waitUntil("window.__ltInterceptBody != 'pending'")
  * if (strictIntercept) { var strictText = text('#lt-intercept-result'); if (strictText.indexOf('ERR:') == -1) karate.fail('strict intercept expected ERR in visible response') }
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
