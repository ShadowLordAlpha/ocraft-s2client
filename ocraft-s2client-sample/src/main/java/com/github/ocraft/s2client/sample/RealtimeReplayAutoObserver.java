package com.github.ocraft.s2client.sample;

import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.camera.CameraModuleObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.INPUT;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class RealtimeReplayAutoObserver {
    private static Map<Integer, String> replay_map = new LinkedHashMap<>();


    private static class SimpleObserver extends S2ReplayObserver {
        private CameraModuleObserver observer;

        private long lastFrame = 0;

        private static long startTime = 0;
        private static boolean productionTabPressed;

        // Virtual key code for the desired key
        static int virtualKeyCode = 0x44; // 'D' key

        public SimpleObserver() {
            observer = new CameraModuleObserver(this);
        }

        public static void pressKey(int c) {
            INPUT input = new INPUT();
            input.type = new WinDef.DWORD(INPUT.INPUT_KEYBOARD);
            input.input.setType("ki");
            input.input.ki.wScan = new WinDef.WORD(0);
            input.input.ki.time = new WinDef.DWORD(0);
            input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
            input.input.ki.wVk = new WinDef.WORD(c);
            input.input.ki.dwFlags = new WinDef.DWORD(WinUser.WM_KEYDOWN);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), (INPUT[]) input.toArray(1), input.size());
            input.input.ki.wVk = new WinDef.WORD(c);
            input.input.ki.dwFlags = new WinDef.DWORD(WinUser.KEYBDINPUT.KEYEVENTF_KEYUP);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), (INPUT[]) input.toArray(1), input.size());
        }

        @Override
        public void onGameStart() {
            productionTabPressed = true;
            observer.onStart();
            startTime = System.nanoTime();
            control().observation().getGameInfo(true);
        }

        @Override
        public void onUnitCreated(UnitInPool unitInPool) {
            observer.moveCameraUnitCreated(unitInPool.unit());
        }

        @Override
        public void onStep() {
            if (3_000_000_000L < (System.nanoTime() - startTime) && (System.nanoTime() - startTime) < 4_000_000_000L && !productionTabPressed) {
                // Find the target window by its title
                WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, "StarCraft II");
                if (hwnd == null) {
                    System.out.println("Window not found");
                    return;
                }

                // Set the target window as the foreground window
                User32.INSTANCE.SetForegroundWindow(hwnd);

                // Simulate key press
                pressKey(virtualKeyCode);
                productionTabPressed = true;
            }
            if ((System.currentTimeMillis() - lastFrame) > 2500) {
                lastFrame = System.currentTimeMillis();
                observer.onFrame();
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void executeCommand(String command, String filePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command, filePath);
            Process process = processBuilder.start();

            // Wait for the process to finish
            int exitCode = process.waitFor();

            // Check the exit code to determine if the command executed successfully
            if (exitCode == 0) {
                System.out.println("Command executed successfully: " + command);
            } else {
                System.out.println("Command failed: " + command);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) throws IOException {

        S2ReplayObserver observer = new SimpleObserver();
        File replay_dir = new File("replays/");
        if (replay_dir.isDirectory()) {
            File[] files = replay_dir.listFiles();
            if (files == null)
                return;
            int totalFiles = files.length;
            for (int i = totalFiles - 1; i >= 0; i--) {
                File file = files[i];
                String fileName = file.getName();
                executeCommand("ReplayChatRemove.exe", "replays\\" + fileName);
                int fileNumber = totalFiles - i; // Adjust the file number calculation if needed
                replay_map.put(fileNumber, fileName);
            }
        }
        S2Coordinator s2Coordinator = S2Coordinator.setup()
                .loadSettings(args)
                .setStepSize(4)
                .setRealtime(true)
                .setProcessPath(Paths.get("C:\\Program Files (x86)\\StarCraft II\\Versions\\Base75689\\SC2_x64.exe"))
                .setDataVersion("B89B5D6FA7CBF6452E721311BFBC6CB2")
                .setShowBurrowed(true)
                .addReplayObserver(observer)
                .setReplayPath(Paths.get("replays/")) // use your own folder of replays, they will go one after the other
                .launchStarcraft();

        if (s2Coordinator.hasReplays()) {
            long lastStepTime = 0;
            while (s2Coordinator.update() && !s2Coordinator.allGamesEnded()) {
                if ((System.currentTimeMillis() - lastStepTime) > 35) {
                    lastStepTime = System.currentTimeMillis();
                    observer.control().waitStep(observer.control().step(1));
                }
            }
        } else {
            System.out.println("No Replays Found!");
        }

        s2Coordinator.quit();
    }
}
