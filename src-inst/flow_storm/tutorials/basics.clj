(ns flow-storm.tutorials.basics
  (:require [flow-storm.tracer :as tracer]))

(declare steps)

(defonce step (atom 0))

(defn print-current-step [] (println (get steps @step)))
(defn step-next [] (swap! step inc) (print-current-step))
(defn step-prev [] (swap! step dec) (print-current-step))
(defn step-reset [] (reset! step 0))

(def steps
  [
   "
Welcome to ClojureStorm basics tutorial.

It will gide you over the basics and help you get started with FlowStorm.

You can always restart this tutorial by evaluating the keyword :tut/basics and move back
and forth by evaluating :tut/next and :tut/prev.

As you can see, all ClojureStorm repl commands are invoked by evaluating keywords at the repl,
called commands from now on.

Try the :help command just to have a sense of what options are available, but don't focus too much
on them now, we are going to cover them later.

Then type :tut/next to continue.
"


   "
If you started this repl using the command from the User's Guide quick start section
you find yourself in a standard(for the most) Clojure repl. You can run any expression
and nothing should be different.

Nothing except single keyword evaluation. Since evaluating single keywords on the repl
is useless (they just return themselves) FlowStorm hijacks some of them as quick commands as
you have already noticed.

Let's use the most important command, the :dbg keyword.

Any time you need the FlowStorm UI, evaluating it is enough, no matter what namespace you are in.

So, you can always start the FlowStorm UI by executing :dbg, and discard it by closing
the window. It will take a couple of seconds the first time, but should open almost instantly
from there on.

Go ahead and start the UI and I see you on the next slide!
"

   "
Great! The first thing we need to do is to tell FlowStorm what to instrument.

We can do this by setting some JVM properties before starting the repl or by using
the FlowStorm Browser (second vertical tab from the top).

For most of your projects you probably going to setup instrumentation via JVM properties
since you don't want to tell it what to instrument every time you start a repl, but this
time we are going to use the browser.

Click on the Browser tab and look at the Instrumentations bottom panel.

Click on `Add` and then `Add instrumentation prefix`. It will as you for a prefix, let's
type the word `tutorial`.

It is a prefix, so this means that any code compiled under `tutorial`, `tutorial.server`,
`tutorial.server.core`, etc, will get instrumented so it can be recorded.

Now we are done, with instrumentation setup for this tutorial.

Another important control is the recording button, the first one on the tool bar.
Clicking it will toggle between recording/paused. Let's click it and leave it on
pause (you should leave it with the circle icon), we don't want anything to be recorded
yet.

Now go back to your repl and let's create a tutorial namespace by typing :

--------------
(ns tutorial)
--------------

On the next slide we are going to start evaluating some under it.
"

   "
Great! Now we need some code to debug. Go ahead and evaluate the function below :
(you can copy and paste them one by one on the repl)

----------------------------------
(defn factorial [n]
  (if (zero? n)
     1
     (* n (factorial (dec n)))))
----------------------------------

Now if you call the function, let's say `(factorial 5)` you should get the
response of 120, and nothing should be different.

But let's say you now want to understand this function execution. For this you just
go to the UI and put the debugger in recording mode, then run `(factorial 5)` again.

This time you should see the debugger UI showing a expandable tree.
This tree is inside a `flow-0` tab, which we are going to ignore for now, and inside a
thread, probably called `[1] main`.
This is the thread recordings exploration tab, which contains tools for exploring this
thread execution.

On the next slide we will start exploring the execution.

Tip: On your daily work you can keep recording paused and turn it on right before executing
     something you are interested in.
"

   "
The default tool is called the `call tree tool`.

The `call tree tool` will show you a expandable overview of your selected thread recordings.

Expand the one that says `(factorial 5)` and keep expanding it. It will show you
a tree of functions calls with its arguments. You can click on any node and the
bottom two panels will show you a pretty print of the arguments vector on the left
and of the return value on the right.

Note: Once opened, the tree will not auto-refresh. Use the refresh button at the root of the tree
      to update it.

Now let's say you are interested in stepping through the code of your factorial function.
We can travel just before (factorial 2) was called. For it, you will have to expand the nodes until you
see the one you are interested in, and then double click the node.

It should take you to the `code stepping tool` with the debugger positioned right at that
point in time.

"

   "
There are a bunch of things going on at the `code stepping tool`.

One thing to notice is that your factorial function code is showing there with
some parts highlighted in pink, and also there is a bar at the top with some controls and
some numbers.

The numbers show the position of the debugger in time for this specific thread. The number at the left
is the current position, and the one on the right shows how many \"expressions executions\" were recorded
so far for this thread.

There are many ways of moving around time with FlowStorm but this are the basic ones :

- By using the controls on the second row of the controls panel. They are stepping controls similar to what you can
  find on most debuggers, but with the extra ability to also step backwards.
  Check out the tooltips to know how they move and give them a try.

- By clicking on the highlights of the form. These are what FlowStorm captured as interesting debugging points for
  this frame. You can click on them (symbols and expressions). Whatever you click will get highlighted in green and the
  debugger will move to that point in time.

Sometimes it is more practical to just click on the value you want to see instead of clicking the next button many times.

Also notice that as you move through execution, two panels on the right change.

The top one shows a pretty print of the current expression value while the bottom one
shows all locals in scope.

The pretty print panel is useful for exploring simple values like in this example but it won't be
enought to explore heavy nested ones.

For that there is a value explorer, which we are going to cover in a couple of slides.

There are many more features on the `code stepping tool` but since this tutorial covers just the
basics, we are going to jump to another tool, the `functions list` tool,
so go ahead and click on the last tab in the bottom left corner.

"

   "
The `functions list tool` shows you all the functions next to how many times they have been called.
This is another way of looking at your recordings, which is very useful in situations.

You should see at least a table with :

                       | Functions            | Calls      |
                       |------------------------------------
                       | `tutorial/factorial` |          6 |

This means that `tutorial/factorial` was called 6 times.

Selecting its row, it will show you a list of all its recorded calls,
together with their arguments, and return values.

You can also use the checkboxes at the top to show/hide some of the arguments, which doesn't
make sense on this case since we have a single argument, but can be handy in more noisy situations.

You can double click on any of the calls at the right to step over code at that specific point in time,
or use the `args` or `ret` buttons to inspect the values if they have nested structure. We haven't
look at the value inspector yet, but we will cover it soon.

Give it a shot, double click on the call with arguments [4] to jump exactly to where (factorial 4) was called.

And that is it for the code exploring tools! Next we will learn about FlowStorm data exploring tools, so when you
are ready move next.
"


   "
Great! Now we are going to learn about FlowStorm value exploring capabilities, but
first let's clean all recordings and try a different example.

For clearing our recordings you can go to the debugger window and hit `Ctlr-L` or
by clicking on the trash can on the toolbar.

This comes handy in two situations. First, to get rid of old recorded data to make everything cleaner, and second,
to free the recorded data so the garbage collector can get rid of it.

Note: there is a bar at the bottom right corner that will show your max heap and how much of it is currently used,
      so you can keep an eye on it and don't have to worry about recording too much.

So go ahead, clear the state and then evaluate the next form :

--------------------

(count (all-ns))

--------------------

Now duoble click on the tree node to jump to the `code stepping` tool (or find it on the bottom tabs),
and then click on the highlighted parenthesis of `(all-ns)` to see this expression value.

As you can see on the top right panel, it is a sequence of namespaces objects.

Go on and click on the `ins` button at the top of the panel to open the value inspector.

This will open the Value Inspector in another window with a bunch of stuff.
You can keep clicking on the highlighted values to keep digging.

You can also go backwards by using the top bar breadcrumbs.

If while digging on values you feel like exploring that value at the repl, you can click on the
`def` button. It will ask you for a name. Let's say you named it `mydata`, now you can go to your
repl and find it bound to the `user/mydata` var. You can define a value for the repl in any value panel
you see in FlowStorm, not just the value inspector.

There is also the `tap` value, to tap what you are seeing like with tap> which is pretty convenient if
you want to send it to a different value explorer like portal, rebl, reveal, etc.
"


   "
For the last feature, let's see jumping to exceptions.

First get rid of the state (Ctrl-L) and then let's eval these forms :

--------------------------------------
(defn foo [n]
  (->> (range n)
       (filter odd?)
       (partition-all 2)
       (map second)
       (drop 10)
       (reduce +)))

(foo 70)
--------------------------------------

An exception should show! Something on the lines of :

       Cannot invoke \"Object.getClass()\" because \"x\" is null

which is pretty confusing.

A new dropdown should appear at the top, showing all the recorded functions
that throwed instead of returned.

You can quickly jump right before the exception
by selecting the last exception and do a step back.

You can now step backwards and try to figure out where the bug is coming from.

Give it a shot, try to see if you can figure it out!
"

   "
We just covered the tip of what FlowStorm can do, but should already take you pretty far.

If you want to dig deeper the User's Guide covers all of it, like :

- Setting up FlowStorm in your projects
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurestorm
- Using FlowStorm with ClojureScript
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurescript
- Using many flows
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_flows_tool
- Power stepping
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_power_stepping
- Searching
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_searching
- Debugging loops
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_loops
- Navigating with the stack
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_stack
- Bookmarks
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_bookmarks
- Multi-thread timelines
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_timeline_tool
- Programmable debugging
  https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_programmable_debugging
- And much more!!!

Before finishing, this are some tips I've found for using FlowStorm efficiently :

- Get rid of all the state (Ctrl-L) before executing the actions you are interested in recording
- Use the jvm options described in :help to configure it, so you don't record unnecessary stuff.

And that is all for the basics. If you find any issues or suggestions feel free
to open a issue in https://github.com/flow-storm/flow-storm-debugger

Now you are ready to go add it to your current projects! give it a try!

https://flow-storm.github.io/flow-storm-debugger/user_guide.html#_clojurestorm

Bye!
"

   ])

(defn instrumentation-and-trace-on? []
  (and (clojure.storm.Emitter/getInstrumentationEnable)
       (tracer/recording?)))

(defn start []
  (if (instrumentation-and-trace-on?)

    (do
      (step-reset)
      (print-current-step))

    (println
     "\nPlease set instrumentation and recording ON.

You can do that by starting the jvm with -Dflowstorm.startRecording=true -Dclojure.storm.instrumentEnable=true
or by evaluating :rec and :inst keywords at the repl and then running :tut/basics again.")))
