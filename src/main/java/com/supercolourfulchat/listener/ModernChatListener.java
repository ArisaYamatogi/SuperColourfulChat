package com.supercolourfulchat.listener;

import com.supercolourfulchat.SccCommand;
import com.supercolourfulchat.compat.ViaVersionBridge;
import com.supercolourfulchat.util.GradientUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModernChatListener implements Listener {

    private final SccCommand toggles;
    private final ViaVersionBridge via;
    private final java.util.concurrent.atomic.AtomicBoolean firstInjectionLogged = new java.util.concurrent.atomic.AtomicBoolean();
    private final Set<UUID> offSkipLogged = ConcurrentHashMap.newKeySet();

    public ModernChatListener(SccCommand toggles, ViaVersionBridge via) {
        this.toggles = toggles;
        this.via = via;
    }

    // LOWEST：记录现代事件开始，供 legacy 监听器识别"事件循环内同步重分发"（Paper 正常路径 legacy 先于现代触发）。
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatLowest(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (!toggles.isEnabled(sender.getUniqueId())) {
            return;
        }
        String text = GradientUtil.plainProjection(
                GradientUtil.stripDanglingHex(LegacyComponentSerializer.legacySection().serialize(event.message())));
        toggles.markModernStarted(sender.getUniqueId(), text);
    }

    // HIGH：必须在 EssentialsChat LOWEST 剥离§码之后、HIGHEST 捕获渲染闭包之前注入渐变。
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (!toggles.isEnabled(sender.getUniqueId())) {
            if (offSkipLogged.add(sender.getUniqueId())) {
                debug(sender, "HIGH skip: toggle is OFF (logged once per player)");
            }
            return;
        }
        if (!sender.hasPermission("scc.use")) {
            debug(sender, "HIGH skip: missing permission scc.use");
            return;
        }
        // 手动码以玩家原始输入为准：EssentialsChat LOWEST 会按权限剥掉§码，剥后检测会漏判。
        String original = LegacyComponentSerializer.legacySection().serialize(event.originalMessage());
        if (GradientUtil.hasManualCodes(original)) {
            debug(sender, "HIGH skip: manual codes in original input: " + original);
            return;
        }
        // EssentialsChat 剥色会把 §x hex 序列剥成裸 §x 残留（'x' 不在其颜色表内），需清除以免客户端渲染异常。
        String text = GradientUtil.stripDanglingHex(LegacyComponentSerializer.legacySection().serialize(event.message()));
        if (text.isEmpty()) {
            debug(sender, "HIGH skip: empty message after cleanup");
            return;
        }
        // 消息已含完整 hex（例如 legacy 重分发兜底已注入）时不再叠加，避免对§码逐字符渐变产生乱码。
        if (GradientUtil.containsHex(text)) {
            debug(sender, "HIGH skip: message already contains hex colors");
            return;
        }
        int start = GradientUtil.randomColor();
        int end = GradientUtil.randomEndColor(start);
        String gradient = GradientUtil.gradient(text, start, end);

        Set<Audience> viewers = event.viewers();
        List<Player> legacy = new ArrayList<>();
        for (Audience audience : new ArrayList<>(viewers)) {
            if (audience instanceof Player && via.isBelow116((Player) audience)) {
                legacy.add((Player) audience);
            }
        }
        if (!legacy.isEmpty()) {
            String downgraded = GradientUtil.downgrade(text, start);
            for (Player recipient : legacy) {
                viewers.remove(recipient);
                recipient.sendMessage("<" + sender.getDisplayName() + "> " + downgraded);
            }
        }
        event.message(LegacyComponentSerializer.legacySection().deserialize(gradient));
        debug(sender, "HIGH injected gradient start=#" + rgbHex(start) + " end=#" + rgbHex(end) + " text=" + text);
        if (firstInjectionLogged.compareAndSet(false, true)) {
            org.bukkit.Bukkit.getLogger().info("[SuperColourfulChat] First gradient injected for " + sender.getName() + " via modern AsyncChatEvent.");
        }
    }

    // MONITOR 兜底：HIGHEST 之后若有插件覆盖渲染器剥掉渐变，这里检查渲染输出并把消息正文重写为渐变。
    // 同时观察取消状态：其他插件取消现代事件后手动重分发 legacy 事件时，由 PublicChatListener 兜底。
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMonitor(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        toggles.clearModernStarted(sender.getUniqueId());
        if (event.isCancelled()) {
            String cancelledText = GradientUtil.plainProjection(
                    GradientUtil.stripDanglingHex(LegacyComponentSerializer.legacySection().serialize(event.message())));
            toggles.markModernCancelled(sender.getUniqueId(), cancelledText);
            debug(sender, "AsyncChatEvent CANCELLED by another plugin; legacy re-dispatch fallback armed");
            return;
        }
        if (!toggles.isEnabled(sender.getUniqueId()) || !sender.hasPermission("scc.use")) {
            return;
        }
        String original = LegacyComponentSerializer.legacySection().serialize(event.originalMessage());
        if (GradientUtil.hasManualCodes(original)) {
            return;
        }
        String current = GradientUtil.stripDanglingHex(LegacyComponentSerializer.legacySection().serialize(event.message()));
        String plain = GradientUtil.plainProjection(current);
        if (plain.isEmpty()) {
            return;
        }
        ChatRenderer renderer = event.renderer();
        if (!(renderer instanceof ChatRenderer.ViewerUnaware)) {
            return;
        }
        ChatRenderer.ViewerUnaware viewerUnaware = (ChatRenderer.ViewerUnaware) renderer;
        Component renderedComponent = viewerUnaware.render(sender, sender.displayName(), event.message());
        String rendered = LegacyComponentSerializer.legacySection().serialize(renderedComponent);
        if (GradientUtil.renderedMessageHasHex(rendered, plain)) {
            return;
        }
        int start = GradientUtil.randomColor();
        int end = GradientUtil.randomEndColor(start);
        String recolored = GradientUtil.recolorMessagePortion(rendered, plain, start, end);
        if (recolored == null) {
            debug(sender, "MONITOR skip: message text not found in rendered output: " + rendered);
            return;
        }
        Component out = LegacyComponentSerializer.legacySection().deserialize(recolored);
        event.renderer(ChatRenderer.viewerUnaware((player, displayName, message) -> out));
        event.message(LegacyComponentSerializer.legacySection().deserialize(GradientUtil.gradient(plain, start, end)));
        debug(sender, "MONITOR safeguard re-injected gradient (renderer output had no hex)");
    }

    private static void debug(Player player, String reason) {
        org.bukkit.Bukkit.getLogger().info("[SuperColourfulChat][debug] " + player.getName() + ": " + reason);
    }

    private static String rgbHex(int rgb) {
        return String.format("%06x", rgb);
    }
}