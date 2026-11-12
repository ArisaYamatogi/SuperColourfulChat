package com.supercolourfulchat;

import com.supercolourfulchat.compat.ViaVersionBridge;
import com.supercolourfulchat.listener.PrivateMessageListener;
import com.supercolourfulchat.listener.PublicChatListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SuperColourfulChatPlugin extends JavaPlugin implements Listener {

    private SccCommand sccCommand;
    private ViaVersionBridge viaBridge;

    @Override
    public void onEnable() {
        sccCommand = new SccCommand();
        viaBridge = new ViaVersionBridge();
        viaBridge.init();

        PluginCommand command = getCommand("scc");
        if (command != null) {
            command.setExecutor(sccCommand);
        } else {
            getLogger().warning("Command 'scc' is missing from plugin.yml; toggle unavailable.");
        }

        getServer().getPluginManager().registerEvents(new PublicChatListener(sccCommand, viaBridge), this);
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().getPlugin("Essentials") != null) {
            try {
                getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, sccCommand, viaBridge), this);
                getLogger().info("Essentials private message gradient hook registered.");
            } catch (Throwable t) {
                getLogger().warning("Failed to hook Essentials private messages: " + t);
            }
        } else {
            getLogger().info("Essentials not found; private message gradient disabled.");
        }
        getLogger().info("ViaVersion bridge " + (viaBridge.isAvailable()
                ? "active (clients below 1.16 receive legacy single color)."
                : "inactive (all clients treated as 1.16+)."));
    }

    @Override
    public void onDisable() {
        if (sccCommand != null) {
            sccCommand.clear();
        }
        if (viaBridge != null) {
            viaBridge.shutdown();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (sccCommand != null) {
            sccCommand.remove(event.getPlayer().getUniqueId());
        }
    }
}