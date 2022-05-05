# Flow-storm debugger

A trace debugger for Clojure 

![demo](./docs/images/screenshot.png)

# Prerequisites

	- jdk11+
    - clojure 1.10.0+
	
# Installing 

Debugger GUI artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-dbg.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-dbg)

Instrumentation artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst)

To check that it is working run a repl with both deps in your dependencies :

```bash
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "2.0.38"} com.github.jpmonettas/flow-storm-inst {:mvn/version "2.0.38"}}}'
```

and then :

```clojure
user> (require '[flow-storm.api :as fs-api]) ;; the only namespace you need to require

user> (fs-api/local-connect) ;; will run the debbuger GUI and get everything ready

user> #rtrace (reduce + (map inc (range 10)))
```

# Documentation 

This demo video is all the documentation so far.

- Flows basics https://www.youtube.com/watch?v=YnpQMrkj4v8

Proper manual coming...

# Notes

Only Clojure local debugging is supported so far, but remote debugging and ClojureScript are planned.

# Some examples

### Debug the clojurescript compiler in one command

Given you can compile and run a cljs file, like :

```bash
clj -Sdeps {:deps {org.clojure/clojurescript {:mvn/version "1.11.4"}}} \
    -M -m cljs.main -t nodejs ./org/foo/myscript.cljs
```

You can run the exact same command under de debugger and instrumenting the entire cljs codebase first using `flow-storm.api/cli-run`, like :

```bash
clj -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.11.4"} com.github.jpmonettas/flow-storm-dbg {:mvn/version "2.0.38"} com.github.jpmonettas/flow-storm-inst {:mvn/version "2.0.38"}}}' \
	-X flow-storm.api/cli-run :instrument-ns '#{"cljs."}'           \
                              :profile ':light'                     \
                              :require-before '#{"cljs.repl.node"}' \
                              :fn-symb 'cljs.main/-main'            \
                              :fn-args '["-t" "nodejs" "./org/foo/myscript.cljs"]';
```
