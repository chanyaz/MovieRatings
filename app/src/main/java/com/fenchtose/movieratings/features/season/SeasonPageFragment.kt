package com.fenchtose.movieratings.features.season

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.fenchtose.movieratings.R
import com.fenchtose.movieratings.analytics.ga.GaCategory
import com.fenchtose.movieratings.analytics.ga.GaEvents
import com.fenchtose.movieratings.analytics.ga.GaScreens
import com.fenchtose.movieratings.base.BaseFragment
import com.fenchtose.movieratings.base.RouterPath
import com.fenchtose.movieratings.model.entity.Movie
import com.fenchtose.movieratings.model.entity.Season
import com.fenchtose.movieratings.model.image.GlideLoader
import com.fenchtose.movieratings.model.image.ImageLoader
import com.fenchtose.movieratings.util.IntentUtils

class SeasonPageFragment: BaseFragment() {

    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager? = null
    private var adapter: EpisodePagerAdapter? = null
    private var poster: ImageView? = null
    private var imageLoader: ImageLoader? = null
    private var currentEpisodeId: String? = null
    private var currentPoster: String? = null

    override fun canGoBack() = true
    override fun getScreenTitle() = R.string.season_page_title
    override fun screenName() = GaScreens.SEASON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.season_page_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideLoader(Glide.with(this))

        tabLayout = view.findViewById(R.id.tabs)
        viewPager = view.findViewById(R.id.viewpager)
        poster = view.findViewById(R.id.poster_view)

        path?.takeIf { it is SeasonPath }?.let {
            it as SeasonPath
        }?.let {
            adapter = EpisodePagerAdapter(requireContext(), it.series, it.episodes, {
                if (it.imdbId != currentEpisodeId && it.poster != currentPoster) {
                    currentPoster = it.poster
                    loadImage(it.poster)
                }
            })
            tabLayout?.setupWithViewPager(viewPager)
            viewPager?.adapter = adapter

            viewPager?.currentItem = it.selectedEpisode - 1
            loadImage(it.series.poster)
            path?.getRouter()?.updateTitle(it.series.title)

            tabLayout?.addOnTabSelectedListener(object: TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    GaEvents.SELECT_EPISODE.track()
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {}
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
            })

            viewPager?.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {

                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                }

                override fun onPageSelected(position: Int) {
                    path?.let{ it as SeasonPath }?.let {
                        val episode = it.episodes.episodes[position]
                        this@SeasonPageFragment.currentEpisodeId = episode.imdbId
                    }
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        var consumed = true
        when(item?.itemId) {
            R.id.action_open_imdb -> {
                GaEvents.OPEN_IMDB.withCategory(GaCategory.EPISODE).track()
                IntentUtils.openImdb(requireContext(), currentEpisodeId)
            }
            else -> consumed = false
        }

        return if (consumed) true else super.onOptionsItemSelected(item)
    }

    private fun loadImage(url: String) {
        poster?.let {
            imageView -> run {
                imageLoader?.loadImage(url, imageView)
            }
        }
    }

    class SeasonPath(val series: Movie, val episodes: Season, val selectedEpisode: Int): RouterPath<SeasonPageFragment>() {
        override fun createFragmentInstance() = SeasonPageFragment()
        override fun showMenuIcons() = intArrayOf(R.id.action_open_imdb)
        override fun category() = GaCategory.SEASON
        override fun toolbarElevation() = R.dimen.toolbar_no_elevation
    }

}