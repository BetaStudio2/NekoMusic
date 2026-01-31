<template>
  <div class="settings-view">
    <div class="settings-container">
      <h2>设置</h2>
      <div class="settings-section">
        <h3>关于</h3>
        <p>NekoMusic PC 版本</p>
        <p>版本号: 1.0.0</p>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.settings-view {
  height: 100%;
  padding: 20px;
}

.settings-container {
  max-width: 800px;
  margin: 0 auto;
}

.settings-container h2 {
  margin-bottom: 20px;
  color: #333;
}

.settings-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.settings-section h3 {
  margin-bottom: 10px;
  color: #666;
}

.settings-section p {
  color: #999;
  margin: 5px 0;
}
</style>