<template>
  <el-dialog
    v-model="visible"
    title="新建项目"
    width="420px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="create-workspace">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="例如：电商后台"
            maxlength="100"
            show-word-limit
            @keyup.enter="handleCreate"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false"> 取消 </el-button>

      <el-button type="primary" :loading="creating" @click="handleCreate">
        创建项目
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:modelValue", "create"]);

const visible = ref(props.modelValue);

const creating = ref(false);

const formRef = ref(null);

const form = ref({
  name: "",
});

const rules = {
  name: [
    {
      required: true,
      message: "请输入项目名称",
      trigger: "blur",
    },
    {
      min: 1,
      max: 100,
      message: "项目名称长度应为 1-100 个字符",
      trigger: "blur",
    },
  ],
};

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value;

    if (value) {
      form.value.name = "";
    }
  }
);

watch(visible, (value) => {
  emit("update:modelValue", value);
});

async function handleCreate() {
  if (!formRef.value) {
    return;
  }

  try {
    await formRef.value.validate();

    creating.value = true;

    emit("create", {
      name: form.value.name.trim(),
      done: () => {
        creating.value = false;
      },
    });
  } catch (error) {
    ElMessage.warning("请输入项目名称");
  }
}

function handleClosed() {
  form.value.name = "";
  creating.value = false;
}
</script>

<style scoped>
.create-workspace {
  padding: 4px 0;
}
</style>