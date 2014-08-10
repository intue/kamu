/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.intue.kamu;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.view.ViewCompat;
import android.text.Spannable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.ListPreloader;
import io.intue.kamu.model.TagMetadata;
import io.intue.kamu.provider.ScheduleContract;
import io.intue.kamu.widget.CollectionView;
import io.intue.kamu.widget.CollectionViewCallbacks;
import io.intue.kamu.widget.MessageCardView;
import io.intue.kamu.util.*;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import static io.intue.kamu.util.LogUtils.LOGD;
import static io.intue.kamu.util.LogUtils.LOGE;
import static io.intue.kamu.util.LogUtils.LOGV;
import static io.intue.kamu.util.LogUtils.LOGW;
import static io.intue.kamu.util.LogUtils.makeLogTag;
import static io.intue.kamu.util.UIUtils.buildStyledSnippet;

/**
 * A {@link android.app.ListFragment} showing a list of sessions. The fragment arguments
 * indicate what is the list of sessions to show. It may be a set of tag
 * filters or a search query.
 */
public class BestNearbyFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>, CollectionViewCallbacks {

    private static final String TAG = makeLogTag(BestNearbyFragment.class);

    /** The number of rows ahead to preload images for */
    private static final int ROWS_TO_PRELOAD = 2;

    private Bundle mArguments;
    private CollectionView mCollectionView;
    private Preloader mPreloader;
    private ImageLoader mImageLoader;

    private TextView mEmptyView;
    private View mLoadingView;

    private Uri mCurrentUri = ScheduleContract.Sessions.CONTENT_URI;

    // the cursor whose data we are currently displaying
    private int mSessionQueryToken;

    // this variable is relevant when we start the sessions loader, and indicates the desired
    // behavior when load finishes: if true, this is a full reload (for example, because filters
    // have been changed); if not, it's just a refresh because data has changed.
    private boolean mSessionDataIsFullReload = false;

    private Cursor mCursor;
    private boolean mIsSearchCursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mImageLoader == null) {
            mImageLoader = new ImageLoader(this.getActivity());
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.fragment_sessions, container, false);
        mCollectionView = (CollectionView) root.findViewById(R.id.sessions_collection_view);
        mPreloader = new Preloader(ROWS_TO_PRELOAD);
        mCollectionView.setOnScrollListener(mPreloader);
        mEmptyView = (TextView) root.findViewById(R.id.empty_text);
        mLoadingView = root.findViewById(R.id.loading);
        return root;
    }

    @Override
    public View newCollectionHeaderView(Context context, ViewGroup parent) {
        return null;
    }

    @Override
    public void bindCollectionHeaderView(Context context, View view, int groupId, String headerLabel) {

    }

    @Override
    public View newCollectionItemView(Context context, int groupId, ViewGroup parent) {
        return null;
    }

    @Override
    public void bindCollectionItemView(Context context, View view, int groupId, int indexInGroup, int dataIndex, Object tag) {

    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        LOGD(TAG, "onCreateLoader, id=" + id + ", data=" + bundle);
        final Intent intent = BaseActivity.fragmentArgumentsToIntent(bundle);
        Uri sessionsUri = intent.getData();

        Loader<Cursor> cursorLoader = null;

        cursorLoader = new CursorLoader(getActivity(), sessionsUri, SessionsQuery.SEARCH_PROJECTION,
                null, null, ScheduleContract.Sessions.SORT_BY_TYPE_THEN_TIME);

        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (getActivity() == null) {
            return;
        }

        int token = cursorLoader.getId();

        mCursor = cursor;
        mIsSearchCursor = token == SessionsQuery.SEARCH_TOKEN;
        LOGD(TAG, "Cursor has " + mCursor.getCount() + " items. Will now update collection view.");
        updateCollectionView();

    }

    private void updateCollectionView() {
        if (mCursor == null) {
            LOGD(TAG, "updateCollectionView: not ready yet no cursor.");
            // not ready!
            return;
        }
        LOGD(TAG, "SessionsFragment updating CollectionView... " + (mSessionDataIsFullReload ?
                "(FULL RELOAD)" : "(light refresh)"));
        mCursor.moveToPosition(-1);
        int itemCount = mCursor.getCount();

        //mMaxDataIndexAnimated = 0;

        CollectionView.Inventory inv = null;
        if (itemCount == 0) {
            showEmptyView();
            inv = new CollectionView.Inventory();
        } else {
            //hideEmptyView();
            //inv = prepareInventory();
        }

        Parcelable state = null;
        if (!mSessionDataIsFullReload) {
            // it's not a full reload, so we want to keep scroll position, etc
            state = mCollectionView.onSaveInstanceState();
        }
        LOGD(TAG, "Updating CollectionView with inventory, # groups = " + inv.getGroupCount()
                + " total items = " + inv.getTotalItemCount());
        mCollectionView.setCollectionAdapter(this);
        mCollectionView.updateInventory(inv, mSessionDataIsFullReload);
        if (state != null) {
            mCollectionView.onRestoreInstanceState(state);
        }
        mSessionDataIsFullReload = false;
    }

    private void showEmptyView() {
        final String searchQuery = ScheduleContract.Sessions.isSearchUri(mCurrentUri) ?
                ScheduleContract.Sessions.getSearchQuery(mCurrentUri) : null;

        if (mCurrentUri.equals(ScheduleContract.Sessions.CONTENT_URI)) {
            // if showing all sessions, the empty view should say "loading..." because
            // the only reason we would have no sessions at all is if we are currently
            // preparing the database from the bootstrap data, which should only take a few
            // seconds.
            mEmptyView.setVisibility(View.GONE);
            mLoadingView.setVisibility(View.VISIBLE);
        } else if (ScheduleContract.Sessions.isUnscheduledSessionsInInterval(mCurrentUri)) {
            // Showing sessions in a given interval, so say "No sessions in this time slot."
            mEmptyView.setText(R.string.no_matching_sessions_in_interval);
            mEmptyView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.GONE);
        } else if (ScheduleContract.Sessions.isSearchUri(mCurrentUri)
                && (TextUtils.isEmpty(searchQuery) || "*".equals(searchQuery))) {
            // Empty search query (for example, user hasn't started to type the query yet),
            // so don't show an empty view.
            mEmptyView.setText("");
            mEmptyView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.GONE);
        } else {
            // Showing sessions as a result of search or filter, so say "No matching sessions."
            mEmptyView.setText(R.string.no_matching_sessions);
            mEmptyView.setVisibility(View.VISIBLE);
            mLoadingView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

    public void reloadFromArguments(Bundle arguments) {
        if (arguments == null) {
            arguments = new Bundle();
        } else {
            // since we might make changes, don't meddle with caller's copy
            arguments = (Bundle) arguments.clone();
        }

        // save arguments so we can reuse it when reloading from content observer events
        mArguments = arguments;

        LOGD(TAG, "SessionsFragment reloading from arguments: " + arguments);
        mCurrentUri = arguments.getParcelable("_uri");

        if (mCurrentUri == null) {
            // if no URI, default to all sessions URI
            LOGD(TAG, "SessionsFragment did not get a URL, defaulting to all sessions.");
            arguments.putParcelable("_uri", ScheduleContract.Sessions.CONTENT_URI);
            mCurrentUri = ScheduleContract.Sessions.CONTENT_URI;
        }

        mSessionQueryToken = SessionsQuery.SEARCH_TOKEN;

        reloadSessionData(true); // full reload

    }

    private void reloadSessionData(boolean fullReload) {
        LOGD(TAG, "Reloading session data: " + (fullReload ? "FULL RELOAD" : "light refresh"));
        mSessionDataIsFullReload = fullReload;
        getLoaderManager().restartLoader(mSessionQueryToken, mArguments, BestNearbyFragment.this);
    }

    public boolean canCollectionViewScrollUp() {
        return ViewCompat.canScrollVertically(mCollectionView, -1);
    }

    public void setContentTopClearance(int topClearance) {
        mCollectionView.setContentTopClearance(topClearance);
    }

    public interface Callbacks {
        public void onSessionSelected(String sessionId, View clickedView);
        public void onTagMetadataLoaded(TagMetadata metadata);
    }

    private class Preloader extends ListPreloader<String> {

        private int[] photoDimens;
        private int displayCols;

        public Preloader(int maxPreload) {
            super(maxPreload);
        }

        public void setDisplayCols(int displayCols) {
            this.displayCols = displayCols;
        }

        public boolean isDimensSet() {
            return photoDimens != null;
        }

        public void setDimens(int width, int height) {
            if (photoDimens == null) {
                photoDimens = new int[] { width, height };
            }
        }

        @Override
        protected int[] getDimensions(String s) {
            return photoDimens;
        }

        @Override
        protected List<String> getItems(int start, int end) {
            // Our start and end are rows, we need to adjust them into data columns
            // The keynote is 1 row with 1 data item, so we need to adjust.
            int keynoteDataOffset = (displayCols - 1);
            int dataStart = start * displayCols - keynoteDataOffset;
            int dataEnd = end * displayCols - keynoteDataOffset;
            List<String> urls = new ArrayList<String>();
//            if (mCursor != null) {
//                for (int i = dataStart; i < dataEnd; i++) {
//                    if (mCursor.moveToPosition(i)) {
//                        urls.add(mCursor.getString(SessionsQuery.PHOTO_URL));
//                    }
//                }
//            }
            return urls;
        }

        @Override
        protected GenericRequestBuilder getRequestBuilder(String url) {
            return mImageLoader.beginImageLoad(url, null, true /*crop*/);
        }
    }

    /**
     * {link com.google.samples.apps.iosched.provider.ScheduleContract.Sessions}
     * query parameters.
     */
    private interface SessionsQuery {

        int SEARCH_TOKEN = 0x3;


        String[] SEARCH_PROJECTION = {
                BaseColumns._ID,
                ScheduleContract.Sessions.SESSION_ID
        };
    }
}
