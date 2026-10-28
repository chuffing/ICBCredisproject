<template>
  <div class="app-layout">
    <Sidebar />
    <div class="main-area">
      <Topbar :title="$route.meta.title" />
      <div class="page-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script>
import Sidebar from '@/components/Sidebar.vue'
import Topbar from '@/components/Topbar.vue'

export default {
  name: 'App',
  components: { Sidebar, Topbar }
}
</script>
