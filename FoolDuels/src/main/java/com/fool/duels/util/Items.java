package com.fool.duels.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class Items {
    private Items() {}

    public static ItemStack make(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item(name));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String line : lore) l.add(Msg.item(line));
            meta.lore(l);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack head(OfflinePlayer owner, String name, String... lore) {
        ItemStack item = make(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        item.setItemMeta(meta);
        return item;
    }

    /** Full-fidelity serialization (keeps all components, custom plugin data, etc.). Empty slots become "". */
    public static String encode(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return "";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack decode(String data) {
        if (data == null || data.isEmpty()) return null;
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    public static List<String> encodeAll(ItemStack[] items) {
        List<String> out = new ArrayList<>(items.length);
        for (ItemStack i : items) out.add(encode(i));
        return out;
    }

    public static ItemStack[] decodeAll(List<String> data, int size) {
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < Math.min(size, data.size()); i++) out[i] = decode(data.get(i));
        return out;
    }
}
