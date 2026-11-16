# Swagger UI

`swagger-ui-bundle.js` and `swagger-ui.css` show the API page at `https://mts.mahjongmadrid.com/api-docs/`
(see [docs/arquitectura/api.md](../../docs/arquitectura/api.md)).

- Source: npm package `swagger-ui-dist`, version `5.34.1` (`npm pack swagger-ui-dist@5.34.1`)
- License: Apache 2.0 (see `LICENSE` and `NOTICE`)
- The files are used without changes. They live in the repository because the site's Content-Security-Policy
  allows scripts from the site only, and a deploy must not depend on a download.
