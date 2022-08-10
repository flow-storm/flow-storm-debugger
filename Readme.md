# Flow-storm debugger

A tracing debugger for Clojure and ClojureScript

![demo](./docs/images/screenshot-light.png)
![demo](./docs/images/screenshot-dark.png)

# Clojure has the repl, does it need a debugger?

I gave a talk recently for the London Clojurians, the first slide goes about my answer to this :
https://www.youtube.com/watch?v=A3AzlqNwUXc&t=934s

# Artifacts

Debugger GUI artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-dbg.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-dbg)

Instrumentation artifact :
[![Clojars Project](https://img.shields.io/clojars/v/com.github.jpmonettas/flow-storm-inst.svg)](https://clojars.org/com.github.jpmonettas/flow-storm-inst)

# Prerequisites

	- jdk11+
    - clojure 1.10.0+

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

## FAQ

### Why did you build this?

In [this  talk](https://www.youtube.com/watch?v=A3AzlqNwUXc) I tried to argument that even as amazing as it is to have a repl to poke around when trying to understand a piece of code, there are some inconveniences, that I think can be greatly improved by a debugger.

   - Adding prints (or tap>) is inconvenient because you need to guess where the problem probably is first. Also need to type it (and then remove it), and exploring complex values is annoying in the console, that is why tools like portal, reveal, rebl, etc exist.
   - Defining function parameters and locals with def (for sub form execution) isn't easy for complex values
   - When functions contains loops, maps, filters, etc with anonymous functions is hard to capture every value for further inspection.
   - Being Clojure a dynamic lang, running the program in my head isn't easy if I'm not familiar with the code base

So I want to stop guessing and want a tool that allows me to see what is happening when a program runs (small expression or entire code bases), for when I'm hunting a bug or when I just want to understand how something works.

But also I think some Clojure constrains (immutability and being expression based) allows us to go beyond steppers.
We can trace everything that happened when a program run and then inspect the execution using multiple tools, being a stepper one of them.

### How does it compare to other Clojure debuggers?

FlowStorm uses the same technique for instrumenting code that Cider uses, they both instrument by code rewriting, and they don't need source maps, they use a custom coordinates system instead.

Cursive is different since uses JDI (Java Debugging Interface) which is more limited for Clojure since it is line, statement and place oriented instead of expression oriented like FlowStorm and Cider.

So how it compares with Cider debugger (which I used full time before FlowStorm) :

   - FlowStorm is a tracing debugger, while Cider is a blocking one
   - FlowStorm supports Clojure and ClojureScript, while Cider only Clojure
   - FlowStorm supports time travel, Cider just step forward
   - FlowStorm provide multiple tools to understand execution, Cider provides forward stepping and some tracing
   - FlowStorm is IDE independent, so if you switch editors/ide you can keep using it
   - Both have data inspection capabilities
   - Both are the same regarding macros, pretty good IMHO
   - Cider is more integrated into the editor oc, while you need a separate window for FlowStorm

## What to do when things don't work?

Please create a [issue](https://github.com/jpmonettas/flow-storm-debugger/issues) if you think you found a bug.

If you are not sure you can ask in :
 - [#flow-storm slack channel](https://clojurians.slack.com/archives/C03KZ3XT0CF)
 - [github discussions](https://github.com/jpmonettas/flow-storm-debugger/discussions)

## Acknowledgements

Thanks to [Cider](https://github.com/clojure-emacs/cider/) debugger for inspiration and some cleaver ideas for code instrumentation.
