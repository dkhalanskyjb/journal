[Serializability of coroutine classes](https://github.com/Kotlin/kotlinx.coroutines/issues/76)
----------------------------------------------------------------------------------------------



[Writing complex actors](https://github.com/Kotlin/kotlinx.coroutines/issues/87)
--------------------------------------------------------------------------------



[Channel possibly not acting fair](https://github.com/Kotlin/kotlinx.coroutines/issues/111)
-------------------------------------------------------------------------------------------

#### Summary

```kotlin
fun main(vararg args: String) {
    val channel = Channel<Int>(Channel.UNLIMITED) // CONFLATED
    val context = newSingleThreadContext("test")

    // producer
    launch(context) {
        var i = 0
        while (true) {
            channel.send(i++)
            // yield()
        }
    }

    // consumer
    launch(context) {
        channel.consumeEach { println(it) }
    }
    Thread.sleep(15000)
}
```

Nothing is printed.

This code from <https://github.com/Kotlin/kotlinx.coroutines/issues/111#issuecomment-333754848>
shows the issue: when producers and consumers share the same thread, the
producer must sometimes yield, or consumers are starved, leading to the elements
being piled up or lost, but never processed.

The proposed solution <https://github.com/Kotlin/kotlinx.coroutines/issues/111#issuecomment-502937117>
is to force fairness periodically.

#### Thoughts

Here's a thing to consider regarding the "suspend periodically" option.
Let's say we suspend for every 32 emissions:

```kotlin
fun main() {
    val channel = Channel<Int>(16, BufferOverflow.DROP_LATEST)
    runBlocking {
        // producer
        val job1 = launch {
            var i = 0
            while (true) {
                channel.send(i++)
                if (i % 32 == 0) {
                    yield()
                }
            }
        }
        // consumer
        val job2 = launch {
            channel.consumeEach { println(it) }
        }
        delay(10)
        listOf(job1, job2).forEach { it.cancelAndJoin() }
    }
}
```
Output:
```kotlin
0
1
2
3
4
5
6
7
8
9
10
11
12
13
14
15
32
33
34
35
36
37
38
39
40
41
42
43
44
45
46
47
48
64
```
Note the missing numbers. So, if we naively wrap the `send` into something that
keeps a counter, we will go from "clearly doesn't work, add an `yield`" to
"looks like it works, let's ship this."

Let's try to work out the semantics that could be expected here.

Try to submit an element, but never throwing elements away.
- If successful, increase the (thread-local?) counter.
  If the counter is 32, yield.
- If *un*successful, due to the buffer being full and needing to throw away
  an element. Reset the counter, yield, then retry.
  If still unsuccessful, this means that the consumer had bigger problems than
  our thread being busy, so we proceed to throw away the element.

This behavior doesn't feel linearizable though.
