import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class SccSerProbe {
    static LegacyComponentSerializer S = LegacyComponentSerializer.legacySection();

    static void show(String label, Component c) {
        String ser = S.serialize(c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ser.length(); i++) {
            char ch = ser.charAt(i);
            if (ch == '\u00a7') sb.append("[S]");
            else sb.append(ch);
        }
        System.out.println(label + " => " + sb);
    }

    public static void main(String[] args) {
        // 1) serialize a hex-colored component: what format does plain legacySection() emit?
        Component hex = Component.text("hello", TextColor.color(0xFF00FF));
        show("serialize(hex FF00FF)", hex);

        // 2) deserialize the Spigot unusual format and re-serialize
        String spigot = "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a7f\u00a7fhello";
        Component d1 = S.deserialize(spigot);
        show("deserialize(Spigot x-format) then serialize", d1);
        System.out.println("   d1.color=" + d1.color() + " children=" + d1.children().size());

        // 3) deserialize the section-hash format and re-serialize
        String hash = "\u00a7#FF00FFhello";
        Component d2 = S.deserialize(hash);
        show("deserialize(#-format) then serialize", d2);
        System.out.println("   d2.color=" + d2.color());

        // 4) gradient string round-trip (what production does)
        String grad = "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a7f\u00a7fh\u00a7x\u00a70\u00a70\u00a7f\u00a7f\u00a70\u00a70e";
        Component d3 = S.deserialize(grad);
        show("gradient round-trip", d3);

        // 5) what does a white prefix + gradient look like serialized
        Component white = Component.text("[Bot] ", TextColor.color(0xFFFFFF))
                .append(Component.text("hi", TextColor.color(0xFF00FF)));
        show("white-prefix + hex", white);

        // 6) explicit unusual-format serializer for comparison
        LegacyComponentSerializer U = LegacyComponentSerializer.builder()
                .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
        System.out.println("unusual.serialize(hex) => " + U.serialize(hex).replace('\u00a7', '&'));
        System.out.println("plain.serialize(hex)   => " + S.serialize(hex).replace('\u00a7', '&'));
    }
}