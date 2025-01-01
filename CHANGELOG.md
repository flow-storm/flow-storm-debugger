# Changelog

## master (unreleased)
	
### New Features

### Changes 
    
### Bugs fixed

## 4.1.0 (01-01-2025)
	
### New Features

    - Add configurable auto jump to exceptions
    - New eql-query-pprint visualizer
    - New webpage visualizer
    - Add support for setting the visualizer via flow-storm.api/data-window-push-val
    - Add debugger/bookmark to set bookmarks from code and also quickly jump to the first one
    - Enable multiple ClojureScript runtimes <> multiple debuggers via "flowstorm_ws_port"
    
### Changes

    - Configurable thread tab auto-update every 1sec (on by default)
    - Remove middleware dependency on cider-nrepl-middleware
    - Aspect extractors signature deprecation. Now it recieves the object and a map with extras
    
### Bugs fixed

    - Fix taps "search value on flows"
    - Fix middleware not working with nrepl > 1.3.0-beta2
    - Fix middleware for ClojureScript
    - Fix identity and equality powerstepping for unwinds

## 4.0.2 (25-11-2024)
	
### New Features
    
### Changes

    - Change flow exceptions combo to only show exception root instead of all unwinds
 
### Bugs fixed

## 4.0.1 (12-11-2024)
	
### New Features
    
### Changes
 
### Bugs fixed

    - Fix middleware for Cider Storm

## 4.0.0 (11-11-2024)
	
### New Features 

    - DataWindow system
    - Outputs tool (Clojure only)
    
### Changes

    - Improved flow search for DataWindows support
    - Update javafx to "21.0.4-ea+1"

### Bugs fixed

    - Fix Quick Jump on multiple flows
    - Fix UI displaying of multi line strings in varios places
    - Fix printers enable state on printers screen open

## 3.17.4 (24-09-2024)
	
### New Features 
    
### Changes

### Bugs fixed
    
    - Fix thread trace count not updating on thread tab refresh
    - Fix ctx menu showing on forms background with left click
    - For ns reload functionality don't read forms with *read-eval* false
    
## 3.17.3 (27-08-2024)
	
### New Features 
    
### Changes

    - Use quoted-string-split on editor open commands
        
### Bugs fixed
    
## 3.17.2 (06-08-2024)
	
### New Features 
    
### Changes

    - Improved multi-thread timeline colors
    - Do not deref all derefables automatically, just atoms, refs, agents and vars and pending realized ones. Leave the reast to snapshot-value system
    
### Bugs fixed

    - Do not deref delays when tracing values
    
## 3.17.1 (26-07-2024)
	
### New Features 

    - Add before-reload and after-reload hooks for the namespace reloading utilities
        
### Changes

    - Update tutorial
    
### Bugs fixed

    - Improve ns auto reload for namespaces loaded with load-file
    
## 3.17.0 (23-07-2024)
	
### New Features 
    
    - Make Search, Printer and Multi-thread-timeline tools work per flow
    - Pake Printer use the multi-thread timeline if available to allow for thread interleaving print debugging
    - New fn-call power stepper
    - Optional automatic namespaces reload after changing prefixes for storm
    
### Changes

    - Flows UI refactor
    - Make stack click jump to beg of the fn instead of prev step
    
### Bugs fixed

## 3.16.0 (03-07-2024)
	
### New Features

    - Add printer support to print on all threads
    - Add printers transform expression (Clojure only)
    
### Changes

    - UI refactor for toolbars
    - UI refactor for printers and multi-thread timeline
    - Set a UI limit of 200 on the Exceptions menu entries to improve UI responsiveness under deep recursions exceptions.
    
### Bugs fixed

    - Fix #181 close children windows if main window closed
    - Fix out of boundaries on step-prev-over and step-next-over
    
## 3.15.8 (21-06-2024)
	
### New Features
    
### Changes 

    - Add recorded threads counter on the threads menu button
    - Clean initialization console logs for ClojureScript
    
### Bugs fixed

    - Fix remote debugging for vanilla
    - Fix windows paths handling in middleware

## 3.15.7 (13-06-2024)
	
### New Features
    
### Changes 
    
### Bugs fixed

    - Fix FlowStorm not starting in OSX because of wrong taskbar icon url

## 3.15.6 (13-06-2024)
	
### New Features
    
    - Add jump to first and last occurrences buttons to power stepping controls
    
### Changes 
        
    - #rtrace now clears the current recording flow before running again
    - Upgrade ikonli-javafx to 12.3.1
    - Add FlowStorm icon to the toolbar and taskbar
    - Namespace FlowStorm resources so they don't collide with other resources
    
### Bugs fixed

    - Fix thread-id lost from thread tab after tab refresh
    - Fix running with nrepl >= 1.2

## 3.15.5 (11-05-2024)
	
### New Features
    
### Changes 

### Bugs fixed

    - Make the code stepper the default tab instead of the tree

## 3.15.4 (06-05-2024)
	
### New Features
    
### Changes 

### Bugs fixed

    - Fix auto tab switch on code jump
    
## 3.15.3 (02-05-2024)
	
### New Features
    
    - Add Goto file:line on the menu
    - Show open in editor for any form that contains line meta
    
### Changes 

    - Show value inspector `def` on the same screen
    - Remove unnamed anonymous functions from Quick Jump 
    - Remove docs builder (now on dyna-spec)
    - Remove tools.build as a dependency
    - Make the coed stepper the default tab instead of the tree

### Bugs fixed

## 3.15.2 (18-04-2024)
	
### New Features

    - Timelines notification updates and thread refresh button
    
### Changes 

### Bugs fixed
            
## 3.15.1 (16-04-2024)
	
### New Features
    
### Changes 

### Bugs fixed

    - Don't run #rtrace when recording is paused
    - Fix vanilla-instrument-var for clojurescript
    - Fix #rtrace for clojurescript
    
## 3.15.0 (16-04-2024)
	
### New Features

    - Implement storm instrumentation management in the browser
    - Add "Copy qualified function symbol" and "Copy function calling form" to form menu
    - Add only-functions? to multi-thread timeline
    
### Changes 

    - The record flow selector combo is now next to the tabs
    - The threads selector is now a menu button instead of a list view to gain some UI space
    - Automatically open first recorded thread
    - Improved graphical tutorial
    - Help menu with tutorial and user's guide
    - Don't start recording by default
    
### Bugs fixed

## 3.14.0 (30-03-2024)
	
### New Features

    - Add support to open forms in editors
    - Add an option to set threads limit to throw
    - There is a new, much more powerfull global search system instead of the old per timeline search.
    - An improved Flows system that allows the user to record into multiple flows.
    - Implement an improved loop navigation system
    
### Changes 
    
    - Improve pprint panes. Now all of the also show exceptions
    - #rtrace0 ... #rtrace5 reader tags were removed since they aren't needed anymore with the new flow system
    
### Bugs fixed

    - Theme dialog panes
    - Fix inspector power stepper

## 3.13.1 (21-03-2024)
	
### New Features
      
### Changes 

    - Bring back the clear recordings button to the toolbar
    - A faster search system for ClojureScript (for every functionality that searches on the timeline)
    
### Bugs fixed

## 3.13.0 (19-03-2024)
	
### New Features

    - Implemented thread trace limit as a fuse for infinite loops/recursion
    - All possibly expensive slow operations are now cancellable. This includes :
      - List functions calls
      - List prints with the printer
      - Values search
      - Multi-thread timeline
      - All power stepping tools
      - Quick jump
    - All functions that collect from the timeline will report as they run, no need to wait to the end. This includes :
      - List functions calls
      - List prints with the printer
      - Multi-thread timeline
      - Add a menu bar to help with discoverability
      - Add functions calls ret render checkbox that update on change
      
### Changes 
    
    - Change Exceptions from a ComboBox to MenuButton
    - Functions calls auto update on selection
    
### Bugs fixed

## 3.12.2 (05-03-2024)
	
### New Features
    
### Changes 
        
### Bugs fixed

    - Fix functions lists for nil returns

## 3.12.1 (05-03-2024)
	
### New Features
    
### Changes 
    
    - Improved functions pane.
    - Change stack double click behavior as per #150
    - Display bookmarks and inspector windows centered on main window
    - Display all dialogs centered on main window
    
### Bugs fixed

## 3.12.0 (15-02-2024)
	
### New Features

    - Add identity-other-thread power stepper
    
### Changes 

    - Big refactor with a ton of improvements to the repl API
    
### Bugs fixed

## 3.11.2 (13-02-2024)
	
### New Features

### Changes 
    
### Bugs fixed

    - Fix quick jump

## 3.11.1 (08-02-2024)
	
### New Features

### Changes 

    - Better execution highlighting system
    - Browser and functions calls list views update as they change with the keyboard
    
### Bugs fixed

    - Fix multi-thread timeline search
    - Close context menu if there is one already open
    
## 3.11.0 (01-02-2024)
	
### New Features
    
    - Add exceptions display

### Changes

    - :ex captured exception system removed - superseded by unwind tracing
    
### Bugs fixed
    
    - [IMPORTANT!] Fix timeline structure when functions unwind (requires ClojureStorm >= 1.11.1-19) 
    - Fix value inspector stack showing val instead of key
    
## 3.10.0 (29-01-2024)
	
### New Features

    - Add a timeline index column to the bookmarks table
    - Add a thread timeline index column to the multi-thread timeline
    - New same-coord and custom-same-coord power steppers
    - New context menu to find recordings from unhighlighted text ("Jump forward here", etc)
    
### Changes

    - Make tooltips have a 400ms delay instead of default 1000ms
    - Display timeline indexes zero based (used to be 1 based) to be less confusing when using the repl api
    - Add thread-ids everywhere thread-names show
    - Show form line next to form namespace if known
    - New stepper buttong layout
    - Centralize bookmarks system (one bookmark system for all flows and threads)
    - Step over doesn't stop at function boundaries anymore
    - Improved value inspector performance on remote runtimes (via :val-preview)

### Bugs fixed

    - Pasting in quick jump box doesn't fire autocomplete
    - Quick jump should create and/or focus the tab
    - Fix tree view freezes when a node contains too many childs
    - Fix quick-jump on Clojure remote
    
## 3.9.1 (12-01-2024)
	
### New Features 

    - Add print-wrap controls on panes

### Changes

    - Don't automatically switch to the Taps tab on new taps

### Bugs fixed

    - Don't trace false as nil
    - Update hansel to 0.1.81 to fix browser var instrumentation in cljc

## 3.9.0 (19-12-2023)
	
### New Features 

    - Add a bookmarking system
    - Add navigation undo/redo system
    
### Changes

    - Upgrade JavaFX to 21.0.1
    - Push minimal supported JDK version to 17 with a path for using it with 11
    - Improve keyboard event handling system to support different layouts
    - Improve following current selected expression
    - Remove double scrolling in code panes
    - Make code stack pane jump a double-click
    - Support multiple debugger instances running at the same time. Useful for debugging multiple build in cljs.
    
### Bugs fixed

    - Fix "Add to prints" not showing on Vanilla
    - Enter on time box focus the code
    
## 3.8.6 (17-11-2023)
	
### New Features 
            
### Changes

    - Don't print handled exception error messages on std-err since it messes up cider
    
### Bugs fixed
    
    - Capture exceptions on cljs remote connect
    - [Remote] Don't crash the debugger if there is an exception initializing the RT through the repl
    
## 3.8.5 (10-11-2023)
	
### New Features 
            
### Changes
        
### Bugs fixed

    - Patch for clojure.pprint bug https://ask.clojure.org/index.php/13455/clojure-pprint-pprint-bug-when-using-the-code-dispatch-table

## 3.8.4 (09-11-2023)
	
### New Features 
    
    - Ctrl-f copies the current qualified funcion symbol to the clipboard
    - Ctrl-Shift-f copies the current function call form
    - Right clicking on a tree node now shows "Copy qualified function symbol"
    - Right clicking on a tree node now shows "Copy function calling form"
    
### Changes
    
    - Big codebase refactor for make it cleaner
    - Improved search functionality (faster and with better UX)
    
### Bugs fixed

    - Fix functions list not showing entire functions names when they are large
    
## 3.8.3 (25-10-2023)
	
### New Features 

    - Add dynamic font inc/dec and theme rotation
    
### Changes

### Bugs fixed

## 3.8.2 (23-10-2023)
	
### New Features 
    
### Changes

    - Upgrading j-system-theme-detector to 3.8.1 to fix a NPE
    - Downgrading JavaFX to 19.0.2 since >20 needs JDK>=17 and we still want JDK11 
    
### Bugs fixed

## 3.8.1 (19-10-2023)
	
### New Features 
    
### Changes

    - Improved code highlighter. Replaces JavaFX standard TextFlow with RichTextFx CodeArea for improved performance.
    - Change hansel to com.github.flow-storm/hansel 0.1.79 for the organization move
    
### Bugs fixed

    - Fix #98 Stepping over big forms is very slow
    
## 3.7.5 (02-10-2023)
	
### New Features 

    - Add function call limits
    
### Changes 

    - Disable functionality that doesn't make sense under storm when working under ClojureStorm or ClojureScriptStorm
    - Improve Printer thread selection so you don't need to constantly re-select thread
        
### Bugs fixed

## 3.7.4 (27-09-2023)
	
### New Features 

    - Add flow-storm.storm-preload for ClojureScriptStorm
    
### Changes 

    - Improved initialization system for remote debugging
    - Reuduce callstack tree nodes args print level and depth for perf (specially on remotes)
    
### Bugs fixed

## 3.7.3 (10-09-2023)
	
### New Features

    - Add multimethod dispatch-val to stack pane
    
### Changes

    - Upgrade hansel to 0.1.78

### Bugs fixed

    - Fix printer goto location without thread selection

## 3.7.1 (06-09-2023)
	
### New Features

### Changes

### Bugs fixed

    - Fix ClojureScript double require issue
    - Fix java.util.ConcurrentModificationException when building timeline

## 3.7.1 (21-08-2023)
	
### New Features

    - Implement quickjump
    - Unblock all breakpoint blocked threads with toolbar and keyboard
    
### Changes
    
### Bugs fixed

    - Fix enabling/disabling of thread breakpoints
    
## 3.7.0 (15-08-2023)
	
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

	
