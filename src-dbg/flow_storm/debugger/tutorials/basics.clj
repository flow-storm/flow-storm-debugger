(ns flow-storm.debugger.tutorials.basics
  (:require [flow-storm.debugger.ui.components :as ui]
            [flow-storm.debugger.state :as dbg-state]
            [flow-storm.debugger.ui.utils :as ui-utils]))

(def steps
  [
   "
<b>Welcome to FlowStorm basics tutorial!</b>

<p>
It will guide you over the basics and help you get started with FlowStorm.

<p> If you started this repl using the command from the User's Guide QuickStart section, you find yourself in a standard (for the most) Clojure repl. You can run any expression and nothing should be different. </p>

<p> Nothing except single keyword evaluation. </p>

<p> Since evaluating single keywords on the repl is useless (they just return themselves) FlowStorm hijacks some of them as quick commands. </p>

<p> Try the <b>:help</b> command just to have a sense of what options are available, but don't focus too much on them now, we are going to cover them later. </p>

<p> Another command you already tried if you are here is the <b>:dbg</b> command, which starts the FlowStorm UI. </p>

<p> Any time you need the FlowStorm UI, evaluating <b>:dbg</b> is enough, no matter what namespace you are in, and you can always discard it by closing the window. It will take a couple of seconds the first time, but should open almost instantly from there on. </p>

<p>Now click on <b>Next</b> and I see you on the next slide!</p>
"

   "
<p>Great! Since we are going to be using the UI throughout the tutorial, let's do it comfortably.</p>

<p> You can <b>toggle the debugger's UI between light/dark themes by hitting (Ctrl-t)</b> or using the <b>View menu</b> which also allows you to  increase/decrease the font size. You can <b>also toggle this tutorial theme by using the button at the bottom right corner</b>, so go ahead and set yourself comfortable first.</p>

<p>Now we are ready! The first thing we are going to do is to create and jump to a namespace for our tutorial. So go ahead and paste the following code in your repl : </p>

<code>
(ns tutorial)
</code>

<p> Next thing we need is to <b>tell FlowStorm what namespaces to instrument</b>, so we can record their function's executions.</p>

<p>We can do this by setting some JVM properties before starting the repl or <b>by using the FlowStorm Browser</b> (second vertical tab from the top).</p>

<p>For most of your projects you probably want to setup instrumentation via JVM properties, so you don't need to tell FlowStorm what to instrument every time you start your project's repl, but this time we are going to use the browser.</p>

<p><b>Click on the Browser tab and look at the instrumentations bottom panel</b>.</p>

<p>There are two ways of adding instrumentation prefixes from the browser. We can find our namespace on the namespaces browser, right click on them, and then select our prefix, or we can use the <b>Add</b> button in the instrumentations bottom panel.</p>

<p>Let's find our `tutorial` namespace on the list (you can filter them using the input at the top), right click on it, and click on `Add instr prefix for tutorial.*`</p>

<p>A window will popup asking if we want to reload all namespaces related to our new prefix, which we can cancel since we don't have anything in our ns yet. </p>

<p>Reloading is important when you add a prefix for namespaces with many functions already loaded. When you reload them you will be recompiling all the functions, and because there is a instrumentation prefix for them now, the functions will compile instrumented, so you can record their execution. </p>

<p>These are prefixes, so this means that for the `tutorial` prefix any code compiled under `tutorial`, `tutorial.server`, `tutorial.server.core`, etc, will get instrumented. Normally adding prefixes for the top namespace of your project and some libs you are interested in will be enough.</p>

<p>Now we are done with instrumentation setup for this tutorial, so <b>let's go back to the Flows vertical tab </b>, which is called the Flows tool and is what we are going to be using for the rest of the tutorial.</p>

<p>The next <b>important control</b> to learn about is <b>the recording button</b>, which is the second one on the Flows tool bar. Clicking it will toggle between recording/paused. Let's leave it on pause for now (you should leave it with the circle icon), we don't want anything to be recorded yet.</p>

<p>On the next slide we are going to start evaluating some code.</p>
"

   "
<p>Great! Now we need some code to debug. Go ahead and evaluate the function below (you can copy and paste it on the repl) :</p>

<code>
<pre>
(defn factorial [n]
  (if (zero? n)
     1
     (* n (factorial (dec n)))))
</pre>
</code>

<p>Now if you call the function, let's say <code>(factorial 5)</code> you should get 120 as your result, like in any normal repl.</p>

<p>But let's say you now want to understand this function execution. For this you just go to the UI and put the debugger in recording mode, then run <code>(factorial 5)</code> again.</p>

<p>This time you should see the debugger UI showing the code stepping tool, which we are going to cover next.</p>

<p>This tool is inside a `flow-0` tab, which we are going to ignore for now, and inside a thread tab, probably called `[1] main` if you are running your repl from a terminal.</p>

<p>This is the thread recordings exploration tab, which contains tools for exploring this thread execution.</p>

<p>Whenever you see a red refresh button next to a thread tab, it means that the UI status is outdated for the thread, click it to refresh it.</p>

<p>On the next slide we will start exploring the execution.</p>

<p class=\"hl\">Tip: On your daily work you can keep recording paused and turn it on right before executing something you are interested in.</p>
"

   "
<p>The default tool is called the <b>code stepping tool</b>.</p>

<p>One thing to notice is that your factorial function code is showing there with some parts highlighted in pink, and also there is a bar at the top with some controls and some numbers.</p>

<p>The numbers show <b>the position of the debugger in time</b> for this specific thread. The number at the left is the current position, and the one on the right shows how many \"expressions executions\" were recorded so far for this thread. You can think of FlowStorm recording the activity on each thread as a timeline of execution steps, in which you can move around.</p>

<p>There are many ways of moving around in time in the `code stepping tool` but these are the basic ones :</p>

<ol>
<li>By using the arrows on the second row of the controls panel. They are stepping controls similar to what you can find on most debuggers, but with the extra ability to also step backwards. Check out the tooltips to know how they move and give them a try.</li>
<li>By clicking on the highlights of the form. These are what FlowStorm captured as interesting debugging points for this frame. What it means by \"this frame\" is that clicking on a expression will take you to points in time inside the current function call. In the case of factorial that it is calling itself many times with clicks you get to navigate around the current call. You can click on any symbols and expressions. Whatever you click will get highlighted in green and the debugger will move to that point in time.</li>
</ol>

<p>When navigating a particular call sometimes it's faster to click on the expression you want to see instead of clicking the next or prev step buttons many times.</p>

<p>Also notice that as you move through execution, two panels on the right change.</p>

<p>The top one shows a <b>the current expression value</b> while the bottom one shows all <b>locals in scope</b>.</p>

<p>There are two tabs for the top one, so two tools for displaying the current expression value. The first one is what FlowStorm calls a data window, which allows you to explore nested value and visualize them in different ways. The second just shows a pretty print of the value.</p>

<p>There is also a quick way to jump to the first execution of a function and it is by using the <b>Quick jump box</b> on the toolbar. It will auto complete with all the recorded functions and selecting one will take you there. It doesn't make much sense for this example since we have only one recorded function, but will be handy in more complex situations. Give it a try!</p>

<p>There are many more features on the code stepping tool but given this tutorial covers just the basics, we are going to skip them and jump right to another tool. The <b>call tree tool</b>. So go ahead and click on <b>the second tab</b> in the bottom left corner.</p>

"

   "
<p>Welcome to the <b>call tree tool</b>.</p>

<p>This tool will show you a expandable tree of the functions calls, which will serve as <b>an overview of your selected thread recordings</b>. It will be very handy when trying to understand an end to end execution, helping you create a mental model of what is going on.</p>

<p>Expand the one that says `(factorial 5)` and keep expanding it. This already makes evident how this recursive factorial function works by calling itself. It shows you a tree of functions calls with its arguments.</p>

<p>You can also click on any node and the bottom two panels will show you a pretty print of the arguments vector on the left and of the return value on the right.</p>

<p>Now let's say you are interested in stepping through the code of your factorial function. We can travel just before `(factorial 2)` was called. For it, you will have to <b>expand the nodes</b> until you see the one that is calling the function with 2, and then <b>double click it</b>.</p>

<p>It should take you to the <b>code stepping tool</b> with the debugger positioned right at that point in time.</p>

<p>You can <b>jump between this tools</b> using the tabs at the bottom left corner. So clicking on the third one, we are now going to learn yet another tool. The <b>functions list tool</b>.</p>

"

   "
<p>The <b>functions list tool</b> shows you all the functions next to how many times they have been called.</p>

<p>This is another way of looking at your recordings, which is very useful in some situations.</p>

<p>You should see at least a table with :</p>

<table border=1>
<thead><tr><td>Functions</td><td>Calls</td></tr></thead>
<tbody><tr><td>tutorial/factorial</td><td>6</td></tr></tbody>
</table>

<p>This means that `tutorial/factorial` was called 6 times.</p>

<p>Selecting a function will show you a list of all recorded calls, together with their arguments and return values.</p>

<p>You can also <b>use the checkboxes at the top</b> to show/hide some of the arguments, which doesn't make sense on this case since we have a single argument, but can be handy in more noisy situations.</p>

<p>You can <b>double click on any of the calls</b> at the right to step over code at that specific point in time, or use the `args` or `ret` buttons to inspect the values if they have nested structure. We haven't look at the value inspector yet, but we will cover it soon.</p>

<p>Give it a shot, double click on the factorial call with arguments `[4]` to jump exactly to where `(factorial 4)` was called.</p>

<p>And that's it for the basic code exploring tools. There are more tools under the `More tools` menu but they are out of the scope of this tutorial!</p>

<p>Next we will learn about <b>FlowStorm data exploring tools</b>, so when you are ready click next.</p>
"


   "
<p>Now we are going to learn about <b>FlowStorm value exploring and visualization capabilities</b>, but first let's <b>clear all recordings</b> and try a more interesting example.</p>

<p>For clearing our recordings you can go to the debugger window and hit `Ctlr-L` or you can also click on the trash can on the toolbar.</p>

<p>This is handy in two situations. First, to get rid of old recorded data to <b>make everything cleaner</b>, and second, to <b>free the recorded data</b> so the garbage collector can get rid of it.</p>

<p class=\"hl\">Note: there is a bar at the bottom right corner that will show your max heap and how much of it is currently used. You can use this to keep an eye on your heap usage so you know when to clear or stop recording.</p>

<p>So go ahead, clear your recordings and then evaluate the next form :</p>

<code>
(count (all-ns))
</code>

<p>Now click on the highlighted expression of `(all-ns)` to see this expression value.</p>

<p>This Clojure function returns a list of all namespaces currently loaded, and as you can see on the top right panel, it is a sequence of namespaces objects.</p>

<p>This panel on the top right is called a data window. There are different places in FlowStorm that allows you to open a data window to explore your values.</p>

<p>From the top, we have the data window id, which we are going to skip for now (take look at the User's guide for a deeper look into data windows).</p>

<p>The next row shows the breadcrumbs, which will allow you to navigate back as you go deeper into nested values.<p/>

<p>The following row contains the visualizer selector, the type of the value you are looking at, the def and finally the copy button.<p/>

<p>Expanding the visualizers drowpdown will let you select all the currently defined visualizers for your current value.</p>

<p>Try clicking on some of the elements to navigate into your nested values. You can also navigate back by using the top bar breadcrumbs.</p>

<p>Now let's introduce a very powerful feature, the `def` button. You can take whatever value you are seeing in any FlowStorm panel back to your repl by giving it a name. You do this by clicking the `def` button, and it will ask you for a name. Let's say you named it `mydata`, now you can go to your repl and find it bound to the `user/mydata` var. <b>You can define a value for the repl in any value panel</b> you see in FlowStorm, not just in data windows.</p>

<p>There is also the `copy` value, which will let you put a pretty print of the current value on your clipboard.</p>
"

   "
<p>For the last basic featur, let's see <b>exceptions</b> debugging.</p>

<p>First let's get rid of the recordings (Ctrl-L) and then eval these buggy function and call it :</p>

<code>
<pre>
(defn foo [n]
  (->> (range n)
       (filter odd?)
       (partition-all 2)
       (map second)
       (drop 10)
       (reduce +)))

(foo 70)
</pre>
</code>

<p>An exception should show on your repl! Something on the lines of :</p>

<code>
Cannot invoke \"Object.getClass()\" because \"x\" is null
</code>

<p>which is pretty confusing.</p>

<p>A <b>red dropdown should appear at the top</b>, showing all the recorded functions that throwed instead of returning.</p>

<p>Hovering over the exception will display the exception message.</p>

<p>You can quickly jump right before an Exception by selecting the first function that throwed and then doing a step back.</p>

<p>You can now keep stepping backwards and try to figure out where the bug is coming from.</p>

<p>Give it a shot, try to see if you can figure it out!</p>
"

   "
<p>We just covered the tip of what FlowStorm can do, if you want to dig deeper the <b>User's Guide covers all of it</b>, like :</p>

<ol>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurestorm\">Setting up FlowStorm in your projects</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurescript\">Using FlowStorm with ClojureScript</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_flows_tool\">Using many flows</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_power_stepping\">Power stepping</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_searching\">Searching</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_loops\">Debugging loops</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_stack\">Navigating with the stack</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_bookmarks\">Bookmarks</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_multi_thread_timeline\">Multi-thread timelines</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_printer\">The printer tool</a></li>
<li> <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_programmable_debugging\">Programmable debugging</a></li>
<li> And much more!!!</li>
</ol>

<p>You can always access the User's guide by clicking on the Help menu at the top.</p>

<p>Before finishing, keep in mind that even if FlowStorm is called a debugger (for lack of a better word) it was designed not just for chasing bugs, but for enhancing your interactive development experience by providing some visibility on what is going on as things execute, so it is pretty handy for other things like help you understanding a system from an execution POV or just running something and checking your assumptions.</p>

<p>Also here are some tips I've found for using FlowStorm efficiently :</p>

<ol>
<li>Keep recording paused when not needed</li>
<li>Get rid of all the state (Ctrl-L) before executing the actions you are interested in recording</li>
<li>If you know the function name you want to see use the Quick jump box to quickly jump to it's first call.</li>
<li>If you see multiple calls, use the functions list to quickly move between them.</li>
<li>When exploring a system you know little about, the search, power-stepping and bookmarking tools can make wrapping your head around the new system much faster.</li>
<li>If there is any kind of looping, mapping or a function is called multiple times the printer tool is your friend.</li>
<li>Use the jvm options described in :help to configure it, so you don't record unnecessary stuff.</li>
</ol>

<p>And that is all for the basics. If you find any issues or suggestions feel free to open a issue in https://github.com/flow-storm/flow-storm-debugger</p>

<p>Now you are ready, so go and <a href=\"https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurestorm\">add it to your projects </a>! Give it a try!</p>

<p>Bye!</p>
"

   ])

(defonce step (atom 0))

(defn step-reset [] (reset! step 0))

(defn- create-tutorial-pane []
  (let [{:keys [web-view set-html]} (ui/web-view)
        steps-lbl (ui/label :text "")
        theme (atom :light)
        update-ui (fn []
                    (let [[bcolor fcolor hl-color] (case @theme
                                                     :light ["#ddd" "#3f474f" "#e5b7e8"]
                                                     :dark  ["#323232" "#ddd" "#6c3670"])
                          theme-styles (format "<style> body {background-color: %s; color: %s; font-family: Arial}</style>" bcolor fcolor)]
                      (ui-utils/set-text steps-lbl (format "%d/%d" (inc @step) (count steps)))
                      (set-html (str "<html><body>"
                                     (format "<style> p.hl {background-color: %s; padding:5px; font-style: italic;} div.slide {padding: 10px} code {padding: 10px}</style>" hl-color)
                                     theme-styles
                                     "<div class=\"slide\">"
                                     (get steps @step)
                                     "</div>"
                                     "</body></html>"))))
        tut-controls (ui/border-pane
                      :paddings [10]
                      :center (ui/h-box
                               :align :center
                               :spacing 10
                               :childs
                               [(ui/button :label "Prev"
                                           :on-click (fn []
                                                       (when (pos? @step)
                                                         (swap! step dec)
                                                         (update-ui))))
                                steps-lbl
                                (ui/button :label "Next"
                                           :on-click (fn []
                                                       (when (< @step (dec (count steps)))
                                                         (swap! step inc)
                                                         (update-ui))))])
                      :right (ui/h-box
                              :align :center-right
                              :childs [(ui/button :label "Toggle theme"
                                                  :on-click (fn []
                                                              (swap! theme {:light :dark, :dark :light})
                                                              (update-ui)))]))]
    (update-ui)
    (ui/border-pane
     :center web-view
     :bottom tut-controls)))

(defn start-tutorials-ui []
  (step-reset)
  (let [window-w 1000
        window-h 1000
        {:keys [x y]} (ui-utils/stage-center-box (dbg-state/main-jfx-stage) window-w window-h)]
    (ui/stage :scene (ui/scene :root (create-tutorial-pane)
                               :window-width  window-w
                               :window-height window-h)
              :title "FlowStorm basics tutorial"
              :x x
              :y y
              :show? true)))
