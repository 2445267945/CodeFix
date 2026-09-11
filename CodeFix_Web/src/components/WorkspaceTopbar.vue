<template>
  <header class="topbar">
    <div class="topbar-left">
      <div class="brand">
        <span class="brand-mark">✦</span>
        <span class="brand-name">Agent IDE</span>
      </div>

      <div class="topbar-divider"></div>

      <div class="workspace-context">
        <span class="workspace-context-name">
          {{ workspaceName || "Agent IDE" }}
        </span>

        <span v-if="sessionId" class="workspace-context-session">
          {{ sessionShortName }}
        </span>
      </div>
    </div>

    <div class="topbar-right">
      <span class="run-state" :class="statusClass">
        <span class="state-dot"></span>
        {{ statusLabel }}
      </span>

      <button
        v-if="canResume"
        class="text-btn"
        :disabled="operating"
        @click="emit('resume')"
      >
        继续
      </button>

      <button
        v-else-if="canRetry"
        class="text-btn"
        :disabled="operating"
        @click="emit('retry')"
      >
        重试
      </button>

      <div class="window-controls">
        <button type="button" title="最小化" @click="emit('minimize')">
          −
        </button>

        <button type="button" title="最大化 / 还原" @click="emit('maximize')">
          □
        </button>

        <button
          type="button"
          class="window-close-btn"
          title="关闭"
          @click="emit('close')"
        >
          ×
        </button>
      </div>
    </div>
  </header>
</template>

<script setup>
defineProps({
  workspaceName: {
    type: String,
    default: "",
  },

  sessionId: {
    type: String,
    default: "",
  },

  sessionShortName: {
    type: String,
    default: "",
  },

  statusLabel: {
    type: String,
    default: "",
  },

  statusClass: {
    type: String,
    default: "idle",
  },

  canResume: {
    type: Boolean,
    default: false,
  },

  canRetry: {
    type: Boolean,
    default: false,
  },

  operating: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits([
  "new-conversation",
  "resume",
  "retry",
  "minimize",
  "maximize",
  "close",
]);
</script>

<style>
.topbar {
  height: 46px;
  flex: 0 0 46px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  min-width: 0;

  padding: 0 8px 0 12px;

  border-bottom: 1px solid var(--border-subtle);

  background: color-mix(in srgb, var(--surface) 92%, transparent);

  backdrop-filter: blur(12px);

  -webkit-app-region: drag;
}

.topbar-left,
.topbar-right {
  min-width: 0;

  display: flex;
  align-items: center;

  gap: 8px;
}

.topbar button,
.topbar .run-state {
  -webkit-app-region: no-drag;
}

.icon-btn {
  width: 28px;
  height: 28px;

  display: grid;
  place-items: center;

  padding: 0;

  border: 0;
  border-radius: 7px;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  font-size: 18px;
}

.icon-btn:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.brand {
  display: flex;
  align-items: center;

  gap: 7px;
}

.brand-mark {
  width: 22px;
  height: 22px;

  display: grid;
  place-items: center;

  border-radius: 7px;

  background: var(--accent-soft);

  color: var(--accent);

  font-size: 11px;
}

.brand-name {
  font-size: 12px;
  font-weight: 700;
}

.topbar-divider {
  width: 1px;
  height: 20px;

  margin: 0 4px;

  background: var(--border-subtle);
}

.workspace-context {
  min-width: 0;

  display: flex;
  align-items: center;

  gap: 8px;
}

.workspace-context-name {
  max-width: 240px;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  font-size: 11px;
  font-weight: 650;
}

.workspace-context-session {
  max-width: 180px;

  overflow: hidden;

  text-overflow: ellipsis;
  white-space: nowrap;

  color: var(--muted);

  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;

  font-size: 9px;
}

.run-state {
  display: inline-flex;
  align-items: center;

  gap: 6px;

  padding: 4px 8px;

  border-radius: 999px;

  color: var(--muted-strong);

  background: var(--surface-soft);

  font-size: 10px;
}

.state-dot {
  width: 6px;
  height: 6px;

  border-radius: 50%;

  background: var(--muted);
}

.run-state.working {
  color: var(--accent);
  background: var(--accent-soft);
}

.run-state.working .state-dot {
  background: var(--accent);

  box-shadow: 0 0 0 3px rgba(76, 91, 212, 0.08);
}

.run-state.done {
  color: var(--success);
  background: var(--success-soft);
}

.run-state.done .state-dot {
  background: var(--success);
}

.run-state.draft {
  color: var(--muted-strong);
  background: var(--surface-soft);
}

.run-state.cancelled {
  color: var(--warning);
  background: var(--warning-soft);
}

.run-state.cancelled .state-dot {
  background: var(--warning);
}

.run-state.error {
  color: var(--danger);
  background: var(--danger-soft);
}

.run-state.error .state-dot {
  background: var(--danger);
}

.text-btn {
  padding: 5px 6px;

  border: 0;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  font-size: 11px;
}

.text-btn:hover {
  color: var(--text);
}

.text-btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.window-controls {
  display: flex;
  align-items: center;

  height: 46px;

  margin-left: 4px;

  -webkit-app-region: no-drag;
}

.window-controls button {
  width: 42px;
  height: 46px;

  display: flex;
  align-items: center;
  justify-content: center;

  padding: 0;

  border: 0;

  background: transparent;

  color: var(--muted-strong);

  cursor: pointer;

  font-family: "Segoe UI", sans-serif;

  font-size: 16px;

  line-height: 1;

  -webkit-app-region: no-drag;
}

.window-controls button:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.window-controls .window-close-btn:hover {
  background: #e81123;
  color: #ffffff;
}
</style>