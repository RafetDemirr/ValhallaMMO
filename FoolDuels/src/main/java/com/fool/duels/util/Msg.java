package com.fool.duels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.List;

/**
 * Reads messages from the "messages" section of config.yml and sends them as MiniMessage.
 * Placeholders are passed as key/value pairs: Msg.send(p, "request-sent", "player", name).
 */
public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static FileConfiguration config;

    private Msg() {}

    public static void load(FileConfiguration cfg) {
        config = cfg;
    }

    public static String raw(String key, Object... placeholders) {
        String s = config.getString("messages." + key, "<red>missing message: " + key);
        return apply(s, placeholders);
    }

    public static List<String> rawList(String key, Object... placeholders) {
        return config.getStringList("messages." + key).stream().map(s -> apply(s, placeholders)).toList();
    }

    private static String apply(String s, Object... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", escape(String.valueOf(placeholders[i + 1])));
        }
        return s;
    }

    /** Placeholder values come from player names / numbers; strip tag openers so they can't inject formatting. */
    private static String escape(String value) {
        return value.replace("<", "").replace(">", "");
    }

    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** Parses with italics disabled, for item names and lore. */
    public static Component item(String miniMessage) {
        return MM.deserialize("<!italic>" + miniMessage);
    }

    public static Component get(String key, Object... placeholders) {
        return MM.deserialize(raw(key, placeholders));
    }

    public static void send(CommandSender to, String key, Object... placeholders) {
        to.sendMessage(MM.deserialize(raw("prefix") + raw(key, placeholders)));
    }

    public static void sendList(CommandSender to, String key, Object... placeholders) {
        for (String line : rawList(key, placeholders)) to.sendMessage(MM.deserialize(line));
    }

    public static void sendAll(Collection<? extends CommandSender> to, String key, Object... placeholders) {
        Component c = MM.deserialize(raw("prefix") + raw(key, placeholders));
        for (CommandSender s : to) s.sendMessage(c);
    }

    public static void broadcast(String key, Object... placeholders) {
        Bukkit.broadcast(MM.deserialize(raw("prefix") + raw(key, placeholders)));
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }
}
