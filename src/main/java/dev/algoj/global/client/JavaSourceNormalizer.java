package dev.algoj.global.client;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Renames a Java submission's entry class to {@code Main} before it goes to Judge0.
 *
 * <p>Judge0 stores the source as {@code Main.java} and runs {@code javac Main.java}
 * → {@code java Main}. Code pasted from elsewhere usually declares something else
 * ({@code public class Solution}), which fails to compile — or, when the class is
 * not public, compiles and then dies because {@code java Main} finds no such class.
 * The editor's starter template already uses {@code Main}, so this only matters for
 * pasted code.
 *
 * <p>Works on a token stream rather than a regex: comments, string/char literals and
 * text blocks are skipped while scanning, so a {@code "class Solution"} inside a
 * string is never mistaken for a declaration. Only identifier tokens are rewritten,
 * which keeps the source byte-identical everywhere else — line numbers in a compile
 * error still line up with what the user sees in the editor.
 *
 * <p>Known limits: a local variable or field spelled exactly like the class is
 * renamed too (rare, and usually still compiles), and anything unexpected — a
 * missing declaration, a name clash, a malformed source — leaves the code untouched.
 * Failing to normalize is always better than failing to judge.
 */
@Slf4j
public final class JavaSourceNormalizer {

    private static final String MAIN = "Main";

    private JavaSourceNormalizer() {
    }

    /** Returns the source with its entry class renamed to {@code Main}, or unchanged. */
    public static String toMainClass(String source) {
        if (source == null || source.isBlank()) return source;
        try {
            return rewrite(source);
        } catch (RuntimeException e) {
            log.warn("Java source normalization skipped: {}", e.toString());
            return source;
        }
    }

    // ── token model ────────────────────────────────────────────────────────

    private enum Kind { IDENTIFIER, PUNCT }

    private record Token(Kind kind, String text, int start, int end) {
        boolean is(String s) {
            return text.equals(s);
        }
    }

    /** One top-level type declaration and the token range of its body. */
    private record TypeDecl(String name, int nameToken, boolean isPublic, int bodyStart, int bodyEnd) {
    }

    // ── rewriting ──────────────────────────────────────────────────────────

    private static String rewrite(String source) {
        List<Token> tokens = tokenize(source);
        if (tokens.isEmpty()) return source;

        List<TypeDecl> topLevel = topLevelTypes(tokens);
        TypeDecl target = pickTarget(tokens, topLevel);
        if (target == null || target.name().equals(MAIN)) return source;
        // Renaming onto a name another top-level type already holds would break
        // the compile rather than fix it.
        if (topLevel.stream().anyMatch(d -> d.name().equals(MAIN))) return source;

        int[] packageRange = packageRange(tokens);

        StringBuilder out = new StringBuilder(source);
        // Right to left so earlier offsets stay valid.
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (t.kind() != Kind.IDENTIFIER || !t.is(target.name())) continue;
            if (packageRange != null && t.start() >= packageRange[0] && t.end() <= packageRange[1]) continue;
            out.replace(t.start(), t.end(), MAIN);
        }
        if (packageRange != null) {
            blank(out, packageRange[0], packageRange[1]);
        }
        return out.toString();
    }

    /**
     * A package declaration would put the class in a directory Judge0 does not
     * create. Blanked instead of deleted so every following line keeps its number.
     */
    private static int[] packageRange(List<Token> tokens) {
        Token first = tokens.get(0);
        if (first.kind() != Kind.IDENTIFIER || !first.is("package")) return null;
        for (Token t : tokens) {
            if (t.kind() == Kind.PUNCT && t.is(";")) {
                return new int[]{first.start(), t.end()};
            }
        }
        return null;
    }

    private static void blank(StringBuilder sb, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = sb.charAt(i);
            if (c != '\n' && c != '\r') sb.setCharAt(i, ' ');
        }
    }

    /** The public type, else the one that declares {@code main(String...)}. */
    private static TypeDecl pickTarget(List<Token> tokens, List<TypeDecl> topLevel) {
        for (TypeDecl d : topLevel) {
            if (d.isPublic()) return d;
        }
        for (TypeDecl d : topLevel) {
            if (declaresMain(tokens, d)) return d;
        }
        return null;
    }

    private static boolean declaresMain(List<Token> tokens, TypeDecl decl) {
        if (decl.bodyStart() < 0 || decl.bodyEnd() < 0) return false;
        for (int i = decl.bodyStart(); i + 2 <= decl.bodyEnd(); i++) {
            Token t = tokens.get(i);
            if (t.kind() == Kind.IDENTIFIER && t.is("main")
                    && tokens.get(i + 1).is("(")
                    && tokens.get(i + 2).is("String")) {
                return true;
            }
        }
        return false;
    }

    // ── scanning ───────────────────────────────────────────────────────────

    private static boolean isTypeKeyword(Token t) {
        return t.kind() == Kind.IDENTIFIER
                && (t.is("class") || t.is("interface") || t.is("enum") || t.is("record"));
    }

    private static List<TypeDecl> topLevelTypes(List<Token> tokens) {
        List<TypeDecl> found = new ArrayList<>();
        int depth = 0;
        boolean sawPublic = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() == Kind.PUNCT) {
                if (t.is("{")) depth++;
                else if (t.is("}")) {
                    depth--;
                    if (depth <= 0) sawPublic = false;
                } else if (t.is(";") && depth == 0) sawPublic = false;
                continue;
            }
            if (depth != 0) continue;
            if (t.is("public")) {
                sawPublic = true;
                continue;
            }
            if (!isTypeKeyword(t)) continue;

            int nameIdx = i + 1;
            if (nameIdx >= tokens.size() || tokens.get(nameIdx).kind() != Kind.IDENTIFIER) {
                sawPublic = false;
                continue;
            }
            int bodyStart = indexOfBodyBrace(tokens, nameIdx + 1);
            int bodyEnd = bodyStart < 0 ? -1 : indexOfMatchingBrace(tokens, bodyStart);
            found.add(new TypeDecl(tokens.get(nameIdx).text(), nameIdx, sawPublic, bodyStart, bodyEnd));
            // An unbalanced body (source mid-edit, or simply broken) makes every
            // later offset a guess — keep what was found and stop scanning. The
            // public type still gets renamed, which is the one error worth fixing.
            if (bodyEnd < 0) break;
            sawPublic = false;
            // Continue from the body's opening brace so nested types are counted
            // by depth (and therefore skipped) rather than treated as top level.
            i = bodyStart;
            depth++;
        }
        return found;
    }

    /** First brace after a declaration header — generics and record headers carry none. */
    private static int indexOfBodyBrace(List<Token> tokens, int from) {
        for (int i = from; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() == Kind.PUNCT && t.is("{")) return i;
        }
        return -1;
    }

    private static int indexOfMatchingBrace(List<Token> tokens, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Kind.PUNCT) continue;
            if (t.is("{")) depth++;
            else if (t.is("}") && --depth == 0) return i;
        }
        return -1;
    }

    // ── tokenizer ──────────────────────────────────────────────────────────

    /**
     * Emits identifiers and the punctuation the scan needs. Comments and literals
     * are consumed without emitting anything, which is what keeps their contents
     * out of the rename.
     */
    private static List<Token> tokenize(String s) {
        List<Token> tokens = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                i = skipLineComment(s, i);
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i = skipBlockComment(s, i);
            } else if (c == '"' && s.startsWith("\"\"\"", i)) {
                i = skipTextBlock(s, i);
            } else if (c == '"' || c == '\'') {
                i = skipQuoted(s, i, c);
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                tokens.add(new Token(Kind.IDENTIFIER, s.substring(i, j), i, j));
                i = j;
            } else if (c == '{' || c == '}' || c == '(' || c == ')' || c == ';') {
                tokens.add(new Token(Kind.PUNCT, String.valueOf(c), i, i + 1));
                i++;
            } else {
                i++;
            }
        }
        return tokens;
    }

    private static int skipLineComment(String s, int i) {
        int j = s.indexOf('\n', i);
        return j < 0 ? s.length() : j + 1;
    }

    private static int skipBlockComment(String s, int i) {
        int j = s.indexOf("*/", i + 2);
        return j < 0 ? s.length() : j + 2;
    }

    private static int skipTextBlock(String s, int i) {
        int j = i + 3;
        while (j < s.length()) {
            if (s.charAt(j) == '\\') j += 2;
            else if (s.startsWith("\"\"\"", j)) return j + 3;
            else j++;
        }
        return s.length();
    }

    /** String and char literals; an unterminated one stops at the line end. */
    private static int skipQuoted(String s, int i, char quote) {
        int j = i + 1;
        while (j < s.length()) {
            char c = s.charAt(j);
            if (c == '\\') j += 2;
            else if (c == quote) return j + 1;
            else if (c == '\n') return j;
            else j++;
        }
        return s.length();
    }
}
