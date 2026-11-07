package org.itonkung.scautoauction;

import java.util.List;
public final class PriceTextParserTest {
    public static void main(String[] args) {
        var a = PriceTextParser.parse(List.of("§7ราคาต่อชิ้น: ⛁ 1,000,000", "§7ราคาต่อสแตค: ⛁ 64,000,000"));
        if (!a.perItem().equals("1,000,000") || !a.perStack().equals("64,000,000"))
            throw new AssertionError("Thai price extraction failed: " + a);
        var b = PriceTextParser.parse(List.of("Price per item: 8.35", "Price per stack: 534.40"));
        if (!b.perItem().equals("8.35") || !b.perStack().equals("534.40"))
            throw new AssertionError("English prices not parsed: " + b);
        var c = PriceTextParser.parse(List.of("minecraft:dragon_egg", "14 components", "Custom item"));
        if (c.found()) throw new AssertionError("Number from non-price tooltip parsed");
        System.out.println("PriceTextParserTest passed");
    }
}
