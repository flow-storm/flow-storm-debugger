# Cider Storm

Cider Storm is an Emacs Cider front-end for the FlowStorm debugger with support for Clojure and ClojureScript.

It brings the time-travel code stepping capabilities of FlowStorm to Emacs, providing an interface 
similar to the Cider debugger one.

Cider Storm isn't trying to re-implement the entire FlowStorm UI, but the most used functionality.
You can always start the full FlowStorm UI if you need the extra tools.

## Pre-requisites

	- cider
	- flow-storm >= 3.6.4
	- when using ClojureStorm, >= 1.11.1-4, or >= 1.12.0-alpha3_2
	
## Installation

First you need to setup FlowStorm as you normally do for your environment. Check https://jpmonettas.github.io/flow-storm-debugger/user_guide.html#_quick_start

Apart from that you need two things. First is to download [cider-storm.el](https://github.com/jpmonettas/flow-storm-debugger/blob/master/editors/cider-storm.el) and load it into emacs, like :

```
(load "/home/user/cider-storm.el")
```

Second is to add the flow-storm nrepl middleware to your list of middlewares automatically loaded by your cider-jack-in commands.

There are multiple ways of accomplishing this depending on your use case, like : 

```
(add-to-list 'cider-jack-in-nrepl-middlewares "flow-storm.nrepl.middleware/wrap-flow-storm")
```

then the middleware will be added every time you do a cider-jack-in. The problem with this approach is that
if you jack-in into a project that doesn't contain FlowStorm on the classpath it will fail.

Another approach is to setup the middlewares like this in your `.dir-locals.el` at the root of each project.

```
((clojure-mode . ((cider-jack-in-nrepl-middlewares . ("flow-storm.nrepl.middleware/wrap-flow-storm"
													  ("refactor-nrepl.middleware/wrap-refactor" :predicate cljr--inject-middleware-p)
													  "cider.nrepl/cider-middleware")))))
```

There are other ways probably depending on what you are using and how you are starting your nrepl servers, but the important thing
is that the middleware should be loaded in the nrepl server for Cider Storm to work.

## Usage

### With ClojureStorm (recommended for Clojure)

If you are using FlowStorm with ClojureStorm, then you don't need to do anything special, you don't even need the FlowStorm UI running.

Start your repl, run your expressions so things get recorded and then when you want to step over a recorded function on emacs
run : M-x cider-storm-debug-fn

This should retrieve a list of all the functions that FlowStorm has recordings for. When you choose one, it should jump to the first 
recorded step for that function, and provide a similar interface to the Cider debugger.

Once the debugging mode is enable you can type `h` to show the keybindings.

### With vanilla FlowStorm

Currently you need to have the FlowStorm UI running (flow-storm.api/local-connect) to use it with vanilla FlowStorm.

After you have your recordings you can explore them following the same instructions described in `With ClojureStorm` .

### With ClojureScript

Currently you need to have the FlowStorm debugger UI connected to use it with ClojureScript.

After you have your recordings you can explore them following the same instructions described in `With ClojureStorm` .

## Tips

### Clear recordings

Same as when using the FlowStorm UI, it is convenient to clear the recordings frequently so you don't get confused with previous recordings.
You can do this from emacs by M-x cider-storm-clear-recordings

### Value inspection

By hitting `i` Cider Storm will inspect the value using the Cider inspector. If you want to inspect values using other inspectors (like the FlowStorm one)
you can hit `t` to tap the value.


