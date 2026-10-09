<template>
  <div class="page-stack">
    <section class="panel">
      <div class="section-heading">
        <div><h2>Redis 版本包</h2><p>平台保存并校验过的安装包，升级时由平台发送给三台执行器。</p></div>
        <button class="button button-primary button-small" type="button" @click="showUpload = true">＋ 上传版本包</button>
      </div>
      <VersionPackageTable :packages="store.packages" />
    </section>

    <section class="panel">
      <div class="section-heading"><div><h2>升级配置</h2><p>版本升级作用于当前正式纳管的三节点 Redis + Sentinel 环境。</p></div></div>
      <form @submit.prevent="submit">
        <label class="field">
          <span>目标 Redis 版本 <em>*</em></span>
          <select v-model.number="versionPackageId" required>
            <option :value="null" disabled>请选择目标版本</option>
            <option v-for="item in availablePackages" :key="item.id" :value="item.id">Redis {{ item.version }} · {{ item.packageName }}</option>
          </select>
        </label>
        <div class="form-divider"></div>
        <div class="section-heading compact"><div><h2>当前升级环境</h2><p>主从角色保持不变，三个节点将统一升级。</p></div></div>
        <EnvironmentPanel />
        <div class="form-divider"></div>
        <label class="field"><span>操作原因 <em>*</em></span><textarea v-model.trim="operationReason" placeholder="请输入本次版本升级原因" required></textarea></label>
        <div class="form-actions"><button class="button button-primary" type="submit" :disabled="submitting || nodes.length !== 3">{{ submitting ? "正在创建…" : "一键升级" }}</button></div>
      </form>
    </section>

    <TaskProgress expected-type="VERSION_UPGRADE" />
    <UploadDialog v-if="showUpload" :submitting="uploading" @close="showUpload = false" @submit="upload" />
  </div>
</template>

<script>
import EnvironmentPanel from "../components/EnvironmentPanel.vue";
import TaskProgress from "../components/TaskProgress.vue";
import UploadDialog from "../components/UploadDialog.vue";
import VersionPackageTable from "../components/VersionPackageTable.vue";
import * as api from "../services/api";
import { state, currentNodes, buildTopologyPayload, acceptCreatedTask, refreshPackages, notify } from "../store/appStore";

export default {
  name: "VersionUpgradeView",
  components: { EnvironmentPanel, TaskProgress, UploadDialog, VersionPackageTable },
  data() {
    return {
      store: state,
      versionPackageId: null,
      operationReason: "",
      submitting: false,
      uploading: false,
      showUpload: false
    };
  },
  computed: {
    /** 当前升级涉及的三台执行器。 */
    nodes() {
      return currentNodes();
    },
    /** 只允许选择平台校验通过的版本包。 */
    availablePackages() {
      return this.store.packages.filter(item => item.verifyStatus === "PASSED");
    }
  },
  methods: {
    /** 创建版本升级任务。 */
    async submit() {
      this.submitting = true;
      try {
        const payload = buildTopologyPayload(this.operationReason);
        payload.versionPackageId = this.versionPackageId;
        const detail = await api.createVersionUpgrade(payload);
        acceptCreatedTask(detail);
      } catch (error) {
        notify(error.message, "error");
      } finally {
        this.submitting = false;
      }
    },
    /** 上传版本包并刷新表格。 */
    async upload(fields) {
      this.uploading = true;
      try {
        const formData = new FormData();
        formData.append("version", fields.version);
        formData.append("uploadedBy", fields.uploadedBy);
        formData.append("file", fields.file);
        const result = await api.uploadVersionPackage(formData);
        notify(result.reusedExisting ? "相同版本包已经存在" : "版本包上传并校验成功", "success");
        await refreshPackages();
        this.showUpload = false;
      } catch (error) {
        notify(error.message, "error");
      } finally {
        this.uploading = false;
      }
    }
  }
};
</script>
