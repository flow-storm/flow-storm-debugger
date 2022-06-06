# Flow-storm debugger

A trace debugger for Clojure and ClojureScript

![demo](./docs/images/screenshot.png)

# Prerequisites

	- jdk11+
    - clojure 1.10.0+

# Artifacts

Debugger GUI artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-dbg.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-dbg)

Instrumentation artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst)

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
but this isn't always possible. If you need to run your debugger remotley check [remote debugging](/docs/Remote_debugging.md) 

# Documentation 

Documentation is still a work in progress ...

## Instrumenting code at the repl

FlowStorm debugger is designed to be use at the repl

```clojure

;; After connecting to the debugger you can 
;; instrument any outer form by placing #trace before it

#trace
(defn sum [a b]
	(+ a b))

(sum 4 5) ;; when any instrumented function gets called it will generate traces you can inspect in the debugger


;; #rtrace (run traced) will instrument a form and run it. 
;; Useful for working at the repl

#rtrace
(->> (range) (filter odd?) (take 10) (reduce +))
```

## Instrumenting entire codebases

You can use `flow-storm.api/instrument-forms-for-namespaces` to bulk instrument all namespaces forms like this :

```clojure
(fs-api/instrument-forms-for-namespaces #{"clojure.string" "com.my-project"} {})

;; Will instrument every form inside clojure.string and also every form under com.my-project including namespaces inside it
```

You can use `flow-storm.api/cli-run` to instrument and then run entire codebases from the command line.

Lets say we want to instrument and run the entire clojurescript compiler codebase.

Given you can compile and run a cljs file, like :

```bash
clj -Sdeps {:deps {org.clojure/clojurescript {:mvn/version "1.11.4"}}} \
    -M -m cljs.main -t nodejs ./org/foo/myscript.cljs
```

You can run the exact same command but instrumenting the entire cljs codebase first like :

```bash
clj -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.11.4"} com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"} com.github.jpmonettas/flow-storm-inst {:mvn/version "RELEASE"}}}' \
	-X flow-storm.api/cli-run :instrument-ns '#{"cljs."}'           \
                              :profile ':light'                     \
                              :require-before '#{"cljs.repl.node"}' \
                              :fn-symb 'cljs.main/-main'            \
                              :fn-args '["-t" "nodejs" "./org/foo/myscript.cljs"]';
```


## Using the debugger

This demo video is all the documentation so far on using the debugger.

- Flows basics https://www.youtube.com/watch?v=YnpQMrkj4v8

## ClojureScript

FlowStorm ClojureScript support is still in its infancy, for instructions on how to use it check [here](./docs/ClojureScript.md)



