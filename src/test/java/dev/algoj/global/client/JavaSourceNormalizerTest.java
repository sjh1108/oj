package dev.algoj.global.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaSourceNormalizerTest {

    private static int lineCount(String s) {
        return s.split("\n", -1).length;
    }

    @Test
    void publicClass_isRenamedToMain() {
        String src = """
                import java.util.*;

                public class Solution {
                    public static void main(String[] args) {
                        System.out.println(1);
                    }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src))
                .contains("public class Main {")
                .doesNotContain("Solution");
    }

    @Test
    void referencesToTheClass_areRenamedToo() {
        String src = """
                public class Solution {
                    int v;
                    Solution(int v) { this.v = v; }
                    static Solution of(int v) { return new Solution(v); }
                    public static void main(String[] args) {
                        System.out.println(Solution.of(3).v);
                    }
                }
                """;

        String out = JavaSourceNormalizer.toMainClass(src);

        assertThat(out).doesNotContain("Solution");
        assertThat(out).contains("Main(int v)").contains("new Main(v)").contains("Main.of(3)");
    }

    @Test
    void alreadyMain_isLeftAlone() {
        String src = """
                public class Main {
                    public static void main(String[] args) { }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src)).isEqualTo(src);
    }

    @Test
    void packageDeclaration_isBlankedKeepingLineNumbers() {
        String src = """
                package com.example.solutions;

                public class Solution {
                    public static void main(String[] args) { }
                }
                """;

        String out = JavaSourceNormalizer.toMainClass(src);

        assertThat(out).doesNotContain("package com.example");
        assertThat(out).contains("public class Main {");
        assertThat(lineCount(out)).isEqualTo(lineCount(src));
        // The declaration's line survives as blanks, so line 3 is still the class.
        assertThat(out.split("\n")[0].trim()).isEmpty();
    }

    @Test
    void classNameInsideCommentsAndStrings_isUntouched() {
        String src = """
                // public class Solution 이라고 쓰면 안 된다
                /* Solution */
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("class Solution");
                        char c = 'S';
                        System.out.println("Solution" + c);
                    }
                }
                """;

        String out = JavaSourceNormalizer.toMainClass(src);

        assertThat(out)
                .contains("// public class Solution 이라고 쓰면 안 된다")
                .contains("/* Solution */")
                .contains("\"class Solution\"")
                .contains("\"Solution\" + c")
                .contains("public class Main {");
    }

    @Test
    void textBlockContents_areUntouched() {
        String src = """
                public class Solution {
                    static final String SQL = \"""
                            public class Solution {}
                            \""";
                    public static void main(String[] args) { System.out.println(SQL); }
                }
                """;

        String out = JavaSourceNormalizer.toMainClass(src);

        assertThat(out).contains("public class Solution {}");
        assertThat(out).contains("public class Main {");
    }

    @Test
    void nonPublicClassWithMain_isRenamed() {
        String src = """
                class Solution {
                    public static void main(String[] args) { }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src)).contains("class Main {");
    }

    @Test
    void classWithoutMainAndWithoutPublic_isLeftAlone() {
        String src = """
                class Helper {
                    int twice(int v) { return v * 2; }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src)).isEqualTo(src);
    }

    @Test
    void nameClashWithExistingMain_isLeftAlone() {
        // Renaming Solution here would collide with the Main that already exists.
        String src = """
                public class Solution {
                    public static void main(String[] args) { Main.run(); }
                }
                class Main {
                    static void run() { }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src)).isEqualTo(src);
    }

    @Test
    void modifiersAndTypeKinds_areHandled() {
        assertThat(JavaSourceNormalizer.toMainClass("public final class Solution { }"))
                .isEqualTo("public final class Main { }");
        assertThat(JavaSourceNormalizer.toMainClass("public class Solution<T> { T v; }"))
                .isEqualTo("public class Main<T> { T v; }");
        assertThat(JavaSourceNormalizer.toMainClass("public enum Solution { A, B; }"))
                .isEqualTo("public enum Main { A, B; }");
        assertThat(JavaSourceNormalizer.toMainClass("public record Solution(int x) { }"))
                .isEqualTo("public record Main(int x) { }");
    }

    @Test
    void nestedTypes_keepTheirNames() {
        String src = """
                public class Solution {
                    static class Node { int v; }
                    public static void main(String[] args) { new Node(); }
                }
                """;

        String out = JavaSourceNormalizer.toMainClass(src);

        assertThat(out).contains("public class Main {").contains("static class Node");
    }

    @Test
    void annotatedClass_isRenamed() {
        String src = """
                @SuppressWarnings("unchecked")
                public class Solution {
                    public static void main(String[] args) { }
                }
                """;

        assertThat(JavaSourceNormalizer.toMainClass(src)).contains("public class Main {");
    }

    @Test
    void malformedSource_isReturnedUnchanged() {
        String src = "public class Solution { public static void main(String[] a) { String s = \"unterminated";

        // Nothing blows up, and a source that cannot compile anyway is not made worse.
        assertThat(JavaSourceNormalizer.toMainClass(src)).contains("class Main");
        assertThat(JavaSourceNormalizer.toMainClass("}{ nonsense")).isEqualTo("}{ nonsense");
    }

    @Test
    void blankOrNullSource_isReturnedAsIs() {
        assertThat(JavaSourceNormalizer.toMainClass(null)).isNull();
        assertThat(JavaSourceNormalizer.toMainClass("   ")).isEqualTo("   ");
    }
}
