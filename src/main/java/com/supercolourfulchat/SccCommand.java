package com.supercolourfulchat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SccCommand implements CommandExecutor {

    private static final long REDISPATCH_WINDOW_MS = 3000L;

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, CancelledChat> modernCancelledAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CancelledChat> modernInProgress = new ConcurrentHashMap<>();

    private static final class CancelledChat {
        final long at;
        final String message;

        CancelledChat(long at, String message) {
            this.at = at;
            this.message = message;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c该命令只能由玩家使用。");
            return true;
        }
        Player player = (Player) sender;
        UUID id = player.getUniqueId();
        boolean nowEnabled;
        if (enabled.remove(id)) {
            nowEnabled = false;
            player.sendMessage("§7炫彩聊天已关闭。");
        } else {
            enabled.add(id);
            nowEnabled = true;
            player.sendMessage("§a炫彩聊天已开启，你发送的消息将使用随机渐变色。");
        }
        org.bukkit.Bukkit.getLogger().info("[SuperColourfulChat] " + player.getName() + " (" + id + ") toggled "
                + (nowEnabled ? "ON" : "OFF") + " via /" + label);
        return true;
    }

    public boolean isEnabled(UUID id) {
        return enabled.contains(id);
    }

    public void remove(UUID id) {
        enabled.remove(id);
        modernCancelledAt.remove(id);
        modernInProgress.remove(id);
    }

    public void clear() {
        enabled.clear();
        modernCancelledAt.clear();
        modernInProgress.clear();
    }

    public void markModernStarted(UUID id, String message) {
        modernInProgress.put(id, new CancelledChat(System.currentTimeMillis(), message));
    }

    public void clearModernStarted(UUID id) {
        modernInProgress.remove(id);
    }

    // 现代事件处理进行中收到相同正文的 legacy 事件 = 其他插件在事件循环内同步重分发。
    // Paper 正常路径里 legacy 事件先于现代事件触发，因此不会误判。
    public boolean isModernInProgress(UUID id, String message) {
        CancelledChat started = modernInProgress.get(id);
        return started != null
                && System.currentTimeMillis() - started.at <= REDISPATCH_WINDOW_MS
                && started.message != null && started.message.equals(message);
    }

    public void markModernCancelled(UUID id, String message) {
        modernCancelledAt.put(id, new CancelledChat(System.currentTimeMillis(), message));
    }

    // 现代事件被其他插件取消后短时间内收到相同正文的 legacy 聊天事件，视为对方手动重分发。
    public boolean consumeModernCancelled(UUID id, String message) {
        CancelledChat cancelled = modernCancelledAt.remove(id);
        return cancelled != null
                && System.currentTimeMillis() - cancelled.at <= REDISPATCH_WINDOW_MS
                && cancelled.message != null && cancelled.message.equals(message);
    }
}