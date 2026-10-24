package com.aetherauth.auth;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.aetherauth.config.ConfigData;

/**
 * Password strength rules. Returns a Turkish, player friendly reason on
 * failure and {@code null} when the password is acceptable.
 */
public final class PasswordPolicy {

    private static final Set<String> COMMON = new HashSet<String>(Arrays.asList(
            "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234", "111111",
            "1234567", "dragon", "123123", "baseball", "abc123", "football", "monkey", "letmein",
            "696969", "shadow", "master", "666666", "qwertyuiop", "123321", "mustang", "1234567890",
            "michael", "654321", "superman", "1qaz2wsx", "7777777", "121212", "000000", "qazwsx",
            "123qwe", "killer", "trustno1", "jordan", "jennifer", "zxcvbnm", "asdfgh", "hunter",
            "buster", "soccer", "harley", "batman", "andrew", "tigger", "sunshine", "iloveyou",
            "2000", "charlie", "robert", "thomas", "hockey", "ranger", "daniel", "starwars",
            "klaster", "112233", "george", "computer", "michelle", "jessica", "pepper", "1111",
            "zxcvbn", "555555", "11111111", "131313", "freedom", "777777", "pass", "maggie",
            "159753", "aaaaaa", "ginger", "princess", "joshua", "cheese", "amanda", "summer",
            "love", "ashley", "6969", "nicole", "chelsea", "biteme", "matthew", "access",
            "yankees", "987654321", "dallas", "austin", "thunder", "taylor", "matrix", "william",
            "corvette", "hello", "martin", "heather", "secret", "merlin", "diamond", "1234qwer",
            "gfhjkm", "hammer", "silver", "222222", "88888888", "anthony", "justin", "test",
            "bailey", "q1w2e3r4t5", "patrick", "internet", "scooter", "orange", "11111",
            "golfer", "cookie", "richard", "samantha", "bigdog", "guitar", "jackson", "whatever",
            "mickey", "chicken", "sparky", "snoopy", "maverick", "phoenix", "camaro", "sexy",
            "peanut", "morgan", "welcome", "falcon", "cowboy", "ferrari", "samsung", "andrea",
            "smokey", "steelers", "joseph", "mercedes", "dakota", "arsenal", "eagles", "melissa",
            "boomer", "booboo", "spider", "nascar", "monster", "tigers", "yellow", "xxxxxx",
            "123123123", "gateway", "marina", "diablo", "bulldog", "qwer1234", "compaq", "purple",
            "hardcore", "banana", "junior", "hannah", "123654", "porsche", "lakers", "iceman",
            "money", "cowboys", "987654", "london", "tennis", "999999", "ncc1701", "coffee",
            "scooby", "0000", "miller", "boston", "q1w2e3r4", "brandon", "yamaha", "chester",
            "mother", "forever", "johnny", "edward", "333333", "oliver", "redsox", "player",
            "nikita", "knight", "fender", "barney", "midnight", "please", "brandy", "chicago",
            "badboy", "iwantu", "slayer", "rangers", "charles", "angel", "flower", "bigdaddy",
            "rabbit", "wizard", "bigdick", "jasper", "enter", "rachel", "chris", "steven",
            "winner", "adidas", "victoria", "natasha", "1q2w3e4r", "jasmine", "winter", "prince",
            "marine", "ghbdtn", "fishing", "cocacola", "casper", "james", "232323", "raiders",
            "888888", "marlboro", "gandalf", "asdfasdf", "crystal", "87654321", "12344321",
            "sexsex", "golden", "blowme", "bigtits", "8675309", "panther", "lauren", "angela",
            "bitch", "spanky", "thx1138", "angels", "madison", "winston", "shannon", "mike",
            "toyota", "blonde", "sophie", "apples", "samson", "russell", "dick", "abcdef",
            "scorpio", "qwerty123", "hahaha", "alexis", "asdfghjkl", "passw0rd", "alex",
            "letmein123", "whatever1", "changeme", "admin", "admin123", "root", "test123"
    ));

    private final ConfigData.Auth config;

    public PasswordPolicy(ConfigData.Auth config) {
        this.config = config;
    }

    public String validate(String password, String username, String email) {
        if (password == null || password.isEmpty()) {
            return "şifre boş olamaz";
        }
        if (password.length() < config.minPasswordLength) {
            return "en az " + config.minPasswordLength + " karakter olmalı";
        }
        if (password.length() > config.maxPasswordLength) {
            return "en fazla " + config.maxPasswordLength + " karakter olabilir";
        }
        if (!password.equals(password.trim())) {
            return "başında veya sonunda boşluk olamaz";
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isWhitespace(c)) {
                return "boşluk içeremez";
            }
            if (Character.isISOControl(c)) {
                return "geçersiz karakter içeriyor";
            }
        }
        if (config.requireDigit && !password.matches(".*\\d.*")) {
            return "en az bir rakam içermeli";
        }
        if (config.requireUppercase && !password.matches(".*[A-Z].*")) {
            return "en az bir büyük harf içermeli";
        }
        if (config.requireSpecial && !password.matches(".*[^A-Za-z0-9].*")) {
            return "en az bir özel karakter içermeli";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (config.blockCommonPasswords && COMMON.contains(lower)) {
            return "çok yaygın bir şifre, farklı bir şifre seç";
        }
        if (username != null && lower.equals(username.toLowerCase(Locale.ROOT))) {
            return "oyuncu adınla aynı olamaz";
        }
        if (email != null) {
            int at = email.indexOf('@');
            String local = at > 0 ? email.substring(0, at) : email;
            if (!local.isEmpty() && lower.equals(local.toLowerCase(Locale.ROOT))) {
                return "e-posta adresinle aynı olamaz";
            }
        }
        return null;
    }
}
