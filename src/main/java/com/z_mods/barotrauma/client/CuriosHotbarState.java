package com.z_mods.barotrauma.client;

public final class CuriosHotbarState {
    private static int selectedGearIndex;

    private CuriosHotbarState() {
    }

    public static int getSelectedGearIndex() {
        return selectedGearIndex;
    }

    public static void clampSelectedGearIndex(int slotCount) {
        if (slotCount <= 0) {
            selectedGearIndex = 0;
            return;
        }

        if (selectedGearIndex >= slotCount) {
            selectedGearIndex = slotCount - 1;
        }
    }

    public static void cycleSelectedGearIndex(double scrollDelta, int slotCount) {
        if (slotCount <= 0 || scrollDelta == 0.0D) {
            return;
        }

        clampSelectedGearIndex(slotCount);

        int direction = scrollDelta > 0.0D ? -1 : 1;
        selectedGearIndex = Math.floorMod(selectedGearIndex + direction, slotCount);
    }
}
