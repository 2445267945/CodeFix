const { contextBridge, ipcRenderer } = require("electron");

console.log("[Electron Preload] preload loaded");

contextBridge.exposeInMainWorld("electronAPI", {
    selectDirectory() {
        return ipcRenderer.invoke("select-directory");
    },

    minimizeWindow() {
        return ipcRenderer.invoke("window-minimize");
    },

    maximizeWindow() {
        return ipcRenderer.invoke("window-maximize");
    },

    closeWindow() {
        return ipcRenderer.invoke("window-close");
    },
});