package indi.f1xal.ru.item;

import indi.f1xal.ru.DeathNote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Создание и распознавание тетради смерти.
 *
 * <p>Тетрадь помечается ключом в {@link org.bukkit.persistence.PersistentDataContainer},
 * поэтому обычные книги и книги, переименованные на наковальне, плагином не считаются.
 */
public final class DeathNoteItem {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static NamespacedKey marker;

    private DeathNoteItem() {
    }

    public static void init(DeathNote plugin) {
        marker = new NamespacedKey(plugin, "death_note");
    }

    /** Чистая тетрадь смерти — с первого момента пустая, без подсказок. */
    public static ItemStack create(DeathNote plugin) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(deserialize(plugin.getConfig().getString("item.display-name", "<red>Тетрадь смерти")));
        meta.lore(describe(plugin.getConfig().getStringList("item.lore")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE);

        if (meta instanceof BookMeta) {
            BookMeta book = (BookMeta) meta;
            book.setAuthor(plugin.getConfig().getString("item.author", ""));
            book.setPages("");
        }

        item.setItemMeta(meta);
        return item;
    }

    private static Component deserialize(String raw) {
        return MINI_MESSAGE.deserialize(raw, TagResolver.empty());
    }

    private static List<Component> describe(List<String> raw) {
        List<Component> lore = new ArrayList<>(raw.size());
        for (String line : raw) {
            lore.add(deserialize(line));
        }
        return lore;
    }

    /** Является ли предмет тетрадью смерти. */
    public static boolean isDeathNote(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material type = item.getType();
        if (type != Material.WRITABLE_BOOK && type != Material.WRITTEN_BOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || marker == null) {
            return false;
        }

        Byte value = meta.getPersistentDataContainer().get(marker, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}
