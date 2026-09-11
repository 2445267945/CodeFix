<template>
  <section class="editor">
    <!-- 顶部文件信息 -->
    <header class="editor-header">
      <div class="editor-file-info">
        <span class="editor-file-icon">
          {{ fileIcon }}
        </span>

        <span class="editor-file-name">
          {{ fileName || "未打开文件" }}
        </span>

        <span v-if="dirty" class="editor-dirty"> ● </span>
      </div>

      <span v-if="filePath" class="editor-path">
        {{ filePath }}
      </span>
    </header>

    <!-- Monaco -->
    <div class="editor-body">
      <div v-if="loading" class="editor-state">正在读取文件…</div>

      <div v-else-if="!filePath" class="editor-state">
        <span class="editor-empty-icon">⌁</span>
        <span>从左侧文件树选择一个文件</span>
      </div>

      <div
        v-show="!loading && !!filePath"
        ref="editorContainer"
        class="monaco-container"
      ></div>
    </div>
  </section>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";

import * as monaco from "monaco-editor";

const props = defineProps({
  filePath: {
    type: String,
    default: "",
  },

  content: {
    type: String,
    default: "",
  },

  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:content", "change", "save"]);

const editorContainer = ref(null);

let editor = null;
let model = null;

/**
 * 当前文件是否被修改
 */
const dirty = ref(false);

/**
 * 文件名
 */
const fileName = computed(() => {
  if (!props.filePath) {
    return "";
  }

  const parts = props.filePath.split("/");

  return parts[parts.length - 1];
});

/**
 * 文件图标
 */
const fileIcon = computed(() => {
  const name = fileName.value.toLowerCase();

  if (name.endsWith(".java")) {
    return "J";
  }

  if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".vue")) {
    return "JS";
  }

  if (
    name.endsWith(".json") ||
    name.endsWith(".xml") ||
    name.endsWith(".yml") ||
    name.endsWith(".yaml")
  ) {
    return "{}";
  }

  if (name.endsWith(".md")) {
    return "M";
  }

  return "·";
});

/**
 * 根据文件路径获取 Monaco Language
 */
function getLanguage(path) {
  const extension = path?.split(".").pop()?.toLowerCase();

  switch (extension) {
    case "java":
      return "java";

    case "js":
    case "jsx":
      return "javascript";

    case "ts":
    case "tsx":
      return "typescript";

    case "json":
      return "json";

    case "xml":
      return "xml";

    case "yml":
    case "yaml":
      return "yaml";

    case "md":
      return "markdown";
    case "vue":
      return "html";
    case "html":
      return "html";

    case "css":
      return "css";

    case "sql":
      return "sql";

    default:
      return "plaintext";
  }
}

/**
 * 创建 Monaco Model
 */
function createModel() {
  if (!props.filePath) {
    return;
  }

  const language = getLanguage(props.filePath);

  const uri = monaco.Uri.file(props.filePath);

  model = monaco.editor.createModel(props.content || "", language, uri);

  return model;
}

/**
 * 创建 Monaco Editor
 */
function createEditor() {
  if (!editorContainer.value) {
    return;
  }

  model = createModel();

  editor = monaco.editor.create(editorContainer.value, {
    model,

    automaticLayout: true,

    theme: "vs-dark",

    fontSize: 13,

    lineHeight: 21,

    minimap: {
      enabled: true,
    },

    scrollBeyondLastLine: false,

    smoothScrolling: true,

    renderWhitespace: "selection",

    cursorBlinking: "smooth",

    wordWrap: "off",

    tabSize: 4,

    insertSpaces: true,

    detectIndentation: true,

    padding: {
      top: 10,
      bottom: 10,
    },

    suggest: {
      showMethods: true,
      showFunctions: true,
    },
  });
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
    emit("save");
  });
  editor.onDidChangeModelContent(() => {
    if (!model) {
      return;
    }

    const value = model.getValue();

    dirty.value = true;

    emit("update:content", value);

    emit("change", {
      path: props.filePath,
      content: value,
    });
  });
}

/**
 * 销毁当前 Model
 */
function disposeModel() {
  if (model) {
    model.dispose();
    model = null;
  }
}

/**
 * 销毁 Editor
 */
function disposeEditor() {
  if (editor) {
    editor.dispose();
    editor = null;
  }

  disposeModel();
}

/**
 * 更新文件
 */
async function loadFile() {
  if (!editorContainer.value) {
    return;
  }

  if (!props.filePath) {
    disposeEditor();
    dirty.value = false;
    return;
  }

  /*
   * 首次创建
   */
  if (!editor) {
    await nextTick();

    if (!editorContainer.value) {
      return;
    }

    createEditor();
    dirty.value = false;
    return;
  }

  /*
   * 当前文件切换
   */
  disposeModel();

  model = createModel();

  editor.setModel(model);

  dirty.value = false;

  await nextTick();

  editor.layout();
}

/**
 * 外部 content 发生变化时同步 Monaco。
 *
 * 注意：
 * 正常情况下用户输入会从 Monaco → emit → 父组件。
 *
 * 这里主要处理：
 * 1. 打开新文件
 * 2. 后端重新加载文件
 */
watch(
  () => props.content,
  (value) => {
    if (!model) {
      return;
    }

    const newValue = value || "";

    if (model.getValue() === newValue) {
      return;
    }

    model.setValue(newValue);

    dirty.value = false;
  }
);

/**
 * 文件路径变化
 */
watch(
  () => props.filePath,
  async () => {
    await nextTick();

    if (!props.filePath) {
      disposeEditor();
      dirty.value = false;
      return;
    }

    await loadFile();
  }
);

/**
 * loading 结束后确保 Editor 有尺寸
 */
watch(
  () => props.loading,
  async (loading) => {
    if (!loading) {
      await nextTick();

      if (props.filePath && !editor) {
        createEditor();
        dirty.value = false;
      }

      editor?.layout();
    }
  }
);

onMounted(async () => {
  await nextTick();

  if (props.filePath && !props.loading) {
    createEditor();
    dirty.value = false;
  }
});

onBeforeUnmount(() => {
  disposeEditor();
});
</script>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;

  width: 100%;
  height: 100%;

  min-width: 0;
  min-height: 0;

  background: var(--surface, #ffffff);
}

.editor-header {
  flex: 0 0 40px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  padding: 0 14px;

  border-bottom: 1px solid var(--border, #e5e7eb);

  background: var(--surface-soft, #fafafa);
}

.editor-file-info {
  display: flex;
  align-items: center;

  min-width: 0;

  gap: 7px;
}

.editor-file-icon {
  width: 18px;
  height: 18px;

  display: grid;
  place-items: center;

  border-radius: 4px;

  background: var(--accent-soft, #eef0ff);

  color: var(--accent, #4c5bd4);

  font-size: 9px;
  font-weight: 700;
}

.editor-file-name {
  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--text, #111827);

  font-size: 12px;
  font-weight: 600;
}

.editor-dirty {
  color: #c2410c;

  font-size: 9px;
}

.editor-path {
  max-width: 55%;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--muted, #9ca3af);

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 10px;
}

.editor-body {
  position: relative;

  flex: 1 1 auto;

  min-width: 0;
  min-height: 0;

  overflow: hidden;
}

.monaco-container {
  width: 100%;
  height: 100%;
}

.editor-state {
  display: flex;
  align-items: center;
  justify-content: center;

  gap: 10px;

  width: 100%;
  height: 100%;

  color: var(--muted, #9ca3af);

  font-size: 13px;
}

.editor-empty-icon {
  font-size: 20px;
}
</style>