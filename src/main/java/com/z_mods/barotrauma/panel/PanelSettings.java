package com.z_mods.barotrauma.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Server-owned settings edited by the multiblock control panel. */
public final class PanelSettings {
    public static final int PHOTO_SLOTS = 12;
    public static final List<String> SUBMARINES = List.of(
            "Азимут", "Барсук", "Бериллия", "Верблюд", "Дюгонь", "Хемуль",
            "Гейра", "Горбун", "Кастрюля", "Косатка", "Косатка-2", "R-29 «Фура»");
    public static final List<String> MISSIONS = List.of(
            "Вражеская подлодка", "Гнездо", "Груз", "Добыча из обломков",
            "Добыча из пещеры", "Добыча из руин", "Добыча минералов",
            "Зачистка инопланетных руин", "Ликвидация на заброшенном аванпосте",
            "Маяк", "Монстры заброшенного аванпоста", "Побег из тюрьмы",
            "Разрушить заброшенный аванпост", "Сканирование инопланетных руин",
            "Сопровождение", "Спасение заброшенного аванпоста", "Уничтожить таламуса", "Чудовище");

    public int gameMode = 1;
    public int submarine = 4;
    public final boolean[] missionEnabled = new boolean[MISSIONS.size()];
    public final String[] photoNames = new String[PHOTO_SLOTS];
    public String levelSeed = "Europa";
    public String saveName = "Новая кампания";
    public int naturalZone;
    public int difficulty = 1;
    public int botCount;
    public int botMode;
    public int betrayalChance;
    public int maxDanger;
    public int minimumPlayers = 1;
    public int respawnMode;
    public boolean respawnShuttle = true;
    public int respawnInterval = 10;
    public int respawnThreshold = 100;
    public int respawnWindow = 10;
    public int skillLossDeath;
    public int skillLossImmediate;
    public int replacementCost = 100;
    public boolean botControl = true;
    public boolean ironMan;
    public int startingSupplies = 1;
    public int startingBalance = 1;
    public int maxMissionsPerRound = 2;
    public boolean radiation;
    public boolean autoRestart;

    public PanelSettings() {
        Arrays.fill(missionEnabled, true);
        for (int i = 0; i < photoNames.length; i++) {
            photoNames[i] = "Подлодка " + (i + 1);
        }
    }

    public PanelSettings copy() {
        return fromTag(toTag());
    }

    public void sanitize() {
        gameMode = Mth.clamp(gameMode, 0, 3);
        submarine = Mth.clamp(submarine, 0, SUBMARINES.size() - 1);
        naturalZone = Mth.clamp(naturalZone, 0, 4);
        difficulty = Mth.clamp(difficulty, 0, 100);
        botCount = Mth.clamp(botCount, 0, 16);
        botMode = Mth.clamp(botMode, 0, 2);
        betrayalChance = Mth.clamp(betrayalChance, 0, 100);
        maxDanger = Mth.clamp(maxDanger, 0, 3);
        minimumPlayers = Mth.clamp(minimumPlayers, 1, 32);
        respawnMode = Mth.clamp(respawnMode, 0, 2);
        respawnInterval = Mth.clamp(respawnInterval, 10, 300);
        respawnThreshold = Mth.clamp(respawnThreshold, 10, 100);
        respawnWindow = Mth.clamp(respawnWindow, 1, 30);
        skillLossDeath = Mth.clamp(skillLossDeath, 0, 100);
        skillLossImmediate = Mth.clamp(skillLossImmediate, 0, 100);
        replacementCost = Mth.clamp(replacementCost, 0, 100);
        startingSupplies = Mth.clamp(startingSupplies, 0, 2);
        startingBalance = Mth.clamp(startingBalance, 0, 2);
        maxMissionsPerRound = Mth.clamp(maxMissionsPerRound, 1, 10);
        levelSeed = clean(levelSeed, "Europa", 32);
        saveName = clean(saveName, "Новая кампания", 40);
        for (int i = 0; i < photoNames.length; i++) {
            photoNames[i] = clean(photoNames[i], "Подлодка " + (i + 1), 40);
        }
    }

    private static String clean(String value, String fallback, int maximum) {
        if (value == null) return fallback;
        String result = value.strip().replaceAll("[\\p{Cntrl}]", "");
        if (result.isEmpty()) return fallback;
        return result.substring(0, Math.min(maximum, result.length()));
    }

    public CompoundTag toTag() {
        sanitize();
        CompoundTag tag = new CompoundTag();
        tag.putInt("GameMode", gameMode);
        tag.putInt("Submarine", submarine);
        tag.putString("LevelSeed", levelSeed);
        tag.putString("SaveName", saveName);
        tag.putInt("NaturalZone", naturalZone);
        tag.putInt("Difficulty", difficulty);
        tag.putInt("BotCount", botCount);
        tag.putInt("BotMode", botMode);
        tag.putInt("BetrayalChance", betrayalChance);
        tag.putInt("MaxDanger", maxDanger);
        tag.putInt("MinimumPlayers", minimumPlayers);
        tag.putInt("RespawnMode", respawnMode);
        tag.putBoolean("RespawnShuttle", respawnShuttle);
        tag.putInt("RespawnInterval", respawnInterval);
        tag.putInt("RespawnThreshold", respawnThreshold);
        tag.putInt("RespawnWindow", respawnWindow);
        tag.putInt("SkillLossDeath", skillLossDeath);
        tag.putInt("SkillLossImmediate", skillLossImmediate);
        tag.putInt("ReplacementCost", replacementCost);
        tag.putBoolean("BotControl", botControl);
        tag.putBoolean("IronMan", ironMan);
        tag.putInt("StartingSupplies", startingSupplies);
        tag.putInt("StartingBalance", startingBalance);
        tag.putInt("MaxMissions", maxMissionsPerRound);
        tag.putBoolean("Radiation", radiation);
        tag.putBoolean("AutoRestart", autoRestart);
        tag.put("Missions", writeBooleans(missionEnabled));
        ListTag names = new ListTag();
        for (String name : photoNames) names.add(StringTag.valueOf(name));
        tag.put("PhotoNames", names);
        return tag;
    }

    private static ListTag writeBooleans(boolean[] values) {
        ListTag list = new ListTag();
        for (boolean value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putBoolean("Value", value);
            list.add(entry);
        }
        return list;
    }

    public static PanelSettings fromTag(CompoundTag tag) {
        PanelSettings value = new PanelSettings();
        if (tag == null || tag.isEmpty()) return value;
        value.gameMode = tag.getInt("GameMode");
        value.submarine = tag.getInt("Submarine");
        value.levelSeed = tag.getString("LevelSeed");
        value.saveName = tag.getString("SaveName");
        value.naturalZone = tag.getInt("NaturalZone");
        value.difficulty = tag.getInt("Difficulty");
        value.botCount = tag.getInt("BotCount");
        value.botMode = tag.getInt("BotMode");
        value.betrayalChance = tag.getInt("BetrayalChance");
        value.maxDanger = tag.getInt("MaxDanger");
        value.minimumPlayers = tag.getInt("MinimumPlayers");
        value.respawnMode = tag.getInt("RespawnMode");
        value.respawnShuttle = tag.getBoolean("RespawnShuttle");
        value.respawnInterval = tag.getInt("RespawnInterval");
        value.respawnThreshold = tag.getInt("RespawnThreshold");
        value.respawnWindow = tag.getInt("RespawnWindow");
        value.skillLossDeath = tag.getInt("SkillLossDeath");
        value.skillLossImmediate = tag.getInt("SkillLossImmediate");
        value.replacementCost = tag.getInt("ReplacementCost");
        value.botControl = tag.getBoolean("BotControl");
        value.ironMan = tag.getBoolean("IronMan");
        value.startingSupplies = tag.getInt("StartingSupplies");
        value.startingBalance = tag.getInt("StartingBalance");
        value.maxMissionsPerRound = tag.getInt("MaxMissions");
        value.radiation = tag.getBoolean("Radiation");
        value.autoRestart = tag.getBoolean("AutoRestart");
        ListTag missions = tag.getList("Missions", 10);
        for (int i = 0; i < value.missionEnabled.length && i < missions.size(); i++) {
            value.missionEnabled[i] = missions.getCompound(i).getBoolean("Value");
        }
        ListTag names = tag.getList("PhotoNames", 8);
        for (int i = 0; i < value.photoNames.length && i < names.size(); i++) {
            value.photoNames[i] = names.getString(i);
        }
        value.sanitize();
        return value;
    }

    public List<String> selectedMissions() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < missionEnabled.length; i++) {
            if (missionEnabled[i]) result.add(MISSIONS.get(i));
        }
        return result;
    }
}
