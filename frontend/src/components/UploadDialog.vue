<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <form class="modal modal-visible" @submit.prevent="submit">
      <div class="modal-card">
        <div class="modal-head">
          <div><p class="eyebrow">VERSION PACKAGE</p><h2>上传 Redis 版本包</h2></div>
          <button class="close-button" type="button" aria-label="关闭" @click="$emit('close')">×</button>
        </div>
        <div class="field-grid two-columns">
          <label class="field"><span>Redis 版本 <em>*</em></span><input v-model.trim="version" placeholder="例如 7.2.5" required></label>
          <label class="field"><span>上传人 <em>*</em></span><input v-model.trim="uploadedBy" required></label>
        </div>
        <label class="file-drop">
          <input type="file" accept=".tar.gz,application/gzip" required @change="chooseFile">
          <span class="file-drop-icon">↑</span><strong>选择 tar.gz 安装包</strong><small>{{ file ? file.name : "文件最大 500 MB" }}</small>
        </label>
        <div class="modal-actions">
          <button class="button button-ghost" type="button" @click="$emit('close')">取消</button>
          <button class="button button-primary" type="submit" :disabled="submitting">{{ submitting ? "上传中…" : "上传并校验" }}</button>
        </div>
      </div>
    </form>
  </div>
</template>

<script>
export default {
  name: "UploadDialog",
  props: {
    submitting: { type: Boolean, default: false }
  },
  data() {
    return { version: "", uploadedBy: "admin", file: null };
  },
  methods: {
    /** 保存用户从文件选择器中选中的版本包。 */
    chooseFile(event) {
      this.file = event.target.files[0] || null;
    },
    /** 把上传字段交给父页面调用接口。 */
    submit() {
      if (!this.file) return;
      this.$emit("submit", { version: this.version, uploadedBy: this.uploadedBy, file: this.file });
    }
  }
};
</script>
