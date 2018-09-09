package com.fenchtose.movieratings.features.moviepage

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.fenchtose.movieratings.R
import com.fenchtose.movieratings.model.entity.MovieCollection
import com.fenchtose.movieratings.widgets.FlexView

class MoviePageFlexView(context: Context, private val flexView: FlexView) {

    private val inflater = LayoutInflater.from(context)

    fun render(collections: List<MovieCollection>, onClick: (MovieCollection) -> Unit,
                       onAddToCollection: () -> Unit) {

        flexView.clearAll()

        collections.forEach {
            val view = inflater.inflate(R.layout.movie_page_collection_item_layout, flexView, false) as TextView
            view.text = it.name
            flexView.addElement(view)
            view.setOnClickListener { _ -> onClick(it) }
        }

        val cta = inflater.inflate(R.layout.movie_page_add_to_collection_layout, flexView, false)
        flexView.addElement(cta)
        cta.setOnClickListener { _ -> onAddToCollection() }

        flexView.requestLayout()
    }

}