Known major issues with kotlinx.coroutines
==========================================

Cancellation
------------

### Reacting to cancellation in user code

#### Language level

Cancellation of coroutines is represented by them resuming with a
`CancellationException`. User code is allowed to catch `CancellationException`
instances, but must always rethrow a `CancellationException`.

```kotlin
// A common approach to rethrowing cancellation exceptions
try {
    // suspending operation
} catch (e: Throwable) {
    if (e is CancellationException)
        ensureActive()
    // process actual exceptions:
    // ...
}
```

This is **mostly** not a problem for code that correctly catches only
the expected exceptions (see "Standard library level" below for an
exception), but the pattern is still widely employed
(<https://grep.app/search?f.lang=Kotlin&q=is+CancellationException>)
and requested (<https://github.com/Kotlin/kotlinx.coroutines/issues/1814>).

The root of the issue lies in Kotlin itself.

Cancellation is fundamentally a different control flow type from
exceptions, in that it shouldn't be caught and processed.
Like exceptions, cancellation does propagate to the bottom of the call
stack.
In reaction to a cancellation request,
you can only gracefully cleanup your resources in `finally` blocks.

#### Standard library level

`CancellationException` is a subtype of `IllegalStateException`:
<https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines.cancellation/-cancellation-exception/>.

Therefore, this benign-looking code is faulty:

```kotlin
try {
    // This will throw `IllegalStateException` if something weird happens
    check(3 < 5) { "Check the initial invariants" }
    delay(100.milliseconds)
    check(4 < 5) { "Check the invariants at the end" }
} catch (e: IllegalStateException) {
    println("Invariants got violated")
}
```

The user expected to catch `IllegalStateException` resulting from some
broken system state, but instead, ended up catching the extremely common
`CancellationException`.

`check` throws a pure `IllegalStateException`, not some subtype, so there
is no edit to be done locally to the `catch` line—you need to write a more
complex handler that distinguishes `CancellationException`, or you need to
move out all `suspend` calls from the `try`/`catch`.

Tracking issue: <https://github.com/Kotlin/kotlinx.coroutines/issues/4073>.

#### `kotlinx.coroutines`

`kotlinx.coroutines.flow.Flow` is an entity representing an asynchronous
stream of values:

```kotlin
val numbersFrom0to4 = flow {
    repeat(5) {
        println("Emitting $it")
        emit(it)
    }
}
numbersFrom0to4.collect { value ->
    println("Collected $value")
}
```

The collector can notify the emitter that it should stop emitting new values
by throwing a `CancellationException` exception from the `collect` call.

The problem is that this doesn't actually lead to a cancellation,
as can be seen by doing this:

```kotlin

val numbersFrom0to4 = flow {
    repeat(5) {
        println("Emitting $it")
        try {
            emit(it)
        } catch (e: CancellationException) {
            println("Oh, we're cancelled, we can't suspend...")
            delay(100.milliseconds)
            println("Nevermind, we actually can.")
            throw e
        }
    }
}
numbersFrom0to4.first() // take the first, cancel the other ones
delay(100.milliseconds) // this shouldn't be cancelled!
```

Because of this discrepancy, the `ensureActive()` pattern for handling
exceptions and correctly propagating `CancellationException` won't work
in flows:
<https://github.com/Kotlin/kotlinx.coroutines/issues/1814#issuecomment-1943224856>.

The correct way to handle this would be to also cancel the `flow { }` block.
It's not feasible, because
1) This is a huge breaking change.
2) Making every `Flow`'s emitter have a separate lifecycle from the collector
   isn't cheap and would lead to a noticeable performance degradation.

### Cancellation can be more efficient

#### Language level

Kotlin doesn't provide a way to figure out if some code needs to run in
response to a cancellation. Consider this simple snippet:

```kotlin
coroutineScope {
    // Start a coroutine...
    val job = launch(Dispatchers.Default) {
        awaitCancellation()
    }
    // Something happens...
    delay(100.milliseconds)
    // Now, cancel the job:
    job.cancel()
}
```

After `awaitCancellation()` gets cancelled, you may expect the coroutine
to just stop executing—after all, there is nothing left for it to do
after it gets cancelled.

Unfortunately, the coroutine doesn't simply get dropped.
Instead, `Dispatchers.Default` receives a request:
"Once one of your threads is available, use it to resume the coroutine
with a `CancellationException`".
If `Dispatchers.Default` threads are busy, this request can take a long
time to even begin executing—and worst of all, that request is trivial
and will only meaningfully result in converting the state of the
coroutine from "getting ready to cancel" to "successfully cancelled".

The reason to go through all this ceremony is to handle cases like this:

```kotlin
coroutineScope {
    // Start a coroutine...
    val job = launch(Dispatchers.Default) {
        try {
            awaitCancellation()
        } finally {
            println("Cleaning up something")
        }
    }
    // Something happens...
    delay(100.milliseconds)
    // Now, cancel the job:
    job.cancel()
}
```

Here, finalizers need to be run, so it would be incorrect to simply drop
the coroutine. `kotlinx.coroutines` has no way to distinguish between the
two cases.

When there is a single coroutine, the difference is miniscule.
However, the problem becomes more insidious when more coroutines are involved.
Consider this:

```kotlin
withContext(Dispatchers.Default) {
    val jobs = List(10000) {
        launch {
            awaitCancellation()
        }
    }
    delay(1.seconds)
    jobs.forEach { cancel() }
}
```

This `cancel` invocation very rapidly puts 10000 tasks into the
`Dispatchers.Default` queue, completely exhausting the dispatcher's capacity.
If there are other tasks that need running, good luck waiting for them to get
queued while `Dispatchers.Default` is busy doing completely unnecessary work!

The language can expose the knowledge that a `suspend` function isn't actually
going to do anything if it resumes exceptionally.
Then, `kotlinx.coroutines` can react to that and skip the unnecessary
resumptions.

#### `kotlinx.coroutines` level

`CancellationException` is a normal exception, and a stacktrace is created and
populated to throw it. This is in most cases completely unnecessary—after all,
good code using `kotlinx.coroutines` doesn't catch, let alone inspect
cancellation exceptions.

The time spent on populating the stacktrace is completely wasted.

If `CancellationException` is no longer an exception at all, the problem
may solve itself, of course, but even now, we can provide a way to opt out of
populating a stack trace. Instead, a preallocated `CancellationException`
instance can be used if the user requests it.

### No one expects it to happen

People commonly write code without considering exceptions, and this is even
more true for cancellations.
`try`/`finally` is omitted for cancellable code that "obviously" can't throw
exceptions, and worst of all, this is difficult to test for and predict.
Cancellations race with normal code execution, and "start the operation, then
*immediately* leave the screen" may not even be reproducible on faster
hardware.

I don't have a good solution for this short of guaranteeing data-race freedom
or imposing RAII on the whole ecosystem **on the language level**.
**On the stdlib level**, we can mitigate some of the issues by making it easier
to clean up resources correctly, by introducing an entity similar to
<https://apidocs.arrow-kt.io/arrow-autoclose/arrow/auto-close-scope.html>.

Diagnostics
-----------

### Uninformative stacktraces

There is an issue with asynchronous programming with Java-like languages
in general: stack traces, which are often the go-to way for diagnosing problems,
become uninformative.

1) Where the exception originated is unclear.

Simplified example (https://pl.kotl.in/AkxeqT-i0):

```kotlin
val taskQueue = Channel<Runnable>(Channel.UNLIMITED)

// An executor for asynchronous tasks
val executor = thread(isDaemon = true) {
    while (true) {
        val task = runBlocking { taskQueue.receive() }
        task.run()
    }
}

fun bar() {
    // Start a task asynchronously
    taskQueue.trySend(Runnable {
        try {
            check(false) { "An invariant got broken." }
        } catch (e: Throwable) {
            println(e.stackTraceToString())
        }
    })
}

fun foo() {
    bar()
}

fun main() {
    foo()
    Thread.sleep(100) // give the task a chance to run
}
```

This code will print

```
java.lang.IllegalStateException: An invariant got broken.
        at FileKt.bar$lambda$0(File.kt:16)
        at FileKt.executor$lambda$0(File.kt:9)
        at kotlin.concurrent.ThreadsKt$thread$thread$1.run(Thread.kt:30)
```

No `main`, no `foo` in sight, and even `bar` is only mentioned indirectly,
through the location of its lambda in code.

2) More importantly, where the exception was *received* is unclear.

Simplified example(https://pl.kotl.in/je00XEDe2):

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.concurrent.*

class Task<T>(
    val runnable: () -> T
) {
    val result = CompletableDeferred<Result<T>>()
}

val taskQueue = Channel<Task<*>>(Channel.UNLIMITED)
val executor = thread(isDaemon = true) {
    while (true) {
        val task = runBlocking { taskQueue.receive() }
        task.result.complete(runCatching { task.runnable() })
    }
}

fun bar() {
    val task = Task {
        check(false) { "An invariant got broken." }
    }.also { taskQueue.trySend(it) }
    runBlocking { task.result.await().getOrThrow() }
}

fun foo() {
    bar()
}

fun main() {
    foo()
    Thread.sleep(100)
}
```

Here, tasks aren't fire-and-forget. After asking another thread to do
some work, you can await its results—which may be exceptions.
If they are exceptions, once again, the stack trace does not include any
information that could help with debugging:

```
Exception in thread "main" java.lang.IllegalStateException: An invariant got broken.
        at FileKt.bar$lambda$0 (File.kt:21)
        at FileKt.executor$lambda$0 (File.kt:15)
        at kotlin.concurrent.ThreadsKt$thread$thread$1.run (Thread.kt:30)
```

#### Language level

##### The mitigation is JVM-only

On the JVM, exceptions expose an API for rewriting their stacktraces.
`kotlinx.coroutines` can optionally notice exceptions crossing the asynchronous
boundary via a mechanism called "stacktrace recovery":
<https://github.com/Kotlin/kotlinx.coroutines/blob/8564f65764d3d05893cec026c6e94250e2b23874/docs/topics/debugging.md#stacktrace-recovery>

On non-JVM targets, exception stacktraces are immutable.
This makes it impossible for us to implement stacktrace recovery for
anything other than the JVM.

##### There is further potential for improving stack traces

(Maybe it's `kotlinx.coroutines`-level now?)

<https://github.com/reformator14/stacktrace-decoroutinator> is another way
to improve the stack traces of asynchronous code,
targeting `suspend` functions specifically.
This is a very popular solution, and making it available out of the box
could be a great help to users.

Since 2.3.20, on the JVM, there is a `wrapContinuation` function that
allows rewriting the stack trace of a `suspend` function.
We could potentially incorporate the debugger mechanism of rewriting
`wrapContinuation` to support a "decoroutinator"-style
behavior.
It is unclear to me if `wrapContinuation` is actually enough for this
purpose, this requires careful attention,
I only learned of the wrapper yesterday.
Further improvements to the language or the stdlib may or may not be required.

Of course, right now, both the decoroutinator and `wrapContinuation` are
JVM-only, too, so to make this multiplatform,
compiler changes are required in any case.

#### `kotlinx.coroutines` level

##### The access to such a basic thing is awkward

We don't provide a way of turning on coroutine stacktrace recovery
in minified Android builds.

In addition, depending on `kotlinx-coroutines-debug` is required,
as well as some extra configuration.
Given how common it is to enable stacktrace recovery in release builds,
we may want to expose the stacktrace recovery API more prominently.

Observability
-------------

### Tracing

Users are in love with tracing, it seems like everyone is trying to implement
their own way to signal "coroutine started"/"function entered"/"coroutine
suspended".

#### Standard library level

The standard library provides "coroutine probes", a set of functions that
coroutines call to notify the interested parties about their lifecycle events.
This, however, is not a proper API: those functions have empty bodies,
and bytecode rewriting is used by `kotlinx-coroutines-debug` to replace these
stubs by some proper implementations to achieve the limited tracing
capabilities that it has.

Even if `kotlinx-coroutines-debug` decides to expose a programmatic API
for subscribing to these events, this is suboptimal, because some runtimes,
like Android, don't allow rewriting bytecode on the fly.
In addition, this rewriting requires a prohibitively slow initialization
whose duration is proportional to the number of classes in the classpath.

If the standard library provides a proper way to inject these callbacks
via some API, this could help solve the traceability problem.

#### `kotlinx.coroutines` level

`kotlinx.coroutines` has two mechanisms for (roughly) tracing:
1) Rewriting the coroutine lifecycle events in `kotlinx-coroutines-debug`, and
2) `ThreadContextElement`, a user-defined set of callbacks for coroutine
   suspensions and resumptions on specific threads.

Both have their unique uses: you can't implement a `ThreadContextElement`
that registers every coroutine creation (like `kotlinx-coroutines-debug`
does), and you can't implement a callback to ensure a thread-local value
stays the same in a given coroutine across threads by using the debug
probes (but `ThreadContextElement` can).

These mechanisms are not in agreement with one another and have completely
separate implementations and inconsistent semantics.

Both should be rethought with tracing in mind.

Another issue is that both mechanisms are currently JVM-only,
though porting the implementation of `ThreadContextElement` to common code
is possible.

### Monitoring dispatcher load

#### `kotlinx.coroutines` level

`Dispatchers.Default` and `Dispatchers.IO` are completely opaque.
There is no way to figure out how many tasks are currently enqueued
or whether `Dispatchers.Default` is even working at its full capacity.
The best you can do is see how many threads are running—but that's
far from the complete picture.

This makes it impossible to perform a higher-level analysis of the system's
behavior. Was there a burst of activity just now? How big was it? Has the
length of the queue beeing gradually increasing or decreasing while the
CPU has been 100% busy for the past minute?
These are the kinds of questions `kotlinx.coroutines` can't answer.

What to use?
------------

#### `kotlinx.coroutines` level

Users routinely get confused about how to structure their code.
There are three main ways to obtain asynchronous behavior:

* `suspend` functions that return values.
* Non-`suspend` functions that start new coroutines and immediately return.
* `Flow`: a `kotlinx.coroutines` primitive that represents an asynchronous
  sequence of values.

These three approaches get mixed up regularly.

For example, the use case or returning an optional value whose computation
needs to suspend is often solved non-idiomatically:

```kotlin
// Correct:
suspend fun findUser(id: Int): User? {
    usersDb.query(id = id, limit = 1).forEach {
        return it
    }
    return null
}
// Usage: findUser(id)

// Incorrect 1 (bad):
fun findUser(id: Int): Flow<User> = flow {
    userDb.query(id = id, limit = 1).forEach {
        emit(it)
        return@flow
    }
}
// Usage: findUser(id).singleOrNull()

// Incorrect 2 (worse):
suspend fun findUser(id: Int): Flow<User> {
    userDb.query(id = id, limit = 1).forEach {
        return flowOf(it) // https://grep.app/search?q=return+flowOf%28
    }
    return emptyFlow() // https://grep.app/search?q=return+emptyFlow%28
}
// Usage: findUser(id).singleOrNull()
```

Another example is unnecessarily forking off coroutines when the caller
could have done it if they wanted to, without breaking structured concurrency:

```kotlin
// Correct:
suspend fun addUser(user: User) {
    withContext(Dispatchers.IO) {
        sqlite.insert(user.toDatabaseRow())
    }
}
// Usage: addUser(user)

// Incorrect (<https://grep.app/search?f.lang=Kotlin&q=%29.launch+%7B>):
fun addUser(user: User) {
    CoroutineScope(Dispatchers.IO).launch {
        sqlite.insert(user.toDatabaseRow())
    }
}
```

Structured concurrency
----------------------

### API structure promotes bad practices

#### `CoroutineScope()`

To start a coroutine, one needs a *coroutine scope* that serves as its parent.
`CoroutineScope` is both the interface whose instances represent coroutine
scopes *and* a function.

The natural consequence is that, when tasked with starting a coroutine,
people try using `launch` or `async`, learn that they need a coroutine scope,
and call the `CoroutineScope()` function.

The problem is that the result of the `CoroutineScope()` function is typically
not what you want.

To see why, as an analogy, consider autocloseable resources like files.
The most robust way to work with them is with the `.use` function
(or use the equivalent `try`/`finally` form):

```kotlin
openFile(path).use { file ->
    // work with the file
} // the `file` closes here automatically, preventing resource leaks
```

If you store a file in a variable or a field, you must take care to close it
once it's no longer needed.

A `CoroutineScope` acquired via a `CoroutineScope()` call is similar to
an autocloseable resource that is stored in a variable and requires
a lot of ceremony to get right:

```kotlin
// What you want in your program:

// `coroutineScope`:
// - Waits for the coroutines spawned in it to finish
// - Cancels the coroutines inside it if the caller gets cancelled
// - Rethrows the errors the child coroutines finished with
coroutineScope {
    launch {
        // do something
    }
    launch {
        // do something else
    }
}

// What you may naturally be inclined to write instead:
val scope = CoroutineScope(Dispatchers.Default)
scope.launch {
    // do something
}
scope.launch {
    // do something else
}

// How you can use `CoroutineScope()` properly:
val job = Job(currentCoroutineContext()[Job])
val scope = CoroutineScope(Dispatchers.Default + job)
val job1 = scope.launch {
    // do something
}
val job2 = scope.launch {
    // do something else
}
listOf(job1, job2).joinAll()
job.complete()
```

In addition to that, it is not obvious to the users that
`launch`, `async`, `withContext`, and other coroutine builders
expose a `CoroutineScope` to their lambdas, so occasionally,
users attempt creating a `CoroutineScope()` when they could simply
have relied on an existing receiver.

Recently, we published a detailed documentation update for this topic:
<https://github.com/Kotlin/kotlinx.coroutines/commit/1a85693bd7457744f6b2b1a6612b05955dc8d796>
We will need to wait and see if this is enough to uproot the established
bad practices.

#### Passing jobs to coroutines

`kotlinx.coroutines` is very flexible, consisting of somewhat orthogonal
components.
A `Job` is a representation of the lifecycle of a coroutine,
but it's also a separate concurrent data structure in its own right
that can be used independently of coroutines.
The connection between `Job`-the-lifecycle and `Job`-the-data-structure
lies in the fact that `Job` is a `CoroutineContext.Element`—something
a coroutine can store and propagate implicitly.

Most coroutine-starting APIs in `kotlinx.coroutines` accept arbitrary
`CoroutineContext.Element` values, *including a* `Job`.
This is a mistake: you want to inherit the `Job` from the parent coroutine,
so that the lifecycle of the child is connected to the lifecycle of the
parent.
However, sometimes, people intentionally want to sever the parent-child ties
in some aspects without realizing that this breaks structured concurrency.

We have started catching these problems in `kotlinx.coroutines`
by introducing overloads that catch some of such scenarios
<https://github.com/Kotlin/kotlinx.coroutines/pull/4435>,
as well as in IDEA <https://youtrack.jetbrains.com/issue/KTIJ-34347>.
See those initiatives for details.

Tracking issue: <https://github.com/Kotlin/kotlinx.coroutines/issues/3670>.

### Non-lexical child scopes

`kotlinx.coroutines` provides a *mostly* nice experience with lexical scoping,
that is, if nested coroutines don't need to outlive a function call:

```kotlin
coroutineScope {
    launch {
        repeat(5) {
            println("Hi from the first coroutine!")
            delay(50.milliseconds)
        }
    } // start one coroutine
    launch {
        repeat(5) {
            println("Hello from the second coroutine!")
            delay(66.milliseconds)
        }
    } // start another coroutine concurrently
} // will wait for the two `launch`'ed coroutines
println("Done with the greetings!")
```

Here, coroutine scopes created by `coroutineScope` and the two `launch`
form a structured concurrency hierarchy: the `launch` coroutines are
*children* of `coroutineScope`. This means:
* Cancelling `coroutineScope`'s scope will cancel the scopes of `launch`.
* If a `launch` fails with an exception, the `coroutineScope` will learn
  about that.
* `coroutineScope` waits for the launched coroutines to complete.

With non-lexically scoped coroutines (for example, those that should be put
into a field of an object), the situation is far worse.
There is no simple way to create a child coroutine scope, and this convoluted
form is required:

```kotlin
class Screen {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun SpinnerOnScreen() {
        // An entity whose lifecycle is nested into the lifecycle of the screen
        val spinnerScope = CoroutineScope(
            scope.coroutineContext + SupervisorJob(scope.coroutineContext.job)
        )
    }
}
```

In addition to that, the basic requirement to suspend while waiting for children
to complete requires even more convoluted code,
as does the use case of gracefully signalling that work in a coroutine scope
is done.

Tracking issue: <https://github.com/Kotlin/kotlinx.coroutines/issues/2758>.

### Multi-parent coroutine scopes

A common scenario requires that a coroutine's lifecycle is subject to *multiple*
separate coroutine scopes.
For example, a coroutine that updates a screen as a file gets downloaded
has two parent lifecycles:

1. The screen: once we leave the screen, even though the download shouldn't stop,
   the updates to the screen don't need to happen.
2. The download procedure: once the download stops, we no longer need to query
   its state.

`kotlinx.coroutines` does not support this, and workarounds are needed
to achieve behaviors resembling structured concurrency, like

```kotlin
val screenUpdatingJob = screenScope.launch {
    // Update the screen...
}
downloadScope.coroutineContext.job.invokeOnCompletion {
    screenUpdatingJob.cancel()
}
```

Tracking issue: <https://github.com/Kotlin/kotlinx.coroutines/issues/814>.

Scheduling
----------

### Scheduling is mediocre on Native

The JVM implementation boasts a sophisticated work-stealing scheduler,
but the Native version is a tiny implementation that performs a fan-out
distribution of tasks over a lazily allocating thread pool, and that's it.

Even naive attempts at porting the scheduler to Native resulted in massive
performance improvements, but relies on an experimental `kotlinx.atomicfu`
multiplatform synchronous mutex that wasn't yet battle-tested enough to
rely on it in production.

### Setting task priorities

A common complaint is the inability to ensure the liveness of important tasks
when other tasks are free to flood the dispatchers.

Issue reports:
* <https://github.com/Kotlin/kotlinx.coroutines/issues/3839>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/3178>

### Creating fairly shared task pools

There is always the risk of tasks from one subsystem flooding the dispatcher.
`limitedParallelism` is a limited (pun intended) way of handling this
that allows limiting the number of threads that are used to run tasks
simultaneously.
This is valuable and important, but not enough. Consider this:

```kotlin
list.map { async { compute(it) } }.awaitAll()
```

For a large list, this will flood the coroutine dispatcher with
hundreds of thousands of tasks.

The problem is twofold:
* First, it's inefficient to create all coroutines
  simultaneously when it's obvious that they can't all be executed yet.
  Even a semaphore with a generous 500 tokens prevents the unbounded
  memory consumption required for the upkeep of coroutines, which,
  while smaller than that of threads, is still non-zero.
* Second, and more importantly, unrelated tasks may starve during this.

Tracking issues:
* <https://github.com/Kotlin/kotlinx.coroutines/issues/172>,
* <https://github.com/Kotlin/kotlinx.coroutines/issues/1147>.

Time
----

### Unpredictable delays

`kotlinx.coroutines` treats delays and timeouts on the terms of the
coroutine dispatcher that executes the `suspend` function.

Example:

```kotlin
withContext(Dispatchers.Main) {
    // Schedules the code that will cancel `downloadPage`
    // to run **on the Main thread** in one seconds:
    val page = withTimeoutOrNull(1.seconds) {
        withContext(Dispatchers.IO) {
            downloadPage()
        }
    }
    if (page == null) {
        setErrorMessage("Timed out downloading the page")
        // Schedules `resetScreen` to run on the Main thread in one second:
        delay(1.seconds)
        resetScreen()
    } else {
        populateScreen(page)
    }
}
```

This works well and maps to the user expectations nicely in some cases
but not others.

```kotlin
val elapsed = measureTime {
    delay(1.seconds)
}
println(elapsed)
```

How much time will pass here?

Depending on what else happens on the coroutine dispatcher,
the delay may well take much longer than just one second.
For example, consider this scenario (https://pl.kotl.in/1aNUYuw9q):

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.concurrent.*
import kotlin.time.*

suspend fun main() {
    newSingleThreadContext("test").use { ctx ->
        withContext(ctx) {
            launch {
                Thread.sleep(1000) // 1 second
            }
            val elapsed = measureTime {
                delay(100) // 100 milliseconds
            }
            println(elapsed)
        }
    }
} // prints: 1.025308249s
```

Here, the following happens:

1. A single-threaded executor is created
   and starts running the `withContext` block
   on that single thread.
2. A new task spanning 1 second is scheduled on it.
3. `measureTime` is entered. `delay(100)` schedules
   the end of time measurement to happen on the
   one thread 100 milliseconds later.
4. The thread in the executor has nothing to do
   and proceeds to execute the 1-second task.
5. 100 milliseconds come and go, and the one thread
   is still busy.
6. Finally, the 1-second task is finished.
   The thread can process the delay now,
   which is very much overdue.

Worse, this only goes for some coroutine dispatchers but not all of them.
If a dispatcher doesn't implement the internal `Delay` interface,
a global delay-processing thread will be used instead.

That delay-processing thread is also not safe from arbitrary delays,
but that can at least be fixed:
<https://github.com/Kotlin/kotlinx.coroutines/pull/4277>.

### Flows

In `Flow`, there are many requests for operators that deal with time.
We haven't had the opportunity yet to focus on them,
but it's a major pain point.

Issues:

* <https://github.com/Kotlin/kotlinx.coroutines/issues/1927>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/3845>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/540>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/2188>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/1302>
* <https://github.com/Kotlin/kotlinx.coroutines/issues/4407>
