package com.z_mods.barotrauma.navigation;

import java.util.Arrays;
import java.util.Locale;

/**
 * Navigation target and mission categories based on the current official
 * Barotrauma mission-class catalogue. Actual location and mission names are
 * world data: generated names such as Conamara are never hardcoded here.
 *
 * Modern Barotrauma permits arbitrary mission type identifiers, therefore the
 * CUSTOM fallback is intentionally preserved for maps, datapacks and future
 * official additions.
 */
public final class NavigationReferenceData {
    private NavigationReferenceData() {
    }

    public enum TargetType {
        OUTPOST("Аванпост"),
        STATION("Станция"),
        ABANDONED_OUTPOST("Заброшенный аванпост"),
        HABITATION_OUTPOST("Жилой аванпост"),
        MILITARY_OUTPOST("Военный аванпост"),
        RESEARCH_OUTPOST("Исследовательский аванпост"),
        MINING_OUTPOST("Шахтёрский аванпост"),
        COLONY("Колония"),
        BEACON_STATION("Маяковая станция"),
        WRECK("Затонувшая подлодка"),
        ALIEN_RUINS("Инопланетные руины"),
        HUNTING_GROUNDS("Охотничьи угодья"),
        CAVE("Пещера"),
        MINING_SITE("Старательский участок"),
        MISSION_TARGET("Цель миссии"),
        START("Начальная станция"),
        END("Конечная станция"),
        CUSTOM("Пользовательская метка");

        private final String russianName;

        TargetType(String russianName) {
            this.russianName = russianName;
        }

        public String russianName() {
            return russianName;
        }

        public static TargetType parse(String raw) {
            if (raw == null || raw.isBlank()) return CUSTOM;
            String normalized = normalize(raw);
            return Arrays.stream(values())
                    .filter(value -> value.name().equals(normalized))
                    .findFirst()
                    .orElse(CUSTOM);
        }
    }

    /**
     * Built-in official mission classes plus the more specific labels used by
     * mission definitions and map makers. CUSTOM accepts any future identifier.
     */
    public enum MissionType {
        SALVAGE("Поиск и спасение имущества"),
        MONSTER("Охота на монстра"),
        CARGO("Доставка груза"),
        BEACON("Активация маяка"),
        NEST("Уничтожение гнезда"),
        MINERAL("Добыча ресурсов"),
        ABANDONED_OUTPOST("Задание на заброшенном аванпосте"),
        ESCORT("Сопровождение и перевозка персонала"),
        PIRATE("Бой с пиратами"),
        GO_TO("Следование к цели"),
        SCAN_ALIEN_RUINS("Сканирование инопланетных руин"),
        ELIMINATE_TARGETS("Ликвидация целей"),
        END("Финальная миссия"),
        COMBAT("Бой"),

        ABANDONED_OUTPOST_ASSASSINATION("Ликвидация на заброшенном аванпосте"),
        ABANDONED_OUTPOST_MONSTERS("Зачистка заброшенного аванпоста"),
        ABANDONED_OUTPOST_RESCUE("Спасение на заброшенном аванпосте"),
        JAILBREAK("Побег из тюрьмы"),
        MINING("Добыча ресурсов"),
        SALVAGE_CAVE("Поиск в пещере"),
        SALVAGE_RUIN("Поиск в руинах"),
        SALVAGE_WRECK("Поиск на затонувшей подлодке"),
        TIME_TRIAL("Испытание на время"),
        HUNTING_GROUNDS("Охотничьи угодья"),
        BEACON_DEATHMATCH("Бой за маяковую станцию"),
        OUTPOST_DEATHMATCH("Бой за аванпост"),
        CUSTOM("Пользовательская миссия");

        private final String russianName;

        MissionType(String russianName) {
            this.russianName = russianName;
        }

        public String russianName() {
            return russianName;
        }

        public static MissionType parse(String raw) {
            if (raw == null || raw.isBlank()) return CUSTOM;
            String normalized = normalize(raw);
            return Arrays.stream(values())
                    .filter(value -> value.name().equals(normalized))
                    .findFirst()
                    .orElse(CUSTOM);
        }
    }

    private static String normalize(String raw) {
        return raw.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
