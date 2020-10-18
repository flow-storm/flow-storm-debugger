# Developers document (WIP)

For the socket protocol description take a look at [here](./protocol.md)

# Building and Running

For developing flow-storm-debugger you need:
- Clojure cli tool
- Shadow-cljs

The [Makefile](../Makefile) contains a bunch of tasks for developing and releasing flow-storm-debugger.

```bash

make watch-ui # Starts shadow-cljs watch for the ui

make watch-css # Starts garden css watch for the ui

make run # Run the debugger server

```
