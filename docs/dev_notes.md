# Developer notes

The purpose of this document is to collect information and tips for people wanting to enhance, fix, debug,
or just learn about the internals of FlowStorm.

## FlowStorm design

FlowStorm is made of three parts :

  * An __instrumentation__ system
  * A __runtime__ system
  * A __debugger__ system

If you want some diagrams of how all this works together take a look at
[here](https://raw.githubusercontent.com/flow-storm/flow-storm-debugger/master/docs/high_level_diagram.svg)

### Instrumentation

The __instrumentation__ system is responsible for instrumenting your code. This means interleaving extra code to trace
what your program is doing. There are currently 3 ways of instrumenting :

  * [Hansel](https://github.com/flow-storm/hansel) a library to add instrumentation by re-writing forms at macroexpansion time
  * [ClojureStorm](https://github.com/flow-storm/clojure) a Clojure dev compiler that will instrument by emitting extra bytecode
  * [ClojureScriptStorm](https://github.com/flow-storm/clojurescript)  a ClojureScript dev compiler that will instrument by emitting extra javascript
  
They are all independent from FlowStorm but you need to choose one of them to use FlowStorm with your programs.

When starting, FlowStorm will setup callbacks to them, which they will use when generating instrumentation.

You can see how it hooks with each of them in :

`flow-storm.tracer/[hansel-config | hook-clojure-storm | hook-clojurescript-storm]` 

Whatever instrumentation system the user chooses, when instrumented code runs it will hit :

  * `flow-storm.tracer/trace-fn-call`
  * `flow-storm.tracer/trace-fn-return`
  * `flow-storm.tracer/trace-fn-unwind`
  * `flow-storm.tracer/trace-expr-exec`
  * `flow-storm.tracer/trace-bind`
  
See the __runtime__ for what happens next.

### Runtime

The __runtime__ is FlowStorm's subsystem that runs inside the debuggee process.

Its responsibility is to record all the traces that arrive via the `flow-storm.tracer/trace-*` set of functions while
`flow-storm.tracer/recording` is `true`.

It will record everything in two registries `flow-storm.runtime.indexes.api/forms-registry` and
`flow-storm.runtime.indexes.api/flow-thread-registry`. Take a look at their docs strings for more info.

The forms registry will store all the instrumented forms by form-id, which is a hash of the form.

The threads registry on the other side will be storing one timeline per thread.
The timeline is the main recording structure, and what every FlowStorm functionality is build upon.

You can see a diagram of the timeline internal structure
[here](https://raw.githubusercontent.com/flow-storm/flow-storm-debugger/master/docs/timeline.svg).

It is currently implemented as `flow-storm.runtime.indexes.timeline-index/ExecutionTimelineTree` type, which internally
uses a mutable list. It would be simpler if this could be an immutable list but the decision was made because it needs
to be fast to build, and without too much garbage generation, so we don't make the debuggee threads too slow. 
With the current architecture transients can't be used because there isn't a trace that indicates that a thread is done,
so it can be persisted. Maybe it can be done in the future if some kind of batching by time is implemented.

All objects that represent traces are defined by types in `flow-storm.runtime.types.*` instead of maps. This is to
reduce memory footprint.

All the timelines can be explored from the repl by using the functions defined in `flow-storm.runtime.indexes.api`.

The __runtime__ exposes all the indexes functionality to debuggers via `flow-storm.runtime.debuggers-api`. The main
difference between this and `flow-storm.runtime.indexes.api` is that the former will return value ids instead of actual
values, since not all values can leave the debuggee process (think of infinite sequences), and also because of
performance, since most of the time, for big nested values, the user is interested in a small part of them and
serializing is expensive.

When debugging locally, the functions in `flow-storm.runtime.debuggers-api` will be called directly, while they will be
called through a websocket in the remote case.

The __runtime__ part is packaged into `com.github.flow-storm/flow-storm-inst` as well as in
`com.github.flow-storm/flow-storm-dbg` artifacts. 

### Debugger

The __debugger__ is the part of FlowStorm that implements all the tools to explore the recordings with a GUI.

Its main entry point is `flow-storm.debugger.main/start-debugger`.

It has a bunch of subsystems that implement different aspects of it :

   * `flow-storm.debugger.state/state`
   * `flow-storm.debugger.runtime-api/rt-api`
   * `flow-storm.debugger.ui.main/ui`
   * `flow-storm.debugger.events-queue/events-queue`
   * `flow-storm.debugger.websocket/websocket-server`
   * `flow-storm.debugger.repl.core/repl`
   
All namespaces have doc strings explaining they purpose and how they work.

Not all subsystems will be running in all modes. For example the `websocket-server` and the `repl` client will be only
running in remote debugging mode, since they are not needed for local debugging.

The __debugger__ uses a custom component system defined in `flow-storm.state-management`, which is a very simple `mount`
like component system. It doesn't use `mount` since we don't want to drag too many dependencies to the user classpath.

The __debugger__ GUI is implemented as a JavaFX application, with all screens implemented in `flow-storm.debugger.ui.*`.

### The coordinate system

All expressions traces will contain a `:coord` field, which specifies the coordinate inside the form with id `:form-id`
a value refers to.

The coordinates are stored as a string, like `"3,2"`. They are stored as strings for performance reasons. This is
because on ClojureStorm, they can be compiled to the strings constant pool, which will be interned when the class is loaded and
they can thereof be referenced by traces.

As an example, the coordinate `"3,2"` for the form :

```clojure
(defn foo [a b] (+ a b))
```

refers to the second symbol `b`  in the `(+ a b)`  expression, which is under coordinate `3`. 

The coordinates are a zero based collection of positional indexes from the root, for all but for maps
and sets. 

For them instead of an index it will be, for a :

    map key : a string K followed by the hash of the key form
    map value: a string V followed by the hash of the key form for the val

For sets it will also be a string K followed by the hash of the set
element form.

As an example :

(defn foo [a b]
  (assoc {1 10
          (+ 42 43) 100}
         :x #{(+ 1 2) (+ 3 4) (+ 4 5) (+ 1 1 (* 2 2))}))

some examples coordinates :

    [3 1 "K-240379483"]   => (+ 42 43)
    [3 2 "K1305480196" 3] => (* 2 2)

You can see an implementation of this hash function in `hansel.utils/clojure-form-source-hash`.

The reason we can't use indices for maps and sets is because the order is not guaranteed.

### Values

As described in the __runtime__ section, recorded values never leave `flow-storm.runtime.debuggers-api`.

They are stored into a registry, and a reference to them is returned. This references are defined in
`flow-storm.types/ValueRef`, which acts as a reified pointer.

Whatever is using `flow-storm.runtime.debuggers-api` will deal with this `ValueRef`s instead of actual values. 

Two functions are also exposed by `flow-storm.runtime.debuggers-api` to work with `ValueRef`s : 
  * `val-pprint` for printing a value into a string representation with provided `print-level` and `print-length`
  * `shallow-val` for returning the "first level" of the value and more `ValueRef`s to keep exploring it
  
The values registry implementation is a little more involved than just a map from (hash value) -> value because not all
values can be hashed, specially infinite sequences. For that, every value is wrapped in a
`flow-storm.runtime.values/HashableObjWrap` which will use `flow-storm.utils/object-id` for the hash.

### Events

Events are a way for the __runtime__ to communicate information to the __debugger__. 

All possible events are defined in `flow-storm.runtime.events`.

If there is nothing subscribed to runtime events the events will accumulate inside
`flow-storm.runtime.events/pending-events`, and as soon as there is a subscription they will be dispatched.
This is to capture events fired by recording when the __debugger__ is still not connected.

On the __debugger__ side they will accumulate on `flow-storm.debugger.events-queue` and will be dispatched by a
specialized thread.

### Remote debugging

Remote debugging means that the __debugger__ and the __runtime__ run in different processes.
This is optional in Clojure but the only way of debugging in ClojureScript.

The difference with local debugging is that the interaction happens over a websocket and a repl connection instead of
direct function calls. Interaction here refers to api calls from the __debugger__ to the __runtime__ and events flowing
the other way around.

The debuggee can start a repl server which the __debugger__ can then connect to, while and the __debugger__ will start a
websocket server that the __runtime__ will connect to by running `flow-storm.runtime.debuggers-api/remote-connect`.

The reason there are two different kinds of connections between the same processes are capabilities and performance.
The repl connection is the only way of accomplishing some functionality in ClojureScript, while the websocket is 
better suited for events dispatches.

Most of the api calls travel trough the websocket connection, you can see which goes through which connection in 
`flow-storm.debugger.runtime-api/rt-api`.

## FlowStorm dev tips

The easiest way in my opinion to work with the FlowStorm codebase is by starting a repl with the `:dev` and `:storm`
aliases (unless one needs to specifically try Vanilla FlowStorm).

The FlowStorm codebase includes a dev namespace inside `src-dev/dev.clj`, which contains some handy code for development,
and is designed so you can work without having to restart the repl.

Specially : 

   * `start-local` to start everything locally, with spec checking the `flow-storm.debugger.state/state`
   * `start-remote` to start only the __debugger__ and connect to a different process, with spec checking the `flow-storm.debugger.state/state`
   * `stop` to shutdown the system gracefully
   * `refresh` which will use tools.namespace to refresh namespaces

After the UI is running, everything you eval under the `dev-tester` namespace will be recorded. The `dev-tester`
namespace contains a bunch of meaningless functions, with the only purpose of generating data for testing the system.

In most situations, you should be able to change and re-eval a function on the repl and retry it without restarting the UI.

## Working with the UI

When tweaking the UI [ScenicView](https://github.com/JonathanGiles/scenic-view) helps a lot, since it allows us to
explore the JavaFX Nodes tree, and also reload css on the fly.

## Using FlowStorm as a backend for other tooling

FlowStorm includes an nRepl middleware `flow-storm.nrepl.middleware` which exposes all its functionality as nRepl
operations.

For examples of tooling using this middleware, take a look at [cider-storm](https://github.com/flow-storm/cider-storm).

## Working with ClojureStorm

Since ClojureStorm is a fork of Clojure, any technique you use for working with the Clojure compiler will apply here.

For example if you start the repl with `:storm`, `:dev` and `:jvm-debugger` you should be able to connect a Java debugger
like IntelliJ's one and add breakpoints. Then run whatever expression at the repl and step with the debugger.

Another handy tool for troubleshooting instrumentation is
[clj-java-decompiler](https://github.com/clojure-goes-fast/clj-java-decompiler). 

Let's say you have instrumented:
```clojure
(defn sum [a b] (+ a b))
```

You can then decompile it into :

```java
public final class dev_tester$sum extends AFunction
{
    public static Object invokeStatic(final Object a, final Object b) {
        Number add;
        try {
            Tracer.traceFnCall(new Object[] { a, b }, "user", "sum", -1340777963);
            Tracer.traceBind(a, "", "a");
            Tracer.traceBind(b, "", "b");
            Tracer.traceExpr(a, "3,1", -1340777963);
            Tracer.traceExpr(b, "3,2", -1340777963);
            add = Numbers.add(a, b);
            Tracer.traceExpr(add, "3", -1340777963);
            Tracer.traceFnReturn(add, "", -1340777963);
        }
        catch (Throwable throwable) {
            Tracer.traceFnUnwind(throwable, "", -1340777963);
            throw throwable;
        }
        return add;
    }

    
    @Override
    public Object invoke(final Object a, final Object b) {
        return invokeStatic(a, b);
    }
}
```
or its bytecode equivalent.

## Working with ClojureScriptStorm

For working with ClojureScript storm we can use FlowStorm! since it is a Clojure codebase. 
Take a look at this for ideas : https://jpmonettas.github.io/my-blog/public/compilers-with-flow-storm.html 

## Installing local versions

You can install both artifacts locally by running :

```bash
$ VERSION=3.8.4-SNAPSHOT make install-inst # for building and installing the inst artifact
$ VERSION=3.8.4-SNAPSHOT make install-dbg  # for building and installing the dbg artifact
```
