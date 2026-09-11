import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
static final class ReportEditor {
    private String title;
    private List<String> body;
    ReportEditor(String title, List<String> body) {
        setState(title, body);
    }
    private void setState(String title, List<String> body) {
        this.title = Objects.requireNonNull(title);
        if (title.isBlank()) throw new IllegalArgumentException("Blank title");
        this.body = new ArrayList<>(List.copyOf(body));
    }
    Memento snapshot() { return new Memento(title, body); }
    void restore(Memento memento) {
        Objects.requireNonNull(memento);
        setState(memento.title(), memento.body());
    }
    void rename(String newTitle) { setState(newTitle, body); }
    void replaceBody(List<String> lines) { setState(title, lines); }
    String title() { return title; }
    List<String> body() { return List.copyOf(body); }

    record Memento(String title, List<String> body) {
        Memento {
            Objects.requireNonNull(title);
            body = List.copyOf(body);
        }
    }
}
static final class EditHistory {
    private final int limit;
    private final Deque<ReportEditor.Memento> undo = new ArrayDeque<>();
    private final Deque<ReportEditor.Memento> redo = new ArrayDeque<>();
    EditHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Invalid history limit");
        this.limit = limit;
    }
    void perform(ReportEditor editor, Runnable edit) {
        Objects.requireNonNull(editor);
        Objects.requireNonNull(edit);
        var before = editor.snapshot();
        edit.run();
        undo.addLast(before);
        while (undo.size() > limit) undo.removeFirst();
        redo.clear();
    }
    boolean undo(ReportEditor editor) {
        if (undo.isEmpty()) return false;
        redo.addLast(editor.snapshot());
        editor.restore(undo.removeLast());
        return true;
    }
    boolean redo(ReportEditor editor) {
        if (redo.isEmpty()) return false;
        undo.addLast(editor.snapshot());
        editor.restore(redo.removeLast());
        return true;
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
var input = new ArrayList<>(List.of("First"));
var editor = new ReportEditor("Draft", input);
input.clear();
var history = new EditHistory(2);
history.perform(editor, () -> editor.rename("Review"));
history.perform(editor, () -> editor.replaceBody(List.of("Updated")));
check(editor.title().equals("Review") && editor.body().equals(List.of("Updated")));
check(history.undo(editor) && editor.body().equals(List.of("First")));
check(history.undo(editor) && editor.title().equals("Draft"));
check(!history.undo(editor));
check(history.redo(editor) && editor.title().equals("Review"));
history.perform(editor, () -> editor.rename("Final"));
check(!history.redo(editor));
history.perform(editor, () -> editor.rename("Published"));
history.perform(editor, () -> editor.rename("Archived"));
check(history.undo(editor) && editor.title().equals("Published"));
check(history.undo(editor) && editor.title().equals("Final"));
check(!history.undo(editor));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

