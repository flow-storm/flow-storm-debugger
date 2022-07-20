# Flow-storm debugger

A tracing debugger for Clojure and ClojureScript

![demo](./docs/images/screenshot.png)

# Artifacts

Debugger GUI artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-dbg.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-dbg)

Instrumentation artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst)

# Prerequisites

	- jdk11+
    - clojure 1.9.0+

# Documentation

Please refer to the [user guide](https://jpmonettas.github.io/flow-storm-debugger/user_guide.html)

# QuickStart (Clojure)

To check that it is working you can run a repl with both deps in your dependencies :

```bash
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"} com.github.jpmonettas/flow-storm-inst {:mvn/version "RELEASE"}}}'
```

and then :

```clojure
user> (require '[flow-storm.api :as fs-api]) ;; the only namespace you need to require

user> (fs-api/local-connect) ;; will run the debbuger GUI and get everything ready

user> #rtrace (reduce + (map inc (range 10)))
```

Calling `flow-storm.api/local-connect` will start the debugger UI under the same JVM your program is running. This is the recommended way of debugging Clojure applications since it is the fastest, 
but this isn't always possible. 
If you need to run your debugger remotley check [user guide](https://jpmonettas.github.io/flow-storm-debugger/user_guide.html)

# QuickStart (ClojureScript)

FlowStorm ClojureScript is in its infancy and doesn't support every feature, take a look at the [user guide](https://jpmonettas.github.io/flow-storm-debugger/user_guide.html) for more info.

First run a standalone debugger :

```
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"}}}' -X flow-storm.debugger.main/start-debugger
```

Then add [![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst) to your ClojureScript project.

Lets say you are using [shadow-cljs](https://clojurescript.org/tools/shadow-cljs), your shadow-cljs.edn should look like :

```bash
$ cat shadow-cljs.edn

{...
 :dependencies [... [com.github.jpmonettas/flow-storm-inst "RELEASE"]]}
 
$ shadow-cljs browser-repl 
```

Now on your ClojureScript repl :

```clojure
cljs.user> (require '[flow-storm.api :as fs-api]) ;; the only namespace you need to require

cljs.user> (fs-api/remote-connect) ;; will connect your JS runtime to the debugger via a websocket 

cljs.user> #rtrace (reduce + (map inc (range 10))) ;; you can use #trace and #rtrace like in Clojure
```

## Some demo videos

- Presentation at London Clojurians https://www.youtube.com/watch?v=A3AzlqNwUXc
- Flows basics https://www.youtube.com/watch?v=YnpQMrkj4v8
- Instrumenting libraries https://youtu.be/YnpQMrkj4v8?t=332
- Debugging the ClojureScript compiler https://youtu.be/YnpQMrkj4v8?t=533
- Browser https://www.youtube.com/watch?v=cnLwRzxrKDk
- Def button https://youtu.be/cnLwRzxrKDk?t=103
- Conditional tracing https://youtu.be/cnLwRzxrKDk?t=133

## What to do when things don't work?

Please create a [issue](https://github.com/jpmonettas/flow-storm-debugger/issues) if you think you found a bug.

If you are not sure you can ask in :
 - [#flow-storm slack channel](https://clojurians.slack.com/archives/C03KZ3XT0CF)
 - [github discussions](https://github.com/jpmonettas/flow-storm-debugger/discussions)

## Acknowledgements

Thanks to [Cider](https://github.com/clojure-emacs/cider/) debugger for inspiration and some cleaver ideas for code instrumentation.
