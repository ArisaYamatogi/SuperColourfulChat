package com.supercolourfulchat.listener;

import com.supercolourfulchat.SccCommand;
import com.supercolourfulchat.compat.ViaVersionBridge;
import com.supercolourfulchat.util.GradientUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PublicChatListener implements Listener {

    private final SccCommand toggles;
    private final ViaVersionBridge via;
    private volatile boolean modernActive;

    public PublicChatListener(SccCommand toggles, ViaVersionBridge via) {
        this.toggles = toggles;
        this.via = via;
    }

    public void setModernActive(boolean modernActive) {
        this.modernActive = modernActive;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String legacyPlain = GradientUtil.plainProjection(GradientUtil.stripDanglingHex(event.getMessage()));
        boolean redispatched = modernActive
                && (toggles.isModernInProgress(sender.getUniqueId(), legacyPlain)
                    || toggles.consumeModernCancelled(sender.getUniqueId(), legacyPlain));
        if (modernActive && !redispatched) {
            return;
        }
        if (redispatched) {
            org.bukkit.Bukkit.getLogger().info("[SuperColourfulChat][debug] " + sender.getName()
                    + ": processing re-dispatched legacy chat event after modern event was cancelled");
        }
        if (!toggles.isEnabled(sender.getUniqueId()) || !sender.hasPermission("scc.use")) {
            return;
        }
        String message = event.getMessage();
        if (GradientUtil.hasManualCodes(message)) {
            return;
        }
        message = GradientUtil.stripDanglingHex(message);
        if (message.isEmpty()) {
            return;
        }
        int start = GradientUtil.randomColor();
        int end = GradientUtil.randomEndColor(start);
        String gradient = GradientUtil.gradient(message, start, end);

        Set<Player> recipients = event.getRecipients();
        List<Player> legacy = new ArrayList<>();
        for (Player recipient : new ArrayList<>(recipients)) {
            if (via.isBelow116(recipient)) {
                legacy.add(recipient);
            }
        }
        if (legacy.isEmpty()) {
            event.setMessage(gradient);
            return;
        }
        String downgraded = GradientUtil.downgrade(message, start);
        String format = event.getFormat();
        String displayName = sender.getDisplayName();
        for (Player recipient : legacy) {
            recipients.remove(recipient);
            String rendered;
            try {
                rendered = String.format(format, displayName, downgraded);
            } catch (Throwable t) {
                rendered = "<" + displayName + "> " + downgraded;
            }
            recipient.sendMessage(rendered);
        }
        event.setMessage(gradient);
    }
}