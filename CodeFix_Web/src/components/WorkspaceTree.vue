<template>
  <div class="tree" role="tree">
    <TreeNode
      v-for="node in tree"
      :key="node.path"
      :node="node"
      :selected-file="selectedFile"
      :depth="0"
      @select="$emit('select', $event)"
      @toggle="$emit('toggle', $event)"
    />
  </div>
</template>

<script setup>
import TreeNode from './WorkspaceTreeNode.vue'

defineProps({
  tree: { type: Array, default: () => [] },
  selectedFile: { type: String, default: '' }
})

defineEmits(['select', 'toggle'])
</script>

<style scoped>
.tree { padding: 0 8px 12px; overflow: auto; height: 100%; }
</style>
