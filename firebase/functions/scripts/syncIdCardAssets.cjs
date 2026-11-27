const { cpSync, mkdirSync } = require("node:fs");
const { dirname, resolve } = require("node:path");

// The PDFs of the backend use the font, the icons and the logos of the app, so they come from the app's own resources.
const root = resolve(__dirname, "../../..");
const drawables = resolve(root, "ui/src/commonMain/composeResources/drawable");
const assets = [
  [resolve(root, "designsystem/src/commonMain/composeResources/font/go3v2.ttf"), "go3v2.ttf"],
  [resolve(drawables, "logo_ema.png"), "logo_ema.png"],
  [resolve(drawables, "icon_chicken.png"), "icon_chicken.png"],
  [resolve(drawables, "icon_wind_east.png"), "icon_wind_east.png"],
  [resolve(drawables, "icon_wind_south.png"), "icon_wind_south.png"],
  [resolve(drawables, "icon_wind_west.png"), "icon_wind_west.png"],
  [resolve(drawables, "icon_wind_north.png"), "icon_wind_north.png"],
];

for (const [source, name] of assets) {
  const target = resolve(__dirname, "../lib/assets", name);
  mkdirSync(dirname(target), { recursive: true });
  cpSync(source, target);
}
