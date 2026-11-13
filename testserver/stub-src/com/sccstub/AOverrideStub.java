package com.sccstub;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AOverrideStub extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AOverrideStub active: strips colors from rendered chat at MONITOR");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatRenderer renderer = event.renderer();
        if (!(renderer instanceof ChatRenderer.ViewerUnaware)) {
            return;
        }
        ChatRenderer.ViewerUnaware inner = (ChatRenderer.ViewerUnaware) renderer;
        event.renderer(ChatRenderer.viewerUnaware((player, displayName, message) -> {
            Component rendered = inner.render(player, displayName, message);
            String legacy = LegacyComponentSerializer.legacySection().serialize(rendered);
            String stripped = legacy.replaceAll("\u00a7x(\u00a7[0-9a-fA-F]){6}", "")
                    .replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
            return LegacyComponentSerializer.legacySection().deserialize(stripped);
        }));
    }
}