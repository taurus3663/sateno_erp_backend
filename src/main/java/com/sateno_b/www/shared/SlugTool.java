package com.sateno_b.www.shared;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class SlugTool {


    public static String generateSlug(String text) {
        Map<Character, String> cyrillicToLatin = new HashMap<>();
        cyrillicToLatin.put('а', "a"); cyrillicToLatin.put('б', "b"); cyrillicToLatin.put('в', "v");
        cyrillicToLatin.put('г', "g"); cyrillicToLatin.put('д', "d"); cyrillicToLatin.put('е', "e");
        cyrillicToLatin.put('ж', "zh"); cyrillicToLatin.put('з', "z"); cyrillicToLatin.put('и', "i");
        cyrillicToLatin.put('й', "y"); cyrillicToLatin.put('к', "k"); cyrillicToLatin.put('л', "l");
        cyrillicToLatin.put('м', "m"); cyrillicToLatin.put('н', "n"); cyrillicToLatin.put('о', "o");
        cyrillicToLatin.put('п', "p"); cyrillicToLatin.put('р', "r"); cyrillicToLatin.put('с', "s");
        cyrillicToLatin.put('т', "t"); cyrillicToLatin.put('у', "u"); cyrillicToLatin.put('ф', "f");
        cyrillicToLatin.put('х', "h"); cyrillicToLatin.put('ц', "ts"); cyrillicToLatin.put('ч', "ch");
        cyrillicToLatin.put('ш', "sh"); cyrillicToLatin.put('щ', "sht"); cyrillicToLatin.put('ъ', "u");
        cyrillicToLatin.put('ь', "y"); cyrillicToLatin.put('ю', "yu"); cyrillicToLatin.put('я', "ya");

        StringBuilder slug = new StringBuilder();
        String input = text.toLowerCase().trim();

        for (char c : input.toCharArray()) {
            if (cyrillicToLatin.containsKey(c)) {
                slug.append(cyrillicToLatin.get(c));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                slug.append(c);
            } else if (c == ' ') {
                slug.append("-");
            }
        }

        String result = slug.toString().replaceAll("-+", "-");
        return result.isEmpty() ? "val-" + System.currentTimeMillis() : result;
    }

    public static String decodeSlug(String slug) {
        return URLDecoder.decode(slug, StandardCharsets.UTF_8);
    }

}
