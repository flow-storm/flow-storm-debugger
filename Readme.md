# flow-storm-debugger

A experimental Clojure and ClojureScript debugger

**WIP!**

[![Clojars Project](https://img.shields.io/clojars/v/jpmonettas/flow-storm-debugger.svg)](https://clojars.org/jpmonettas/flow-storm-debugger)

## Running
```bash
clj -Sdeps '{:deps {jpmonettas/flow-storm-debugger {:mvn/version "0.2.4"}}}' -m flow-storm-debugger.server
```

And point your browser to http://localhost:7722

## Instrumenting your code

Add [flow-storm](https://github.com/jpmonettas/flow-storm) dependency to your app and start tracing your functions.
