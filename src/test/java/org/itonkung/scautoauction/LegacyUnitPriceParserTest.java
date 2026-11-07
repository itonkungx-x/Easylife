package org.itonkung.scautoauction;
import java.util.List;
public class LegacyUnitPriceParserTest {
    public static void main(String[] args) {
        String one = LegacyUnitPriceParser.extract(List.of("ราคาต่อชิ้น: ⛁ 1,000,000", "ราคาต่อสแตค: ⛁ 64,000,000"));
        if (!"1,000,000".equals(one)) throw new AssertionError(one);
        String two = LegacyUnitPriceParser.extract(List.of("minecraft:dragon_egg", "14 component(s)"));
        if (!"".equals(two)) throw new AssertionError(two);
        String three = LegacyUnitPriceParser.extract(List.of("Price per item: 1.25"));
        if (!"1.25".equals(three)) throw new AssertionError(three);
        System.out.println("LegacyUnitPriceParserTest passed");
    }
}
