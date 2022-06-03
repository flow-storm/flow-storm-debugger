# ClojureScript

Just add [![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst) to your ClojureScript project.

Then run a standalone debugger :

```
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"}}}' -X flow-storm.debugger.main/start-debugger
```

Now on any clojurescript file :

```clojure
user> (require '[flow-storm.api :as fs-api]) ;; the only namespace you need to require

user> (fs-api/remote-connect) ;; will connect your JS runtime to the debugger via websocket 

user> #rtrace (reduce + (map inc (range 10))) ;; you can use #trace and #rtrace like in Clojure
```

# Notes

## NodeJs prerequisites 

On nodejs you need to install `websocket` npm dependency.

```
npm install websocket --save
```

## Limitations

ClojureScript `flow-storm.api` doesn't support `instrument-var`, `instrument-files-for-namespaces` and `cli-run` yet.

The debugger doesn't support `Flow re run`, `Fully instrument form`, and `Un-instrument form` yet.

## Tested on

  - NodeJs
  - Browser
