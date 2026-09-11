import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
record TextStyle(String fontFamily, int pointSize, boolean bold, int rgb) {
    TextStyle {
        Objects.requireNonNull(fontFamily);
        if (fontFamily.isBlank() || pointSize < 1 || pointSize > 200 ||
                rgb < 0 || rgb > 0xFFFFFF)
            throw new IllegalArgumentException("Invalid style");
    }
}
record TextRun(String text, int x, int y, TextStyle style) {
    TextRun {
        Objects.requireNonNull(text);
        Objects.requireNonNull(style);
        if (x < 0 || y < 0) throw new IllegalArgumentException("Negative position");
    }
}
static final class StylePool {
    private final int maximumEntries;
    private final Map<TextStyle, TextStyle> styles = new HashMap<>();
    StylePool(int maximumEntries) {
        if (maximumEntries < 1) throw new IllegalArgumentException("Invalid capacity");
        this.maximumEntries = maximumEntries;
    }
    TextStyle style(String family, int size, boolean bold, int rgb) {
        var candidate = new TextStyle(family, size, bold, rgb);
        var existing = styles.get(candidate);
        if (existing != null) return existing;
        if (styles.size() < maximumEntries) styles.put(candidate, candidate);
        return candidate;
    }
    int retainedStyles() { return styles.size(); }
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var pool = new StylePool(1);
var first = pool.style("Inter", 12, false, 0x233739);
var same = pool.style("Inter", 12, false, 0x233739);
check(first == same);
var a = new TextRun("Revenue", 20, 40, first);
var b = new TextRun("Margin", 20, 60, same);
check(a.style() == b.style() && a.y() != b.y());
var boldA = pool.style("Inter", 12, true, 0x233739);
var boldB = pool.style("Inter", 12, true, 0x233739);
check(boldA.equals(boldB) && boldA != boldB);
check(pool.retainedStyles() == 1);
check(pool.style("Inter", 12, false, 0x233739) == first);
expect(IllegalArgumentException.class, () -> pool.style("Inter", 0, false, 0));
expect(IllegalArgumentException.class, () -> new TextRun("Bad", -1, 0, first));
check(new StylePool(1).style("Inter", 12, false, 0x233739).equals(first));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

