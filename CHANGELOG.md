# Changelog

## master (unreleased)
	
### New Features
    
    - Add "Search value on Flows" to taps
    - Add "Timeline tool" implementation
    - Add power stepping to stepping controls
    - Add "Printer tool" implementation
    
### Changes
    
### Bugs fixed 

    - Fix NPE after closing thread tab 

## 3.6.10 (19-07-2023)
	
### New Features
         
### Changes

    - Upgrade to hansel 0.1.74 for a couple of bug fixes. Check hansel changelog.
    
### Bugs fixed 

## 3.6.9 (06-07-2023)
	
### New Features
         
### Changes
    
### Bugs fixed

    - Fix ability to capture what happens before the debugger connects on ClojureScript 
    
## 3.6.8 (03-07-2023)
	
### New Features

    - Code stepping follow value allows you to step to the next/prev expression that evaluates to the same value. Useful for understanding how values flow through programs
    - Value inspector follow value, same as before.
    - New code stepping search tool
    
### Changes
    
    - The call stack tree search tool was removed since it was buggy and hard to fix because of how javaFx lazy TreeView works
    
### Bugs fixed

## 3.6.7 (30-06-2023)
	
### New Features
        
### Changes

    - Upgrade to hansel 0.1.69 with improved coordinate system
    
### Bugs fixed

    - Fix call tree highlight current frame

## 3.6.6 (29-06-2023)
	
### New Features

    - Add debugger window title configurable via :title and flowstorm.title prop
    - Add support for debugging litteral maps and sets of any size
    
### Changes

    - Upgrades to hansel 0.1.65 and supports new coordinate system
    - Reintroduced #rtrace ^{:thread-trace-limit N}

### Bugs fixed

    - issues/65 GUI icon hover making the icon unreadable

## 3.6.5 (16-06-2023)
	
### New Features
		
### Changes

    - Add flow-storm-find-flow-fn-call to nrepl middleware
    
### Bugs fixed

## 3.6.4 (13-06-2023)
	
### New Features

	- Add flow-storm.nrepl.middleware for editors integration
		
### Changes

	- Improve step out functionality
	
### Bugs fixed

## 3.6.3 (06-06-2023)
	
### New Features
		
### Changes
	
	- Fix flow-storm.runtime.values/value-type for sorte-maps
	- Upgrade hansel to 0.1.63
	
### Bugs fixed

	- Fix step over for the (map some-fn) etc cases
	
## 3.6.2 (01-06-2023)
	
### New Features
		
### Changes
	
	- Exclude org.slf4j/slf4j-nop
	
### Bugs fixed

## 3.6.1 (30-05-2023)
	
### New Features
		
### Changes

	- Make pprint panes a TextArea so we can copy it's content
	- Upgrade hansel to 0.1.60 to fix ClojureScript namespace instrumentation issue
	
### Bugs fixed

## 3.6.0 (19-05-2023)
	
### New Features
	
	- Add functions list refresh button
	- Add step-prev-over and step-next-over buttons and keybindings
	- Pprint panel now display value type
	- Add define all current frame bindings
	- Improve auto-scrolling when stepping on the code tool
	
### Changes
	
	- Keep functions and tree state when switching tabs
	- Much improved value inspector
	- Update hansel to 0.1.56 so #trace (deftest ...) works
	- BREAKING! flow-storm.runtime.values/snapshot-value defmethod was replaced by flow-storm.runtime.values/SnapshotP protocol for performance
	
### Bugs fixed	

	- Fix goto-location
	- Respect flowstorm.startRecording before :dbg on ClojureStorm
	
## 3.5.1 (01-05-2023)
	
### New Features

	- Add "Highlight current frame" on the call tree tool
	- Add stack tab to code stepping tool
	- Add a result pane on the functions list tool
	- Add double click on calls tree tool node steps code
	- Control recording from the UI
	- Add threads breakpoints
	- Add basic keyboard support 
	- Allow DEF button functionality to specify a NS
	
### Changes
	
	- Made search functionality faster and simplified it's code
	- #rtrace automatically opens the code stepping tool in the last position
	- Upgrade hansel to 0.1.54
	- Signal error when trying to use #rtrace with ClojureStorm
	
### Bugs fixed

	- Don't crash if tracer functions are called before the system is fully started
	- Fix Clojure remote debugging race condition
	- Keep the thread list split pane size correct after window resize

## 3.4.1 (17-04-2023)
	
### New Features

### Changes
	
### Bugs fixed

	- [CRITICAL] For remote connections fix repl-watchdog spamming the repl 
	
## 3.4.0 (16-04-2023)
	
### New Features

	- Add support for ClojureStorm
	- Add a separate threads list and thread tabs contain names
	- Add step up button
	- Redesign Flows tools UX
	
### Changes

	- A bunch of performance improvements
	
### Bugs fixed

	- Many bug fixes (sorry for the low detail, this is a big release)

## 3.3.325 (09-04-2023)
	
### New Features
   
### Changes

	- Exclude guava as a transitive dep in tools.build since it breaks shadow-cljs 2.21.0
	- Upgrade hansel to 0.1.50
	
### Bugs fixed
    
## 3.3.320 (16-02-2023)
	
### New Features
   
### Changes

    - Upgrade openjfx to 19.0.2.1
	- Upgrade hansel to 0.1.46
	
### Bugs fixed

## 3.3.315 (30-01-2023)
	
### New Features
   
### Changes

	- Upgrade to hansel 0.1.42 which contains a couple of bug fixes
	
### Bugs fixed
    
## 3.3.313 (29-01-2023)
	
### New Features
   
### Changes

### Bugs fixed

	- Update to hansel 0.1.38 which contains a couple of bug fiexs
	
## 3.3.309 (29-12-2022)
	
### New Features
   
### Changes

	- Improve docs file generation. Generated docs will be put into flow-docs.edn instead of samples.edn since
	  it is less likely to collide. Also the data format has been improved for extensibility
	
### Bugs fixed

## 3.3.307 (26-12-2022)
	
### New Features
   
### Changes

	- Update hansel dependency to 0.1.35
	
### Bugs fixed
    
## 3.3.303 (20-12-2022)
	
### New Features
   
### Changes
		
### Bugs fixed

	- Handle OS theme detector exceptions
	
## 3.3.301 (16-12-2022)
	
### New Features
   
### Changes
	
	- Update hansel to 0.1.31 which contains a critical bug
	- Improve value inspector styles
	
### Bugs fixed
	
	- Fix docs examples display
	
### 3.3.295 (14-12-2022)
	
## New Features
   
   - Add Flow Docs - Generate projects functions documentation by sampling their executions.
   
### Changes

### Bugs fixed
	
	- Fix a ConcurrentModificationException on debuggers_api/reference_frame_data!
	
## 3.2.283 (29-11-2022)
	
## New Features
    
	- Add browser var recursive instrumentation
	
### Changes

### Bugs fixed

	- Update to hansel 0.1.22 to fix ClojureScript go blocks instrumentation
    
## 3.2.271 (16-11-2022)
	
## New Features
    
	- New tap button allows you to tap> any value from the UI (nice to integrate with other tooling like portal)
	- New locals "tap value" allows you to tap> any locals
	
### Changes

    - Migrate to hansel for instrumentation (should be much better than previous instrumentation system)
	- Value inspector navigation bar now shows keys instead of val text
	
### Bugs fixed
    
	- Fix automatic [un]instrumentation watcher
	
## 3.1.263 (18-10-2022)
	
## New Features

### Changes
	    
### Bugs fixed

	- Fix clojure instrument entire namespace for non libraries
	
## 3.1.261 (18-10-2022)
	
## New Features

### Changes
	
	- Remove flow-storm hard dependency on org.clojure/clojurescript artifact. Will lazy require when needed for ClojureScript, assuming the dependency will be provided

### Bugs fixed

## 3.1.259 (17-10-2022)
	
## New Features

	- Add #rtrace ^{:thread-trace-limit X} where X can be a integer. Execution will throw after tracing X times for a trace. Useful for debugging possibly infinite loops
    - Add support for snapshoting mutable values via flow-storm.runtime.values/snapshot-value multimethod
	- Add #tap-stack-trace, to tap the current stack trace wherever you add it
	- Add support for core.async/go blocks instrumentation
	- Add Ctrl+MouseWheel on forms to step prev/next
	
### Changes
	
	- Immediately highlihgt the first trace when creating a flow in the debugger
	- Remove unnecessary first-fn-call-event
	
### Bugs fixed

    - Alt+Tab now works on MacOs for switching between the debugger and the repl windows (thanks to Lucy Wang @lucywang000) 
	- Fix extend-protocol and extend-type instrumentations for ClojureScript
	- Fix instrumentation breaking variadic functions in ClojureScript
	
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

	
