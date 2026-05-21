Non-throwing constructors
=========================

<https://github.com/Kotlin/kotlinx-datetime/issues/68>

Problem: sometimes, parameters to be passed to constructors are user-supplied.
In this scenario, throwing exceptions is non-idiomatic: the error is expected.

Typically, to support non-throwing operations, we take the throwing operation
`foo` and create the corresponding operation `fooOrNull()`.

Examples:
* `firstOrNull`, accompanying `first`.
* `lastOrNull`, accompanying `last`.
* `parseOrNull`, accompanying `parse`.
* Dozens of them.

Of particular interest are things like `fun String.toIntOrNull(): Int?`.

BigCode has found the most functions called `fooOrBar`:
<https://bigcode.intellij.net/query/64755624-1825-4bd9-8a24-b1f565724d64>.

Manually the `createOrUpdate` and `getAndPut`-like functions and unclear
domain-specific things from the statistics, we get this top of results
(function/number of repositories):

* `firstOrNull`: 67970
* `getOrNull`: 46273
* `toIntOrNull`: 43515
* `lastOrNull`: 23605
* `getOrDefault`: 22488
* `toDoubleOrNull`: 22450
* `getColumnIndexOrThrow`: 17985
* `getOrElse`: 14741
* `toMediaTypeOrNull`: 14354
* `maxOrNull`: 11346
* `toLongOrNull`: 10461
* `getOrThrow`: 8120
* `singleOrNull`: 7854
* `exceptionOrNull`: 7681
* `toFloatOrNull`: 7610
* `minOrNull`: 6557
* `findByIdOrNull`: 4926
* `elementAtOrNull`: 3171
* `toHttpUrlOrNull`: 2358
* `getStringOrNull`: 1975
* `removeFirstOrNull`: 1860
* `removeLastOrNull`: 1781
* `randomOrNull`: 1686
* `executeAsOneOrNull`: 1661
* `firstOrError`: 1525
* `elementAtOrElse`: 1179
* `toBooleanStrictOrNull`: 1131
* `toBigDecimalOrNull`: 1091
* `propertyOrNull`: 1021
* `lastItemOrNull`: 978
* `getIntOrNull`: 937
* `insertOrThrow`: 880
* `getOrFail`: 842
* `nextOrNull`: 739
* `getLongOrNull`: 724
* `startCoroutineUninterceptedOrReturn`: 704
* `awaitFirstOrNull`: 703
* `parseOrNull`: 674
* `maxByOrNull`: 673
* `digitToIntOrNull`: 671
* `singleOrError`: 650
* `toShortOrNull`: 633
* `toBigIntegerOrNull`: 622
* `valueOrNull`: 618
* `awaitSingleOrNull`: 588
* `firstOrDefault`: 587
* `justOrEmpty`: 585
* `toByteOrNull`: 583
* `receiveOrNull`: 578
* `getCompletionExceptionOrNull`: 517
* `maxWithOrNull`: 471
* `getValueOrNull`: 471
* `mapToOneOrNull`: 453
* `collectionSizeOrDefault`: 446
* `fqNameOrNull`: 438
* `toUIntOrNull`: 432
* `indexOfOrNull`: 428
* `lastBlockStatementOrThis`: 385
* `getSuperClassOrAny`: 378
* `fromStringOrNull`: 377
* `getTypeParameterDescriptorOrNull`: 368
* `getOrEmpty`: 367
* `toULongOrNull`: 365
* `toSingletonMapOrSelf`: 364
* `toBooleanOrNull`: 358
* `firstIsInstanceOrNull`: 338
* `currentOrNull`: 338
* `getOrElseNullable`: 335
* `getColorOrThrow`: 327
* `relativeToOrNull`: 322
* `toUShortOrNull`: 322
* `getDoubleOrNull`: 320
* `visibilityModifierTypeOrDefault`: 318
* `minWithOrNull`: 315
* `reduceOrNull`: 310
* `loadClassOrNull`: 308
* `getOrImplicitDefault`: 305
* `collectionSizeOrNull`: 303
* `toIntExactOrNull`: 303
* `getCellOrNull`: 303
* `lastIndexOfOrNull`: 303
* `toLongExactOrNull`: 300
* `toByteExactOrNull`: 300
* `toTypeElementOrNull`: 299
* `toShortExactOrNull`: 299
* `nextInsertedOrNull`: 298
* `minByOrNull`: 297
* `toVersionOrNull`: 293
* `getQualifiedExpressionForSelectorOrThis`: 291
* `maxOfOrNull`: 286
* `getExtensionOrNull`: 285
* `parentOrNull`: 283
* `getReferencedClassOrNull`: 282
* `nextOrSame`: 280
* `findByIdOrThrow`: 279
* `getResourceIdOrThrow`: 277

We can group them by their purpose:

* A fallible computation or `null`:
  `firstOrNull`, `getOrNull`, `lastOrNull`, `maxOrNull`,
  `singleOrNull`, `exceptionOrNull`, `minOrNull`, `findByIdOrNull`,
  `elementAtOrNull`, `getStringOrNull` (JSON maps), `removeFirstOrNull`,
  `removeLastOrNull`, `randomOrNull`, `executeAsOneOrNull` (SQLDelight),
  `propertyOrNull`, `lastItemOrNull`, `getIntOrNull` (map of settings),
  `nextOrNull`, `getLongOrNull`, `awaitFirstOrNull`,
  `parseOrNull`, `maxByOrNull`, `digitToIntOrNull`,
  `valueOrNull` (something Compose), `awaitSingleOrNull`, `receiveOrNull`,
  `getCompletionExceptionOrNull`, `maxWithOrNull`, `getValueOrNull`,
  `mapToOneOrNull`, `fqNameOrNull`, `toUIntOrNull`, `indexOfOrNull`,
  `getTypeParameterDescriptorOrNull`, `firstIsInstanceOrNull`, `currentOrNull`,
  `relativeToOrNull`, `getDoubleOrNull`, `minWithOrNull`, `reduceOrNull`,
  `loadClassOrNull`, `collectionSizeOrNull`, `getCellOrNull`,
  `lastIndexOfOrNull`, `nextInsertedOrNull`, `minByOrNull`, `maxOfOrNull`,
  `getExtensionOrNull`, `parentOrNull`, `getReferencedClassOrNull`
* A fallible computation or some other result:
  `getOrDefault`, `getOrElse`, `elementAtOrElse`,
  `startCoroutineUninterceptedOrReturn`, `firstOrDefault`,
  `justOrEmpty` (RxJava), `collectionSizeOrDefault`, `getSuperClassOrAny`,
  `getOrEmpty`, `visibilityModifierTypeOrDefault`,
  `getOrImplicitDefault`
* Explicit mention of throwing:
  `getColumnIndexOrThrow`, `getOrThrow`, `firstOrError` (RxJava),
  `insertOrThrow`, `getOrFail`, `singleOrError` (RxJava),
  `getColorOrThrow`, `findByIdOrThrow`, `getResourceIdOrThrow`
* Fallible construction from the receiver:
  `toIntOrNull`, `toDoubleOrNull`, `toMediaTypeOrNull`,
  `toLongOrNull`, `toFloatOrNull`, `toHttpUrlOrNull`,
  `toBooleanStrictOrNull`, `toBigDecimalOrNull`,
  `toShortOrNull`, `toBigIntegerOrNull`, `toByteOrNull`,
  `toULongOrNull`, `toBooleanOrNull`, `toUShortOrNull`,
  `toIntExactOrNull`, `toLongExactOrNull`, `toByteExactOrNull`,
  `toTypeElementOrNull`, `toShortExactOrNull`, `toVersionOrNull`,
* Fallible construction as a companion object method:
  `fromStringOrNull`
* A fallible computation or *this*:
  `lastBlockStatementOrThis`, `toSingletonMapOrSelf`,
  `getQualifiedExpressionForSelectorOrThis`, `nextOrSame`
* Unclear: `getOrElseNullable`

Proposal:

* Introduce `Local(Date)?(Time)?.fromOrNull` and
  establish the precedent of non-throwing construction.
* From now on, `fromOrNull` to other classes where non-validated user input is
  expected to be passed directly.

