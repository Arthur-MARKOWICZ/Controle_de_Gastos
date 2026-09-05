package br.com.controlegastos.identity.application;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

class RecoveryCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int GROUP_SIZE = 5;
    private static final int GROUP_COUNT = 2;
    private static final int CODE_COUNT = 10;

    private final SecureRandom random = new SecureRandom();

    List<String> generate() {
        List<String> codes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            codes.add(generateOne());
        }
        return codes;
    }

    private String generateOne() {
        StringBuilder code = new StringBuilder();
        for (int group = 0; group < GROUP_COUNT; group++) {
            if (group > 0) {
                code.append('-');
            }
            for (int i = 0; i < GROUP_SIZE; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return code.toString();
    }
}
