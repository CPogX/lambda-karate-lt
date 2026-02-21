function fn(config) {
  var cfg = config || {};
  var props = karate.properties || {};

  function prop(name, fallback) {
    var value = props[name];
    if (value === null || value === undefined) {
      return fallback;
    }
    var text = ('' + value).trim();
    return text.length === 0 ? fallback : text;
  }

  function boolValue(value, fallback) {
    if (value === null || value === undefined) {
      return fallback;
    }
    if (typeof value === 'boolean') {
      return value;
    }
    var text = ('' + value).trim().toLowerCase();
    if (text === 'true' || text === '1' || text === 'yes') {
      return true;
    }
    if (text === 'false' || text === '0' || text === 'no') {
      return false;
    }
    return fallback;
  }

  function csv(value) {
    if (!value) {
      return [];
    }
    var parts = ('' + value).split(',');
    var out = [];
    for (var i = 0; i < parts.length; i++) {
      var item = parts[i].trim();
      if (item.length > 0 && out.indexOf(item) === -1) {
        out.push(item);
      }
    }
    return out;
  }

  function normalizePlatform(platformName) {
    if (!platformName) {
      return platformName;
    }
    var normalized = ('' + platformName).trim().toLowerCase();
    if (normalized === 'windows 11' || normalized === 'win11' || normalized === 'win 11') {
      return 'win11';
    }
    if (normalized === 'windows 10' || normalized === 'win10' || normalized === 'win 10') {
      return 'win10';
    }
    if (normalized === 'macos sequoia') {
      return 'macOS Sequoia';
    }
    return platformName;
  }

  var scenarioName = (karate.info && karate.info.scenarioName)
    ? karate.info.scenarioName
    : (cfg.defaultScenarioName || 'karate-lambdatest-scenario');

  var runtime = {
    gridUrl: cfg.gridUrl || prop('karate.grid.url', 'https://hub.lambdatest.com/wd/hub'),
    driverType: cfg.driverType || prop('karate.driver.type', 'chromedriver'),
    browserName: cfg.browserName || prop('karate.browser.name', 'Chrome'),
    browserVersion: cfg.browserVersion || prop('karate.browser.version', 'latest'),
    platformName: normalizePlatform(cfg.platformName || prop('karate.platform.name', 'win11')),
    headless: cfg.headless !== undefined ? boolValue(cfg.headless, false) : boolValue(prop('karate.browser.headless', ''), false),
    timeout: cfg.timeout || 30000,
    build: cfg.build || prop('lt.build', 'lambda-karate-lt'),
    name: cfg.name || prop('lt.name', scenarioName),
    project: cfg.project || prop('lt.project', 'lambda-karate-lt'),
    username: cfg.username || prop('lt.username', ''),
    accessKey: cfg.accessKey || prop('lt.accessKey', ''),
    tunnelName: cfg.tunnelName || prop('lt.tunnel.name', ''),
    tags: csv(prop('lt.tags', '')),
    userFiles: csv(prop('lt.user.files', ''))
  };

  runtime.buildWebDriverSession = function (overrides) {
    var o = overrides || {};
    var alwaysMatch = o.alwaysMatch || {};
    if (!alwaysMatch.browserName) {
      alwaysMatch.browserName = runtime.browserName;
    }
    if (!alwaysMatch.browserVersion && runtime.browserVersion) {
      alwaysMatch.browserVersion = runtime.browserVersion;
    }
    if (!alwaysMatch.platformName && runtime.platformName) {
      alwaysMatch.platformName = runtime.platformName;
    }

    var ltOptions = alwaysMatch['LT:Options'] || {};

    if (!ltOptions.user && runtime.username) {
      ltOptions.user = runtime.username;
    }
    if (!ltOptions.accessKey && runtime.accessKey) {
      ltOptions.accessKey = runtime.accessKey;
    }
    if (!ltOptions.project && runtime.project) {
      ltOptions.project = runtime.project;
    }
    if (!ltOptions.build && runtime.build) {
      ltOptions.build = runtime.build;
    }
    if (!ltOptions.name && runtime.name) {
      ltOptions.name = runtime.name;
    }
    if (!ltOptions.tags && runtime.tags.length) {
      ltOptions.tags = runtime.tags;
    }
    if (runtime.tunnelName) {
      if (ltOptions.tunnel === undefined) {
        ltOptions.tunnel = true;
      }
      if (!ltOptions.tunnelName) {
        ltOptions.tunnelName = runtime.tunnelName;
      }
    }
    if (runtime.userFiles.length && !ltOptions['lambda:userFiles']) {
      ltOptions['lambda:userFiles'] = runtime.userFiles;
    }

    alwaysMatch['LT:Options'] = ltOptions;
    if (alwaysMatch.webSocketUrl === undefined) {
      alwaysMatch.webSocketUrl = true;
    }

    return { capabilities: { alwaysMatch: alwaysMatch } };
  };

  runtime.buildDriver = function (overrides) {
    var o = overrides || {};
    var webDriverSession = runtime.buildWebDriverSession({
      alwaysMatch: o.alwaysMatch || o.capabilities || {}
    });

    return {
      type: o.type || runtime.driverType,
      start: o.start === undefined ? false : o.start,
      webDriverUrl: o.webDriverUrl || o.gridUrl || runtime.gridUrl,
      webDriverSession: o.webDriverSession || webDriverSession,
      timeout: o.timeout || runtime.timeout
    };
  };

  runtime.createAfterScenario = function () {
    return function () {
      var Interop = Java.type('io.cpogx.lambdatest.interop.LambdaWebDriverInterop');
      var status = 'passed';
      var sessionId = null;
      try {
        status = Interop.lambdaStatusForError(karate.info ? karate.info.errorMessage : null);
      } catch (statusError) {
        karate.log('lambda status resolve failed', statusError + '');
      }

      try {
        if (typeof driver !== 'undefined' && driver) {
          sessionId = Interop.sessionId(driver);
        }
      } catch (sessionError) {
        karate.log('lambda session id read failed', sessionError + '');
      }

      try {
        if (typeof driver !== 'undefined' && driver) {
          Interop.lambdaStatus(driver, status);
        }
      } catch (e1) {
        karate.log('lambda status update failed', e1 + '');
      }

      try {
        if (typeof driver !== 'undefined' && driver) {
          driver.quit();
        }
      } catch (e2) {
        karate.log('driver quit failed', e2 + '');
      }

      try {
        if (sessionId) {
          var videoBytes = Interop.downloadSessionVideo(sessionId, runtime.username, runtime.accessKey);
          if (videoBytes) {
            var videoName = 'lambdatest-video-' + sessionId + '.mp4';
            karate.write(videoBytes, 'lambdatest-videos/' + videoName);
            karate.embed(videoBytes, 'video/mp4');
          } else {
            karate.log('lambda video unavailable for scenario', karate.info ? karate.info.scenarioName : '');
          }
        } else {
          karate.log('lambda video unavailable because session id was not resolved');
        }
      } catch (videoError) {
        karate.log('lambda video download failed', videoError + '');
      }
    };
  };

  return runtime;
}
