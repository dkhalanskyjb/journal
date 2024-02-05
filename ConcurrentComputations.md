Ruminations on concurrent computations
--------------------------------------

### Lifecycle of concurrent tasks

In kotlinx-coroutines, conceptually, a piece of code running in a coroutine can
be in one of the following states:

* Unstarted: it was created, but its code is not yet running.
* Running: it's running.
* Cancelling: there's a request to stop the computation the coroutine is doing,
  but nothing is decided yet.
* Finished: the code completed its computation, either with an error or with a
  result.
* Cancelled: the code obeyed the request to stop the computation and finished
  with a `CancellationException` (or was cancelled before even starting).
* Cancelled (fake): the code finished with a `CancellationException`, but not
  because it detected the request for cancellation.

The lifecycle can be described as such:

```
Unstarted    /----------------> Cancelled (fake) <-|
 \          /                                      |
  \--> Running --------->|                         |
   \    \                |----> Finished           |
    \    \          /--->|                         |
     \    \        /                               |
      \    \--> Cancelling ---> Cancelled          |
       \            \               /|             |
        \----------o \ o-----------/               |
                      \                            |
                       \---------------------------|
```

### Lifecycle of task collections

Additionally, the task collection in which this code executes can be in one of
the following states:

* Waiting: the tasks are running unhindered.
* Cancelled and waiting: the task collection was asked to stop what it's doing,
  and it's trying to forward the request to the tasks.
* Failed and waiting: the task collection encountered an error and is trying to
  cancel all the tasks.
* Completed: it's done waiting, all of the tasks have finished.

```
Waiting ----------------------------------->|
  \                                         |
   \--> Cancelled and waiting ------------->|
    \     \                                 |--> Completed
     \     \                                |
      \     \-->|                           |
       \        |--> Failed and waiting --->|
        \------>|
```

### Relationship between tasks and task collections

A typical task collection (created by `launch`, `async`, `runBlocking`,
`coroutineScope`...) is often identified with the "main" task, the one provided
in the code block. Their state machines are merged: after the code of the
main task itself completes, it starts waiting for the other tasks in the
collection.

With the current terminology, the task collection is called a
"coroutine scope"; if there is a main task, it's called a "coroutine"; the other
tasks in the collection are called "child coroutines".

There is very little difference between the main task and all the other ones.

### Error handling

As is right now, error handling happens as follows: if some code on which a
coroutine depends fails, the whole coroutine fails with the same exception, and
the other code gets cancelled.

It only remembers a single failure. We can observe this as follows:

```kotlin
import kotlinx.coroutines.*

fun main() {
    runCatching {
        runBlocking {
            launch {
                delay(1000)
                try {
                    throw IllegalArgumentException("X")
                } finally {
                    println("thrown X")
                }
            }
            launch {
                try {
                    delay(2000)
                    throw IllegalStateException("Y")
                } finally {
                    println("thrown Y")
                }
            }
        }
    }.exceptionOrNull()?.printStackTrace()
}
```

This code prints:

```
thrown X
thrown Y
java.lang.IllegalArgumentException: X
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt$main$1$1$1.invokeSuspend(CheckKotlinCoroutines.kt:11)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:231)
	at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:164)
	at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:470)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl(CancellableContinuationImpl.kt:504)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl$default(CancellableContinuationImpl.kt:493)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeUndispatched(CancellableContinuationImpl.kt:591)
	at kotlinx.coroutines.EventLoopImplBase$DelayedResumeTask.run(EventLoop.common.kt:490)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:81)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:55)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:34)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt.main(CheckKotlinCoroutines.kt:7)
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt.main(CheckKotlinCoroutines.kt)
```

Exception `Y` is simply swallowed.

However, the "main" task of a coroutine scope has special behavior: if *it*
throws an exception, that exception also gets reported.

```kotlin
import kotlinx.coroutines.*

fun main() {
    runBlocking {
        runCatching {
            coroutineScope {
                val job1 = launch {
                    delay(1000)
                    try {
                        throw IllegalArgumentException("X")
                    } finally {
                        println("thrown X")
                    }
                }
                println(runCatching { job1.join() })
                throw IllegalStateException("Y")
            }
        }.exceptionOrNull()?.printStackTrace()
    }
}
```

```
Failure(kotlinx.coroutines.JobCancellationException: ScopeCoroutine is cancelling; job=ScopeCoroutine{Cancelling}@7907ec20)
java.lang.IllegalArgumentException: X
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt$main$1$1$1$job1$1.invokeSuspend(CheckKotlinCoroutines.kt:12)
	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
	at kotlinx.coroutines.DispatchedTaskKt.resume(DispatchedTask.kt:231)
	at kotlinx.coroutines.DispatchedTaskKt.dispatch(DispatchedTask.kt:164)
	at kotlinx.coroutines.CancellableContinuationImpl.dispatchResume(CancellableContinuationImpl.kt:470)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl(CancellableContinuationImpl.kt:504)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeImpl$default(CancellableContinuationImpl.kt:493)
	at kotlinx.coroutines.CancellableContinuationImpl.resumeUndispatched(CancellableContinuationImpl.kt:591)
	at kotlinx.coroutines.EventLoopImplBase$DelayedResumeTask.run(EventLoop.common.kt:490)
	at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:277)
	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:81)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:55)
	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:34)
	at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt.main(CheckKotlinCoroutines.kt:6)
	at checkKotlinCoroutines.CheckKotlinCoroutinesKt.main(CheckKotlinCoroutines.kt)
	Suppressed: java.lang.IllegalStateException: Y
		at checkKotlinCoroutines.CheckKotlinCoroutinesKt$main$1$1$1.invokeSuspend(CheckKotlinCoroutines.kt:18)
		at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
		at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:102)
		... 8 more
```

### Dependency edges

When two pieces of code run concurrently, their relationship to one another can
be described using just three properties:

* How should `B` react when `A` fails?
* How should `B` react when `A` completes successfully?
* Should `B` wait for `A` to complete?


