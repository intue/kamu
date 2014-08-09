package io.intue.kamu;


import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

import io.intue.kamu.model.TagMetadata;

public class BestNearbyActivity extends BaseActivity implements BestNearbyFragment.Callbacks {

    // How is this Activity being used?
    private static final int MODE_BEST_NEARBY = 0; // as top-level "Explore" screen

    private int mMode = MODE_BEST_NEARBY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_best_nearby);
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
}
