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

    public PublicChatListener(SccCommand toggles, ViaVersionBridge via) {
        this.toggles = toggles;
        this.via = via;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!toggles.isEnabled(sender.getUniqueId())) {
            return;
        }
        String message = event.getMessage();
        if (GradientUtil.hasManualCodes(message)) {
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
            recipient.sendMessage(String.format(format, displayName, downgraded));
        }
        event.setMessage(gradient);
    }
}