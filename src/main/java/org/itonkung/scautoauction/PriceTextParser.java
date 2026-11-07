package org.itonkung.scautoauction;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locale-independent price parsing. Does not infer prices from unrelated numbers. */
public final class PriceTextParser {
    private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,4})?)");
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");
    private PriceTextParser() {}

    public record Prices(String perItem, String perStack) {
        public boolean found() { return !perItem.isBlank() || !perStack.isBlank(); }
    }

    public static Prices parse(List<String> lines) {
        String item = "";
        String stack = "";
        for (String raw : lines) {
            if (raw == null) continue;
            String line = FORMATTING.matcher(raw).replaceAll("");
            String key = line.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\u00a0\\u200b\\u200c\\u200d]+", "");
            String numericPart = line;
            int colon = Math.max(line.indexOf(':'), line.indexOf('：'));
            if (colon >= 0 && colon + 1 < line.length()) numericPart = line.substring(colon + 1);
            String number = findMoney(numericPart);
            if (number.isEmpty()) continue;
            if (item.isEmpty() && (key.contains("ราคาต่อชิ้น") || key.contains("ราคาชิ้น")
                || key.contains("priceperitem") || key.contains("eachprice"))) {
                item = number;
            } else if (stack.isEmpty() && (key.contains("ราคาต่อสแตค") || key.contains("ราคาต่อสแต็ก")
                || key.contains("ราคาต่อstack") || key.contains("priceperstack"))) {
                stack = number;
            }
        }
        return new Prices(item, stack);
    }

    private static String findMoney(String text) {
        Matcher matcher = NUMBER.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }
}
