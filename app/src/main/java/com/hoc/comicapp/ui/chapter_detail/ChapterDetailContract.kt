package com.hoc.comicapp.ui.chapter_detail

import androidx.viewpager2.widget.ViewPager2
import com.hoc.comicapp.base.Intent
import com.hoc.comicapp.base.SingleEvent
import com.hoc.comicapp.base.ViewState
import com.hoc.comicapp.domain.models.ComicAppError
import com.hoc.comicapp.domain.models.getMessage
import com.hoc.comicapp.ui.chapter_detail.ChapterDetailViewState.Detail
import io.reactivex.Observable
import com.hoc.comicapp.domain.models.ChapterDetail as ChapterDetailDomain

interface ChapterDetailInteractor {
  fun getChapterDetail(
    chapterLink: String,
    chapterName: String? = null,
    time: String? = null,
    view: String? = null
  ): Observable<ChapterDetailPartialChange.Initial_Retry_LoadChapter_PartialChange>

  fun refresh(chapterLink: String): Observable<ChapterDetailPartialChange.RefreshPartialChange>
}

sealed class ChapterDetailViewIntent : Intent {
  data class Initial(val initial: Detail.Initial) :
    ChapterDetailViewIntent()

  object Refresh : ChapterDetailViewIntent()
  object Retry : ChapterDetailViewIntent()
  object LoadNextChapter : ChapterDetailViewIntent()
  object LoadPrevChapter : ChapterDetailViewIntent()
  data class LoadChapter(val link: String, val name: String) : ChapterDetailViewIntent()

  data class ChangeOrientation(@ViewPager2.Orientation val orientation: Int) :
    ChapterDetailViewIntent()
}

data class ChapterDetailViewState(
  val isLoading: Boolean,
  val isRefreshing: Boolean,
  val errorMessage: String?,
  val detail: Detail?,
  @ViewPager2.Orientation val orientation: Int
) : ViewState {

  sealed class Detail {
    data class Initial(
      val chapterLink: String,
      val chapterName: String,
      val time: String,
      val view: String
    ) : Detail()

    data class Data(val chapterDetail: ChapterDetailDomain) : Detail()
  }

  companion object {
    fun initial(): ChapterDetailViewState {
      return ChapterDetailViewState(
        isLoading = true,
        isRefreshing = false,
        detail = null,
        errorMessage = null,
        orientation = ViewPager2.ORIENTATION_VERTICAL
      )
    }
  }
}

sealed class ChapterDetailPartialChange {
  abstract fun reducer(state: ChapterDetailViewState): ChapterDetailViewState

  sealed class Initial_Retry_LoadChapter_PartialChange : ChapterDetailPartialChange() {
    override fun reducer(state: ChapterDetailViewState): ChapterDetailViewState {
      return when (this) {
        is InitialData -> {
          state.copy(detail = this.initial)
        }
        is Data -> {
          state.copy(
            isLoading = false,
            errorMessage = null,
            detail = Detail.Data(this.data)
          )
        }
        is Error -> {
          state.copy(
            isLoading = false,
            errorMessage = this.error.getMessage()
          )
        }
        Loading -> {
          state.copy(
            isLoading = true,
            errorMessage = null
          )
        }
      }
    }

    data class InitialData(val initial: Detail.Initial) : Initial_Retry_LoadChapter_PartialChange()
    data class Data(val data: ChapterDetailDomain) : Initial_Retry_LoadChapter_PartialChange()
    data class Error(val error: ComicAppError) : Initial_Retry_LoadChapter_PartialChange()
    object Loading : Initial_Retry_LoadChapter_PartialChange()
  }

  sealed class RefreshPartialChange : ChapterDetailPartialChange() {
    override fun reducer(state: ChapterDetailViewState): ChapterDetailViewState {
      return when (this) {
        is Success -> {
          state.copy(
            isRefreshing = false,
            errorMessage = null,
            detail = Detail.Data(this.data)
          )
        }
        is Error -> {
          state.copy(isRefreshing = false)
        }
        Loading -> {
          state.copy(isRefreshing = true)
        }
      }
    }

    data class Success(val data: ChapterDetailDomain) : RefreshPartialChange()
    data class Error(val error: ComicAppError) : RefreshPartialChange()
    object Loading : RefreshPartialChange()
  }
}

sealed class ChapterDetailSingleEvent : SingleEvent {
  data class MessageEvent(val message: String) : ChapterDetailSingleEvent()
}