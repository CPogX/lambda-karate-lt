function fn() {
  return {
    env: karate.env,
    baseUrl: karate.properties['base.url'] || 'https://www.selenium.dev'
  };
}
