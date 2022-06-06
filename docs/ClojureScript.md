# ClojureScript

FlowStorm ClojureScript is in its infancy and doesn't support every feature, take a look at [limitations](#limitations) for a full description.
Said that, you can instrument any ClojureScript form by using #rtrace and #trace.

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

# Notes

## NodeJs prerequisites 

On nodejs you need to install `websocket` npm dependency, like this : 

```
npm install websocket --save
```

## Limitations

ClojureScript `flow-storm.api` doesn't support `instrument-var`, `instrument-files-for-namespaces` and `cli-run` yet.

The debugger GUI doesn't support `Flow re run`, `Fully instrument form`, and `Un-instrument form` yet.

## Tested on

  - NodeJs
  - Browser
