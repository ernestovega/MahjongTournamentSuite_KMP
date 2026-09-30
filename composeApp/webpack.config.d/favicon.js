const faviconSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="14" fill="#02B16B"/>
  <rect x="13" y="9" width="38" height="46" rx="5" fill="#FFFDF5"/>
  <path d="M22 44c2-10 5-18 10-24 5 6 8 14 10 24" fill="none" stroke="#02B16B" stroke-width="4" stroke-linecap="round"/>
  <path d="M25 25h14" stroke="#02B16B" stroke-width="4" stroke-linecap="round"/>
</svg>`;

config.plugins = config.plugins || [];
config.plugins.push({
  apply(compiler) {
    compiler.hooks.thisCompilation.tap("CopyFaviconPlugin", (compilation) => {
      compilation.emitAsset(
        "favicon.svg",
        new compiler.webpack.sources.RawSource(faviconSvg),
      );
    });
  },
});
