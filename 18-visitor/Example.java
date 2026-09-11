import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
sealed interface Node permits Paragraph, Section {
    <R> R accept(Visitor<R> visitor);
}
interface Visitor<R> {
    R visitParagraph(Paragraph paragraph);
    R visitSection(Section section);
}
record Paragraph(String text) implements Node {
    Paragraph { Objects.requireNonNull(text); }
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitParagraph(this);
    }
}
record Section(String heading, List<Node> children) implements Node {
    Section {
        Objects.requireNonNull(heading);
        children = List.copyOf(children);
    }
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitSection(this);
    }
}
static final class PlainText implements Visitor<String> {
    public String visitParagraph(Paragraph paragraph) {
        return paragraph.text() + "\n";
    }
    public String visitSection(Section section) {
        var text = new StringBuilder(section.heading()).append('\n');
        for (var child : section.children()) text.append(child.accept(this));
        return text.toString();
    }
}
static final class ParagraphCount implements Visitor<Long> {
    public Long visitParagraph(Paragraph paragraph) { return 1L; }
    public Long visitSection(Section section) {
        long count = 0;
        for (var child : section.children())
            count = Math.addExact(count, child.accept(this));
        return count;
    }
}
private static int checks;
static void check(boolean ok) { if (!ok) throw new AssertionError("Check "+(checks+1)); checks++; }
@FunctionalInterface interface Throwing { void run() throws Exception; }
static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
 try { action.run(); } catch (Throwable t) { if (!type.isInstance(t)) throw new AssertionError(t); checks++; return; }
 throw new AssertionError("Expected "+type.getSimpleName());
}
public static void main(String[] args) throws Exception {
Node document = new Section("Report", List.of(
    new Paragraph("Summary"),
    new Section("Details", List.of(new Paragraph("Finding")))));
check(document.accept(new PlainText()).equals("Report\nSummary\nDetails\nFinding\n"));
check(document.accept(new ParagraphCount()) == 2L);
check(new Section("Empty", List.of()).accept(new ParagraphCount()) == 0L);
Node leaf = new Paragraph("Leaf");
check(leaf.accept(new PlainText()).equals("Leaf\n"));
check(leaf.accept(new ParagraphCount()) == 1L);
var input = new ArrayList<Node>(List.of(leaf));
var section = new Section("Snapshot", input);
input.clear();
check(section.accept(new ParagraphCount()) == 1L);
expect(NullPointerException.class, () -> new Paragraph(null));
expect(UnsupportedOperationException.class, () -> section.children().clear());
System.out.println("PASS: "+checks+" behavioral checks");
}
}

