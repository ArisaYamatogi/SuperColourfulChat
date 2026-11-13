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
    private org.bukkit.command.Command previousSccCommand;
    private boolean sccReclaimed;

    @Override
    public void onEnable() {
        sccCommand = new SccCommand();
        viaBridge = new ViaVersionBridge();
        viaBridge.init();

        PluginCommand command = getCommand("scc");
        if (command != null) {
            command.setExecutor(sccCommand);
            reclaimSccCommand(command);
            // 其他插件可能在自己的 onEnable（晚于我们）里强占 /scc，全部插件启用后再查一次。
            getServer().getScheduler().runTask(this, () -> reclaimSccCommand(command));
        } else {
            getLogger().warning("Command 'scc' is missing from plugin.yml; toggle unavailable.");
        }

        PublicChatListener publicChatListener = new PublicChatListener(sccCommand, viaBridge);
        getServer().getPluginManager().registerEvents(publicChatListener, this);
        registerModernChatListener(publicChatListener);
        getServer().getPluginManager().registerEvents(this, this);

        if (getServer().getPluginManager().getPlugin("Essentials") != null) {
            try {
                getServer().getPluginManager().registerEvents(new PrivateMessageListener(this, sccCommand, viaBridge), this);
            } catch (Throwable t) {
                getLogger().warning("Failed to hook Essentials private messages: " + t);
            }
        }
    }

    @Override
    public void onDisable() {
        if (sccCommand != null) {
            sccCommand.clear();
        }
        if (viaBridge != null) {
            viaBridge.shutdown();
        }
        restoreSccCommand();
    }

    // 其他插件先注册 /scc 时 Bukkit 会把我们的命令挤到 supercolourfulchat:scc，导致玩家 /scc 拨不动开关。
    // 这里检测并夺回 /scc，禁用插件时还原原命令。
    private void reclaimSccCommand(PluginCommand ours) {
        try {
            org.bukkit.command.CommandMap commandMap = commandMap();
            if (commandMap == null) {
                return;
            }
            org.bukkit.command.Command existing = commandMap.getCommand("scc");
            if (existing == null || existing == ours) {
                return;
            }
            String owner = existing instanceof PluginCommand
                    ? ((PluginCommand) existing).getPlugin().getName() : existing.getClass().getName();
            getLogger().warning("/scc command owner at enable: '" + owner + "' (conflict)");
            java.util.Map<String, org.bukkit.command.Command> known = knownCommands(commandMap);
            if (known == null) {
                getLogger().warning("/scc is owned by '" + owner + "' and could not be reclaimed; use /supercolourfulchat:scc instead.");
                return;
            }
            previousSccCommand = known.put("scc", ours);
            sccReclaimed = true;
            getLogger().warning("/scc was owned by '" + owner + "'; reclaimed for SuperColourfulChat (restored on disable).");
        } catch (Throwable t) {
            getLogger().warning("Could not inspect /scc command ownership: " + t);
        }
    }

    private void restoreSccCommand() {
        if (!sccReclaimed) {
            return;
        }
        try {
            org.bukkit.command.CommandMap commandMap = commandMap();
            if (commandMap == null) {
                return;
            }
            java.util.Map<String, org.bukkit.command.Command> known = knownCommands(commandMap);
            if (known != null) {
                if (previousSccCommand != null) {
                    known.put("scc", previousSccCommand);
                } else {
                    known.remove("scc");
                }
            }
        } catch (Throwable t) {
            getLogger().warning("Could not restore previous /scc command: " + t);
        }
    }

    // Server.getCommandMap() 在旧 API 上不存在，CraftServer 一直有该公开方法，反射获取以兼容 1.16。
    private org.bukkit.command.CommandMap commandMap() {
        try {
            Object map = getServer().getClass().getMethod("getCommandMap").invoke(getServer());
            return map instanceof org.bukkit.command.CommandMap ? (org.bukkit.command.CommandMap) map : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, org.bukkit.command.Command> knownCommands(org.bukkit.command.CommandMap commandMap) {
        Class<?> clazz = commandMap.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (java.util.Map<String, org.bukkit.command.Command>) field.get(commandMap);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (sccCommand != null) {
            sccCommand.remove(event.getPlayer().getUniqueId());
        }
    }

    private void registerModernChatListener(PublicChatListener legacyListener) {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Class<?> clazz = Class.forName("com.supercolourfulchat.listener.ModernChatListener");
            Listener listener = (Listener) clazz.getConstructor(SccCommand.class, ViaVersionBridge.class)
                    .newInstance(sccCommand, viaBridge);
            getServer().getPluginManager().registerEvents(listener, this);
            legacyListener.setModernActive(true);
        } catch (Throwable t) {
            // 非 Paper 服务端无 AsyncChatEvent：静默回退到 legacy 聊天管线。
        }
    }
}