<template>
  <div v-if="packages.length" class="data-table-wrap">
    <table class="data-table">
      <thead><tr><th>Redis 版本</th><th>安装包</th><th>文件大小</th><th>上传时间</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="item in packages" :key="item.id">
          <td><strong>Redis {{ item.version }}</strong></td>
          <td><span class="file-name">{{ item.packageName }}</span></td>
          <td>{{ formatBytes(item.packageSize) }}</td>
          <td>{{ formatDateTime(item.createTime) }}</td>
          <td><span class="badge" :class="item.verifyStatus === 'PASSED' ? 'badge-success' : 'badge-danger'">{{ item.verifyStatus === "PASSED" ? "可用" : "不可用" }}</span></td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-else class="empty-state">还没有上传 Redis 版本包。</div>
</template>

<script>
export default {
  name: "VersionPackageTable",
  props: {
    packages: { type: Array, required: true }
  },
  methods: {
    /** 将字节数格式化为易读容量。 */
    formatBytes(bytes) {
      if (bytes === null || bytes === undefined) return "-";
      const units = ["B", "KB", "MB", "GB"];
      let value = Number(bytes);
      let unitIndex = 0;
      while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex += 1;
      }
      return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
    },
    /** 格式化后端 LocalDateTime。 */
    formatDateTime(value) {
      return value ? String(value).replace("T", " ").slice(0, 16) : "-";
    }
  }
};
</script>
