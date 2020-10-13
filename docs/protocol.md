# Debugger socket message protocol

## Packet format

### :flow-storm/init-trace

- :flow-id [REQ]
- :form-id [REQ]
- :form-flow-id [REQ]
- :form [REQ]
- :args-vec [OPT]
- :fn-name [OPT]

#### Example

```clojure
[:flow-storm/init-trace {:flow-id 1
                         :form-id 1267089144
                         :form-flow-id 321
                         :form "(defn bar [] (let [a 10] (->> (range (foo a a)) (map inc) (filter odd?) (reduce +))))"
                         :args-vec []
                         :fn-name "bar"}]
```

### :flow-storm/add-trace

- :flow-id [REQ]
- :form-id [REQ]
- :form-flow-id [REQ]
- :coor [REQ]
- :result [REQ]

#### Example

```clojure
[:flow-storm/add-trace {:flow-id 1
                        :form-id 1267089144
                        :form-flow-id 321
                        :coor [3 2 1 1 1]
                        :result "10"}]
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
