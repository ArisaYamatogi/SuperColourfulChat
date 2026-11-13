import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import java.awt.Color;

public class SccBungeeProbe {
    static String vis(String s) { return s.replace('\u00a7', '&'); }
    public static void main(String[] a) {
        System.out.println("bungee ChatColor.of(red).toString => " + vis(ChatColor.of(new Color(255, 0, 0)).toString()));
        ComponentBuilder b = new ComponentBuilder();
        b.append("h").color(ChatColor.of(new Color(255, 0, 0)));
        b.append("i").color(ChatColor.of(new Color(255, 127, 0)));
        TextComponent tc = new TextComponent(b.create());
        System.out.println("ColorfulChat-style toLegacyText => " + vis(tc.toLegacyText()));
        TextComponent t2 = new TextComponent("hello");
        t2.setColor(ChatColor.of(new Color(0, 255, 255)));
        System.out.println("single hex toLegacyText => " + vis(t2.toLegacyText()));
    }
}