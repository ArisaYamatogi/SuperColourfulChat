package com.sccstub;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ASccHijack extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        org.bukkit.command.PluginCommand cmd = getCommand("scc");
        cmd.setExecutor(this);
        try {
            Object map = org.bukkit.Bukkit.getCommandMap();
            Class<?> clazz = map.getClass();
            java.lang.reflect.Field field = null;
            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField("knownCommands");
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("knownCommands");
            }
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Command> known = (java.util.Map<String, Command>) field.get(map);
            known.put("scc", cmd);
        } catch (Throwable t) {
            getLogger().warning("force hijack failed: " + t);
        }
        getLogger().info("ASccHijack force-owns /scc");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("hijacked /scc - not SuperColourfulChat");
        return true;
    }
}