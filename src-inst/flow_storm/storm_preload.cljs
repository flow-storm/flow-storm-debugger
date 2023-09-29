(ns flow-storm.storm-preload
  (:require [cljs.storm.tracer]
            [flow-storm.api :as fs-api]))

;; setup storm callback functions
(js* "try {
         cljs.storm.tracer.trace_expr_fn=flow_storm.tracer.trace_expr_exec;
         cljs.storm.tracer.trace_fn_call_fn=flow_storm.tracer.trace_fn_call;
         cljs.storm.tracer.trace_fn_return_fn=flow_storm.tracer.trace_fn_return;
         cljs.storm.tracer.trace_bind_fn=flow_storm.tracer.trace_bind;
         cljs.storm.tracer.trace_form_init_fn=flow_storm.tracer.trace_form_init;
         console.log(\"ClojureScriptStorm functions plugged in.\");
       } catch (error) {console.log(\"ClojureScriptStorm not detected.\")}")

(fs-api/setup-runtime)
;; automatically try to make a "remote" connection
;; to localhost:7722 (the default)
;; Keep this is for the case where no repl is going to be available
;; so we fire remote connect con preload
(fs-api/remote-connect {})
