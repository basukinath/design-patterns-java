# Java Design Patterns — Runnable Examples

Runnable Java 17+ examples for 22 design patterns. Each folder is independent and intentionally uses the default package so an example can be compiled directly without a build tool.

## Patterns

1. Factory
2. Strategy
3. Builder
4. Adapter
5. Decorator
6. Chain of Responsibility
7. Observer
8. State
9. Proxy
10. Facade
11. Template Method
12. Command
13. Specification
14. Composite
15. Abstract Factory
16. Bridge
17. Prototype
18. Visitor
19. Mediator
20. Flyweight
21. Memento
22. Singleton

## Run an example

Compile and run one folder at a time because most folders intentionally contain a class named Example.

For Strategy:

    cd 02-strategy
    javac --release 17 Example.java
    java Example

Factory has two source files:

    cd 01-factory
    javac --release 17 ReportExports.java FactoryChecks.java
    java FactoryChecks

Some examples use in-memory implementations instead of databases, message queues, framework proxies, cloud providers, or remote services. The code demonstrates each pattern's behavior; it does not reproduce the operational guarantees of those external systems.
