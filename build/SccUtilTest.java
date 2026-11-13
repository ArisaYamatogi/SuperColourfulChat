import com.supercolourfulchat.util.GradientUtil;

public class SccUtilTest {
    static int failures = 0;

    static void check(String name, boolean cond) {
        System.out.println((cond ? "PASS " : "FAIL ") + name);
        if (!cond) failures++;
    }

    public static void main(String[] args) {
        String hex = "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a7f\u00a7f";
        check("dangling bare section-x not manual", !GradientUtil.hasManualCodes("\u00a7x324892748724"));
        check("full hex is manual", GradientUtil.hasManualCodes(hex + "hello"));
        check("amp code manual", GradientUtil.hasManualCodes("&ahello"));
        check("section code manual", GradientUtil.hasManualCodes("\u00a7ahello"));
        check("amp hash manual", GradientUtil.hasManualCodes("&#FF0000hello"));
        check("mini hex manual", GradientUtil.hasManualCodes("<#FF0000>hello"));
        check("plain digits not manual", !GradientUtil.hasManualCodes("324892748724"));
        check("strip dangling", GradientUtil.stripDanglingHex("\u00a7x324892748724").equals("324892748724"));
        check("strip keeps full hex", GradientUtil.stripDanglingHex(hex + "hi").equals(hex + "hi"));
        check("strip mixed", GradientUtil.stripDanglingHex("a\u00a7xb" + hex + "c").equals("ab" + hex + "c"));

        String g = GradientUtil.gradient("hello", 0xFF0000, 0x0000FF);
        check("gradient has 5 hex blocks", g.split("\u00a7x", -1).length - 1 == 5);
        String emoji = GradientUtil.gradient("a\uD83D\uDE00b", 0x00FF00, 0xFF00FF);
        check("emoji codepoint intact", emoji.contains("\uD83D\uDE00"));
        check("emoji 3 colors", emoji.split("\u00a7x", -1).length - 1 == 3);
        char nearest = GradientUtil.nearestLegacyCode(0xFF5555);
        check("nearest legacy red = c", nearest == 'c');
        check("nearest pure red = 4 (dark red)", GradientUtil.nearestLegacyCode(0xFF0000) == '4');
        check("downgrade prefix", GradientUtil.downgrade("hi", 0x0000AA).equals("\u00a71hi"));

        check("containsHex full", GradientUtil.containsHex(hex + "hi"));
        check("containsHex bare x false", !GradientUtil.containsHex("\u00a7xhi"));
        check("containsHex legacy false", !GradientUtil.containsHex("\u00a7ahi"));
        check("plainProjection strips hex", GradientUtil.plainProjection(hex + "h" + hex + "i").equals("hi"));
        check("plainProjection strips legacy", GradientUtil.plainProjection("\u00a7a[\u00a7bOWNER\u00a7a] \u00a7rname: hi").equals("[OWNER] name: hi"));
        check("plainProjection dangling x", GradientUtil.plainProjection("\u00a7xabc").equals("abc"));

        String renderedWhite = "\u00a77[\u00a7fOWNER\u00a77] \u00a7fname\u00a77-> \u00a7fhello world";
        check("rendered white has no msg hex", !GradientUtil.renderedMessageHasHex(renderedWhite, "hello world"));
        String renderedGrad = "\u00a77[OWNER] name-> " + GradientUtil.gradient("hello world", 0xFF0000, 0x0000FF);
        check("rendered gradient has msg hex", GradientUtil.renderedMessageHasHex(renderedGrad, "hello world"));
        String prefixHexOnly = hex + "[OWNER] name-> hello world";
        check("prefix hex not msg hex", !GradientUtil.renderedMessageHasHex(prefixHexOnly, "hello world"));
        check("msg not found fallback whole", GradientUtil.renderedMessageHasHex(hex + "other", "hello world"));

        String recolored = GradientUtil.recolorMessagePortion(renderedWhite, "hello world", 0xFF0000, 0x0000FF);
        check("recolor keeps prefix", recolored != null && recolored.startsWith("\u00a77[\u00a7fOWNER\u00a77] \u00a7fname\u00a77-> "));
        check("recolor message now hex", GradientUtil.renderedMessageHasHex(recolored, "hello world"));
        check("recolor plain intact", GradientUtil.plainProjection(recolored).equals("[OWNER] name-> hello world"));
        check("recolor missing msg null", GradientUtil.recolorMessagePortion(renderedWhite, "absent", 0, 0xFFFFFF) == null);
        String trailing = "\u00a77[OWNER] name-> \u00a7fhello world\u00a7r";
        String recoloredTrailing = GradientUtil.recolorMessagePortion(trailing, "hello world", 0x123456, 0x654321);
        check("recolor trailing reset absorbed", GradientUtil.plainProjection(recoloredTrailing).equals("[OWNER] name-> hello world"));
        String msgFirst = "hello world \u00a77<- name";
        String recoloredFirst = GradientUtil.recolorMessagePortion(msgFirst, "hello world", 0x123456, 0x654321);
        check("recolor message-first format", GradientUtil.renderedMessageHasHex(recoloredFirst, "hello world")
                && GradientUtil.plainProjection(recoloredFirst).equals("hello world <- name"));

        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }
}