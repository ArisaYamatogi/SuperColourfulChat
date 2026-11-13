import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.BaseComponent;

public class SccBungeeRoundTrip {
    static String vis(String s) { return s.replace('\u00a7', '&'); }
    static void rt(String label, String legacy) {
        System.out.println(label + " input: " + vis(legacy));
        BaseComponent[] parsed = TextComponent.fromLegacyText(legacy);
        System.out.println("  plainText: " + TextComponent.toPlainText(parsed));
        System.out.println("  re-legacy: " + vis(TextComponent.toLegacyText(parsed)));
    }
    public static void main(String[] a) {
        rt("WITH leading f", "\u00a7f\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70h\u00a7x\u00a7f\u00a7f\u00a77\u00a7f\u00a70\u00a70i");
        rt("NO leading f", "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70h\u00a7x\u00a7f\u00a7f\u00a77\u00a7f\u00a70\u00a70i");
        rt("white then plain", "\u00a7fhello");
    }
}