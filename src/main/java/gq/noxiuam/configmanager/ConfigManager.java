package gq.noxiuam.configmanager;



import com.cheatbreaker.client.CheatBreaker;
import com.cheatbreaker.client.module.AbstractModule;
import com.cheatbreaker.client.module.staff.StaffModule;
import com.cheatbreaker.client.ui.element.type.ColorPickerColorElement;
import com.cheatbreaker.client.ui.module.CBGuiAnchor;
import com.cheatbreaker.client.util.dash.Station;
import com.google.gson.*;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Noxiuam
 * https://noxiuam.gq
 */
public class ConfigManager {

    public final File profileDir;
    private final File configDir;
    private final File globalConfig;
    private final File mutesConfig;
    private final File defaultConfig;

    public ConfigManager() {
        this.configDir = new File(Minecraft.getMinecraft().mcDataDir + File.separator + "config" + File.separator + "client");
        this.globalConfig = new File(this.configDir + File.separator + "global.json");
        this.mutesConfig = new File(this.configDir + File.separator + "mutes.cfg");
        this.defaultConfig = new File(this.configDir + File.separator + "default.json");
        this.profileDir = new File(this.configDir + File.separator + "profiles");
    }

    public void write() {
        if (this.createRequiredFiles()) {
            CheatBreaker.getInstance().getModuleManager().minmap.getVoxelMap().getMapOptions().saveAll();
            this.writeGlobalConfig(this.globalConfig);
            this.writeMutesConfig(this.mutesConfig);
            this.writeProfile(CheatBreaker.getInstance().activeProfile.getName());
        }
    }

    public void read() {
        if (this.createRequiredFiles()) {
            this.readGlobalConfig(this.globalConfig);
            this.readMutesConfig(this.mutesConfig);
            if (CheatBreaker.getInstance().activeProfile == null) {
                CheatBreaker.getInstance().activeProfile = CheatBreaker.getInstance().profiles.get(0);
            } else {
                this.readProfile(CheatBreaker.getInstance().activeProfile.getName());
            }
            CheatBreaker.getInstance().getModuleManager().minmap.getVoxelMap().getMapOptions().loadAll();
        }
    }

    private boolean createRequiredFiles() {
        try {
            return !(!this.configDir.exists() && !this.configDir.mkdirs() || !this.defaultConfig.exists() && !this.defaultConfig.createNewFile() || !this.globalConfig.exists() && !this.globalConfig.createNewFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @SneakyThrows
    public void readProfile(String profileName) {
        ArrayList<AbstractModule> playerMods = new ArrayList<>(CheatBreaker.getInstance().moduleManager.modules);

        if (profileName.equalsIgnoreCase("default")) {
            CheatBreaker.getInstance().activeProfile = CheatBreaker.getInstance().profiles.get(0);

            for (AbstractModule module : playerMods) {
                module.setState(module.defaultState);
                module.setAnchor(module.defaultGuiAnchor);
                module.setTranslations(module.defaultXTranslation, module.defaultYTranslation);
                module.setRenderHud(module.defaultRenderHud);

                for (int i = 0; i < module.getSettingsList().size(); ++i) {
                    try {
                        module.getSettingsList().get(i).setValue(module.getDefaultSettingsValues().get(i), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return;
        }

        File profiles = new File(this.configDir + File.separator + "profiles");
        File config = profiles.exists() || profiles.mkdirs() ? new File(profiles + File.separator + profileName + ".json") : null;

        JsonParser parser = new JsonParser();

        try {
            parser.parse(new FileReader(config)).getAsJsonObject();
        } catch (IllegalStateException e) {
            this.createProfile(config);
            this.writeProfile(profileName);
            return;
        }

        if (!config.exists()) {
            this.writeProfile(profileName);
            return;
        }

        JsonObject configObj = parser.parse(new FileReader(config)).getAsJsonObject();

        for (AbstractModule module : playerMods) {
            JsonObject modObject = configObj.getAsJsonObject(module.getName());
            JsonObject info = modObject.getAsJsonObject("info");
            JsonObject settings = modObject.getAsJsonObject("settings");

            String guiAnchor = null;
            boolean state = info.get("state").getAsBoolean();

            try {
                guiAnchor = info.get("position").getAsString();
            } catch (NullPointerException ignored) {
                // no gui anchor, probably not a hud mod.
            }

            float xTranslation = info.get("xTranslation").getAsFloat();
            float yTranslation = info.get("yTranslation").getAsFloat();
            boolean renderHUD = info.get("renderHUD").getAsBoolean();

            module.setState(state);
            if (guiAnchor != null) {
                module.setAnchor(this.getCBGuiAnchor(guiAnchor));
            }
            module.setXTranslation(xTranslation);
            module.setYTranslation(yTranslation);
            module.setRenderHud(renderHUD);

            for (Setting setting : module.getSettingsList()) {
                try {
                    JsonElement settingValue = settings.get(setting.getLabel());

                    switch (setting.getType()) {
                        case BOOLEAN:
                            setting.setValue(settingValue.getAsBoolean());
                            break;

                        case INTEGER:
                            if (module.isStaffModule() && setting == ((StaffModule) module).getKeybindSetting()) {
                                ((StaffModule) module).getKeybindSetting().setValue(settingValue.getAsInt());
                                break;
                            }

                            if (setting.getLabel().toLowerCase().contains("color")) {
                                JsonObject container = settingValue.getAsJsonObject();
                                boolean chroma = container.get("chroma").getAsBoolean();
                                int color = container.get("color").getAsInt();

                                if (color > (Integer) setting.getMaximumValue() || color < (Integer) setting.getMinimumValue())
                                    continue;

                                setting.rainbow = chroma;
                                setting.setValue(color);
                                break;
                            }

                            setting.setValue(settingValue.getAsInt());
                            break;

                        case FLOAT:
                            float f = settingValue.getAsFloat();
                            if (!(f <= (Float) setting.getMaximumValue()) || !(f >= (Float) setting.getMinimumValue()))
                                break;
                            setting.setValue(f);
                            break;

                        case DOUBLE:
                            double d = settingValue.getAsDouble();
                            if (!(d <= (Double) setting.getMaximumValue()) || !(d >= (Double) setting.getMinimumValue()))
                                break;
                            setting.setValue(d);
                            break;

                        case STRING_ARRAY:
                            boolean bl = false;
                            for (String value : setting.getAcceptedValues()) {
                                if (!value.equalsIgnoreCase(settingValue.getAsString())) continue;
                                bl = true;
                            }
                            if (!bl) break;
                            setting.setValue(settingValue.getAsString());
                            break;

                        case STRING:
                            if (setting.getLabel().equalsIgnoreCase("label")) {
                                break;
                            }

                            String val = settingValue.getAsString();
                            if (setting == CheatBreaker.getInstance().getModuleManager().toggleSprint.flyBoostString) {
                                val = val.replaceAll("%FPS%", "%BOOST%");
                            }
                            setting.setValue(val.replaceAll("&([abcdefghijklmrABCDEFGHIJKLMNR0-9])|(&$)", "§$1"));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

    }

    // added to get anchors more easily.
    private CBGuiAnchor getCBGuiAnchor(String name) {
        for (CBGuiAnchor cbGuiAnchor : CBGuiAnchor.values()) {
            if (cbGuiAnchor.getLabel().equalsIgnoreCase(name)) {
                return cbGuiAnchor;
            }
        }
        return CBGuiAnchor.RIGHT_TOP;
    }

    @SneakyThrows
    public void writeProfile(String string) {
        if (string.equalsIgnoreCase("default")) {
            return;
        }

        File profilesDir = new File(this.configDir + File.separator + "profiles");
        File profileCfg = profilesDir.exists() || profilesDir.mkdirs() ? new File(profilesDir + File.separator + string + ".json") : null;
        ArrayList<AbstractModule> modules = new ArrayList<>(CheatBreaker.getInstance().moduleManager.modules);

        JSONObject configObj = new JSONObject();
        for (AbstractModule mod : modules) {

            JSONObject modObj = new JSONObject();
            JSONObject modInfo = new JSONObject();
            JSONObject settings = new JSONObject();

            modInfo.put("state", mod.isEnabled());
            if (mod.getGuiAnchor() != null) {
                modInfo.put("position", mod.getGuiAnchor().getLabel());
            }
            modInfo.put("xTranslation", mod.getXTranslation());
            modInfo.put("yTranslation", mod.getYTranslation());
            modInfo.put("renderHUD", mod.isRenderHud());

            for (Setting setting : mod.getSettingsList()) {
                if (setting.getLabel().equalsIgnoreCase("label")) {
                    continue;
                } if (setting.getType().equals(Setting.Type.INTEGER) && setting.getLabel().toLowerCase().contains("color")) {
                    JSONObject colorOptions = new JSONObject();
                    colorOptions.put("chroma", setting.rainbow);
                    colorOptions.put("color", setting.getValue());
                    settings.put(setting.getLabel(), colorOptions);
                } else {
                    settings.put(setting.getLabel(), setting.getValue());
                }
            }

            modObj.put("info", modInfo);
            modObj.put("settings", settings);

            configObj.put(mod.getName(), modObj);
        }

        FileWriter config = new FileWriter(profileCfg);
        config.write(this.beautifyJson(configObj));
        config.close();
    }

    @SneakyThrows
    public void createGlobalConfigFile(File globalConfig) {
        globalConfig.createNewFile();
        FileWriter config = new FileWriter(globalConfig);
        config.write("{}");
        config.close();
    }

    @SneakyThrows
    public void createProfile(File profile) {
        profile.createNewFile();
        FileWriter config = new FileWriter(profile);
        config.write("{}");
        config.close();
    }

    @SneakyThrows
    public void readGlobalConfig(File file) {
        JsonParser parser = new JsonParser();
        try {
            parser.parse(new FileReader(file)).getAsJsonObject();
        } catch (IllegalStateException ignored) {
            this.createGlobalConfigFile(file);
            this.writeGlobalConfig(file);
            return;
        }

        if (!file.exists()) {
            this.createGlobalConfigFile(file);
            this.writeGlobalConfig(file);
            return;
        }

        try {
            JsonObject globalConfigObj = parser.parse(new FileReader(file)).getAsJsonObject();

            if (globalConfigObj.get("activeProfile") != null) {
                String profileName = globalConfigObj.get("activeProfile").getAsString();

                File profileFile = null;
                File profilesDir = new File(this.configDir + File.separator + "profiles");
                if (profilesDir.exists() || profilesDir.mkdirs()) {
                    profileFile = new File(profilesDir + File.separator + profileName + ".json");
                }
                if (profileFile == null || !profileFile.exists()) {
                    this.writeProfile(profileName);
                    this.readGlobalConfig(file);
                    return;
                }
                Profile finalProfile = null;
                for (Profile profile : CheatBreaker.getInstance().profiles) {
                    if (!profileName.equalsIgnoreCase(profile.getName())) continue;
                    finalProfile = profile;
                }
                if (finalProfile != null && !finalProfile.getName().equalsIgnoreCase("default")) {
                    CheatBreaker.getInstance().activeProfile = finalProfile;
                }
            }

            if (globalConfigObj.get("favoriteColors") != null) {
                String favoriteColors = globalConfigObj.get("favoriteColors").getAsString();
                String[] declaration = favoriteColors.split(",");
                for (String color : declaration) {
                    try {
                        CheatBreaker.getInstance().getGlobalSettings().favouriteColors.add(new ColorPickerColorElement(1.0f, Integer.parseInt(color)));
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            }

            if (globalConfigObj.get("favoriteStations") != null) {
                String favoriteStations = globalConfigObj.get("favoriteStations").getAsString();
                String[] declaration = favoriteStations.split(",");
                for (String stationName : declaration) {
                    try {
                        for (Station station : CheatBreaker.getInstance().getRadioManager().getStations()) {
                            if (!station.getName().equalsIgnoreCase(stationName)) continue;
                            station.setFavourite(true);
                        }
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            }


            if (globalConfigObj.get("xrayBlocks") != null) {
                String xrayBlocks = globalConfigObj.get("xrayBlocks").getAsString();
                CheatBreaker.getInstance().getModuleManager().xray.lIllIllIlIIllIllIlIlIIlIl().clear();
                String[] declaration = xrayBlocks.split(",");
                for (String blockId : declaration) {
                    try {
                        CheatBreaker.getInstance().getModuleManager().xray.lIllIllIlIIllIllIlIlIIlIl().add(Integer.parseInt(blockId));
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            }

            // To whoever see's this, Decencies never added the keybinding edits CheatBreaker did.

//            if (globalConfigObj.get("settings").getAsJsonObject().get("keybinds") != null) {
//                JsonObject keybindsObj = globalConfigObj.get("settings").getAsJsonObject().get("keybinds").getAsJsonObject();
//
//                for (Map.Entry<String, JsonElement> keybindName : keybindsObj.entrySet()) {
//                    for (KeyBinding keyBinding : Minecraft.getMinecraft().gameSettings.keyBindings) {
//                        if (!keyBinding.isCheatBreakerKeybind || !keybindName.getKey().equalsIgnoreCase(keyBinding.getKeyDescription()))
//                            continue;
//                        keyBinding.setKeyCode(Integer.parseInt(String.valueOf(keybindName.getValue())));
//                    }
//                }
//            }

            if (globalConfigObj.get("profileIndexes") != null) {
                String profileIndexes = globalConfigObj.get("profileIndexes").getAsString();
                String[] declaration = profileIndexes.split("]\\[");
                for (String profileName : declaration) {
                    profileName = profileName.replaceFirst("\\[", "");
                    String[] index = profileName.split(",", 2);
                    try {
                        int n = Integer.parseInt(index[1]);
                        for (Profile profile : CheatBreaker.getInstance().profiles) {
                            if (n == 0 || !profile.getName().equalsIgnoreCase(index[0])) continue;
                            profile.setIndex(n);;
                        }
                    } catch (NumberFormatException numberFormatException) {
                        numberFormatException.printStackTrace();
                    }
                }
            }

            if (globalConfigObj.get("settings") != null) {
                for (Setting setting : CheatBreaker.getInstance().getGlobalSettings().settingsList) {
                    JsonElement settingValue = globalConfigObj.get("settings").getAsJsonObject().get(setting.getLabel());

                    switch (setting.getType()) {
                        case BOOLEAN:
                            setting.setValue(settingValue.getAsBoolean());
                            break;

                        case INTEGER:
                            if (setting.getLabel().toLowerCase().contains("color")) {
                                JsonObject container = settingValue.getAsJsonObject();
                                boolean chroma = container.get("chroma").getAsBoolean();
                                int color = container.get("color").getAsInt();

                                if (color > (Integer) setting.getMaximumValue() || color < (Integer) setting.getMinimumValue())
                                    continue;

                                setting.rainbow = chroma;
                                setting.setValue(color);
                                break;
                            }

                            int valueAsInt = settingValue.getAsInt();
                            setting.rainbow = false;
                            if (valueAsInt > (Integer) setting.getMaximumValue() || valueAsInt < (Integer) setting.getMinimumValue())
                                continue;
                            setting.setValue(valueAsInt);
                            break;

                        case FLOAT:
                            float valueAsFloat = settingValue.getAsFloat();
                            if (!(valueAsFloat <= (Float) setting.getMaximumValue()) || !(valueAsFloat >= (Float) setting.getMinimumValue()))
                                break;
                            setting.setValue(valueAsFloat);
                            break;

                        case DOUBLE:
                            double d = settingValue.getAsDouble();
                            if (!(d <= (Double) setting.getMaximumValue()) || !(d >= (Double) setting.getMinimumValue()))
                                break;
                            setting.setValue(d);
                            break;

                        case STRING_ARRAY:
                            boolean bl = false;
                            for (String value : setting.getAcceptedValues()) {
                                if (!value.equalsIgnoreCase(settingValue.getAsString())) continue;
                                bl = true;
                            }
                            if (!bl) break;
                            setting.setValue(settingValue.getAsString());
                            break;

                        case STRING:
                            if (setting.getLabel().equalsIgnoreCase("label")) {
                                break;
                            }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        this.writeGlobalConfig(file);
    }

    public void writeGlobalConfig(File file) {
        try {
            ArrayList<Setting> globalSettings = new ArrayList<>(CheatBreaker.getInstance().getGlobalSettings().settingsList);
            JSONObject configObj = new JSONObject();
            JSONObject keybindsObj = new JSONObject();
            JSONObject settingsObj = new JSONObject();

            if (CheatBreaker.getInstance().activeProfile != null && !CheatBreaker.getInstance().activeProfile.getName().equals("default")) {
                configObj.put("activeProfile", CheatBreaker.getInstance().activeProfile.getName());
            }

            for (Setting setting : globalSettings) {
                if (setting.getLabel().equalsIgnoreCase("label")) {
                    continue;
                }
                if (setting.getLabel().toLowerCase().endsWith("color")) {
                    JSONObject colorSettings = new JSONObject();
                    colorSettings.put("color", setting.getColorValue());
                    colorSettings.put("chroma", setting.rainbow);
                    settingsObj.put(setting.getLabel(), colorSettings);
                    continue;
                }
                settingsObj.put(setting.getLabel(), setting.getValue());
            }

            // Again, he never added the Keybinding edits.
//            for (KeyBinding keyBind : Minecraft.getMinecraft().gameSettings.keyBindings) {
//                if (!(keyBind.isCheatBreakerKeybind)) continue;
//                keybindsObj.put(keyBind.getKeyDescription(), keyBind.getKeyCode());
//            }
            settingsObj.put("keybinds", keybindsObj);

            String favColors = "";
            for (ColorPickerColorElement colorPickerColorElement : CheatBreaker.getInstance().getGlobalSettings().favouriteColors) {
                favColors = favColors + colorPickerColorElement.color + (CheatBreaker.getInstance().getGlobalSettings().favouriteColors.indexOf(colorPickerColorElement) == CheatBreaker.getInstance().getGlobalSettings().favouriteColors.size() - 1 ? "" : ",");
            }
            if (!favColors.equals("")) {
                configObj.put("favoriteColors", favColors);
            }

            StringBuilder stations = new StringBuilder();
            for (Station station : CheatBreaker.getInstance().getRadioManager().getStations()) {
                if (!station.isFavourite()) continue;
                if (stations.length() != 0) {
                    stations.append(",");
                }
                stations.append(station.getName());
            }
            if (!stations.toString().equals("")) {
                configObj.put("favoriteStations", stations);
            }

            StringBuilder selectedXRayBlocks = new StringBuilder();
            for (int blocks : CheatBreaker.getInstance().getModuleManager().xray.lIllIllIlIIllIllIlIlIIlIl()) {
                if (selectedXRayBlocks.length() != 0) {
                    selectedXRayBlocks.append(",");
                }
                selectedXRayBlocks.append(blocks);
            }
            configObj.put("xrayBlocks", selectedXRayBlocks);

            StringBuilder profileIndexes = new StringBuilder();
            for (Profile profile : CheatBreaker.getInstance().profiles) {
                profileIndexes.append("[").append(profile.getName()).append(",").append(profile.getIndex()).append("]");
            }

            configObj.put("profileIndexes", profileIndexes);
            configObj.put("settings", settingsObj);

            FileWriter config = new FileWriter(file);
            config.write(this.beautifyJson(configObj));
            config.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readMutesConfig(File file) {
        try {
            String line;
            if (!file.exists()) {
                this.writeMutesConfig(file);
                return;
            }

            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    UUID playerId = UUID.fromString(line);
                    // this should be getMutedUsers(), but it was never mapped.
                    CheatBreaker.getInstance().getNetHandler().getUuidList().add(playerId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Just leave these as line readers, no use imo
    public void writeMutesConfig(File mutesConfig) {
        try {
            if (!mutesConfig.exists()) {
                mutesConfig.createNewFile();
            }

            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(mutesConfig));
            for (UUID playerId : CheatBreaker.getInstance().getNetHandler().getAnotherUuidList()) { // voice channel's users
                bufferedWriter.write(playerId.toString());
                bufferedWriter.newLine();
            }

            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // custom way of making them look pretty, you are welcome.
    private String beautifyJson(JSONObject configArray) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement configElement = JsonParser.parseString(configArray.toString());
        return gson.toJson(configElement);
    }

}
