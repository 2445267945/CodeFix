<!-- src/components/CodeViewer.vue -->

<template>
  <div class="code-viewer">
    <div class="code-header">
      <div class="file-info">
        <span class="file-name">
          {{ fileName || "Java Source" }}
        </span>

        <el-button v-if="code" link size="small" @click="copyCode">
          复制
        </el-button>
      </div>
    </div>

    <div v-if="!code" class="empty-code">暂无代码</div>

    <pre v-else class="code-content"><code>{{ code }}</code></pre>
  </div>
</template>

<script setup>
import { ElMessage } from "element-plus";

const props = defineProps({
  code: {
    type: String,
    default: "",
  },

  fileName: {
    type: String,
    default: "",
  },
});

async function copyCode() {
  if (!props.code) {
    return;
  }

  try {
    await navigator.clipboard.writeText(props.code);
    ElMessage.success("代码已复制");
  } catch (error) {
    console.error("复制代码失败:", error);
    ElMessage.error("复制失败");
  }
}
</script>

<style scoped>
.code-viewer {
  width: 100%;
  overflow: hidden;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #1e1e1e;
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 14px;
  border-bottom: 1px solid #333;
  background: #252526;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-name {
  color: #d4d4d4;
  font-size: 13px;
}

.code-content {
  box-sizing: border-box;
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: #d4d4d4;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  tab-size: 4;
  white-space: pre;
}

.code-content code {
  font-family: inherit;
}

.empty-code {
  padding: 40px 20px;
  color: #909399;
  text-align: center;
  background: #1e1e1e;
}
</style>