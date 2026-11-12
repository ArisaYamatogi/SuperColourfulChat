package com.supercolourfulchat.listener;

import com.earth2me.essentials.ISettings;
import com.earth2me.essentials.User;
import com.earth2me.essentials.messaging.IMessageRecipient;
import com.earth2me.essentials.messaging.IMessageRecipient.MessageResponse;
import com.supercolourfulchat.SccCommand;
import com.supercolourfulchat.compat.ViaVersionBridge;
import com.supercolourfulchat.util.GradientUtil;
import net.ess3.api.IEssentials;
import net.ess3.api.IUser;
import net.ess3.api.events.PrivateMessagePreSendEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public final class PrivateMessageListener implements Listener {

    private static final Pattern MINI_MESSAGE_TAGS = Pattern.compile("<[^>]*>");

    private final JavaPlugin plugin;
    private final SccCommand toggles;
    private final ViaVersionBridge via;
    private volatile Method i18nMethod;

    public PrivateMessageListener(JavaPlugin plugin, SccCommand toggles, ViaVersionBridge via) {
        this.plugin = plugin;
        this.toggles = toggles;
        this.via = via;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrivateMessage(PrivateMessagePreSendEvent event) {
        IMessageRecipient sender = event.getSender();
        if (!(sender instanceof IUser)) {
            return;
        }
        Player senderBase = ((IUser) sender).getBase();
        if (senderBase == null || !toggles.isEnabled(senderBase.getUniqueId())) {
            return;
        }
        String message = event.getMessage();
        if (GradientUtil.hasManualCodes(message)) {
            return;
        }
        IMessageRecipient recipient = event.getRecipient();
        if (recipient == null) {
            return;
        }
        int start = GradientUtil.randomColor();
        int end = GradientUtil.randomEndColor(start);
        String gradient = GradientUtil.gradient(message, start, end);
        String downgraded = GradientUtil.downgrade(message, start);

        boolean senderOld = via.isBelow116(senderBase);
        boolean recipientOld = isLegacyUser(recipient);

        if (senderOld == recipientOld) {
            event.setMessage(recipientOld ? downgraded : gradient);
            return;
        }
        event.setCancelled(true);
        dispatchManually(sender, recipient, message, gradient, downgraded, senderOld, recipientOld);
    }

    private boolean isLegacyUser(IMessageRecipient recipient) {
        if (!(recipient instanceof IUser)) {
            return false;
        }
        Player base = ((IUser) recipient).getBase();
        return base != null && via.isBelow116(base);
    }

    private void dispatchManually(IMessageRecipient sender, IMessageRecipient recipient, String original,
                                  String gradient, String downgraded,
                                  boolean senderOld, boolean recipientOld) {
        try {
            if (sender instanceof User && ((User) sender).isMuted()) {
                notifyMuted(sender);
                socialSpy(sender, recipient, original, original, true);
                return;
            }
            MessageResponse response = recipient.onReceiveMessage(sender, recipientOld ? downgraded : gradient);
            switch (response) {
                case UNREACHABLE:
                    sendTl(sender, "recentlyForeverAlone", recipient.getDisplayName());
                    break;
                case MESSAGES_IGNORED:
                    sendTl(sender, "msgIgnore", recipient.getDisplayName());
                    break;
                case SENDER_IGNORED:
                    break;
                default:
                    if (response == MessageResponse.SUCCESS_BUT_AFK && recipient instanceof IUser) {
                        String afkMessage = ((IUser) recipient).getAfkMessage();
                        if (afkMessage != null) {
                            sendTl(sender, "userAFKWithMessage", recipient.getDisplayName(), afkMessage);
                        } else {
                            sendTl(sender, "userAFK", recipient.getDisplayName());
                        }
                    }
                    sendTl(sender, "msgFormat", tlOf(sender, "meSender"), recipient.getDisplayName(),
                            senderOld ? downgraded : gradient);
                    socialSpy(sender, recipient, gradient, downgraded, false);
                    break;
            }
            if (response.isSuccess()) {
                sender.setReplyRecipient(recipient);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Gradient private message dispatch failed, falling back to plain message: " + t);
            try {
                sendTl(sender, "msgFormat", tlOf(sender, "meSender"), recipient.getDisplayName(), original);
                sendTl(recipient, "msgFormat", sender.getDisplayName(), tlOf(recipient, "meRecipient"), original);
            } catch (Throwable ignored) {
                sender.sendMessage(original);
                recipient.sendMessage(original);
            }
        }
    }

    private void notifyMuted(IMessageRecipient sender) {
        try {
            Method method = sender.getClass().getDeclaredMethod("notifyMuted");
            method.setAccessible(true);
            method.invoke(sender);
        } catch (Throwable t) {
            sendTl(sender, "voiceSilenced");
        }
    }

    private void socialSpy(IMessageRecipient sender, IMessageRecipient recipient, String gradient, String downgraded, boolean muted) {
        try {
            Plugin essPlugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if (!(essPlugin instanceof IEssentials)) {
                return;
            }
            IEssentials ess = (IEssentials) essPlugin;
            ISettings settings = ess.getSettings();
            if (!settings.isSocialSpyMessages()) {
                return;
            }
            boolean displayNames = false;
            try {
                Object result = settings.getClass().getMethod("isSocialSpyDisplayNames").invoke(settings);
                displayNames = Boolean.TRUE.equals(result);
            } catch (Throwable ignored) {
            }
            String senderName = nameOf(sender, displayNames);
            String recipientName = nameOf(recipient, displayNames);
            String prefix = translate(muted ? "socialSpyMutedPrefix" : "socialSpyPrefix");
            for (User user : ess.getOnlineUsers()) {
                if (user == null || user == sender || user == recipient) {
                    continue;
                }
                Player base = user.getBase();
                if (base == null || base.hasPermission("essentials.chat.spy.exempt") || !user.isSocialSpyEnabled()) {
                    continue;
                }
                String message = via.isBelow116(base) ? downgraded : gradient;
                String body = translate("socialSpyMsgFormat", senderName, recipientName, message);
                user.sendMessage(MINI_MESSAGE_TAGS.matcher(prefix + body).replaceAll(""));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String nameOf(IMessageRecipient recipient, boolean displayNames) {
        if (displayNames) {
            try {
                String name = recipient.getDisplayName();
                if (name != null) {
                    return name;
                }
            } catch (Throwable ignored) {
            }
        }
        return recipient.getName();
    }

    // sendTl/tlSender only exist on IMessageRecipient since EssentialsX 2.2x; older cores get the native I18n.tl + sendMessage equivalent.
    private void sendTl(IMessageRecipient target, String key, Object... args) {
        try {
            Method method = target.getClass().getMethod("sendTl", String.class, Object[].class);
            method.invoke(target, key, args);
        } catch (Throwable reflectionFailed) {
            target.sendMessage(translate(key, args));
        }
    }

    private String tlOf(IMessageRecipient target, String key) {
        try {
            Method method = target.getClass().getMethod("tlSender", String.class, Object[].class);
            Object result = method.invoke(target, key, new Object[0]);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Throwable ignored) {
        }
        return translate(key);
    }

    private String translate(String key, Object... args) {
        try {
            Method method = i18nMethod;
            if (method == null) {
                Class<?> i18n = Class.forName("com.earth2me.essentials.I18n");
                try {
                    method = i18n.getMethod("tlLiteral", String.class, Object[].class);
                } catch (NoSuchMethodException e) {
                    method = i18n.getMethod("tl", String.class, Object[].class);
                }
                i18nMethod = method;
            }
            return (String) method.invoke(null, key, args);
        } catch (Throwable t) {
            throw new IllegalStateException("Essentials I18n unavailable: " + t);
        }
    }
}