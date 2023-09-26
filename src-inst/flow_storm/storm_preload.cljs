(ns flow-storm.storm-preload
  (:require [cljs.storm.tracer]
            [flow-storm.api :as fs-api]))

(js* "try {
         cljs.storm.tracer.trace_expr_fn=flow_storm.tracer.trace_expr_exec;
         cljs.storm.tracer.trace_fn_call_fn=flow_storm.tracer.trace_fn_call;
         cljs.storm.tracer.trace_fn_return_fn=flow_storm.tracer.trace_fn_return;
         cljs.storm.tracer.trace_bind_fn=flow_storm.tracer.trace_bind;
         cljs.storm.tracer.trace_form_init_fn=flow_storm.tracer.trace_form_init;
         console.log(\"ClojureScriptStorm functions plugged in.\");
       } catch (error) {console.log(\"ClojureScriptStorm not detected.\")}")

(fs-api/remote-connect {})
