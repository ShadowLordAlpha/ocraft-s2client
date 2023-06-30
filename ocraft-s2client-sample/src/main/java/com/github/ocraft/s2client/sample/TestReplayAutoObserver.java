package com.github.ocraft.s2client.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ocraft.s2client.bot.S2Coordinator;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.bot.camera.CameraModuleObserver;
import com.github.ocraft.s2client.bot.gateway.UnitInPool;
import com.github.ocraft.s2client.protocol.data.UnitAttribute;
import com.github.ocraft.s2client.protocol.data.UnitType;
import com.github.ocraft.s2client.protocol.data.Units;
import com.github.ocraft.s2client.protocol.game.PlayerInfo;
import com.github.ocraft.s2client.protocol.game.PlayerType;
import com.github.ocraft.s2client.protocol.unit.Unit;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.INPUT;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.ocraft.s2client.bot.enums.UnitTypeNames.getLabelFromName;

public class TestReplayAutoObserver {
    private static String player1_name;
    private static String player2_name;
    private static String map_name;
    private static Map<Integer, String> replay_map = new LinkedHashMap<>();


    private static class SimpleObserver extends S2ReplayObserver {
        private CameraModuleObserver observer;
        private ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> dataMap = new LinkedHashMap<>();

        private long lastFrame = 0;

        private long startTime = 0;

        private int nbReplay;

        // Virtual key code for the desired key
        int virtualKeyCode = 0x44; // 'D' key

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
            nbReplay++;
            observer.onStart();
            startTime = observation().getGameInfo().getNanoTime();
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
        }

        @Override
        public void onUnitCreated(UnitInPool unitInPool) {
            observer.moveCameraUnitCreated(unitInPool.unit());
        }

        @Override
        public void onStep() {
            control().getObservation();
            if ((System.currentTimeMillis() - lastFrame) > 2000) {
                boolean deleted = false;
                File file = new File("D:\\Workspace\\python\\commentary-sc2\\data\\game_info.txt");
                if (file.exists()) {
                    deleted = file.delete();
                }
                if (deleted) {
                    try (PrintWriter writer = new PrintWriter(file)) {
                        fillMapWithPlayers();
                        fillMapWithArmy();
                        fillMapWithBuildings();
                        fillMapWithWorkers();
                        String json = objectMapper.writeValueAsString(dataMap);
                        writer.println(json);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
                lastFrame = System.currentTimeMillis();
                observer.onFrame();
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void fillMapWithPlayers() {
            String replayFileName = replay_map.get(nbReplay);
            dataMap.put("ingame_time_in_minutes", (System.nanoTime() - startTime) / 1_000_000_000 / 60);
            if (replayFileName != null) {
                Pattern pattern = Pattern.compile("\\d+_(.*?)_(.*?)_(.*?)\\.");
                Matcher matcher = pattern.matcher(replayFileName);
                if (matcher.find()) {
                    player1_name = matcher.group(1);
                    player2_name = matcher.group(2);
                    map_name = matcher.group(3);
                } else {
                    System.out.println("Player names not found in the file path.");
                }
            }
            for (PlayerInfo player : observation().getGameInfo().getPlayersInfo()) {
                if (player.getPlayerType().orElse(PlayerType.OBSERVER) != PlayerType.OBSERVER) {
                    dataMap.put("Player" + player.getPlayerId(), new LinkedHashMap<String, Object>());
                    Map<String, Object> details = (Map<String, Object>) dataMap.get("Player" + player.getPlayerId());
                    details.put("name", player.getPlayerId() == 1 ? player1_name : player2_name);
                    details.put("race", player.getActualRace().get());
                    details.put("units", new LinkedHashMap<String, Integer>());
                    details.put("buildings", new LinkedHashMap<String, Integer>());
                }
            }
        }

        private void fillMapWithWorkers() {
            if (!control().observation().getUnits().isEmpty()) {
                for (UnitInPool unitInPool : control().observation().getUnits().stream().filter(u -> u.isAlive() && u.getUnit().isPresent()).collect(Collectors.toList())) {
                    if (unitInPool.getUnit().isPresent()) {
                        Unit unit = unitInPool.getUnit().get();
                        if (isWorkerType(unit.getType()) && (unit.getOwner() == 1 || unit.getOwner() == 2)) {
                            Map<String, Object> details = (Map<String, Object>) dataMap.get("Player" + unit.getOwner());
                            Map<String, Integer> unitMap = (Map<String, Integer>) details.get("units");
                            boolean unitExists = unitMap.containsKey(getLabelFromName(unit.getType().toString()));
                            if (unitExists) {
                                Integer value = unitMap.get(getLabelFromName(unit.getType().toString()));
                                value += 1;
                                unitMap.put(getLabelFromName(unit.getType().toString()), value);
                            } else {
                                unitMap.put(getLabelFromName(unit.getType().toString()), 1);
                            }
                        }

                    }
                }
            }
        }

        private void fillMapWithBuildings() {
            if (!control().observation().getUnits().isEmpty()) {
                for (UnitInPool unitInPool : control().observation().getUnits().stream().filter(u -> u.isAlive() && u.getUnit().isPresent()).collect(Collectors.toList())) {
                    if (unitInPool.getUnit().isPresent()) {
                        Unit unit = unitInPool.getUnit().get();
                        if (isBuilding(unit.getType()) && (unit.getOwner() == 1 || unit.getOwner() == 2)) {
                            Map<String, Object> details = (Map<String, Object>) dataMap.get("Player" + unit.getOwner());
                            Map<String, Integer> buildingMap = (Map<String, Integer>) details.get("buildings");
                            boolean buildingExists = buildingMap.containsKey(getLabelFromName(unit.getType().toString()));
                            if (buildingExists) {
                                Integer value = buildingMap.get(getLabelFromName(unit.getType().toString()));
                                value += 1;
                                buildingMap.put(getLabelFromName(unit.getType().toString()), value);
                            } else {
                                buildingMap.put(getLabelFromName(unit.getType().toString()), 1);
                            }
                        }

                    }
                }
            }
        }

        private void fillMapWithArmy() {
            if (!control().observation().getUnits().isEmpty()) {
                for (UnitInPool unitInPool : control().observation().getUnits().stream().filter(u -> u.isAlive() && u.getUnit().isPresent()).collect(Collectors.toList())) {
                    if (unitInPool.getUnit().isPresent()) {
                        Unit unit = unitInPool.getUnit().get();
                        if (isArmyUnitType(unit.getType()) && (unit.getOwner() == 1 || unit.getOwner() == 2)) {
                            Map<String, Object> details = (Map<String, Object>) dataMap.get("Player" + unit.getOwner());
                            Map<String, Integer> unitMap = (Map<String, Integer>) details.get("units");
                            boolean unitExists = unitMap.containsKey(getLabelFromName(unit.getType().toString()));
                            if (unitExists) {
                                Integer value = unitMap.get(getLabelFromName(unit.getType().toString()));
                                value += 1;
                                unitMap.put(getLabelFromName(unit.getType().toString()), value);
                            } else {
                                unitMap.put(getLabelFromName(unit.getType().toString()), 1);
                            }
                        }

                    }
                }
            }
        }

        /**
         * Check if a unit type is a worker
         *
         * @param type
         * @return
         */
        protected boolean isWorkerType(UnitType type) {
            if (type.equals(Units.TERRAN_SCV) || type.equals(Units.TERRAN_MULE) || type.equals(Units.PROTOSS_PROBE) || type.equals(Units.ZERG_DRONE) || type.equals(Units.ZERG_DRONE_BURROWED)) {
                return true;
            }

            return false;
        }

        /**
         * Check if a unit should be counted as army or not
         *
         * @param type
         * @return
         */
        protected boolean isArmyUnitType(UnitType type) {
            if (isWorkerType(type)) {
                return false;
            }
            if (type == Units.ZERG_OVERLORD) {
                return false;
            }  // Excluded here the overlord transport etc to count them as army unit
            if (isBuilding(type)) {
                return false;
            }
            if (type == Units.ZERG_EGG) {
                return false;
            }
            if (type == Units.ZERG_LARVA) {
                return false;
            }
            if (type == Units.PROTOSS_INTERCEPTOR) {
                return false;
            }

            return true;
        }

        /**
         * Check if a unit type is a building, We do this by just checking if it has the structure attribute
         *
         * @param type
         * @return
         */
        protected boolean isBuilding(UnitType type) {
            return observation().getUnitTypeData(false).get(type).getAttributes().contains(UnitAttribute.STRUCTURE);
        }

    }

    public static void main(String... args) throws IOException {

        S2ReplayObserver observer = new SimpleObserver();
        File replay_dir = new File("C:/Users/kille/Downloads/replays/");
        if (replay_dir.isDirectory()) {
            File[] files = replay_dir.listFiles();
            if (files == null)
                return;
            int totalFiles = files.length;
            for (int i = totalFiles - 1; i >= 0; i--) {
                File file = files[i];
                String fileName = file.getName();
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
                .setReplayPath(Paths.get("C:/Users/kille/Downloads/replays/")) // use your own folder of replays, they will go one after the other
                .launchStarcraft();

        if (s2Coordinator.hasReplays()) {
            long lastStepTime = 0;
            while (s2Coordinator.update() && !s2Coordinator.allGamesEnded()) {
                if ((System.currentTimeMillis() - lastStepTime) > 40) {
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
