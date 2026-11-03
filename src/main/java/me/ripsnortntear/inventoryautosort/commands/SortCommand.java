package me.ripsnortntear.inventoryautosort.commands;

import me.ripsnortntear.inventoryautosort.InventoryAutoSort;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SortCommand implements CommandExecutor, TabCompleter {

    private final InventoryAutoSort plugin;

    public SortCommand(InventoryAutoSort plugin) {
        this.plugin = plugin;
    }

    private void sendMessage(CommandSender sender, String message) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        sender.sendMessage(component);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("inventoryautosort.reload") && !sender.hasPermission("inventoryautosort.admin")) {
                sendMessage(sender, "&cYou don't have permission to reload InventoryAutoSort.");
                return true;
            }
            plugin.getConfigManager().load();
            sendMessage(sender, "&a[InventoryAutoSort] Configuration reloaded successfully!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cOnly players can toggle personal inventory sorting.");
            sendMessage(sender, "&7Use &e/" + label + " reload &7to reload the configuration.");
            return true;
        }

        if (!player.hasPermission("inventoryautosort.use")) {
            sendMessage(player, "&cYou don't have permission to use InventoryAutoSort.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            boolean disabled = plugin.getPreferenceManager().toggle(uuid);
            if (disabled) {
                sendMessage(player, "&cInventoryAutoSort &fdisabled. &7Use &e/" + label + " &7to re-enable.");
            } else {
                sendMessage(player, "&aInventoryAutoSort &fenabled! &7Double right-click any empty slot to sort!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            plugin.getPreferenceManager().setDisabled(uuid, false);
            sendMessage(player, "&aInventoryAutoSort &fenabled! &7Double right-click any empty slot to sort!");
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            plugin.getPreferenceManager().setDisabled(uuid, true);
            sendMessage(player, "&cInventoryAutoSort &fdisabled. &7Use &e/" + label + " on &7to re-enable.");
            return true;
        }

        sendHelp(player, label);
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sendMessage(sender, "&6=== &eInventoryAutoSort Commands &6===");
        sendMessage(sender, "&e/" + label + " &7- Toggle auto-sorting on or off");
        sendMessage(sender, "&e/" + label + " on &7- Enable auto-sorting");
        sendMessage(sender, "&e/" + label + " off &7- Disable auto-sorting");
        if (sender.hasPermission("inventoryautosort.reload") || sender.hasPermission("inventoryautosort.admin")) {
            sendMessage(sender, "&e/" + label + " reload &7- Reload configuration");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            if (sender instanceof Player) {
                options.add("toggle");
                options.add("on");
                options.add("off");
            }
            options.add("help");
            if (sender.hasPermission("inventoryautosort.reload") || sender.hasPermission("inventoryautosort.admin")) {
                options.add("reload");
            }

            for (String opt : options) {
                if (opt.startsWith(input)) {
                    completions.add(opt);
                }
            }
        }
        return completions;
    }
}
