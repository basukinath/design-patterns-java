import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
sealed interface Content permits Paragraph, Section {
    void writeTo(Appendable output, int depth) throws IOException;
    long paragraphCount();
}
record Paragraph(String text) implements Content {
    Paragraph {
        Objects.requireNonNull(text);
        if (text.isBlank()) throw new IllegalArgumentException("Blank paragraph");
    }
    public void writeTo(Appendable output, int depth) throws IOException {
        output.append("  ".repeat(depth)).append(text).append('\n');
    }
    public long paragraphCount() { return 1; }
}
record Section(String heading, List<Content> children) implements Content {
    Section {
        Objects.requireNonNull(heading);
        if (heading.isBlank()) throw new IllegalArgumentException("Blank heading");
        children = List.copyOf(children);
    }
    public void writeTo(Appendable output, int depth) throws IOException {
        output.append("  ".repeat(depth)).append(heading).append('\n');
        for (var child : children) child.writeTo(output, depth + 1);
    }
    public long paragraphCount() {
        long total = 0;
        for (var child : children)
            total = Math.addExact(total, child.paragraphCount());
        return total;
    }
}
static String render(Content content) throws IOException {
    var output = new StringBuilder();
    content.writeTo(output, 0);
    return output.toString();
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
var paragraph = new Paragraph("Finding");
var input = new ArrayList<Content>();
input.add(paragraph);
var details = new Section("Details", input);
input.clear();
var report = new Section("Report", List.of(new Paragraph("Summary"), details));
check(report.paragraphCount() == 2);
check(render(report).equals("Report\n  Summary\n  Details\n    Finding\n"));
check(details.children().size() == 1);
expect(UnsupportedOperationException.class, () -> details.children().clear());
check(new Section("Empty", List.of()).paragraphCount() == 0);
check(new Section("Repeated", List.of(paragraph, paragraph)).paragraphCount() == 2);
expect(IllegalArgumentException.class, () -> new Paragraph(" "));
expect(NullPointerException.class, () -> new Section("Bad", Arrays.asList((Content) null)));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

