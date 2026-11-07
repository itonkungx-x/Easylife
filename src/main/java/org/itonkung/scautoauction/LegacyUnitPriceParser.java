package org.itonkung.scautoauction;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Price parsing adapted from user's archived ItemPriceExtractor v1.2.0 source.
 * Specifically recognizes the per-item field without accidentally using
 * stack-price or unrelated tooltip numbers.
 */
public final class LegacyUnitPriceParser {
    private static final Pattern UNIT = Pattern.compile(
        "(?:ราคาต่อ\\s*ชิ้น|ราคาต่อ\\s*หน่วย|ราคาชิ้นละ|ต่อชิ้น|" +
        "price\\s*per\\s*(?:item|piece|unit)|unit\\s*price|price\\s*each|each\\s*price)" +
        "\\s*[:：]?\\s*[^0-9+\\-]{0,35}((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,6})?)",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private LegacyUnitPriceParser() {}

    public static String extract(List<String> lines) {
        if (lines == null) return "";
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.replaceAll("(?i)§[0-9a-fk-or]", "")
                .replace('\u00a0',' ').replace('\u200b',' ');
            Matcher m = UNIT.matcher(line);
            if (!m.find()) continue;
            try {
                BigDecimal price = new BigDecimal(m.group(1).replace(",",""));
                if (price.signum() >= 0) return m.group(1);
            } catch (NumberFormatException ignored) {}
        }
        return "";
    }
}
