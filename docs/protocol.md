# Debugger socket message protocol (WIP)

## Packet format

### :flow-storm/init-trace

- :flow-id [REQ]
- :form-id [REQ]
- :form-flow-id [REQ]
- :form [REQ]
- :fixed-flow-id-starter? [OPT] Signals that this is the starting trace of a fixed flow-id trace.
- :args-vec [OPT]
- :fn-name [OPT]
- :timestamp [REQ]

#### Example

```clojure
[:flow-storm/init-trace {:flow-id 1
						 :form-id 1267089144
						 :form-flow-id 321
						 :form "(defn bar [] (let [a 10] (->> (range (foo a a)) (map inc) (filter odd?) (reduce +))))"
						 :args-vec []
						 :fn-name "bar"
						 :timestamp 1605808610611}]
```

### :flow-storm/add-trace

- :flow-id [REQ]
- :form-id [REQ]
- :form-flow-id [REQ]
- :coor [REQ]
- :result [REQ]
- :err [OPT]  A map like {:error/message "..."} in case a exception ocurred evaluating this form. The :result is not present when this key is.
- :timestamp [REQ]

#### Example

```clojure
[:flow-storm/add-trace {:flow-id 1
						:form-id 1267089144
						:form-flow-id 321
						:coor [3 2 1 1 1]
						:result "10"
						:timestamp 1605808610611}]
```

### :flow-storm/add-bind-trace

- :flow-id [REQ]
- :form-id [REQ]
- :form-flow-id [REQ]
- :coor [REQ]
- :symbol [REQ]
- :value [REQ]

#### Example

```clojure
[:flow-storm/add-bind-trace {:flow-id 1
							 :form-id 1267089144
							 :form-flow-id 321
							 :coor [3]
							 :symbol "a"
							 :value "10"}]
```

### :flow-storm/ref-init-trace 

- :ref-id [REQ]
- :ref-name [OPT]
- :init-val [REQ]
- :timestamp [REQ]

#### Example

```clojure
[:flow-storm/ref-init-trace {:ref-id 1
                             :ref-symb :person-state
                             :init-val {:name "foo" :age 37}
							 :timestamp 1605808610611}]
```

### :flow-storm/ref-trace

- :ref-id [REQ]
- :patch [REQ]
- :timestamp [REQ]

#### Example

```clojure
[:flow-storm/ref-trace {:ref-id 1
                        :patch [[[:age] :r 38]]
						:timestamp 1605808610611}]
```

### :flow-storm/tap-trace

- :tap-id [REQ]
- :tap-name [OPT]
- :value [REQ]
- :timestamp [REQ]
						
#### Example
```clojure
[:flow-storm/tap-trace {:tap-id 1
                        :tap-name "test-tap"
                        :value {:value 42}
						:timestamp 1605808610611}]
```
