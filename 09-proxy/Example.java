import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
enum Permission { READ_REPORT }
record Identity(String tenant, Set<Permission> permissions) {
    Identity {
        Objects.requireNonNull(tenant);
        permissions = Set.copyOf(permissions);
    }
}
record DocumentRef(String tenant, String id) {
    DocumentRef {
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(id);
        if (tenant.isBlank() || id.isBlank()) throw new IllegalArgumentException("Invalid reference");
    }
}
interface Documents {
    String read(DocumentRef reference);
}
record DocumentStore(Map<DocumentRef, String> contents) implements Documents {
    DocumentStore { contents = Map.copyOf(contents); }
    public String read(DocumentRef reference) {
        String text = contents.get(reference);
        if (text == null) throw new NoSuchElementException("Document unavailable");
        return text;
    }
}
record AuthorizedDocuments(Documents target, Supplier<Identity> identity)
        implements Documents {
    AuthorizedDocuments {
        Objects.requireNonNull(target);
        Objects.requireNonNull(identity);
    }
    public String read(DocumentRef reference) {
        Objects.requireNonNull(reference);
        Identity caller = identity.get();
        if (caller == null || !caller.permissions().contains(Permission.READ_REPORT)
                || !caller.tenant().equals(reference.tenant()))
            throw new SecurityException("Report access denied");
        return target.read(reference);
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
var ref = new DocumentRef("tenant-a", "report-1");
var store = new DocumentStore(Map.of(ref, "private report"));
var reader = new Identity("tenant-a", Set.of(Permission.READ_REPORT));
check(new AuthorizedDocuments(store, () -> reader).read(ref).equals("private report"));
int[] calls = {0};
Documents tracked = r -> { calls[0]++; return "text"; };
expect(SecurityException.class, () -> new AuthorizedDocuments(tracked, () -> null).read(ref));
expect(SecurityException.class, () -> new AuthorizedDocuments(tracked, () -> new Identity("tenant-a", Set.of())).read(ref));
expect(SecurityException.class, () -> new AuthorizedDocuments(tracked, () -> reader).read(new DocumentRef("tenant-b", "report-1")));
check(calls[0] == 0);
expect(NoSuchElementException.class, () -> new AuthorizedDocuments(store, () -> reader).read(new DocumentRef("tenant-a", "missing")));
check(!store.contents().containsKey(new DocumentRef("tenant-b", "report-1")));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

