package com.fenchtose.movieratings.features.searchpage

import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.bumptech.glide.Glide
import com.fenchtose.movieratings.BuildConfig
import com.fenchtose.movieratings.R
import com.fenchtose.movieratings.analytics.ga.GaCategory
import com.fenchtose.movieratings.analytics.ga.GaEvents
import com.fenchtose.movieratings.analytics.ga.GaScreens
import com.fenchtose.movieratings.base.BaseFragment
import com.fenchtose.movieratings.base.BaseMovieAdapter
import com.fenchtose.movieratings.base.RouterPath
import com.fenchtose.movieratings.base.redux.Dispatch
import com.fenchtose.movieratings.base.router.Navigation
import com.fenchtose.movieratings.features.info.InfoPageBottomView
import com.fenchtose.movieratings.features.moviecollection.collectionpage.MovieCollectionOp
import com.fenchtose.movieratings.features.moviepage.MoviePath
import com.fenchtose.movieratings.features.trending.TrendingPath
import com.fenchtose.movieratings.features.updates.Banner
import com.fenchtose.movieratings.features.updates.Load
import com.fenchtose.movieratings.features.updates.UpdateBannersState
import com.fenchtose.movieratings.model.db.like.LikeMovie
import com.fenchtose.movieratings.model.db.movieCollection.AddToCollection
import com.fenchtose.movieratings.model.entity.Movie
import com.fenchtose.movieratings.model.entity.MovieCollection
import com.fenchtose.movieratings.model.image.GlideLoader
import com.fenchtose.movieratings.util.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class SearchPageFragment: BaseFragment() {

    private var progressbar: ProgressBar? = null
    private var attributeView: TextView? = null
    private var searchView: EditText? = null
    private var clearButton: View? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: BaseMovieAdapter? = null
    private var adapterConfig: SearchAdapterConfig? = null
    private var appInfoContainer: ViewGroup? = null

    private var bannerContainer: View? = null

    private var watcher: TextWatcher? = null
    private var querySubject: PublishSubject<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.search_page_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressbar = view.findViewById(R.id.progressbar)
        attributeView = view.findViewById(R.id.api_attr)
        recyclerView = view.findViewById(R.id.recyclerview)
        searchView = view.findViewById(R.id.search_view)
        clearButton = view.findViewById(R.id.clear_button)

        bannerContainer = view.findViewById(R.id.update_banner_container)

        path?.takeIf { it is SearchPageFragment.SearchPath.Default }?.let {
            appInfoContainer = view.findViewById<ViewGroup?>(R.id.bottom_container)?.apply {
                findViewById<InfoPageBottomView>(R.id.info_page_container).apply {
                    setRouter(it.getRouter(), it.category())
                    findViewById<View?>(R.id.settings_view)?.show(false)
                    findViewById<View?>(R.id.share_view)?.show(false)
                }

                val router = it.getRouter()
                findViewById<View>(R.id.trending_cta).setOnClickListener {
                    GaEvents.TAP_TRENDING_PAGE.track()
                    router?.let {
                        dispatch?.invoke(Navigation(it, TrendingPath()))
                    }
                }
            }
        }

        val adapterConfig = SearchAdapterConfig(GlideLoader(Glide.with(this)),
                {
                    GaEvents.LIKE_MOVIE.withCategory(path?.category()).track()
                    dispatch?.invoke(LikeMovie(it, !it.liked))
                },
                {
                    movie, sharedElement ->
                    GaEvents.OPEN_MOVIE.withCategory(path?.category()).track()
                    path?.getRouter()?.let {
                        dispatch?.invoke(Navigation(it, MoviePath(movie, sharedElement)))
                    }
                },
                {dispatch?.invoke(SearchAction.LoadMore(path is SearchPath.AddToCollection))},
                createExtraLayoutHelper())

        val adapter = BaseMovieAdapter(requireContext(), adapterConfig)
        adapterConfig.adapter = adapter

        adapter.setHasStableIds(true)

        recyclerView?.let {
            it.adapter = adapter
            it.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            it.visibility = View.GONE
        }

        this.adapter = adapter
        this.adapterConfig = adapterConfig

        clearButton?.setOnClickListener {
            clearQuery()
            searchView?.setText("")
            GaEvents.CLEAR_SEARCH.withCategory(path?.category()).track()
        }

        watcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable) {
                querySubject?.onNext(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        }

        val subject: PublishSubject<String> = PublishSubject.create()
        val d = subject
                .doOnNext {
                    if (it.isEmpty()) {
                        clearQuery()
                    } else {
                        clearButton?.visibility = View.VISIBLE
                    }
                }
                .debounce(800, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter { it.length > 2 }
                .subscribeBy(
                        onNext = {
                            dispatch?.invoke(SearchAction.Search(it, path is SearchPath.AddToCollection))
                        }
                )

        subscribe(d)
        searchView?.addTextChangedListener(watcher)
        this.querySubject = subject

        render({
            state, dispatch ->
            if (path is SearchPath.AddToCollection) {
                if (!state.collectionSearchPages.isEmpty()) {
                    render(state.collectionSearchPages.last(), dispatch)
                }
            } else {
                render(state.searchPage, dispatch)
                if (state.searchPage.progress == Progress.Default) {
                    render(state.updatesBanners, dispatch)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        dispatch?.invoke(Load(BuildConfig.VERSION_CODE))
    }

    private fun render(state: SearchPageState, dispatch: Dispatch) {
        this.dispatch = dispatch
        if (state.query != searchView?.text?.toString()) {
            searchView?.setText(state.query)
        }

        bannerContainer?.show(false)

        when(state.progress) {
            is Progress.Default -> {
                clearData()
            }

            is Progress.Loading -> {
                showLoading(true)
                appInfoContainer?.bottomSlide(500)
            }

            is Progress.Error, is Progress.PaginationError -> showApiError()

            is Progress.Success -> {
                setData(state.movies)
                appInfoContainer?.bottomSlide(500)
                if (state.progress is Progress.Success.Pagination) {
                    adapterConfig?.showLoadingMore(false)
                }
            }

            is Progress.Paginating -> adapterConfig?.showLoadingMore(true)
        }

        if (state.fixScroll) {
            fixScroll()
            dispatch(SearchAction.ScrollFixed(path is SearchPath.AddToCollection))
        }
    }

    private fun render(state: CollectionSearchPageState, dispatch: Dispatch) {
        render(state.searchPageState, dispatch)
        state.collectionOp?.let {
            val resId = when(it) {
                is MovieCollectionOp.Exists -> R.string.movie_collection_movie_exists
                is MovieCollectionOp.AddError -> R.string.movie_collection_movie_error
                is MovieCollectionOp.Added -> R.string.movie_collection_movie_added
                else -> 0
            }

            if (resId != 0) {
                showSnackbar(requireContext().getString(resId, state.collectionOp.collection.name))
                val _path = path
                if (_path is SearchPageFragment.SearchPath.AddToCollection) {
                    dispatch.invoke(ClearCollectionOp)
                }
            }
        }
    }

    private fun render(state: UpdateBannersState, dispatch: Dispatch) {
        state.banners.firstOrNull()?.let { item ->
            path?.getRouter()?.let { router ->
                Banner.Builder(requireContext(), bannerContainer!!)
                    .withUpdateBanner(item, router, dispatch)
                    .build()
                    .show()
                GaEvents.UPDATE_BANNER_SHOWN.withLabelArg(item.id).track()
            }

        }
    }

    private fun clearData() {
        showLoading(false)
        appInfoContainer?.slideUp(500)
        clearButton?.visibility = View.GONE
        adapter?.data?.clear()
        adapter?.notifyDataSetChanged()
        recyclerView?.post {
            recyclerView?.visibility = View.GONE
        }
    }

    private fun fixScroll() {
        recyclerView?.post {
            recyclerView?.scrollToPosition(0)
        }
    }

    private fun setData(movies: List<Movie>) {
        showLoading(false)
        adapter?.let {
            it.data.clear()
            it.data.addAll(movies)
            it.notifyDataSetChanged()
            /*if (it.data != movies) {
                if (it.data.size == movies.size) {
                    // maybe some movies updated.
                    val updated = movies.mapIndexed {
                        index, movie -> if (it.data[index] != movie) index else -1
                    }.filter { it >= 0 }

                    updated.forEach {
                        adapter?.data?.swapIfUpdated(movies[it], it)
                        adapter?.notifyItemChanged(it)
                    }

                } else {
                    it.data.clear()
                    it.data.addAll(movies)
                    it.notifyDataSetChanged()
                }
            }*/
        }
        recyclerView?.show(true)

    }

    private fun clearQuery() {
        dispatch?.invoke(SearchAction.ClearSearch(path is SearchPath.AddToCollection))
    }

    private fun showLoading(status: Boolean) {
        if (status) {
            progressbar?.visibility = View.VISIBLE
            attributeView?.visibility = View.GONE
            recyclerView?.visibility = View.GONE
        } else {
            attributeView?.visibility = View.VISIBLE
            progressbar?.visibility = View.GONE
        }
    }

    private fun showApiError() {
        showLoading(false)
        adapterConfig?.showLoadingMore(false)
        showSnackbarWithAction(requireContext().getString(R.string.search_page_api_error_content),
                R.string.search_page_try_again_cta,
                View.OnClickListener {
                    dispatch?.invoke(SearchAction.Reload(searchView?.text?.toString()?: "", path is SearchPath.AddToCollection))
                })
    }

    private fun createExtraLayoutHelper(): (() -> SearchItemViewHolder.ExtraLayoutHelper)? {
        return when(path) {
            is SearchPath.AddToCollection -> {
                return ::createAddToCollectionExtraLayout
            }

            else -> null
        }
    }

    private fun createAddToCollectionExtraLayout(): SearchItemViewHolder.ExtraLayoutHelper {
        return AddToCollectionMovieLayoutHelper(object : AddToCollectionMovieLayoutHelper.Callback {
            override fun onAddRequested(movie: Movie) {
                GaEvents.ADD_TO_COLLECTION.withCategory(path?.category()).track()
                path?.takeIf { it is SearchPath.AddToCollection }?.let {
                    dispatch?.invoke(AddToCollection((it as SearchPath.AddToCollection).collection, movie))
                }
            }
        })
    }

    override fun canGoBack(): Boolean {
        return true
    }

    override fun getScreenTitle(): Int {
        return when(path) {
            is SearchPageFragment.SearchPath.AddToCollection -> R.string.search_page_add_to_collection_title
            else -> R.string.search_page_title
        }
    }

    override fun screenName(): String {
        path?.takeIf { it is SearchPath }?.let {
            return when(it as SearchPath) {
                is SearchPath.Default -> GaScreens.SEARCH
                is SearchPath.AddToCollection -> GaScreens.COLLECTION_SEARCH
            }
        }

        return GaScreens.SEARCH
    }

    sealed class SearchPath: RouterPath<SearchPageFragment>() {

        class Default: SearchPageFragment.SearchPath() {
            override fun createFragmentInstance(): SearchPageFragment {
                return SearchPageFragment()
            }

            override fun showMenuIcons() = intArrayOf()
            override fun showBackButton() = false
            override fun category() = GaCategory.SEARCH
        }

        class AddToCollection(val collection: MovieCollection): SearchPageFragment.SearchPath() {
            override fun createFragmentInstance(): SearchPageFragment {
                return SearchPageFragment()
            }

            override fun showBackButton() = true
            override fun category() = GaCategory.COLLECTION_SEARCH
            override fun initAction() = InitCollectionSearchPage(collection)
            override fun clearAction() = ClearCollectionSearchPage
        }
    }

}