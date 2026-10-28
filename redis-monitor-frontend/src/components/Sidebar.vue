<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-icon">R</span>
      Redis监控平台
    </div>
    <div class="menu-group">
      <router-link to="/dashboard" class="menu-head" active-class="active">
        <span>▦</span>监控仪表
      </router-link>
    </div>
    <div class="menu-group">
      <div class="menu-head"><span>▦</span>监控管理</div>
      <router-link to="/realtime" class="menu-item" active-class="active">实时监控</router-link>
      <router-link to="/history" class="menu-item" active-class="active">历史监控</router-link>
    </div>
    <div class="menu-group">
      <div class="menu-head"><span>⚠</span>告警管理</div>
      <router-link to="/alarm-rules" class="menu-item" active-class="active">告警监控</router-link>
      <router-link to="/alarm-records" class="menu-item" active-class="active">告警记录</router-link>
    </div>
  </aside>
</template>

<script>
export default { name: 'Sidebar' }
</script>
