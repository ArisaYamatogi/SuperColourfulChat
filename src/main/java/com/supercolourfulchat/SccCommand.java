package com.supercolourfulchat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SccCommand implements CommandExecutor {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c该命令只能由玩家使用。");
            return true;
        }
        Player player = (Player) sender;
        UUID id = player.getUniqueId();
        if (enabled.remove(id)) {
            player.sendMessage("§7炫彩聊天已关闭。");
        } else {
            enabled.add(id);
            player.sendMessage("§a炫彩聊天已开启，你发送的消息将使用随机渐变色。");
        }
        return true;
    }

    public boolean isEnabled(UUID id) {
        return enabled.contains(id);
    }

    public void remove(UUID id) {
        enabled.remove(id);
    }

    public void clear() {
        enabled.clear();
    }
}