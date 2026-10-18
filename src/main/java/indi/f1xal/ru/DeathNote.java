package indi.f1xal.ru;

import indi.f1xal.ru.command.DeathNoteCommand;
import indi.f1xal.ru.config.Messages;
import indi.f1xal.ru.item.DeathNoteItem;
import indi.f1xal.ru.listener.BookListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Тетрадь смерти.
 *
 * <p>Игрок получает особую книгу командой {@code /deathnote}, вписывает в неё
 * ник жертвы и нажимает «Готово» — жертва мгновенно погибает, а тетрадь
 * очищается и готова к следующей записи.
 */
public final class DeathNote extends JavaPlugin {

    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        DeathNoteItem.init(this);
        reload();

        DeathNoteCommand command = new DeathNoteCommand(this);
        PluginCommand pluginCommand = getCommand("deathnote");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Команда deathnote не найдена в plugin.yml — плагин работает не полностью.");
        }

        getServer().getPluginManager().registerEvents(new BookListener(this), this);

        getLogger().info("Плагин DeathNote включён.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Плагин DeathNote выключен.");
    }

    /** Перечитывает config.yml и сообщения. */
    public void reload() {
        reloadConfig();
        if (messages == null) {
            messages = new Messages(this);
        } else {
            messages.reload();
        }
    }

    public Messages messages() {
        return messages;
    }
}
