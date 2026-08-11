package com.z_mods.barotrauma.client;

import com.z_mods.barotrauma.hotbar.ExtraHotbar;
import com.z_mods.barotrauma.network.ModNetworking;
import com.z_mods.barotrauma.network.SlotBindingPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable registry-backed binding editor for extra hotbar slots. */
public final class SlotBindingConfiguratorScreen extends Screen {
    private final String[] bindings = new String[ExtraHotbar.MAX_EXTRA];
    private final List<Entry> allEntries = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private EditBox search;
    private int selectedSlot;
    private int scroll;
    private int listX;
    private int listY;
    private int listW;
    private int visibleRows;

    public SlotBindingConfiguratorScreen(String[] initialBindings) {
        super(Component.literal("Настройщик привязки слотов"));
        if (initialBindings != null) {
            for (int i = 0; i < Math.min(bindings.length, initialBindings.length); i++) {
                bindings[i] = initialBindings[i] == null ? "" : initialBindings[i];
            }
        }
        for (int i = 0; i < bindings.length; i++) if (bindings[i] == null) bindings[i] = "";

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || item == Items.AIR) continue;
            ItemStack stack = item.getDefaultInstance();
            String name = stack.getHoverName().getString();
            boolean block = item instanceof BlockItem;
            allEntries.add(new Entry(id.toString(), name, stack, block));
        }
        allEntries.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT))
                .thenComparing(e -> e.id));
        filtered.addAll(allEntries);
    }

    @Override
    protected void init() {
        int panelW = Math.min(620, width - 30);
        int panelX = (width - panelW) / 2;
        listX = panelX + 16;
        listY = 76;
        listW = panelW - 32;
        visibleRows = Math.max(5, (height - listY - 48) / 22);

        search = new EditBox(font, listX, 48, listW, 20, Component.literal("Поиск"));
        search.setHint(Component.literal("Поиск по названию или registry id..."));
        search.setResponder(this::rebuild);
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.literal("◀ слот"), b -> changeSlot(-1))
                .bounds(panelX + 16, 18, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("слот ▶"), b -> changeSlot(1))
                .bounds(panelX + 86, 18, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Очистить привязку"), b -> clearBinding())
                .bounds(panelX + panelW - 170, 18, 154, 20).build());
    }

    private void changeSlot(int delta) {
        int count = Math.max(1, ExtraHotbar.getClientAppliedCount());
        selectedSlot = Math.floorMod(selectedSlot + delta, count);
    }

    private void clearBinding() {
        bindings[selectedSlot] = "";
        ModNetworking.CHANNEL.sendToServer(new SlotBindingPackets.ServerboundSetSlotBinding(selectedSlot, ""));
    }

    private void rebuild(String value) {
        String q = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        if (q.isEmpty()) filtered.addAll(allEntries);
        else {
            for (Entry entry : allEntries) {
                if (entry.id.toLowerCase(Locale.ROOT).contains(q)
                        || entry.name.toLowerCase(Locale.ROOT).contains(q)
                        || (entry.block && (q.equals("блок") || q.equals("block")))) {
                    filtered.add(entry);
                }
            }
        }
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int panelW = listW + 32;
        int panelX = listX - 16;
        g.fill(panelX, 8, panelX + panelW, height - 12, 0xE8101715);
        g.renderOutline(panelX, 8, panelW, height - 20, 0xFF5F8F83);

        String binding = bindings[selectedSlot];
        String current = binding.isBlank() ? "не назначен" : displayName(binding);
        g.drawCenteredString(font, "ПРИВЯЗКА ПРЕДМЕТОВ / БЛОКОВ К ДОП. СЛОТАМ", width / 2, 21, 0xFFF1E6B7);
        g.drawString(font, "Слот " + (10 + selectedSlot) + ": " + current, listX + 145, 24, 0xFF7ED6BD, false);

        int maxScroll = Math.max(0, filtered.size() - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        int end = Math.min(filtered.size(), scroll + visibleRows);
        for (int row = scroll; row < end; row++) {
            Entry entry = filtered.get(row);
            int y = listY + (row - scroll) * 22;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + 20;
            boolean selected = entry.id.equals(binding);
            g.fill(listX, y, listX + listW, y + 20, selected ? 0xCC245B4C : hover ? 0xCC263631 : 0xAA151D1B);
            g.renderOutline(listX, y, listW, 20, selected ? 0xFF84E1C5 : 0xFF405A53);
            g.renderItem(entry.stack, listX + 2, y + 2);
            g.drawString(font, entry.block ? "[БЛОК]" : "[ПРЕДМЕТ]", listX + 23, y + 6,
                    entry.block ? 0xFF9FC4FF : 0xFFFFD99A, false);
            String line = entry.name + "  (" + entry.id + ")";
            g.drawString(font, line, listX + 82, y + 6, 0xFFE7EEE9, false);
        }

        g.drawString(font, "ПКМ с привязанным предметом/блоком в руке → сразу переместить в назначенный слот.",
                listX, height - 27, 0xFFB9C8C3, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String displayName(String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return idText;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return idText;
        return item.getDefaultInstance().getHoverName().getString() + " (" + idText + ")";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY) return false;
        int rowOnScreen = (int)((mouseY - listY) / 22.0D);
        if (rowOnScreen < 0 || rowOnScreen >= visibleRows) return false;
        int index = scroll + rowOnScreen;
        if (index < 0 || index >= filtered.size()) return false;
        Entry entry = filtered.get(index);
        bindings[selectedSlot] = entry.id;
        ModNetworking.CHANNEL.sendToServer(new SlotBindingPackets.ServerboundSetSlotBinding(selectedSlot, entry.id));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < height - 38) {
            int maxScroll = Math.max(0, filtered.size() - visibleRows);
            scroll = Math.max(0, Math.min(maxScroll, scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    private record Entry(String id, String name, ItemStack stack, boolean block) {}
}
