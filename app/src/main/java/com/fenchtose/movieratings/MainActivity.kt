package com.fenchtose.movieratings

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v4.os.ConfigurationCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.FrameLayout
import com.fenchtose.movieratings.analytics.events.toGaEvent
import com.fenchtose.movieratings.base.RouterBaseActivity
import com.fenchtose.movieratings.base.router.Router
import com.fenchtose.movieratings.features.info.AppInfoFragment
import com.fenchtose.movieratings.model.db.displayedRatings.DbDisplayedRatingsStore
import com.fenchtose.movieratings.model.inAppAnalytics.DbHistoryKeeper
import com.fenchtose.movieratings.model.inAppAnalytics.HistoryKeeper
import com.fenchtose.movieratings.model.inAppAnalytics.PreferenceUserHistory
import com.fenchtose.movieratings.model.preferences.SettingsPreferences
import com.fenchtose.movieratings.model.preferences.UserPreferences
import com.fenchtose.movieratings.util.AccessibilityUtils
import com.fenchtose.movieratings.util.IntentUtils
import com.fenchtose.movieratings.util.PackageUtils
import com.fenchtose.movieratings.widgets.bottomnavigation.BottomNavigationBar
import com.fenchtose.movieratings.widgets.bottomnavigation.MenuItem


class MainActivity : RouterBaseActivity() {

    private var container: FrameLayout? = null

    private var preferences: UserPreferences? = null
    private var historyKeeper: HistoryKeeper? = null

    private val TTS_REG_CHECK = 11

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.fragment_container)

        preferences = SettingsPreferences(this)

        val bottomNavigationBar = findViewById<BottomNavigationBar>(R.id.bottom_navigation_bar)
        bottomNavigationBar.update(listOf(
                MenuItem(1, R.drawable.ic_search_accent_24dp, Router.ROOT_SEARCH),
                MenuItem(2, R.drawable.ic_person_onyx_accent_24dp, Router.ROOT_PERSONAL),
                MenuItem(3, R.drawable.ic_collections_accent_24dp, Router.ROOT_COLLECTIONS),
                MenuItem(4, R.drawable.ic_info_outline_accent_24dp, Router.ROOT_INFO)
        ), 0)

        bottomNavigationBar.addListener { position, item, reselected ->
            // TODO GA
            Log.d("Bottom nav", "item selected: $position - $item")
            getRouter()?.switchRoot(item.root, reselected)
        }

        historyKeeper = DbHistoryKeeper(
                PreferenceUserHistory(this),
                DbDisplayedRatingsStore.getInstance(MovieRatingsApplication.database.displayedRatingsDao()),
                preferences!!)

        historyKeeper?.appOpened()

        checkForTTS()
        checkForLocale()

        initializeRouter(
                findViewById(R.id.toolbar),
                {},
                {},
                ::buildPathAndStart
        )
    }


    /*private fun onAccessibilityActivated() {
        getRouter()?.onBackRequested()
        // Show a dialog?
        val builder = AlertDialog.Builder(this)
                .setTitle(R.string.accessibility_enabled_dialog_title)
                .setMessage(R.string.accessibility_enabled_dialog_content)
                .setNeutralButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }

        if (PackageUtils.hasInstalled(this, PackageUtils.NETFLIX)) {
            builder.setPositiveButton(R.string.accessibility_enabled_open_netflix) { dialog, _ ->
                dialog.dismiss()
                IntentUtils.launch3rdParty(this, PackageUtils.NETFLIX)
            }
        }
    }*/

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            TTS_REG_CHECK -> {
                preferences?.setEnabled(UserPreferences.TTS_AVAILABLE, resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
            }
        }
    }

    private fun checkForTTS() {
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        if (PackageUtils.isIntentCallabale(this, intent)) {
            startActivityForResult(intent, TTS_REG_CHECK)
        } else {
            preferences?.setEnabled(UserPreferences.TTS_AVAILABLE, false)
            Log.e("Flutter", "TTS is not supported")
        }
    }

    private fun checkForLocale() {
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        if (locales.size() > 0) {
            if (!locales[0].toLanguageTag().startsWith("en") && preferences?.isSettingEnabled(UserPreferences.LOCALE_INFO_SHOWN) == false) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.locale_info_dialog_title)
                        .setMessage(R.string.locale_info_dialog_content)
                        .setPositiveButton(android.R.string.ok) {
                            dialog, _ ->
                                preferences?.setEnabled(UserPreferences.LOCALE_INFO_SHOWN, true)
                                dialog.dismiss()
                        }
                        .setCancelable(false)
                        .show()
            }
        }
    }

    private fun buildPathAndStart(router: Router) {
        val intent = intent
        var history: Router.History = Router.History()
        var root: String

        if (intent != null) {
            val historyBundle = intent.getBundleExtra(Router.HISTORY)
            if (historyBundle != null) {
                history = Router.History(historyBundle)
            }

            if (intent.hasExtra("ga_event")) {
                intent.getBundleExtra("ga_event").toGaEvent()?.track()
            }

            // TODO fix root based deeplinking
            root = Router.ROOT_SEARCH
        }

        if (!history.isEmpty()) {
            val grouped = history.history.groupBy { router.canHandleKey(it.first) }
            grouped[true]?.map {
                router.buildRoute(it.second)
            }

            val redirects = grouped[false]
            redirects?.let {
                if (it.isNotEmpty()) {
                    // Take the first key at the moment. We don't have the logic right now
                    when(it[0].first) {
                        "RateApp" -> {
                            historyKeeper?.ratedAppOnPlaystore()
                            IntentUtils.openPlaystore(this)
                            supportFinishAfterTransition()
                            return
                        }
                    }
                }
            }
            root = Router.ROOT_SEARCH
        } else {
            root = if (preferences?.isSettingEnabled(UserPreferences.ONBOARDING_SHOWN) == false && !AccessibilityUtils.hasAllPermissions(this)) {
                router.buildRoute(Router.ROOT_INFO, AppInfoFragment.AppInfoPath(), true)
                preferences?.setEnabled(UserPreferences.ONBOARDING_SHOWN, true)
                Router.ROOT_INFO
            } else {
                Router.ROOT_SEARCH
            }
        }

        router.start(root)
    }

}
