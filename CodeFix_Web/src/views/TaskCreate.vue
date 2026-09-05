<!-- src/views/TaskCreate.vue -->

<template>
  <div class="task-create-page">
    <div class="page-header">
      <div>
        <h2>新建代码审计任务</h2>
        <p>提交 Java 代码后，Agent 将异步分析并修复代码问题。</p>
      </div>

      <el-button link @click="goBack"> 返回任务列表 </el-button>
    </div>

    <el-card class="create-card" shadow="never">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <!-- 文件名 -->
        <el-form-item label="文件名" prop="fileName">
          <el-input
            v-model="form.fileName"
            placeholder="例如：OrderService.java"
            clearable
          />
        </el-form-item>

        <!-- Java代码 -->
        <el-form-item label="Java 代码" prop="code">
          <el-input
            v-model="form.code"
            type="textarea"
            :rows="24"
            resize="vertical"
            placeholder="请粘贴需要审计的 Java 代码..."
          />
        </el-form-item>

        <!-- 操作 -->
        <div class="form-actions">
          <el-button @click="goBack"> 取消 </el-button>

          <el-button
            type="primary"
            :loading="taskStore.loading"
            @click="handleCreate"
          >
            开始审计
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";

import { useTaskStore } from "../stores/task";

const router = useRouter();
const taskStore = useTaskStore();

const formRef = ref(null);

const form = reactive({
  fileName: "",
  code: "",
});

const rules = {
  code: [
    {
      required: true,
      message: "请输入 Java 代码",
      trigger: "blur",
    },
  ],
};

/**
 * 创建 Task
 */
async function handleCreate() {
  if (!formRef.value) {
    return;
  }

  try {
    await formRef.value.validate();

    const result = await taskStore.createTask({
      code: form.code,
      fileName: form.fileName || null,
    });

    ElMessage.success("任务创建成功");

    // 创建成功后直接进入任务详情
    await router.push(`/tasks/${result.taskId}`);
  } catch (error) {
    // Element Plus 表单校验失败时，不额外弹错误
    if (error === false) {
      return;
    }

    ElMessage.error(taskStore.error || "创建任务失败");
  }
}

/**
 * 返回任务列表
 */
function goBack() {
  router.push("/tasks");
}
</script>

<style scoped>
.task-create-page {
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.create-card {
  max-width: 1200px;
  margin: 0 auto;
  border-radius: 10px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

:deep(.el-textarea__inner) {
  padding: 14px;
  color: #d4d4d4;
  background: #1e1e1e;
  border-color: #303030;
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
}

:deep(.el-textarea__inner::placeholder) {
  color: #777;
}

@media (max-width: 768px) {
  .task-create-page {
    padding: 12px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}
</style>