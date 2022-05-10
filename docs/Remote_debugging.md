clj -Sdeps '{:deps {com.github.jpmonettas/flow-storm-dbg {:mvn/version "2.1.43"}}}' -X flow-storm.debugger.main/start-debugger

clj -Sdeps '{:deps {org.clojure/clojurescript {:mvn/version "1.11.4"} com.github.jpmonettas/flow-storm-inst {:mvn/version "2.1.43"}}}' \
        -X flow-storm.api/cli-run :instrument-ns '#{"cljs."}'           \
                              :profile ':light'     :verbose? true                \
                              :require-before '#{"cljs.repl.node"}' \
                              :fn-symb 'cljs.main/-main' :host '"localhost"' :port 7722           \
                              :fn-args '["-t" "nodejs" "/home/jmonetta/demo/org/foo/myscript.cljs"]';
