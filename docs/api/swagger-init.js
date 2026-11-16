// Starts Swagger UI on openapi.json. Its own file because the site's Content-Security-Policy forbids inline scripts.
window.ui = SwaggerUIBundle({
  url: "/api-docs/openapi.json",
  dom_id: "#swagger-ui",
  deepLinking: true,
  docExpansion: "list",
  defaultModelsExpandDepth: 0,
  displayRequestDuration: true,
  filter: true,
  persistAuthorization: false,
  // "Try it out" only for reads: changes are made from the apps, never by hand against production.
  supportedSubmitMethods: ["get"],
  // The page lives on the same site as the API, so the first server is the right one in a browser.
  validatorUrl: null,
});
