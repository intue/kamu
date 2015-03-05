package io.intue.kamu;


import android.app.ActionBar;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import io.intue.kamu.model.TagMetadata;
import io.intue.kamu.util.AnalyticsManager;
import io.intue.kamu.util.PrefUtils;
import io.intue.kamu.util.UIUtils;
import io.intue.kamu.widget.CollectionView;
import io.intue.kamu.widget.DrawShadowFrameLayout;

public class BestNearbyActivity extends BaseActivity implements
        BestNearbyFragment.Callbacks,
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener{

    // How is this Activity being used?
    private static final int MODE_BEST_NEARBY = 0; // as top-level "Explore" screen

    private int mMode = MODE_BEST_NEARBY;

    private final static String SCREEN_LABEL = "Best Nearby";

    private BestNearbyFragment mBestNearbyFra = null;

    private View mButterBar;
    private DrawShadowFrameLayout mDrawShadowFrameLayout;

    // time when the user last clicked "refresh" from the stale data butter bar
    private long mLastDataStaleUserActionTime = 0L;

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_best_nearby);

        getLPreviewUtils().trySetActionBar();
        ActionBar ab = getActionBar();

        overridePendingTransition(0, 0);

        AnalyticsManager.sendScreenView(SCREEN_LABEL);

        if (mMode == MODE_BEST_NEARBY) {
            // no title (to make more room for navigation and actions)
            // unless Nav Drawer opens
            ab.setTitle(getString(R.string.app_name));
            ab.setDisplayShowTitleEnabled(false);
        }

        mButterBar = findViewById(R.id.butter_bar);
        mDrawShadowFrameLayout = (DrawShadowFrameLayout) findViewById(R.id.main_content);
        registerHideableHeaderView(mButterBar);

        mLocationClient = new LocationClient(this, this, this);
    }

    /*
     * Called when the Activity is no longer visible at all.
     * Stop updates and disconnect.
     */
    @Override
    public void onStop() {

        // After disconnect() is called, the client is considered "dead".
        mLocationClient.disconnect();

        super.onStop();
    }

    /*
     * Called when the Activity is restarted, even before it becomes visible.
     */
    @Override
    public void onStart() {

        super.onStart();

        /*
         * Connect the client. Don't re-start any requests here;
         * instead, wait for onResume()
         */
        mLocationClient.connect();

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        CollectionView collectionView = (CollectionView) findViewById(R.id.sessions_collection_view);
        if (collectionView != null) {
            enableActionBarAutoHide(collectionView);
        }

        mBestNearbyFra = (BestNearbyFragment) getFragmentManager().findFragmentById(
                R.id.sessions_fragment);
        if (mBestNearbyFra != null && savedInstanceState == null) {
//            Bundle args = intentToFragmentArguments(getIntent());
//            mBestNearbyFra.reloadFromArguments(args);
        }

        registerHideableHeaderView(findViewById(R.id.headerbar));
    }

    @Override
    public void onSessionSelected(String sessionId, View clickedView) {

    }

    @Override
    public void onTagMetadataLoaded(TagMetadata metadata) {

    }

    @Override
    protected int getSelfNavDrawerItem() {
        // we only have a nav drawer if we are in top-level Explore mode.
        return mMode == MODE_BEST_NEARBY ? NAVDRAWER_ITEM_BEST_NEARBY : NAVDRAWER_ITEM_INVALID;
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (mBestNearbyFra != null) {
            return mBestNearbyFra.canCollectionViewScrollUp();
        }
        return super.canSwipeRefreshChildScrollUp();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkShowStaleDataButterBar();
    }

    private void checkShowStaleDataButterBar() {
        final boolean showingFilters = findViewById(R.id.filters_box) != null
                && findViewById(R.id.filters_box).getVisibility() == View.VISIBLE;
        final long now = UIUtils.getCurrentTime(this);
        final boolean inSnooze = (now - mLastDataStaleUserActionTime < Config.STALE_DATA_WARNING_SNOOZE);
        final long staleTime = now - PrefUtils.getLastSyncSucceededTime(this);
        final long staleThreshold = (now >= Config.CONFERENCE_START_MILLIS && now
                <= Config.CONFERENCE_END_MILLIS) ? Config.STALE_DATA_THRESHOLD_DURING_CONFERENCE :
                Config.STALE_DATA_THRESHOLD_NOT_DURING_CONFERENCE;
        final boolean isStale = (staleTime >= staleThreshold);
        final boolean bootstrapDone = PrefUtils.isDataBootstrapDone(this);
        final boolean mustShowBar = bootstrapDone && isStale && !inSnooze && !showingFilters;

        if (!mustShowBar) {
            mButterBar.setVisibility(View.GONE);
        } else {
            UIUtils.setUpButterBar(mButterBar, getString(R.string.data_stale_warning),
                    getString(R.string.description_refresh), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mButterBar.setVisibility(View.GONE);
                            updateFragContentTopClearance();
//                            mLastDataStaleUserActionTime = UIUtils.getCurrentTime(
//                                    BrowseSessionsActivity.this);
                            requestDataRefresh();
                        }
                    }
            );
        }
        updateFragContentTopClearance();
    }

    // Updates the Best Nearby fragment content top clearance to take our chrome into account
    private void updateFragContentTopClearance() {
        BestNearbyFragment frag = (BestNearbyFragment) getFragmentManager().findFragmentById(
                R.id.sessions_fragment);
        if (frag == null) {
            return;
        }

        View filtersBox = findViewById(R.id.filters_box);

        final boolean filterBoxVisible = filtersBox != null
                && filtersBox.getVisibility() == View.VISIBLE;
        final boolean butterBarVisible = mButterBar != null
                && mButterBar.getVisibility() == View.VISIBLE;

        int actionBarClearance = UIUtils.calculateActionBarSize(this);
        int butterBarClearance = butterBarVisible
                ? getResources().getDimensionPixelSize(R.dimen.butter_bar_height) : 0;
        int filterBoxClearance = filterBoxVisible
                ? getResources().getDimensionPixelSize(R.dimen.filterbar_height) : 0;
        int secondaryClearance = butterBarClearance > filterBoxClearance ? butterBarClearance :
                filterBoxClearance;
        int gridPadding = getResources().getDimensionPixelSize(R.dimen.explore_grid_padding);

        setProgressBarTopWhenActionBarShown(actionBarClearance + secondaryClearance);
        mDrawShadowFrameLayout.setShadowTopOffset(actionBarClearance + secondaryClearance);
        frag.setContentTopClearance(actionBarClearance + secondaryClearance + gridPadding);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Location location = mLocationClient.getLastLocation();
        mBestNearbyFra.reloadFromArguments(location);

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
