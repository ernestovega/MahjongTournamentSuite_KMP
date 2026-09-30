const fs = require("fs");
const path = require("path");

const appLogoPath = path.resolve(
  __dirname,
  "../../../../composeApp",
  "src/commonMain/composeResources/drawable/app_logo.png",
);
const appLogo = fs.readFileSync(appLogoPath);

config.plugins = config.plugins || [];
config.plugins.push({
  apply(compiler) {
    compiler.hooks.thisCompilation.tap("CopyFaviconPlugin", (compilation) => {
      compilation.emitAsset(
        "favicon.png",
        new compiler.webpack.sources.RawSource(appLogo),
      );
    });
  },
});
