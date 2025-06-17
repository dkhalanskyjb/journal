How equality could work in Kotlin
=================================

Executive summary
-----------------

This is a document about the `==` operator.

Incorrect usages of `==` are a problem that actually affects our users:
<https://youtrack.jetbrains.com/issue/KT-72766>.
See <https://youtrack.jetbrains.com/issue/KT-72766/K2-No-Operator-cannot-be-applied-when-comparing-data-classesfocus=Comments-27-11005554.0-0>
in particular.

In Java-like languages,
for `equals` to be correct, it should in almost all cases have this property:
if `Common` has its own `equals` logic that has
`child1 : Child1 : Common == child2 : Child2 : Common`, then its children
*are not allowed* to implement their own `equals` logic.
The cases where this rule is violated, but the `equals` contract is satisfied,
behave unintuitively.

To mitigate the pains that come from invalid `==` implementations or from
using `==` incorrectly, we can introduce new syntax that
allows defining `==` behavior in a safer manner.
This change is backward-compatible, and the new syntax looks natural,
not requiring any annotations.

The new syntax:

```kotlin
class MyClass(val x: Int) {
    operator fun equals(other: MyClass): Boolean = x == other.x
}
```

The introduced type safety:

```kotlin
fun f() {
    val a = MyClass(3)
    val b = MyClass(4)
    val c = true
    println(a == b) // compiles!
    // println(a == c) // does not compile!
}
```

Inherent restrictions:

* `operator fun equals` arguments can not have type parameters.
* `operator fun equals` can not be an extension function.

Initially, there are other restrictions on `operator fun equals`:
only final classes can override `equals`, and only `Self` can be the argument.
However, there are ways to make this functionality more flexible
if there is demand for that, while improving type safety
over `equals(other: Any?)` every step of the way.

The old `equals(other: Any?)` function should be considered deeply niche,
and the IDE should suggest transforming it into `equals(other: Self)`
if the form is the usual one.

Prior art
---------

Without subclassing, the notion of equality is straightforward:

* Haskell: `(==) :: a -> a -> Bool`
* Rust: `fn eq(&self, other: &Self) -> bool`
  (complicated by some instances, like `NaN` and `NaN` being incomparable,
  leading to two iterfaces for equality: `PartialEq` and `Eq`)

Subclassing introduces a problem, as with it, values of different types
can be meaningfully compared (for example, `LinkedList` and `ArrayList`).

* F# does not do anything special, it just delegates to what C# does.
* Swift has two mechanisms for defining equality:
  - `isEqual(_ object: Any?) -> Bool`
    This function is overridden and used dynamically.
  - `static func == (lhs: Self, rhs: Self) -> Bool` in the `Equatable` protocol.
    This function is resolved statically.
  - The interactions between these two mechanisms can be surprising:
    <https://noahgilmore.com/blog/nsobject-equatable>

Interactions between equality and subclassing
---------------------------------------------

There is a rule of thumb governing how `equals` interacts with subclassing,
and that rule is: if a parent type defines how its elements are compared,
the child type can not introduce its own logic.

### Examples

Before formulating an exact statement, some examples
(omitting `hashCode` for brevity).

```kotlin
class Pair<T, U>(val a: T, val b: U) {
    override fun equals(other: Any?) =
        other is Pair<*, *> && a == other.a && b == other.b
}
```

This implementation in a vacuum is correct, and its comparison category is
exactly the type `Pair<*, *>`. No other instances can be compared with a `Pair`.

```kotlin
object True {
    override fun equals(other: Any?) =
        other is True || other is MyBoolean && other.b
}

object False {
    override fun equals(other: Any?) =
        other is False || other is MyBoolean && !other.b
}

class MyBoolean(private val b: Boolean) {
    override fun equals(other: Any?) =
        other is MyBoolean && b == other.b ||
        b && other is True ||
        !b && other is False
}
```

This is another correct example, defining *two* comparison categories,
one including `True` and `MyBoolean(true)`, the other one being
`False` and `MyBoolean(false)`.

It is unusual for a class to belong to several comparison categories, though.

```kotlin
class UnorderedList<T>(
    private val underlyingList: List<T>
): List<T> by underlyingList {
    override fun equals(other: Any?) =
        if (other is UnorderedList<*>) {
            underlyingList.toSet() == other.underlyingList.toSet()
        } else {
            underlyingList == other
        }
}
```

This implementation is incorrect, because `List<*>` *already is*
a comparison category, but this implementation attempts to add a new hierarchy
level to it, making some non-equal elements equal.
It breaks transitivity:
`listOf(1, 2) == unorderedListOf(1, 2) == unorderedListOf(2, 1) == listOf(2, 1)`.

```kotlin
class ListWithOperationCount<T>(
    private val underlyingList: List<T>,
    private var operationsMade: Int = 0,
): MutableList<T> by underlyingList {
    override fun equals(other: Any?) =
        if (other is ListWithOperationCount<*>) {
            underlyingList == other.underlyingList &&
            operationsMade == other.operationsMade
        } else {
            underlyingList == other
        }

    override fun add(element: T): Boolean {
        ++operationsMade
        return super.add(element)
    }

    override fun remove(element: E): Boolean {
        ++operationsMade
        return super.remove(element)
    }

    // and so on
}
```

This implementation is also incorrect, again because `List<*>` already is
a comparison category, but for the opposite reason: now, the implementation
attempts to add a new hierarchy level, making some *equal* elements
non-equal.
This breaks transitivity again, but with the opposite scheme:
`ListWithOperationCount(listOf(), 0) == listOf() == ListWithOperationCount(listOf(), 2)`.

One can attempt by fixing this having this class belong to several comparison
categories, `MyBoolean`-style:

- If no operations were performed, it's in the comparison category defined by
  `List`:
  `listOf() == ListWithOperationCount(listOf(), null)`.
- If operations *were* performed, it's in a separate comparison category,
  defined only for other `ListWithOperationCount` instances where
  `operationsMade` is defined.

```kotlin
class ListWithOperationCount<T>(
    private val underlyingList: List<T>,
    private var operationsMade: Int = 0,
): MutableList<T> by underlyingList {
    override fun equals(other: Any?) =
        if (other is ListWithOperationCount<*>) {
            underlyingList == other.underlyingList &&
            operationsMade == other.operationsMade
        } else {
            operationsMade == 0 && underlyingList == other
        }

    override fun add(element: T): Boolean {
        ++operationsMade
        return super.add(element)
    }

    override fun remove(element: E): Boolean {
        ++operationsMade
        return super.remove(element)
    }

    // and so on
}
```

*This* implementation is once again incorrect:
`listOf() == ListWithOperationCount(listOf(), 5) != listOf()`

Together, these examples mean the following:

* If the comparison category dictates that elements are equal,
  they can't be made non-equal.
* If the comparison category dictates that elements are non-equal,
  they can't be made equal.

### Statement

The `equals` contract gives us this validity criterion:

**If `a` is equal to `b`, then `a == c` if and only if `c == b`.**

With the above scenarios in mind, we can extract a more specific rule for
interactions between equality notions and subclassing:

**If `A <: B` and the equality logic of `B` requires that
`a is A` is equal to `b is B`, `b is not A`,
then `A` also must fully delegate its equality notion to `B`'s logic**.

Here, "`B`'s logic" can be either an explicit `equals` implementation in `B`,
a textual contract (like the one for `java.util.Collection`), or, for `sealed B`
hierarchies, just the total of all `equals` implementations taken together.

We can see that according to this rule, there are no hierarchies to equality
notions: a class either has an equality notion or it doesn't, and if it does,
all its subclasses must obey that notion without introducing their own logic.

#### Exceptions to the rule

The statement is not mathematical,
not just because it relies on the informal notion of "`B`'s logic",
but also because the `equals` contract is more lenient than that.
There *are* cases when `A` does not *have* to delegate its whole equality logic
to `B`.

All the provided examples, however, fit the simplified rule.

Let us reconstruct the specific circumstances where no `equals` contract is
violated, but the rule of thumb still deems the equality notion incorrect.

We see that having `b is B`, `b is not A` equal to `a is A` defines the
equality of `a` with every other value.
This means that the exceptions are only the cases where some `a' is A` is *not*
equal to anything outside `A`.

When can this happen? For example, it can not happen for any implementors
of `List<T>`, as `ArrayList<T>` will happily compare itself to any `List<T>`
using only the `List` methods that any `List` must implement, so for any
`List<T>`, there is some `ArrayList<T>` equal to it.
Therefore, the problem can only happen when some portion of the data used
by the supertype for equality comparisons that allows distinguishing between
subtypes.

Example:

```kotlin
abstract class Message(val contents: String) {
    // implementor's note: only use tags from `knownTags`
    abstract val tag: String

    override fun equals(other: Any?) =
        other is Message && tag == other.tag && contents == other.contents
}

class UrgentMessage(contents: String) : Message(contents) {
    override val tag = "urgent"

    // can not meaningfully override `equals`
}

class DatedMessageWithTag(
    override val tag: String,
    val sentAt: kotlin.time.Instant,
    contents: String
) : Message(contents) {
    override fun equals(other: Any?) =
        if (tag in knownTags) {
            super.equals(other)
        } else {
            other is DatedMessageWithTag &&
            sentAt == other.sentAt &&
            super.equals(other)
        }
}

// tags that can be used by messages that are
// not of type `DatedMessageWithTag`
val knownTags = listOf("urgent")
```

Here, a `DatedMessageWithTag` instance can use the external knowledge that no
value of any other type may have the `tag` that it has, and therefore,
can not be equal to it.
Therefore, this implementation is *correct*, but does not fit the rule of thumb
and also looks odd.

### Consequences for the programmer

#### Incorrect by-delegation for interfaces constraining equality

This simple code is incorrect:

```kotlin
object C : List<Int> by listOf(1, 2, 3)
```

because

```kotlin
(C == listOf(1, 2, 3)) == false
(listOf(1, 2, 3) == C) == true
```

To implement this, one needs

```kotlin
private val cList = listOf(1, 2, 3)

class C : List<Int> by cList {
    override fun equals(other: Any?) = cList == other
    override fun hashCode() = cList.hashCode()
}
```

By implementing the `List` interface, you enter `List`'s equality notions,
and those are not satisfied by default.

#### Diamond inheritance hierarchies with equality

A class can not inherit from/implement two supertypes each of which has its
own equality logic.

No two+-element collection can be both a `List` and a `Set`, because both `List`
and `Set` defines an `equals` contract, and they are incompatible.

```kotlin
val mySetList = [1, 2]
listOf(1, 2) == mySetList1 // true
mySetList == setOf(1, 2) // true
listOf(1, 2) == setOf(1, 2) // true by transitivity, but is false
```

#### Unclear equality notions

According to the rule, given a type `T`, there is at most one parent type
defining the equality notion for all of its children (including `T`),
it can be unclear at a glance
(or even by checking which `equals` implementations are provided)
which type it is.

```kotlin
interface UnresolvedZonedDateTime

class ZonedDateTime : UnresolvedZonedDateTime

fun UnresolvedZonedDateTime.withDate(date: LocalDate): UnresolvedZonedDateTime

// will this comparison work, or is it always `false`?
zdt.withDate(date) == zdt
// It depends on whether `ZonedDateTime` is
// 1. just an `UnresolvedZonedDateTime` with `override val resolved = true`
//    (which means the equality is probably on `UnresolvedZonedDateTime`)
// 2. some value with extra data compared to `UnresolvedZonedDateTime`
//    (which means the equality must be on `ZonedDateTime`)
// Understanding the structure of these data types and how equality works
// is a prerequisite for just reading this code.
```

Proposal
--------

### Minimum viable implementation

1. Allow defining `operator fun equals(other): Boolean` with the following
restrictions:
  - It must be a member, it can not be an extension.
  - Only final classes can define this function.
  - `other` has to be exactly the same type as the declaring class.
2. If this function is defined, then `fun equals(other: Any?): Boolean`
is automatically overridden with this implementation (unless one was explicitly
provided, for example, for optimizing the dispatching):
```kotlin
override fun equals(other: Any?): Boolean =
    this === other || other is Self && equals(other)
```
3. If this function is defined, then attempts to statically call
   `equals(other: Any?)` with a parameter that can't be cast down to `Self`
   should emit an error, as it is not going to return `true` in any scenario.
4. Add an IDE inspection suggesting to rewrite `equals(Any?)` of the correct
   form into the `operator fun` one if the other conditions are fulfilled.

Benefits:

- Now, it is entirely clear what function gets called if it is done statically.
  Just look at its signature.
- Type safety: if the author of the class decided that this implementation is
  enough (which it often is),
  comparisons with other random garbage are prohibited.
- Potentially improved performance due to lack of virtualization for statically
  resolved calls to `equals` (I don't know if that actually has an effect).
- Backward compatibility: only red code becomes green, this feature is opt-in.

### Fundamental restrictions of the approach

While some restrictions are negotiable, some are *fundamental*:

* `operator fun equals` must be a member, it can not be an extension.
  The operator function can not be an extension function, as `equals(Any?)` need
  to be compiled before the set of available extension functions
  (which can be user-defined) is known.
* In `equals(other: U)`,
  `U` is not allowed to have type arguments with values other than `<*>`.
  Allowing `U` to have type arguments could mean this overload:
  `ImmutableIntList.equals(ImmutableList<Number>)`.
  Passing `ImmutableList<Any>` to the generated `equals(other: Any?)`
  would then call this overload and possibly cause a `ClassCastException`.
* If `T.equals(U)` is defined, `U : V`,
  `V` can potentially have more implementations than just `U`,
  and `V` itself defines an `equals` implementation
  (either with `Any?` or the `operator fun`),
  `T.equals(V)` also must be defined.
  This encodes the rule about subtypes not being allowed to introduce their own
  `equals` logic:
  you *are* allowed to define your own `T.equals(U)`, but only as a performance
  optimization, you still have to recognize the equality comparison rules
  defined for the supertype `V`.


The other restrictions are negotiable and have to do with making sure it is
not straightforward to violate `equals` contracts by defining arbitrary
functions that don't agree with each other.

### Enhancement 1: allow `equals` up the inheritance tree

This approach can be developed further to remove some of the limitations and
add type safety and clear static dispatches in a backward-compatible manner to
more equality-overriding use cases.

The relaxed restriction in addition to the fundamental ones is just this:

- `T.equals(U)` is only allowed if `T : U`.

Then, `fun equals(other: Any?): Boolean` gets desugared into this:

```kotlin
override fun equals(other: Any?): Boolean = this === other || when (other) {
    // for each available overload,
    // in the order in which static resolution would prioritize the overload
    is Parent1 -> equals(other)
    is Parent2 -> equals(other)
    is Grandparent1 -> equals(other)
    // ...
}
```

At this point, the IDE should also highlight any manual `equals(other: Any?)`
implementations, as comparisons with values that are not in your inheritance
tree is already very niche.

### Enhancement 2: Allow `equals` with arbitrary types

The "same hierarchy" restriction can also be lifted,
but it is more challenging to implement and encourages odd practices.

- If any `T.equals(V)` is defined,
  then least one `T.equals(U)` has to be defined where `T : U` or `T = U`.
  This ensures that `t == t` can be called.
- `T.equals(U)` is only allowed if there is also a `operator fun equals`
  that would accept a call of the form `U.equals(T)`
  or if `U` or one of its supertypes implements `equals(other: Any?)`.
  This ensures that symmetry is at least theoretically possible.
- Similarly, transitivity can be ensured by checking that
  if it's possible to call `t == u` and `u == v` using some of the
  `equals` overloads (including the legacy ones), then `t == v` should
  also be callable.

