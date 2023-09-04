(ns flow-storm.preload
  (:require [flow-storm.api :as fs-api]
            [flow-storm.tracer :as tracer]))

;; TODO: make this conditional
(js* "cljs.storm.tracer.trace_expr_fn=flow_storm.tracer.trace_expr_exec;")
(js* "cljs.storm.tracer.trace_fn_call_fn=flow_storm.tracer.trace_fn_call;")
(js* "cljs.storm.tracer.trace_fn_return_fn=flow_storm.tracer.trace_fn_return;")
(js* "cljs.storm.tracer.trace_bind_fn=flow_storm.tracer.trace_bind;")
(js* "cljs.storm.tracer.trace_form_init_fn=flow_storm.tracer.trace_form_init;")

(fs-api/remote-connect {})
