module.exports = {
  // 关闭 Terser 多进程压缩：小型管理台单进程压缩耗时差异很小，
  // 同时避免在受限构建环境/容器中 fork worker 失败（spawn EPERM）。
  parallel: false,
  devServer: {
    port: 8080,
    proxy: {
      '/monitor': {
        target: 'http://localhost:8100',
        changeOrigin: true
      },
      '/alarm': {
        target: 'http://localhost:8100',
        changeOrigin: true
      },
      '/metric': {
        target: 'http://localhost:8100',
        changeOrigin: true
      },
      // 操作审计接口（AuditController @RequestMapping("/audit")）必须单独代理，
      // 否则仪表盘请求 /audit/logs 会被 devServer 直接 404，平台端收不到请求
      '/audit': {
        target: 'http://localhost:8100',
        changeOrigin: true
      },
      '/api': {
        target: 'http://localhost:8100',
        changeOrigin: true
      }
    }
  }
}
