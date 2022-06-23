# Changelog

## master (unreleased)

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

	
