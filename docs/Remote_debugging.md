## Clojure remote debugging

For cases where you need to run the debuggee and the debugger on different machines (like for debugging a phone application),  first start a debugger with :

```
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "RELEASE"}}}' -X flow-storm.debugger.main/start-debugger
```

and then your debuggee only needs `com.github.jpmonettas/flow-storm-inst` dependency :

```bash
clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-inst {:mvn/version "RELEASE"}}}'
```

on your debuggee you can use `flow-storm.api/remote-connect` like this:


```clojure
(require '[flow-storm.api :as fs-api]) ;; require the api as usual

(fs-api/remote-connect) ;; defaults to localhost:7722

;; or

(fs-api/remote-connect {:host "localhost" :port 7722}) ;; you can specify host and port if you want

#rtrace (+ 1 2 (* 2 3)) ;; trace something
```
