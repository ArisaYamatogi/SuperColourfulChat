package com.sccstub;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class ACancelStub extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ACancelStub active: cancels modern chat and re-dispatches legacy");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String message = LegacyComponentSerializer.legacySection().serialize(event.message());
        Set<Player> recipients = new HashSet<>();
        for (Audience audience : event.viewers()) {
            if (audience instanceof Player) {
                recipients.add((Player) audience);
            }
        }
        event.setCancelled(true);
        AsyncPlayerChatEvent legacy = new AsyncPlayerChatEvent(true, sender, message, recipients);
        legacy.setFormat("<%1$s> %2$s");
        Bukkit.getPluginManager().callEvent(legacy);
        if (legacy.isCancelled()) {
            return;
        }
        String rendered;
        try {
            rendered = String.format(legacy.getFormat(), sender.getDisplayName(), legacy.getMessage());
        } catch (Throwable t) {
            rendered = "<" + sender.getDisplayName() + "> " + legacy.getMessage();
        }
        for (Player recipient : legacy.getRecipients()) {
            recipient.sendMessage(rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }
}