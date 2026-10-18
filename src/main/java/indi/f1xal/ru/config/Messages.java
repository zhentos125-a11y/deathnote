package indi.f1xal.ru.config;

import indi.f1xal.ru.DeathNote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит сообщения из config.yml и умеет превращать их в Adventure-компоненты.
 * Форматирование — MiniMessage.
 */
public final class Messages {

    private static final String ROOT = "messages.";

    private final DeathNote plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final List<String> prefix = new ArrayList<>();
    private List<String> noPrefix = new ArrayList<>();

    public Messages(DeathNote plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix.clear();
        prefix.addAll(plugin.getConfig().getStringList("prefix"));

        noPrefix = new ArrayList<>();
        for (String path : plugin.getConfig().getStringList("no-prefix")) {
            // В конфиге префикс указывается коротко ("actionbar-success"),
            // а getString ищет по полному пути ("messages.actionbar-success").
            noPrefix.add(strip(path));
        }
    }

    private static String strip(String path) {
        return path.startsWith(ROOT) ? path.substring(ROOT.length()) : path;
    }

    private static String resolve(String path) {
        return path.startsWith(ROOT) ? path : ROOT + path;
    }

    /** Сообщение без префикса-по умолчанию. */
    public Component get(String path) {
        return get(path, TagResolver.empty());
    }

    public Component get(String path, TagResolver resolver) {
        String raw = plugin.getConfig().getString(resolve(path));
        if (raw == null) {
            return Component.text("[DeathNote] Неизвестное сообщение: " + path);
        }

        if (prefix.isEmpty() || noPrefix.contains(strip(path))) {
            return miniMessage.deserialize(raw, resolver);
        }

        StringBuilder builder = new StringBuilder();
        for (String line : prefix) {
            builder.append(line).append('\n');
        }
        return miniMessage.deserialize(builder.append(raw).toString(), resolver);
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public void send(CommandSender sender, String path, TagResolver resolver) {
        sender.sendMessage(get(path, resolver));
    }

    /** Сообщение над полосой быстрого доступа — префикс там не нужен. */
    public void actionBar(Player player, String path) {
        player.sendActionBar(get(path));
    }

    public void actionBar(Player player, String path, TagResolver resolver) {
        player.sendActionBar(get(path, resolver));
    }
}
