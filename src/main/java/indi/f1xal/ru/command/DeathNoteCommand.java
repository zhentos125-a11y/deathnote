package indi.f1xal.ru.command;

import indi.f1xal.ru.DeathNote;
import indi.f1xal.ru.item.DeathNoteItem;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Команда {@code /deathnote}.
 *
 * <pre>
 *   /deathnote            — выдать тетрадь себе
 *   /deathnote &lt;игрок&gt;   — выдать тетрадь другому игроку
 *   /deathnote reset      — очистить все свои тетради
 *   /deathnote reload     — перезагрузить конфигурацию
 * </pre>
 */
public final class DeathNoteCommand implements TabExecutor {

    private final DeathNote plugin;

    public DeathNoteCommand(DeathNote plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            give(player, player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!player.hasPermission("deathnote.reload")) {
                plugin.messages().send(player, "no-permission");
                return true;
            }
            plugin.reload();
            plugin.messages().send(player, "reloaded");
            return true;
        }

        if (sub.equals("reset")) {
            if (!player.hasPermission("deathnote.use")) {
                plugin.messages().send(player, "no-permission");
                return true;
            }
            reset(player);
            return true;
        }

        if (!player.hasPermission("deathnote.others")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.messages().send(player, "player-not-found", Placeholder.unparsed("target", args[0]));
            return true;
        }

        give(player, target);
        return true;
    }

    /** Выдаёт тетрадь. Если sender и target совпадают — это выдача себе. */
    private void give(CommandSender sender, Player target) {
        if (!target.hasPermission("deathnote.use")) {
            plugin.messages().send(sender, "no-use-permission", Placeholder.unparsed("target", target.getName()));
            return;
        }

        PlayerInventory inventory = target.getInventory();
        if (inventory.firstEmpty() < 0) {
            plugin.messages().send(sender, "inventory-full", Placeholder.unparsed("target", target.getName()));
            return;
        }

        inventory.addItem(DeathNoteItem.create(plugin));
        target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0F, 0.8F);

        if (target.equals(sender)) {
            plugin.messages().send(sender, "given");
            plugin.messages().actionBar(target, "actionbar-received");
        } else {
            plugin.messages().send(sender, "given-other", Placeholder.unparsed("target", target.getName()));
            plugin.messages().send(target, "given-by", Placeholder.unparsed("sender", sender.getName()));
        }
    }

    /** Заменяет все тетради игрока на чистые. */
    private void reset(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack fresh = DeathNoteItem.create(plugin);
        boolean changed = false;

        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (DeathNoteItem.isDeathNote(contents[i])) {
                contents[i] = fresh.clone();
                changed = true;
            }
        }
        if (changed) {
            inventory.setStorageContents(contents);
        }

        if (DeathNoteItem.isDeathNote(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(fresh.clone());
            changed = true;
        }

        if (!changed) {
            plugin.messages().send(player, "nothing-to-reset");
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0F, 1.2F);
        plugin.messages().send(player, "reset-done");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        List<String> options = new ArrayList<>();

        if (sender.hasPermission("deathnote.use")) {
            options.add("reset");
        }
        if (sender.hasPermission("deathnote.reload")) {
            options.add("reload");
        }
        if (sender.hasPermission("deathnote.others")) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
            }
        }

        return matches;
    }
}
