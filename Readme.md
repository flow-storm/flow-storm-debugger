# flow-storm-debugger

A experimental Clojure and ClojureScript debugger

## Running
```bash
clj -Sdeps '{:deps {flow-storm-debugger {:mvn/version "0.1.0"}}}' -m flow-storm-debugger.server
```

And point your browser to http://localhost:7722

## Instrumenting your code

Add [flow-storm](https://github.com/jpmonettas/flow-storm) dependency to your app and start tracing your functions.
