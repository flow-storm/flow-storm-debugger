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

To check that it working run a repl with both deps in your dependencies :

```bash
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "2.0.0-alpha-SNAPSHOT"} com.github.jpmonettas/flow-storm-inst {:mvn/version "2.0.0-alpha-SNAPSHOT"}}}'
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

Only Clojure local debugging is supported so far, but remote debugging and ClojureScript are planned.

# Some examples

### Debug the clojurescript compiler in one command

Given you can compile and run a cljs file, like :

```bash
clj -Sdeps {:deps {org.clojure/clojure {:mvn/version "1.11.0"}          \
					org.clojure/clojurescript {:mvn/version "1.11.4"}}} \
    -M -m cljs.main -t nodejs /home/jmonetta/tmp/cljstest/foo/script.cljs
```

You can run the exact same command under de debugger and instrumenting the entire cljs codebase first using `flow-storm.api/trampoline`, like :

```bash
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "2.0.0-alpha-SNAPSHOT"} com.github.jpmonettas/flow-storm-inst {:mvn/version "2.0.0-alpha-SNAPSHOT"} org.clojure/clojure {:mvn/version "1.11.0"} org.clojure/clojurescript {:mvn/version "1.11.4"}}}' \
	-X flow-storm.api/trampoline :ns-set '#{"cljs."}'                                           \
	                             :profile ':light'                                              \
								 :fn-symb 'cljs.main/-main'                                     \
								 :fn-args '["-t" "nodejs" "/home/jmonetta/tmp/cljstest/foo/script.cljs"]'
```

### Debug depsta while building flow-storm jars

```bash
clj -X:dbg:inst:dev:build flow-storm.api/trampoline :ns-set '#{\"hf.depstar\"}' \
                                                    :fn-symb 'hf.depstar/jar'   \
													:fn-args '[{:jar "flow-storm-dbg.jar"         \
													            :aliases [:dbg]                   \
																:paths-only false                 \
																:sync-pom true                    \
																:version "1.1.1"                  \
																:group-id "com.github.jpmonettas" \
																:artifact-id "flow-storm-dbg"}]'
```
