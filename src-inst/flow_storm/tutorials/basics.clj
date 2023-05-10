(ns flow-storm.tutorials.basics
  (:require [flow-storm.tracer :as tracer]))

(declare steps)

(def step (atom 0))

(defn print-current-step [] (println (get steps @step)))
(defn step-next [] (swap! step inc) (print-current-step))
(defn step-prev [] (swap! step dec) (print-current-step))
(defn step-reset [] (reset! step 0))

(def steps
  [
   "
Welcome to ClojureStorm basics tutorial.

You can always restart this tutorial by evaluating the keyword :tut/basics and move back
and forth by evaluating :tut/next and :tut/prev.

As you can see, all ClojureStorm repl commands are invoked by evaluating keywords at the repl,
called commands from now on.

Try the :help command just to have a sense of what options are available, but don't focus too much
on them since we are going to cover them later.

Then type :tut/next to continue.
"


   "
Let's jump right into debugging some code, but first let's load the debugger UI.

You can always start the FlowStorm UI by executing :dbg, and discard it by just closing
the window. It will take a couple of seconds the first time, but should open almost instantly
from there on.

So go ahead and start the UI and I see you on the next slide!
"

   "
Great! Now we need some code to debug, so go ahead and evaluate the next expressions:
(you can copy and paste them one by one on the repl)

----------------------------------
(defn factorial [n]
  (if (zero? n)
     1
     (* n (factorial (dec n)))))

(factorial 5)
----------------------------------

In your repl, you should see the result, like a normal Clojure repl, with the only difference
that everything that just happened got recorded. The UI will not show much yet, but follow along,
on the next slide we will be using it to explore what we just recorded.

Note: The recording will always happen independently of the UI running or not.
      This means you can work normally at your repl and open the debugger to see what just happened.

Note2: What gets recorded depends on the value of clojure.storm.instrumentOnlyPrefixes and
       clojure.storm.instrumentSkipPrefixes. This tutorial will only work over the `user` namespace
       but the basic way of using it in your project will be to set :

            -Dclojure.storm.instrumentOnlyPrefixes=your-project-top-ns,lib1-top-ns,lib2-top-ns

       and all namespaces you are interested in debugging.

Next we will start using the debugger.
"

   "
Great, now let's focus on the FlowStorm UI.

We will focus on the Flows tab for now. On the left you
should see a list of threads for which we have recordings.

If you are running this from a bare bones clj command you should see
just the `main` thread there, but if you are running it connected to nrepl (when using Cider, etc)
you probably see more threads like `nREPL-session-d7edf6bc-a548-4ce9-a283-71a331a30dc4`.

You can double click on any thread to explore the recordings, or if you just want to jump to the last
thing recorded in your repl thread, type the :last command.

Go ahead, try it and then move next.
"


   "
You should be seeing a \"thread exploring tab\".

If you typed :last in the previous step, you should be seeing the `code stepping tool`, while if you
double clicked on a thread you should be seeing the `call tree tool`. They are different tools to explore
the recordings. You can swap between tools in the bottom left corner of the \"thread exploring tab\".

Go and click the first one, which is the `call tree tool`, then move next.
"

   "
The `call tree tool` will show you a expandable overview of your recordings.

Expand the one that says `(factorial 5)` and keep expanding it. It will show you
a tree of functions calls with its arguments. You can click on any nodes and the
bottom two panels will show you a pretty print of the arguments vector on the left
and of the return value on the right.

Note: You are going to see other repl evaluations also related to Clojure loading the UI namespaces.

Note2: Once opened the tree will not auto-refresh. Use the refresh button at the root of the tree
       to update it.

Now let's say you are interested in stepping through the code of your factorial function.
We can travel just before (factorial 2) was called. For it, you will have to expand the nodes until you
see the one you are interested in, then right click on it, and select `Step code` from the menu.

It should take you to the `code stepping tool` with the debugger positioned right at that
point in time.
"

   "
There are a bunch of things going on at the `code stepping tool`.

Some things to notice is that your factorial function code is showing there with
some parts highlighted in pink, and also there is a bar at the top with some controls and
some numbers.

The numbers show the position of the debugger in time for this specific thread. The number at the left
is the current position, and the one on the right shows how many \"expressions executions\" were recorded
so far for this thread.

There are 3 ways of moving around time from this screen.

The first one is by clicking the controls (check out the tool tips to know how they move).

The second one is by clicking on the highlights of the form. These are what FlowStorm captured as interesting
debugging points for this frame. You can click on them (symbols and expressions). It will get highlighted in green and the
debugger will move to that point in time. Sometimes it is more practical to just click on the value you want to see
instead of clicking the next button many times.

The third one is by just typing a number on the current step input and then hitting enter.
This come in handy when you are trying to understand a complicated execution flow, since you can
take notes of interesting positions and then come back by just typing the position.

Also notice that as you move in time two panels on the right change.
The top one shows a pretty print of the current expression value while the bottom one
shows all locals in scope. This example values are super simple, given they are just integers,
but for more complex values sometimes the pretty print is going to be too noisy and we would be better suited
by a value explorer, which we are going to cover in a couple of slides.

Before leaving the `code stepping tool` let's learn one more future, loops debugging!


"


   "
FlowStorm contains a feature that can be pretty handy when debugging loops.

First let's define and call a iterative version of our factorial function :

-----------------------------------------
(defn iter-factorial [n]
   (loop [i n
          result 1]
     (if (zero? i)
       result
       (recur (dec i) (* result i)))))

(iter-factorial 5)
-----------------------------------------

Copy and paste the previous forms, then lets jump right at the end of the iter-factorial call.
For this you can type :last, and then start stepping backwards using the single step back arrow
until you get inside the body of the loop.

Now try clicking any of the highlighted forms.

This time, the debugger is not moving immediately since it is asking first to choose what expression
you are interested in. Clicking on any of the menu values will make the debugger move to that position in time.

There is one special option at the beginning of all loop menus which reads `Goto Last Iteration`.
This is going to be useful when debugging loops with many iterations since the menu is only going to list the first 100.
Most of the time we are also interested in the end of the loop, so we can use this options to quickly jump there.

Before finishing the execution exploring tools, let's check one more tool, the `functions list tool`,
so go ahead and click on the last tab in the bottom left corner.
"


   "
The `functions list tool` shows you all the functions next to how many times they have been called.
This is another way of looking at your recordings, which is sometimes useful.

You should see at least a table with :

                       `user/factorial`                    6
                       `user/iter-factorial`               1

If you double click on the `user/factorial` one, it will show you a list of all the calls with their arguments.
You can also use the checkboxes at the top to show/hide some of the arguments, which doesn't
make sense on this case but can come handy on more noisy situations.

You can double click on any of the calls at the left to step over code at that specific point in time.

Give it a shot, double click on the call with arguments [4] to jump exactly to where (factorial 4) was called.

And that is it for the code exploring tools! Next we will learn about FlowStorm data exploring tools, so when you
are ready move next.
"


   "
Great! Now we are going to learn about FlowStorm value exploring capabilities, but
first let's clean all recordings and try a different example.

For clearing our recordings you can go to the debugger window and hit `Ctlr-L` or
by clicking on the top left button (the one with the trash can icon). This comes handy
in two situations. First, to get rid of old recorded data to make everything cleaner, and second,
to free the recorded data so the garbage collector can get rid of it.

Note: there is a bar at the bottom right corner that will show your max heap and how much of it is used,
      so you can keep an eye on it and don't have to worry about recording too much.

So go ahead, clear the state and then evaluate the next form :

--------------------

(count (all-ns))

--------------------

and then type :last

Now click on the highlighted parenthesis of `(all-ns)` to see the returned value.

As you can see on the top right panel, it is a sequence of namespaces objects.

Go on and click on the `ins` button at the top of the panel to open the value inspector.

This will open the Value Inspector in another window with a bunch of stuff.
You can keep clicking on the highlighted values to keep digging.

You can also go backwards by using the top bar breadcrumbs.

If while digging on values you feel like exploring that value at the repl, you can click on the
`def` button, it will ask you for a name, let's say you named it `mydata`, now you can go to your
repl and find it bound to the `user/mydata` var. You can define a value for the repl in any value panel
you see in FlowStorm, not just the value inspector.

There is also the `tap` value, to tap what you are seeing like with tap> which is pretty convenient if
you want to send it to a different value explorer like portal, rebl, reveal, etc.
"


   "
For the last feature, let's see jumping to exceptions.

First get rid of the state (Ctrl-L) and then let's eval this forms :

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

You can quickly jump right before the last exception
by executing :ex

It should open the thread and point the debugger right before the exception happened.
You can then step backwards and try to figure out where the bug is coming from.

Let's se if you can figure it out!
"


   "
Before closing let's go over some of the important configuration options and commands
displayed by :help.

- :rec and :stop

If you want to pause tracing because you currently don't need it and you don't want
recordings to consume your heap space, you can enable/disable it with :rec and :stop

- :inst and :noinst

You can also enable/disable compiler instrumentation. Important: when you disable it, it will
not remove instrumentation from already loaded functions, you will have to re-evaluate them.

Disabling instrumentation is useful when you want to measure performance or in any other situation
where you don't want instrumentation.
If you start ClojureStorm with disabled instrumentation it should be exactly the vanilla
Clojure compiler.

- JVM opts -Dclojure.storm.instrumentOnlyPrefixes and -Dclojure.storm.instrumentSkipPrefixes

If you start the jvm with -Dclojure.storm.instrumentOnlyPrefixes=my-project,lib1,lib2.core
it means that all functions under my-project.*, lib.*  and lib2.core.* will get instrumented,
everything else will be skipped and be compiled exactly like with the vanilla Clojure compiler.

On the other side you have -Dclojure.storm.instrumentSkipPrefixes to specify what to skip instead
of what to instrument, in case you want to instrument everything but certain libs.

Everything under clojure.* will be un-instrumented by default, but if you set the right prefixes you can then
re-eval the clojure.* functions you are interested in and instrument them.
"


   "
Before closing this are some tips I've found for using FlowStorm efficiently :

- Get rid of all the state (Ctrl-L) before executing the actions you are interested in recording
- Use the jvm options described in :help to configure it so you don't record unnecessary stuff.

And that is all for the basics. If you find any issues or suggestions feel free
to open a issue in https://github.com/jpmonettas/flow-storm-debugger

Now let's go add it to your current project and give it a try.
All you have to do is to edit your deps.edn file and add a :dev alias with a config like:

----------------------------------
{:classpath-overrides {org.clojure/clojure nil}
 :extra-deps {com.github.jpmonettas/clojure {:mvn/version \"LATEST-AVAILABLE-VERSION\"}
              com.github.jpmonettas/flow-storm-dbg {:mvn/version \"LATEST-AVAILABLE-VERSION\"}}
 :jvm-opts [\"-Dflowstorm.startRecording=true\"
            \"-Dclojure.storm.instrumentEnable=true\"
            \"-Dclojure.storm.instrumentOnlyPrefixes=YOUR-PROJECT-TOP-NS\"]}

Cheers and Good luck!!
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
