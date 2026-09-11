import java.io.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public final class Example {
  record Document(String title, long version) {}

  interface Command<R> {
    R execute(DocumentStore receiver);
  }

  record RenameDocument(UUID commandId, UUID documentId, long expectedVersion, String newTitle)
      implements Command<Document> {
    RenameDocument {
      Objects.requireNonNull(commandId);
      Objects.requireNonNull(documentId);
      Objects.requireNonNull(newTitle);
      if (expectedVersion < 0 || newTitle.isBlank())
        throw new IllegalArgumentException("Invalid rename");
    }

    public Document execute(DocumentStore receiver) {
      return receiver.rename(this);
    }
  }

  record Applied(RenameDocument command, Document result) {}

  static final class DocumentStore {
    private final Map<UUID, Document> documents = new HashMap<>();
    private final Map<UUID, Applied> completed = new HashMap<>();

    DocumentStore(UUID id, String title) {
      documents.put(Objects.requireNonNull(id), new Document(title, 0));
    }

    synchronized Document read(UUID id) {
      var value = documents.get(id);
      if (value == null) throw new NoSuchElementException("Document missing");
      return value;
    }

    synchronized Document rename(RenameDocument command) {
      var prior = completed.get(command.commandId());
      if (prior != null) {
        if (!prior.command().equals(command))
          throw new IllegalArgumentException("Command id reused with different input");
        return prior.result();
      }
      var current = read(command.documentId());
      if (current.version() != command.expectedVersion())
        throw new IllegalStateException("Document changed");
      var updated = new Document(command.newTitle(), Math.incrementExact(current.version()));
      documents.put(command.documentId(), updated);
      completed.put(command.commandId(), new Applied(command, updated));
      return updated;
    }
  }

  private static int checks;

  static void check(boolean ok) {
    if (!ok) throw new AssertionError("Check " + (checks + 1));
    checks++;
  }

  @FunctionalInterface
  interface Throwing {
    void run() throws Exception;
  }

  static void expect(Class<? extends Throwable> type, Throwing action) throws Exception {
    try {
      action.run();
    } catch (Throwable t) {
      if (!type.isInstance(t)) throw new AssertionError(t);
      checks++;
      return;
    }
    throw new AssertionError("Expected " + type.getSimpleName());
  }

  public static void main(String[] args) throws Exception {
    var id = UUID.randomUUID();
    var store = new DocumentStore(id, "Draft");
    var command = new RenameDocument(UUID.randomUUID(), id, 0, "Final");
    var result = command.execute(store);
    check(result.title().equals("Final") && result.version() == 1);
    check(command.execute(store).equals(result));
    check(store.read(id).version() == 1);
    expect(
        IllegalArgumentException.class,
        () -> new RenameDocument(command.commandId(), id, 0, "Other").execute(store));
    expect(
        IllegalStateException.class,
        () -> new RenameDocument(UUID.randomUUID(), id, 0, "Stale").execute(store));
    check(store.read(id).title().equals("Final"));
    expect(
        NoSuchElementException.class,
        () ->
            new RenameDocument(UUID.randomUUID(), UUID.randomUUID(), 0, "Missing").execute(store));
    System.out.println("PASS: " + checks + " behavioral checks");
  }
}
