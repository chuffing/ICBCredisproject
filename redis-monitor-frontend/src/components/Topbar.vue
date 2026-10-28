<template>
  <header class="topbar">
    <span class="crumb">首页 / <b>{{ title }}</b></span>
    <div class="topbar-right">
      <span class="user-info">admin</span>
    </div>
  </header>
</template>

<script>
export default {
  name: 'Topbar',
  props: { title: { type: String, default: '' } }
}
</script>
