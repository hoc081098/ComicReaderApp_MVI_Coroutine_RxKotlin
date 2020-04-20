package com.hoc.comicapp.base

import androidx.annotation.CheckResult
import androidx.lifecycle.LiveData
import com.hoc.comicapp.utils.Event
import com.shopify.livedataktx.LiveDataKtx
import io.reactivex.Observable
import io.reactivex.disposables.Disposable

/**
 * Object that will subscribes to a MviView's [MviIntent]s,
 * process it and emit a [MviViewState] back.
 *
 * @param I Top class of the [MviIntent] that the [MviViewModel] will be subscribing to.
 * @param S Top class of the [MviViewState] the [MviViewModel] will be emitting.
 * @param E Top class of the [MviSingleEvent] that the [MviViewModel] will be emitting.
 */
interface MviViewModel<I : MviIntent, S : MviViewState, E : MviSingleEvent> {
  val state: LiveDataKtx<S>

  val singleEvent: LiveData<Event<E>>

  @CheckResult fun processIntents(intents: Observable<I>): Disposable
}