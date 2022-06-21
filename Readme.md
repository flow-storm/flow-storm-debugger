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

FlowStorm debugger is designed to be use at the repl, so whenever you need to trace what a piece of code is doing
you can add `#trace` before its top level form and re-evaluate it.

```clojure

;; After connecting to the debugger you can 
;; instrument any top level form by placing #trace before it

#trace
(defn sum [a b]
	(+ a b))

(sum 4 5) ;; when any instrumented function gets called it will generate traces you can inspect in the debugger


;; #rtrace (run traced) will instrument a form and run it. 
;; Useful for working at the repl

#rtrace
(->> (range) (filter odd?) (take 10) (reduce +))
```

## Conditional tracing

Lets say you are working on a game, and the function you want to trace is being called 60 times per second. 
Tracing the function with `#trace` is impractical since it is going to flood the debugger with traces and you are probably 
not interested in all of them.

You can use `#ctrace` instead. It will instrument the entire form but disable traces until you enable them by adding `^{:trace/when ...}` meta 
on the form you are interested in. For example :

```clojure
#trace ;; <- will trace only on the enabled cases
(defn foo [a]
   (+ a 10))

#ctrace ;; <- will instrument but disable all tracing until it is enabled by :trace/when meta
(defn boo []
   (->> (range 10)
        (map (fn sc [i]
               ^{:trace/when (<= 2 i 4)} ;; <- will enable traces only when i is between 2 and 4
               (foo i)))
        (reduce +)))

(boo)
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
clj -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.11.57"}}}' \
    -M -m cljs.main -t nodejs ./org/foo/myscript.cljs
```

You can run the exact same command but instrumenting the entire cljs codebase first like :

```bash
clj -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.11.57"} com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"} com.github.jpmonettas/flow-storm-inst {:mvn/version "RELEASE"}}}' \
	-X flow-storm.api/cli-run :instrument-ns '#{"cljs."}'           \
                              :profile ':light'                     \
                              :require-before '#{"cljs.repl.node"}' \
							  :excluding-ns '#{"cljs.vendor.cognitect.transit"}' \
                              :fn-symb 'cljs.main/-main'            \
                              :fn-args '["-t" "nodejs" "./org/foo/myscript.cljs"]';
```


## Using the debugger

This videos are all the documentation so far on using the debugger.

- Flows basics https://www.youtube.com/watch?v=YnpQMrkj4v8
- Instrumenting libraries https://youtu.be/YnpQMrkj4v8?t=332
- Debugging the ClojureScript compiler https://youtu.be/YnpQMrkj4v8?t=533
- Browser https://www.youtube.com/watch?v=cnLwRzxrKDk
- Def button https://youtu.be/cnLwRzxrKDk?t=103
- Conditional tracing https://youtu.be/cnLwRzxrKDk?t=133

### Defining a value for working at the repl

Every panel where FlowStorm pretty prints a value contains a `def` button. Clicking on it will ask you
for a name and define that value under that name on your runtime, so you can work with it at the repl.

Lets say you have named it `my-value` then on Clojure you can access it at the repl under `user/my-value`
while in ClojureScript it is going to be under `js/my-value`.

## ClojureScript

FlowStorm ClojureScript support is still in its infancy, for instructions on how to use it check [here](./docs/ClojureScript.md)

## What to do when things don't work?

Please create a [issue](https://github.com/jpmonettas/flow-storm-debugger/issues) if you think you found a bug.

If you are not sure you can ask in :
 - [#flow-storm slack channel](https://clojurians.slack.com/archives/C03KZ3XT0CF)
 - [github discussions](https://github.com/jpmonettas/flow-storm-debugger/discussions)


