package indi.f1xal.ru.listener;

import indi.f1xal.ru.DeathNote;
import indi.f1xal.ru.item.DeathNoteItem;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ловит момент, когда игрок нажимает «Готово» (подписывает книгу),
 * и превращает записанный ник в мгновенную смерть жертвы.
 */
public final class BookListener implements Listener {

    private static final Pattern COLOR_CODES = Pattern.compile("[§&][0-9A-Fa-fK-ORk-or]");
    private static final Pattern JUNK = Pattern.compile("[^A-Za-z0-9_]");

    private final DeathNote plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public BookListener(DeathNote plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        // Событие срабатывает и при наборе текста, и при подписи.
        // Нас интересует только подпись — это и есть кнопка «Готово».
        if (!event.isSigning()) {
            return;
        }

        Player writer = event.getPlayer();
        if (!writer.hasPermission("deathnote.use")) {
            return;
        }

        // Слот из пакета — основной источник: по нему же будем
        // чинить предмет, даже если подпись всё-таки прошла.
        int slot = resolveSlot(writer, event.getSlot());
        if (slot < 0) {
            return;
        }

        BookMeta meta = event.getNewBookMeta();
        List<String> pages = meta.getPages() == null ? Collections.emptyList() : meta.getPages();

        String wanted = extractName(pages);
        Player victim = wanted == null ? null : findPlayer(wanted);

        boolean allowSelfKill = plugin.getConfig().getBoolean("settings.allow-self-kill", false);
        String immunePermission = plugin.getConfig().getString("settings.immune-permission", "deathnote.immune");
        boolean immune = immunePermission != null && !immunePermission.isEmpty() && victim != null
                && victim.hasPermission(immunePermission);

        boolean safe = victim != null && (victim.getGameMode() != GameMode.CREATIVE
                && victim.getGameMode() != GameMode.SPECTATOR && !victim.isInvulnerable());

        boolean canKill = victim != null
                && (allowSelfKill || !victim.equals(writer))
                && !immune
                && safe
                && !onCooldown(writer);

        // Подпись отменяем: игрок получит чистую тетрадь вместо «записанной» книги.
        if (canKill || plugin.getConfig().getBoolean("settings.always-clear", true)) {
            event.setCancelled(true);
            refresh(writer, slot);
        }

        if (wanted == null) {
            plugin.messages().send(writer, "empty");
            plugin.messages().actionBar(writer, "actionbar-empty");
            return;
        }
        if (victim == null) {
            plugin.messages().send(writer, "target-not-found", Placeholder.unparsed("target", wanted));
            return;
        }
        if (!allowSelfKill && victim.equals(writer)) {
            plugin.messages().send(writer, "self-kill");
            return;
        }
        if (immune) {
            plugin.messages().send(writer, "immune", Placeholder.unparsed("target", victim.getName()));
            return;
        }
        if (!safe) {
            plugin.messages().send(writer, "target-safe", Placeholder.unparsed("target", victim.getName()));
            return;
        }
        if (onCooldown(writer)) {
            plugin.messages().send(writer, "cooldown", Placeholder.unparsed("time", cooldownLeft(writer)));
            return;
        }

        consumeCooldown(writer);
        kill(writer, victim);
    }

    // ------------------------------------------------------------------
    //  Убийство
    // ------------------------------------------------------------------

    private void kill(Player writer, Player victim) {
        boolean showKiller = plugin.getConfig().getBoolean("settings.show-killer", true)
                && !writer.equals(victim);

        // damage() нужен ради сообщения о смерти («убит игроком X»).
        // Но его могут отменить другие плагины, поэтому следом идёт
        // гарантированный добивающий удар — как в самой тетради.
        if (showKiller) {
            victim.damage(Double.MAX_VALUE, writer);
        }

        if (!victim.isDead()) {
            victim.setHealth(0.0D);
        }

        plugin.messages().send(writer, "success", Placeholder.unparsed("target", victim.getName()));
        plugin.messages().actionBar(writer, "actionbar-success", Placeholder.unparsed("target", victim.getName()));
        writer.playSound(writer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.2F);

        if (plugin.getConfig().getBoolean("settings.victim-message", true)) {
            plugin.messages().send(victim, "victim-message", Placeholder.unparsed("killer", writer.getName()));
        }
    }

    // ------------------------------------------------------------------
    //  Работа с книгой
    // ------------------------------------------------------------------

    /**
     * Тетрадь ли это по слоту из пакета?
     * Слот — основной источник, но {@code getSlot()} помечен на удаление,
     * поэтому при сбое ищем единственную тетрадь в инвентаре.
     *
     * <p>Именно «единственную»: старый вариант искал тетрадь «с текстом»
     * и цеплялся за любую свежую, из-за чего убивал не того.
     */
    private int resolveSlot(Player player, int slot) {
        PlayerInventory inventory = player.getInventory();

        if (slot >= 0 && slot < inventory.getSize()) {
            ItemStack direct = slot == 40
                    ? inventory.getItemInOffHand()
                    : inventory.getItem(slot);

            if (DeathNoteItem.isDeathNote(direct)) {
                return slot;
            }
        }

        // Запасной путь: слот недостоверен, но тетрадь в инвентаре одна.
        int found = -1;
        for (int i = 0; i < inventory.getStorageContents().length; i++) {
            if (DeathNoteItem.isDeathNote(inventory.getItem(i))) {
                if (found != -1) {
                    return -1;
                }
                found = i;
            }
        }

        if (found != -1) {
            return found;
        }

        return DeathNoteItem.isDeathNote(inventory.getItemInOffHand()) ? 40 : -1;
    }

    /**
     * Кладёт чистую тетрадь в тот же слот, который игрок подписал.
     * Опираемся на позицию, а не на метку предмета: если подпись всё-таки
     * прошла, сервер заменит книгу на WRITTEN_BOOK и метка потеряется.
     */
    private void refresh(Player player, int slot) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inventory = player.getInventory();
            if (slot < 0 || slot >= inventory.getSize()) {
                return;
            }

            ItemStack fresh = DeathNoteItem.create(plugin);

            if (slot == 40) {
                inventory.setItemInOffHand(fresh);
                return;
            }

            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                return;
            }

            // Слот мог занять другой предмет — тогда не трогаем инвентарь.
            if (!DeathNoteItem.isDeathNote(current) && current.getType() != Material.WRITTEN_BOOK) {
                return;
            }

            inventory.setItem(slot, fresh);
        });
    }

    // ------------------------------------------------------------------
    //  Разбор записанного ника
    // ------------------------------------------------------------------

    /**
     * Достаёт ник из страниц книги. Поддерживает варианты вроде
     * {@code Steve}, {@code Ник: Steve}, {@code "Steve"} и подчёркивания.
     *
     * @return ник или {@code null}, если ничего не вписано
     */
    private String extractName(List<String> pages) {
        List<String> lines = new ArrayList<>();

        for (String page : pages) {
            if (page == null) {
                continue;
            }

            for (String line : page.split("\n")) {
                String clean = clean(line);
                if (!clean.isEmpty() && !lines.contains(clean)) {
                    lines.add(clean);
                }
            }
        }

        // Приоритет: точное совпадение с ником игрока в сети.
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (String line : lines) {
                if (line.equalsIgnoreCase(online.getName())) {
                    return online.getName();
                }
            }
        }

        return lines.isEmpty() ? null : lines.get(0);
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }

        String value = COLOR_CODES.matcher(raw).replaceAll("");
        value = value.replace('"', ' ').replace('\'', ' ').replace('«', ' ').replace('»', ' ');

        // «Ник: Steve» — берём то, что после двоеточия.
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }

        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0) {
            return "";
        }

        return JUNK.matcher(parts[0]).replaceAll("");
    }

    private Player findPlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return online;
            }
        }

        return null;
    }

    // ------------------------------------------------------------------
    //  Кулдаун
    // ------------------------------------------------------------------

    private boolean onCooldown(Player player) {
        long cooldown = plugin.getConfig().getLong("settings.cooldown", 0L);
        if (cooldown <= 0) {
            return false;
        }

        Long last = lastUse.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldown * 1000L;
    }

    private String cooldownLeft(Player player) {
        long cooldown = plugin.getConfig().getLong("settings.cooldown", 0L) * 1000L;
        Long last = lastUse.get(player.getUniqueId());
        if (last == null) {
            return "0.0";
        }

        double left = (cooldown - (System.currentTimeMillis() - last)) / 1000.0D;
        return String.format(Locale.ROOT, "%.1f", Math.max(left, 0.0D));
    }

    private void consumeCooldown(Player player) {
        lastUse.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** Тетрадь не должна выпадать на землю — иначе её поднимет кто угодно. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("settings.drop-on-death", false)) {
            event.getDrops().removeIf(DeathNoteItem::isDeathNote);
        }
    }
}
