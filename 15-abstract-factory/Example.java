import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.*;
import java.math.*;
import java.io.*;

public final class Example {
enum Backend { PRIMARY, ARCHIVE }
record StoredObject(Backend backend, UUID id) {
    StoredObject {
        Objects.requireNonNull(backend);
        Objects.requireNonNull(id);
    }
}
interface ObjectStore {
    StoredObject put(byte[] content);
}
interface DownloadLinks {
    java.net.URI forObject(StoredObject object);
}
interface StorageFactory {
    ObjectStore createStore();
    DownloadLinks createLinks();
}
static final class MemoryStorageFactory implements StorageFactory {
    private final Backend backend;
    private final Map<UUID, byte[]> objects = new HashMap<>();
    MemoryStorageFactory(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }
    public ObjectStore createStore() {
        return content -> {
            Objects.requireNonNull(content);
            var id = UUID.randomUUID();
            objects.put(id, content.clone());
            return new StoredObject(backend, id);
        };
    }
    public DownloadLinks createLinks() {
        return object -> {
            if (object.backend() != backend)
                throw new IllegalArgumentException("Wrong storage family");
            if (!objects.containsKey(object.id()))
                throw new NoSuchElementException("Object missing in this namespace");
            return java.net.URI.create("demo://" +
                    backend.name().toLowerCase(Locale.ROOT) + "/" + object.id());
        };
    }
}
static final class ReportDelivery {
    private final ObjectStore store;
    private final DownloadLinks links;
    ReportDelivery(StorageFactory factory) {
        Objects.requireNonNull(factory);
        store = Objects.requireNonNull(factory.createStore());
        links = Objects.requireNonNull(factory.createLinks());
    }
    java.net.URI deliver(byte[] content) {
        return links.forObject(store.put(content));
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
var primary = new MemoryStorageFactory(Backend.PRIMARY);
var archive = new MemoryStorageFactory(Backend.ARCHIVE);
check(new ReportDelivery(primary).deliver(new byte[]{1}).getHost().equals("primary"));
check(new ReportDelivery(archive).deliver(new byte[]{2}).getHost().equals("archive"));
var reference = primary.createStore().put(new byte[]{3});
check(primary.createLinks().forObject(reference).getScheme().equals("demo"));
expect(IllegalArgumentException.class, () -> archive.createLinks().forObject(reference));
expect(NoSuchElementException.class, () -> primary.createLinks().forObject(new StoredObject(Backend.PRIMARY, UUID.randomUUID())));
expect(NullPointerException.class, () -> primary.createStore().put(null));
var anotherPrimary = new MemoryStorageFactory(Backend.PRIMARY);
expect(NoSuchElementException.class, () -> anotherPrimary.createLinks().forObject(reference));
System.out.println("PASS: "+checks+" behavioral checks");
}
}

