import {
    app,
    BrowserWindow,
    dialog,
    ipcMain,
} from "electron";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

function createWindow() {
    const win = new BrowserWindow({
        width: 1440,
        height: 900,
        minWidth: 1000,
        minHeight: 700,
        frame: false,
        backgroundColor: "#ffffff",
        webPreferences: {
            preload: path.join(__dirname, "preload.cjs"),
            contextIsolation: true,
            nodeIntegration: false,
        },
    });

    win.loadURL("http://localhost:5173");
      win.webContents.openDevTools();
}

ipcMain.handle("window-minimize", (event) => {
    const win = BrowserWindow.fromWebContents(event.sender);

    if (win) {
        win.minimize();
    }
});

ipcMain.handle("window-maximize", (event) => {
    const win = BrowserWindow.fromWebContents(event.sender);

    if (!win) {
        return;
    }

    if (win.isMaximized()) {
        win.unmaximize();
    } else {
        win.maximize();
    }
});

ipcMain.handle("window-close", (event) => {
    const win = BrowserWindow.fromWebContents(event.sender);

    if (win) {
        win.close();
    }
});

ipcMain.handle("select-directory", async () => {
    console.log("[Electron Main] 收到选择目录请求");

    const result = await dialog.showOpenDialog({
        properties: ["openDirectory"],
    });

    console.log(
        "[Electron Main] dialog result =",
        result
    );

    if (
        result.canceled ||
        result.filePaths.length === 0
    ) {
        return null;
    }

    return result.filePaths[0];
});

app.whenReady().then(() => {
    createWindow();

    app.on("activate", () => {
        if (
            BrowserWindow.getAllWindows().length === 0
        ) {
            createWindow();
        }
    });
});

app.on("window-all-closed", () => {
    if (process.platform !== "darwin") {
        app.quit();
    }
});