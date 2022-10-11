# Changelog

## master (unreleased)
	
## New Features
	
	- Add support for core.async/go blocks instrumentation
	
### Changes
	
	- Immediately highlihgt the first trace when creating a flow in the debugger
	- Remove unnecessary first-fn-call-event
	
### Bugs fixed

    - Alt+Tab now works on MacOs for switching between the debugger and the repl windows (thanks to Lucy Wang @lucywang000) 
	
## 3.0.236 (7-10-2022)
	
## New Features
	
### Changes
    
	- Improves theming
	- Fix dynamic vars not being re-evaluated as dynamic in cljs
	
### Bugs fixed

    - Fix dynamic vars not being re-evalueated as dynamic in ClojureScript
	- Fix timeout issues on remote connection
	
## 3.0.231 (6-10-2022)
	
## New Features
	
	- Add `Instrument form without bindings` to form context menu
	- Add got to last trace on code loops context-menu
	
### Changes
    
	- Full [un]instrumentation synchronization between the browser and #trace (Clojure only)
	- Now you can [un]instrument single vars from the browser, even if they where defined at the repl (Clojure only)
	- Improved Single var [un]instrumentation from the browser (Clojure and ClojureScript)
	
### Bugs fixed
        
	- Fix show-error on the clojure local path
	
## 3.0.216 (29-9-2022)
	
## New Features
	
	- flow-storm.api/cli-run now supports :flow-id key
	
### Changes
    
### Bugs fixed

	- Windows pprint form problem
	- Bunch of minor small bug fixes

## 3.0.208 (28-9-2022)
	
## New Features
	            
### Changes

	- Instrummented code should run much faster due to removed unnecessary runtime ctx rebinding
	
### Bugs fixed

	- Fix browser navigation after instrumentation bug

## 3.0.198 (24-9-2022)
	
## New Features
	
	- Automatic event retention. For ClojureScript or remote Clojure you don't need to remote-connect by hand
	  when you want to capture traces before the debugger is connected. Just need to require flow-storm.api on your main.
	- Automatic ui cleaning on reconnection
	
### Changes
	
	- Remote connection now accepts :debugger-host and :runtime-host keys (check out the documentation)
	
### Bugs fixed
	
	- Fix the more button on value inspector
	- Fix for infinite sequence handling
	
## 3.0.188 (22-9-2022)
	
## New Features
	
	- Automatic remote connection management. The re-connect button was removed from the toolbar since it isn't needed anymore
	
### Changes
	
### Bugs fixed

	- Fix javafx platform not initialized exception when there is a error connecting to a repl
	
## 3.0.184 (21-9-2022)
	
## New Features
        
### Changes
	
### Bugs fixed

    - Add support for remote debugging without repl connection (clojure and clojurescript)
	- Show nrepl errors on the UI
    - Fix ClojureScript re-run flow
	- Fix a deadlock caused by the event system
	
## 3.0.173 (15-9-2022)
	
## New Features
        
	3.0 is a full redesign! So it is full of changes, and fixes. Most remarkable things are :
	
	- ClojureScript is feature par with Clojure now, so every feature is available to both languages.
    - Remote debugging can be accomplished by just connecting to nrepl server (socket repl support on the roadmap)
    - A programable API (https://jpmonettas.github.io/flow-storm-debugger/user_guide.html#_programmable_debugging)
    - Enables the posibility to integrate it with IDEs/editors

### Changes

### Bugs fixed	
	
## 2.3.141 (15-08-2022)
	
## New Features

	* Add inspect for locals
	* Can jump to any index by typing the idx number in the thread controls

### Changes

	* Locals print-length is now 20 and print-level 5
	* Make the value inspector show more info on dig nodes

### Bugs fixed

## 2.3.131 (10-08-2022)

## New Features

    * Add a proper lazy and recursive value inspector
	* Add tap tool (support for tap>)
	* New functions for shutting down the debugger and connections gracefully.
	  When starting with `flow-storm.api/local-connect` or `flow-storm.api/remote-connect` you can shut it down with `flow-storm.api/stop` 
	  When starting a standalone debugger with `flow-storm.debugger.main/start-debugger` you can shutdown with `flow-storm.debugger.main/stop-debugger`

    * Add support for light and dark themes, in selected or automatic mode. Checkout the user guide for more info. (thanks to Liverm0r!)
	* Thread tabs can be closed and reordered
	
### Changes

	* The entire debugger was refactored to manage state with mount
	
### Bugs fixed

    * Fix #28 - Callstack tree args and ret listviews should expand to the bottom
	* Fix #34 - instrument-forms-for-namespaces not instrumenting (def foo (fn [...] ...))
	
## 2.2.114 (07-07-2022)

## New Features

	* Add bottom bar progress indicator when running commands
	* Add reload tree button on callstack tree tabs
	* Add search bar to functions list
	* This release also contains big internal refactors	to make the codebase cleaner and more efficient
	
### Changes

	* Automatically deref all reference values in traces
	* Automatically change to flow tab on new flow
	
### Bugs fixed

## 2.2.99 (10-06-2022)

## New Features

	* Add jump to first and last traces on thread controls (useful for exceptions debugging)
	* Add print-level and print-meta controls on pprint value panels
    * Improve re-run flow UX
	* Namespace instrumentation now accepts :verbose? to log known and unknown instrumentation errors details
	* Add flow-storm.api/uninstrument-forms-for-namespaces to undo instrument-form-for-namespaces instrumentation
	* Add ctx menu on locals to define vars from values
	* Add browser namespaces instrumentation/uninstrumentation
	* Add browser instrumentation synchronization (for everything but #trace)
	* Add #rtrace0 ... #rtrace5, like #rtrace but with different flow-ids
	* Add double clicking on flows functions window executes show function calls
	
### Changes
    
	* Remove flow-storm.api/run since #rtrace(runi) is enough
	* Flows functions window now have checkboxes for selecting fncall arguments to print
	
### Bugs fixed
	
	* Fix re run flow for #rtrace case
	* Fix local binding instrumentation and debugging
	
## 2.2.68 (10-06-2022)

## New Features
	
	* Add def value button on every value panel to define the value so you can work with it at the repl
	* Add namespaces browser with instrumentation capabilities
	
### Changes
    
### Bugs fixed

## 2.2.64 (09-06-2022)

## New Features
	
	* Add conditional tracing via #ctrace and ^{:trace/when ...} meta
	
### Changes
    
### Bugs fixed

## 2.2.59 (06-06-2022)

## New Features

### Changes
    
### Bugs fixed

	* Fix run-command for the local connection path

## 2.2.57 (06-06-2022)

## New Features

	* Add Clojurescript support
	* Remote debugging via `flow-storm.api/remote-connect` and `flow-storm.api/cli-run`
    	
### Changes

	* `flow-storm.api/cli-run` now accepts :host and :port

### Bugs fixed

## 2.0.38 (02-05-2022)

## New Features

	* Add styles customization via a user provided styles file
	* The debugger can instrument and debug itself
	
### Changes

### Bugs fixed

## 2.0.0 (18-04-2022)

	
