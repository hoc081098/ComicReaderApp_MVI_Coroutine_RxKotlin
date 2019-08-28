package com.hoc.comicapp.ui.detail

import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.hoc.comicapp.base.BaseViewModel
import com.hoc.comicapp.domain.models.ComicDetail.Chapter
import com.hoc.comicapp.domain.models.getMessage
import com.hoc.comicapp.domain.thread.RxSchedulerProvider
import com.hoc.comicapp.utils.exhaustMap
import com.hoc.comicapp.utils.notOfType
import com.hoc.comicapp.worker.DownloadComicWorker
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.rx2.rxSingle
import timber.log.Timber

@ExperimentalCoroutinesApi
class ComicDetailViewModel(
  private val comicDetailInteractor: ComicDetailInteractor,
  private val workManager: WorkManager,
  rxSchedulerProvider: RxSchedulerProvider
) :
  BaseViewModel<ComicDetailIntent, ComicDetailViewState, ComicDetailSingleEvent>() {
  override val initialState = ComicDetailViewState.initialState()

  private val intentS = PublishRelay.create<ComicDetailIntent>()
  private val stateS = BehaviorRelay.createDefault<ComicDetailViewState>(initialState)

  override fun processIntents(intents: Observable<ComicDetailIntent>) =
    intents.subscribe(intentS)!!

  private val initialProcessor =
    ObservableTransformer<ComicDetailIntent.Initial, ComicDetailPartialChange> { intent ->
      intent.flatMap {
        comicDetailInteractor.getComicDetail(
          viewModelScope,
          it.link,
          it.title,
          it.thumbnail
        ).doOnNext {
          val message =
            (it as? ComicDetailPartialChange.InitialRetryPartialChange.Error ?: return@doOnNext)
              .error
              .getMessage()
          sendMessageEvent("Get detail comic error: $message")
        }
      }
    }

  private val retryProcessor =
    ObservableTransformer<ComicDetailIntent.Retry, ComicDetailPartialChange> { intent ->
      intent.flatMap {
        comicDetailInteractor.getComicDetail(viewModelScope, it.link)
          .doOnNext {
            val message =
              (it as? ComicDetailPartialChange.InitialRetryPartialChange.Error ?: return@doOnNext)
                .error
                .getMessage()
            sendMessageEvent("Retry get detail comic error: $message")
          }
      }
    }

  private val refreshProcessor =
    ObservableTransformer<ComicDetailIntent.Refresh, ComicDetailPartialChange> { intentObservable ->
      intentObservable
        .exhaustMap { intent ->
          comicDetailInteractor
            .refreshPartialChanges(
              coroutineScope = viewModelScope,
              link = intent.link
            )
            .doOnNext {
              sendMessageEvent(
                when (it) {
                  is ComicDetailPartialChange.RefreshPartialChange.Success -> "Refresh successfully"
                  is ComicDetailPartialChange.RefreshPartialChange.Error -> "Refresh not successfully, error: ${it.error.getMessage()}"
                  else -> return@doOnNext
                }
              )
            }
        }
    }

  private val intentToViewState = ObservableTransformer<ComicDetailIntent, ComicDetailViewState> {
    it.publish { shared ->
      Observable.mergeArray(
        shared.ofType<ComicDetailIntent.Initial>().compose(initialProcessor),
        shared.ofType<ComicDetailIntent.Refresh>().compose(refreshProcessor),
        shared.ofType<ComicDetailIntent.Retry>().compose(retryProcessor)
      )
    }.doOnNext { Timber.d("partial_change=$it") }
      .scan(initialState) { state, change -> change.reducer(state) }
      .distinctUntilChanged()
      .observeOn(rxSchedulerProvider.main)
  }

  init {
    val filteredIntent = intentS
      .compose(intentFilter)
      .doOnNext { Timber.d("intent=$it") }

    filteredIntent
      .compose(intentToViewState)
      .doOnNext { Timber.d("view_state=$it") }
      .subscribeBy(onNext = stateS::accept)
      .addTo(compositeDisposable)

    stateS
      .subscribeBy(onNext = ::setNewState)
      .addTo(compositeDisposable)

    filteredIntent
      .ofType<ComicDetailIntent.DownloadChapter>()
      .map { it.chapter }
      .flatMap { chapter ->
        enqueueDownloadComicWorker(chapter)
          .toObservable()
          .onErrorReturn { chapter to it }
      }
      .observeOn(rxSchedulerProvider.main)
      .subscribeBy {
        when {
          it.second === null -> {
            Timber.d("Enqueue success $it")
            sendMessageEvent("Enqueued download ${it.first.chapterName}")
          }
          else -> {
            Timber.d("Enqueue error $it")
            sendMessageEvent("Enqueued error: ${it.first.chapterName}")
          }
        }
      }
      .addTo(compositeDisposable)
  }

  private fun sendMessageEvent(message: String) {
    sendEvent(ComicDetailSingleEvent.MessageEvent(message))
  }

  private companion object {
    private val intentFilter = ObservableTransformer<ComicDetailIntent, ComicDetailIntent> {
      it.publish { shared ->
        Observable.mergeArray(
          shared.ofType<ComicDetailIntent.Initial>().take(1),
          shared.notOfType<ComicDetailIntent.Initial, ComicDetailIntent>()
        )
      }
    }
  }

  private fun enqueueDownloadComicWorker(chapter: Chapter): Single<Pair<Chapter, Throwable?>> {
    return rxSingle {
      val workRequest = OneTimeWorkRequestBuilder<DownloadComicWorker>()
        .setInputData(
          workDataOf(
            DownloadComicWorker.CHAPTER_LINK to chapter.chapterLink
          )
        )
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        )
        .build()
      workManager.enqueue(workRequest).await()
      chapter to null
    }
  }
}