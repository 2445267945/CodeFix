<template>
  <div class="node" role="treeitem">
    <button
      class="row"
      :class="{
        selected: isFile && displayNode.path === selectedFile,
      }"
      :style="{
        paddingLeft: `${8 + depth * 14}px`,
      }"
      type="button"
      @click="handleClick"
    >
      <span class="chevron" :class="{ empty: isFile }">
        {{ isDirectory ? (expanded ? "⌄" : "›") : "" }}
      </span>

      <span class="icon" :class="isDirectory ? 'folder' : fileTone">
        {{ isDirectory ? (expanded ? "▾" : "▸") : fileIcon }}
      </span>

      <span class="name" :title="displayNode.path">
        {{ displayName }}
      </span>

      <span v-if="node.modified" class="modified"> M </span>
    </button>

    <div
      v-if="isDirectory && expanded && displayChildren.length"
      class="children"
    >
      <WorkspaceTreeNode
        v-for="child in displayChildren"
        :key="child.path"
        :node="child"
        :selected-file="selectedFile"
        :depth="depth + 1"
        @select="$emit('select', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";

const props = defineProps({
  node: {
    type: Object,
    required: true,
  },

  selectedFile: {
    type: String,
    default: "",
  },

  depth: {
    type: Number,
    default: 0,
  },
});

const emit = defineEmits(["select"]);

const expanded = ref(props.node.expanded !== false);

/** Compress a directory-only chain: com -> xd -> controller becomes com.xd.controller. */
const displayNode = computed(() => {
  let current = props.node;
  const names = [current.name];

  while (
    current.type === "DIRECTORY" &&
    Array.isArray(current.children) &&
    current.children.length === 1 &&
    current.children[0]?.type === "DIRECTORY"
  ) {
    current = current.children[0];
    names.push(current.name);
  }

  return { ...current, displayName: names.join(".") };
});

const displayName = computed(() => displayNode.value.displayName);
const displayChildren = computed(() => displayNode.value.children || []);
const isDirectory = computed(() => displayNode.value.type === "DIRECTORY");

const isFile = computed(() => displayNode.value.type === "FILE");

const fileTone = computed(() => {
  const name = (displayNode.value.name || "").toLowerCase();

  if (name.endsWith(".java")) {
    return "java";
  }

  if (name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".vue")) {
    return "js";
  }

  if (
    name.endsWith(".json") ||
    name.endsWith(".xml") ||
    name.endsWith(".yml") ||
    name.endsWith(".yaml")
  ) {
    return "config";
  }

  if (name.endsWith(".md")) {
    return "md";
  }

  return "file";
});

const fileIcon = computed(() => {
  switch (fileTone.value) {
    case "java":
      return "J";

    case "js":
      return "JS";

    case "config":
      return "{}";

    case "md":
      return "M";

    default:
      return "·";
  }
});

function handleClick() {
  if (isDirectory.value) {
    expanded.value = !expanded.value;
    return;
  }

  if (isFile.value) {
    emit("select", displayNode.value.path);
  }
}
</script>

<style scoped>
.node {
  min-width: 0;
}

.row {
  width: 100%;
  min-height: 29px;

  display: grid;
  grid-template-columns:
    13px
    22px
    minmax(0, 1fr)
    auto;

  gap: 4px;
  align-items: center;

  border: 0;
  border-radius: 6px;

  padding-right: 7px;

  background: transparent;
  color: var(--text-soft);

  text-align: left;
  cursor: pointer;
}

.row:hover {
  background: var(--surface-hover);
}

.row.selected {
  background: var(--accent-soft);
  color: var(--text);
}

.chevron {
  color: var(--muted);
  font-size: 14px;
  line-height: 1;
}

.chevron.empty {
  width: 13px;
}

.icon {
  height: 18px;

  display: inline-grid;
  place-items: center;

  border-radius: 4px;

  font-size: 9px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.icon.folder {
  color: #a56b13;
  background: #fff4dd;
}

.icon.java {
  color: #a83f31;
  background: #fff0ed;
}

.icon.js {
  color: #8d6b00;
  background: #fff8d9;
}

.icon.config {
  color: #6d58a3;
  background: #f2eeff;
}

.icon.md {
  color: #4674a7;
  background: #edf5ff;
}

.icon.file {
  color: var(--muted-strong);
  background: var(--surface-soft);
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  font: 11px / 1.3 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.modified {
  color: var(--accent);

  font: 700 9px / 1 ui-monospace, monospace;
}

.children {
  min-width: 0;
}
</style>
