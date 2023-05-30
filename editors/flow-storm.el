(require 'cider)

(defcustom flow-storm-theme "dark"

  "The theme to use when starting the debugger via flow-storm-start"

  :type 'string
  :options '("dark" "light")
  :group 'flow-storm)


(defmacro ensure-connected (&rest forms)
  `(if (cider-connected-p)
	   (progn ,@forms)
	 (message "Cider should be connected first")))


(defun flow-storm-start ()

  "Set up the debugger and show the GUI."

  (interactive)
  (ensure-connected
   (cider-interactive-eval (format "((requiring-resolve 'flow-storm.api/local-connect) {:theme :%s})" flow-storm-theme))))

(defun flow-storm-stop ()

  "Close the debugger window and clean its state."
  
  (interactive)  
  (ensure-connected
   (cider-interactive-eval "((requiring-resolve 'flow-storm.api/stop))")))

(defun flow-storm-instrument-current-ns (arg)

  "Instrument the namespace you are currently on."
  
  (interactive "P")  
  (ensure-connected
   (let* ((prefix (eq (first arg) 4))
		  (current-ns (cider-current-ns))
		  (inst-fn-name (if prefix
							"uninstrument-namespaces-clj"
						  "instrument-namespaces-clj"))
		  (clj-cmd (format "(let [inst-ns (requiring-resolve 'flow-storm.api/%s)]
                              (inst-ns #{\"%s\"} {:prefixes? false}))"
						   inst-fn-name
						   current-ns)))
     
     (cider-interactive-eval clj-cmd))))

(defun flow-storm-instrument-current-form ()

  "Instrument the form you are currently on."
  
  (interactive)  
  (ensure-connected
   (let* ((current-ns (cider-current-ns))
		  (form (cider-defun-at-point))
		  (clj-cmd (format "(flow-storm.api/instrument* {} %s)" form)))
	 (cider-interactive-eval clj-cmd nil nil `(("ns" ,current-ns))))))

(defun flow-storm-tap-last-result ()

  "Tap *1 (last evaluation result)"
  
  (interactive)
  (ensure-connected
   (cider-interactive-eval "(tap> *1)")))

(defun flow-storm-show-current-var-doc ()

  "Show doc for var under point"

  (interactive)

  (cider-try-symbol-at-point
   "Flow doc for"
   (lambda (var-name)
	 (let* ((info (cider-var-info var-name))
			(fn-ns (nrepl-dict-get info "ns"))
			(fn-name (nrepl-dict-get info "name"))
            (clj-cmd (format "(flow-storm.api/show-doc '%s/%s)" fn-ns fn-name)))
	   (when (and fn-ns fn-name)
         (cider-interactive-eval clj-cmd))))))

(defun flow-storm-rtrace-last-sexp ()

  "#rtrace current form"

  (interactive)

  (ensure-connected
   (let* ((current-ns (cider-current-ns))
		  (form (cider-last-sexp))
		  (clj-cmd (format "(flow-storm.api/runi {} %s)" form)))
	 (cider-interactive-eval clj-cmd nil nil `(("ns" ,current-ns))))))


(defvar cider-flow-storm-map
  (let (cider-flow-storm-map)
    (define-prefix-command 'cider-flow-storm-map)
	
    (define-key cider-flow-storm-map (kbd "s") #'flow-storm-start)

	(define-key cider-flow-storm-map (kbd "x") #'flow-storm-stop)
	
	(define-key cider-flow-storm-map (kbd "n") #'flow-storm-instrument-current-ns)
    
	(define-key cider-flow-storm-map (kbd "f") #'flow-storm-instrument-current-form)

	(define-key cider-flow-storm-map (kbd "t") #'flow-storm-tap-last-result)

	(define-key cider-flow-storm-map (kbd "d") #'flow-storm-show-current-var-doc)

	(define-key cider-flow-storm-map (kbd "r") #'flow-storm-rtrace-last-sexp)
	
    cider-flow-storm-map)
  "CIDER flow-storm keymap.")

(define-key cider-mode-map (kbd "C-c C-f") 'cider-flow-storm-map)
