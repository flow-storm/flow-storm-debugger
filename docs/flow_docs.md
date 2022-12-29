# Flow-docs

Generate projects functions documentation by sampling their executions.

**NOTE: Everything here is still experimental, buggy and subject to change!***

**NOTE: This assumes you are running flow-storm >= 3.3.295***

## Rationale

There are a bunch of trade offs when it comes to choosing dynamic languages like Clojure instead of static typed ones. 

One downside is the lack of documentation on the shape of functions arguments and return values. Because of it, when reading code 
you are left with hoping the author (or you) wrote good doc strings, specs, the parents functions have some info or plain guessing from names.

But it doesn't need to be that bad. Some of this shapes can be derived from execution pretty cheaply. How? By instrumenting the entire code base, 
running the tests (or any functions that exercise the code base) and sample every fn call and return.

The result is data that contains a bunch of information about your functions only known at runtime, over which you can build tooling like a 
documentation browser, be consumed by something like cljdoc, etc.

The generated data will not be comprehensive but will provide a good guide for most situations. 

##  QuickStart

If you want to use this, there are 3 tasks you are probably interested in :

	- Generating docs for your projects
	- Publishing your docs
	- Consuming docs from yours or other people projects

Next there are some details on how to accomplish each of this tasks.

### Generating docs

Lets say we want to generate documentation for datascript(https://github.com/tonsky/datascript/). 

First we clone the repo. Then we can generate it by calling `flow-storm.api/cli-doc`. 

For convenience we are going to create a script `document.sh` like this :

```
#!/bin/bash

clj -Sforce -Sdeps '{:deps {com.github.jpmonettas/flow-storm-inst {:mvn/version "RELEASE"}}}' \
    -X:test flow-storm.api/cli-doc \
    :result-name '"datascript-flow-docs-1.4.0"' \
    :print-unsampled? true \
    :instrument-ns '#{"datascript"}' \
    :fn-symb 'datascript.test/test-clj' \
    :fn-args '[]' \
    :examples-pprint? true \
    :examples-print-length 2 \
    :examples-print-level 3 
```

The idea behind `flow-storm.api/cli-doc` is to act as a trampoline, so it will instrument our code base as specified by `:instrument-ns` 
then call whatever function provided by `:fn-symb` and `:fn-args`.

For this case we are going to instrument every namespace that starts with "datascript" and then run `datascript.test/test-clj` without arguments.

For the rest of the options check `flow-storm.api/cli-doc` doc string.

It will output 3 useful things :

- datascript-flow-docs-1.4.0.jar containing just a sample.edn file with all the data
- the coverage percentage (how many fns were sampled over the instrumented ones)
- unsampled fns, which are all the functions that were instrumented but the test never called

So if you are running your tests, as a bonus you will get your test "coverage" and a list of functions your 
tests aren't exercising, you should see something like this after it finishes :

![demo](./images/flow_docs_cli.png)

### Publishing docs

Given the docs are already in jar format you can publish them to your local repo or any maven repo (like Clojars) 
with the usual mvn utilities.

### Consuming docs

FlowStorm debugger provides a way of visualizing whatever docs you have on your classpath.

For this you can add the docs and FlowStorm to your classpaths as usual, like :

```
clj -Sforce -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"} dsdocs/dsdocs {:local/root "/home/user/datascript/datascript-flow-docs-1.4.0.jar"}}}'
```	

or if you want to use the documentation I already generated and uploaded to my clojars group try :

```
clj -Sforce -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"} com.github.jpmonettas/datascript-flow-docs {:mvn/version "1.4.0"}}}'
```	

and now we can run the debugger :

```
(require '[flow-storm.api :as fs-api])

(fs-api/local-connect)
```

The documentation will be available under the Docs tool.

You can search and click over all the functions you have loaded from all your imported docs to see the details.

Currently it shows fns meta, arguments, returns, and call examples.

![demo](./images/flow_docs_browser.png)

## TIPS

If you are using the emacs integration you can do `C-c C-f d` (flow-storm-show-current-var-doc) to show the current function documentation 
in the debugger.
