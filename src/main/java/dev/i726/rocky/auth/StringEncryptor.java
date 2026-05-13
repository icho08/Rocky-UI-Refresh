package dev.i726.rocky.auth;

/**
 * Pre-bakes encrypted strings directly into bytecode as char arrays.
 * The plaintext never exists as a string literal — only XOR'd garbage is stored.
 *
 * To generate encrypted values for a new string, run:
 *   java StringEncryptor "your-string-here" "your-key-here"
 */
public final class StringEncryptor {

    private StringEncryptor() {}

    /**
     * Decrypts a char array that was pre-encrypted with XOR against the given key chars.
     * No string literal ever appears in bytecode — only the scrambled char values.
     */
    public static String decrypt(char[] encrypted, char[] key) {
        char[] result = new char[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            result[i] = (char) (encrypted[i] ^ key[i % key.length]);
        }
        return new String(result);
    }

    /**
     * Utility: encrypt a string and print the char array literals for pasting into code.
     * Run once locally to generate values, then delete this main method.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StringEncryptor <text> <key>");
            return;
        }
        String text = args[0];
        String key  = args[1];

        System.out.println("// Original: " + text);
        System.out.println("// Key:      " + key);

        // encrypted chars
        StringBuilder encChars = new StringBuilder("new char[]{ ");
        for (int i = 0; i < text.length(); i++) {
            char c = (char) (text.charAt(i) ^ key.charAt(i % key.length()));
            encChars.append((int) c);
            if (i < text.length() - 1) encChars.append(", ");
        }
        encChars.append(" }");

        // key chars
        StringBuilder keyChars = new StringBuilder("new char[]{ ");
        for (int i = 0; i < key.length(); i++) {
            keyChars.append((int) key.charAt(i));
            if (i < key.length() - 1) keyChars.append(", ");
        }
        keyChars.append(" }");

        System.out.println("\nPaste into AuthManager:");
        System.out.println("StringEncryptor.decrypt(");
        System.out.println("    " + encChars + ",");
        System.out.println("    " + keyChars);
        System.out.println(");");
    }
}
