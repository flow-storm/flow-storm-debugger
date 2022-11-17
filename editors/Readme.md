# FlowStorm editors/IDEs integration tips

## Emacs

*Support if for Clojure only so far*, ClojureScript comming soon.

There is currently a very minimal Emacs integration defined in flow-storm.el.

You can load it by adding `(load "flow-storm.el")` at the end of your `.emacs.d/init.el`.

It will provide the following commands :

- `C-c C-f s` (flow-storm-start) : Run the debugger
- `C-c C-f x` (flow-storm-stop) : Close the debugger
- `C-c C-f n` (flow-storm-instrument-current-ns) : Instrument the current namespace
- `C-c C-f f` (flow-storm-instrument-current-form) : Evaluate the current top level form instrumented (just re evaluate it normally to uninstrument it)
- `C-c C-f t` (flow-storm-tap-last-result) : Will just `(tap> *1)`

Whenever you [un]instrument a function or a entire namespace it will show on the flow-storm browser, in the instrumentation panel. So you
can look there to keep track of what you have currently instrumented.

## VSCode

## IntelliJ IDEA (Cursive)
