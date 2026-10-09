const path = require("path");

module.exports = {
  publicPath: "/",
  outputDir: path.resolve(__dirname, "../src/main/resources/static"),
  assetsDir: "assets",
  filenameHashing: true,
  productionSourceMap: false,
  devServer: {
    port: 8080,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8100",
        changeOrigin: true
      }
    }
  }
};
