# Flow-storm debugger

A Clojure and ClojureScript debugger with some unique features.

![demo](./docs/images/demo.gif)

[![Clojars Project](https://img.shields.io/clojars/v/jpmonettas/flow-storm-debugger.svg)](https://clojars.org/jpmonettas/flow-storm-debugger)

## Features

- **Clojure and ClojureScript** (browser, nodejs and react-native) support.
- **Step** through expressions **forward and backward in time**.
- **Exceptions** debugging.
- **Locals** inspection.
- **Multiple flows** tracing (see [flows](#flows)).
- **Save, load and share** your debugging sessions (see [saving and loading](#saving-and-loading)).
- Trace **reference state changes**.
- **Call tree** execution analyzer (see [call tree](#call-tree)).
- **Layers** execution analyzer (see [layers](#layers)).
- **Inspect and explore** large expression **results** by pprinting and a collapsible tree.
- Multiple ways of **jumping in time**.
- Library code tracing.
- And more...

## Running the debugger

```bash
clj -Sdeps '{:deps {jpmonettas/flow-storm-debugger {:mvn/version "0.5.0"}}}' -m flow-storm-debugger.main
```

And that's it !! One instance of the debugger is enough for all your Clojure and ClojureScript projects.

## Instrumenting your code

#### TLDR;

Add [![Clojars Project](https://img.shields.io/clojars/v/jpmonettas/flow-storm.svg)](https://clojars.org/jpmonettas/flow-storm) to your project.

```clojure
(require '[flow-storm.api :as fsa])

;; connect to the debugger
(fsa/connect)

;; trace something
#trace
(->> (range)
	 (map inc)
	 (take 10)
	 (reduce +))

```

For more details on connecting to the debugger and instrumenting your code check [flow-storm](https://github.com/jpmonettas/flow-storm).

## Users Guide

### General overview

Flow-storm is a trace based debugger. This means when you run your instrumented code it will not block execution,
instead, it will trace what is happening as it execute.

It is made of two components :

- a [instrumentation library](https://github.com/jpmonettas/flow-storm), which you will need to add to your project dev dependencies
in order to instrument your code.
- this debugger, which will collect the traces and provide you tools to analyze them.

### Debugger Tools

The debugger currently provides four tools to help you analyze your instrumented code traces. 
(For ways of instrumeting your code take a look at [here](https://github.com/jpmonettas/flow-storm))

Each tool will be explained in more detail in the next sections but this is a small summary of how can they help you :

- [Flows](#flows) will help you analyze how your code executes. Provides stepper like functionality, function call analisys, locals, expressions results and a bunch of ways for moving thru time.
- [Refs](#refs) will help you analyze your refs (atoms, vars, ...) state changes by allowing you to step thru them and see the differences.
- [Taps](#taps) shows you all values your connected process generated via clojure `tap>`. You can inspect them pprinted or with a collapsible tree.
- [Timeline](#timeline) shows you a overview of everything the debugger has received ordered by their timestamps, also allows you to jump into deeper analysis of whatever you are interested in.

#### Flows <a name="flows"></a>

Flows is going to be your stepper, but a stepper that can jump around in time.

![Flows](./docs/images/flows.png)

There are a bunch of tools packed in that screenshot, so I will try to explain them separately.

First, every execution of your instrumented code will get its own tab, and can be analyzed independently.

**The top bar** contains the main flow controls and is divided in three.

The top left buttons let you step the execution forward and backwards by one step, and you can quickly restart
the execution by using the restart button.

The middle part shows the current position on the flow so `3/672` means that 672 traces had been collected for this flow and
we are currently on trace `3`.

Finally the top right buttons let you load or save flows to files. This can be useful for sharing them so other people can take a look
or just because you want to analyze them later.

Next we have the **code panel**.

![Code](./docs/images/flows-factorial-code.png)

The code panel shows the relevant code for this flow, marking in red the current expression.

You can analyze the current expression result by using the **result panel** :

![Result](./docs/images/flows-factorial-results.png)

Any value in the result panel can be visualized pprinted or with a collapsible tree. 
This can be toggled on/off by the small button in the top left corner.

The bottom right panel contains the **locals panel** which shows the bindings values available for the current expression.

![Locals](./docs/images/locals.png)

The first row (the one with the right arrow) is special, and always shows the current expression result.
Clicking on any row will show that value in the result panel.

In a different tab next to the **code panel** lays the **layers** and **tree** panels.

![Layers](./docs/images/flows-factorial-layers.png)

The **layers panel**  is useful for analyzing iterations, doesn't matter if they are loops or recursive functions.

When loops are executed, expressions inside a loop are executed multiple times, possibly returning
different values or "layers" in time.

The layers tools allows you to inspect all the values the current expression evaluated to in different iterations.

Clicking on any layer will also move the debugger to that point in time.

The **tree panel** shows how all the functions in this flow are being called, their arguments and their return values.

![Tree](./docs/images/flows-factorial-tree.png)

Clicking on function calls or returns will moves the debugger to that position in time.

In case any errors or exceptions happens while executing traced code, the **errors panel** will popup and 
the debugger will automatically position itself right on the expression that throwed the error.

![demo](./docs/images/errors.png)

Clicking on errors will move the debugger to that point in time.

##### Flows keyboard bindings

Key | Command 
-----|---------------------
x    | Close selected flow
X    | Close all flows

#### Refs <a name="refs"></a>

![Refs](./docs/images/refs.png)

#### Taps <a name="taps"></a>

![Taps](./docs/images/taps.png)

#### Timeline <a name="timeline"></a>

![Timeline](./docs/images/timeline.png)

### Debugger command line options and customizations

The debugger accept some command line options you can use to configure some aspects of it :

```bash
clj -Sdeps '{:deps {jpmonettas/flow-storm-debugger {:mvn/version "0.5.0-SNAPSHOT"}}}' -m flow-storm-debugger.main --help

Usage : flow-storm-debugger [OPTIONS]
  -fs, --font-size size  13     Font Size
  -p, --port PORT        7722   Port number
  -t, --theme THEME      :dark  Theme, can be light or dark
  -h, --help

```

For example you can choose between light or dark themes :

![themes](./docs/images/themes.png)

## Developers section

If you are interested in developing flow-storm-debugger take a look at [here](./docs/devs.md).
