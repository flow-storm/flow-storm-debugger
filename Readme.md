# Flow-storm debugger

A trace debugger for Clojure 

![demo](./docs/images/screenshot.png)

# Installing 

Debugger GUI artifact :
[![Clojars Project](https://img.shields.io/clojars/v/jpmonettas/flow-storm-dbg.svg)](https://clojars.org/jpmonettas/flow-storm-dbg)

Instrumentation artifact :
[![Clojars Project](https://img.shields.io/clojars/v/jpmonettas/flow-storm-inst.svg)](https://clojars.org/jpmonettas/flow-storm-inst)

To check that it working run a repl with both deps in your dependencies :

```bash
clj -Sdeps '{:deps {jpmonettas/flow-storm-dbg {:mvn/version "2.0.0-beta-SNAPSHOT"} jpmonettas/flow-storm-inst {:mvn/version "2.0.0-beta-SNAPSHOT"}}}'
```

and then :

```clojure
user> (require '[flow-storm.api :as fs-api]) ;; the only namespace you need to require

user> (fs-api/local-connect) ;; will run the debbuger GUI and get everything ready

user> #rtrace (reduce + (map inc (range 10)))
```

# Documentation 

This demo videos are all the documentation so far.

- Flows basics (soon ...)
- Debugging cli tools (soon ...)
- Debugging the clojurescript compiler (soon ...)

Proper manual coming...

# Notes

Only Clojure local debugging is supported now, but remote debugging and ClojureScript are planned.
