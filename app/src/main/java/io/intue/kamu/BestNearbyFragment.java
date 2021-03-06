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
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.intue.kamu.model.TagMetadata;
import io.intue.kamu.model.Venue;
import io.intue.kamu.provider.ScheduleContract;
import io.intue.kamu.widget.CollectionView;
import io.intue.kamu.widget.CollectionViewCallbacks;
import io.intue.kamu.widget.MessageCardView;
import io.intue.kamu.util.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
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
        CollectionViewCallbacks {

    private static final String TAG = makeLogTag(BestNearbyFragment.class);

    /**
     * The number of rows ahead to preload images for
     */
    private static final int ROWS_TO_PRELOAD = 2;

    private Bundle mArguments;
    private CollectionView mCollectionView;
    private Preloader mPreloader;
    private ImageLoader mImageLoader;

    private TextView mEmptyView;
    private View mLoadingView;

    private Uri mCurrentUri = ScheduleContract.Sessions.CONTENT_URI;

    private static final int HERO_GROUP_ID = 123;

    // the cursor whose data we are currently displaying
    private int mSessionQueryToken;

    // this variable is relevant when we start the sessions loader, and indicates the desired
    // behavior when load finishes: if true, this is a full reload (for example, because filters
    // have been changed); if not, it's just a refresh because data has changed.
    private boolean mSessionDataIsFullReload = false;

    private List<Venue> mresult;


    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onSessionSelected(String sessionId, View clickedView) {}

        @Override
        public void onTagMetadataLoaded(TagMetadata metadata) {}
    };

    private Callbacks mCallbacks = sDummyCallbacks;


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
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        return inflater.inflate(R.layout.list_item_explore_header, parent, false);
    }

    @Override
    public void bindCollectionHeaderView(Context context, View view, int groupId, String groupLabel) {
        TextView tv = (TextView) view.findViewById(android.R.id.text1);
        if (tv != null) {
            tv.setText(groupLabel);
        }
    }

    @Override
    public View newCollectionItemView(Context context, int groupId, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        int layoutId;

        layoutId = (groupId == HERO_GROUP_ID) ? R.layout.list_item_session_hero :
                R.layout.list_item_session_summarized;

        return inflater.inflate(layoutId, parent, false);
    }

    @Override
    public void bindCollectionItemView(Context context, View view, int groupId, int indexInGroup, int dataIndex, Object tag) {
//        if (mCursor == null || !mCursor.moveToPosition(dataIndex)) {
//            LOGW(TAG, "Can't bind collection view item, dataIndex=" + dataIndex +
//                    (mCursor == null ? ": cursor is null" : ": bad data index."));
//            return;
//        }

        if(mresult == null){
            return;
        }
        Venue result = mresult.get(dataIndex);

        final String sessionId = result.getId();
        if (sessionId == null) {
            return;
        }

        // first, read session info from cursor and put it in convenience variables
        final String sessionTitle = result.getName();
        //final String speakerNames = "SessionsQuery.SPEAKER_NAMES";
        //final String sessionAbstract = "SessionsQuery.ABSTRACT";
        //final long sessionStart = 44454544;
        //final long sessionEnd = 334343433;
        //final String roomName = "SessionsQuery.ROOM_NAME";
        int sessionColor = 0;
        sessionColor = sessionColor == 0 ? getResources().getColor(R.color.transparent)
                : sessionColor;
        //final String snippet = "SessionsQuery.SNIPPET";
        final Spannable styledSnippet =  null;
        final boolean starred = false;
        //final String[] tags = "A,B,C".split(",");

        // now let's compute a few pieces of information from the data, which we will use
        // later to decide what to render where
        final boolean hasLivestream = false;
        final long now = UIUtils.getCurrentTime(context);
        final boolean happeningNow = false;

        // text that says "LIVE" if session is live, or empty if session is not live
        //final String liveNowText =  "";

        // get reference to all the views in the layout we will need
        final TextView titleView = (TextView) view.findViewById(R.id.session_title);
        final TextView subtitleView = (TextView) view.findViewById(R.id.session_subtitle);
        final TextView shortSubtitleView = (TextView) view.findViewById(R.id.session_subtitle_short);
        final TextView snippetView = (TextView) view.findViewById(R.id.session_snippet);
        //final TextView abstractView = (TextView) view.findViewById(R.id.session_abstract);
        //final TextView categoryView = (TextView) view.findViewById(R.id.session_category);
        final View boxView = view.findViewById(R.id.info_box);
        final View sessionTargetView = view.findViewById(R.id.session_target);

        if (sessionColor == 0) {
            // use default
            sessionColor = getResources().getColor(R.color.transparent);
        }
        sessionColor = UIUtils.scaleSessionColorToDefaultBG(sessionColor);

        ImageView photoView = (ImageView) view.findViewById(R.id.session_photo_colored);
        if (photoView != null) {
            if (!mPreloader.isDimensSet()) {
                final ImageView finalPhotoView = photoView;
                photoView.post(new Runnable() {
                    @Override
                    public void run() {
                        mPreloader.setDimens(finalPhotoView.getWidth(), finalPhotoView.getHeight());
                    }
                });
            }
            // colored
            photoView.setColorFilter(UIUtils.setColorAlpha(sessionColor,
                    UIUtils.SESSION_PHOTO_SCRIM_ALPHA));
        } else {
            photoView = (ImageView) view.findViewById(R.id.session_photo);
        }
        ((BaseActivity) getActivity()).getLPreviewUtils().setViewName(photoView,
                "photo_" + sessionId);



        // when we load a photo, it will fade in from transparent so the
        // background of the container must be the session color to avoid a white flash
        ViewParent parent = photoView.getParent();
        if (parent != null && parent instanceof View) {
            ((View) parent).setBackgroundColor(sessionColor);
        } else {
            photoView.setBackgroundColor(sessionColor);
        }

        String photo = result.getPhotoUrl();
        if (!TextUtils.isEmpty(photo)) {
            mImageLoader.loadImage(photo, photoView, true /*crop*/);
        } else {
            // cleaning the (potentially) recycled photoView, in case this session has no photo:
            photoView.setImageDrawable(null);
        }

        // render title
        titleView.setText(sessionTitle == null ? "?" : sessionTitle);

        // render subtitle into either the subtitle view, or the short subtitle view, as available
        if (subtitleView != null) {
            subtitleView.setText(result.getAddress());
        } else if (shortSubtitleView != null) {
            shortSubtitleView.setText(result.getAddress());
        }

        // render category
//        if (categoryView != null) {
//            TagMetadata.Tag groupTag = mTagMetadata.getSessionGroupTag(tags);
//            if (groupTag != null && !Config.Tags.SESSIONS.equals(groupTag.getId())) {
//                categoryView.setText(groupTag.getName());
//                categoryView.setVisibility(View.VISIBLE);
//            } else {
//                categoryView.setVisibility(View.GONE);
//            }
//        }

        // if a snippet view is available, render the session snippet there.
        if (snippetView != null) {
            //if (mIsSearchCursor) {
                // render the search snippet into the snippet view
                snippetView.setText(styledSnippet);
//            } else {
//                // render speaker names and abstracts into the snippet view
//                mBuffer.setLength(0);
//                if (!TextUtils.isEmpty(speakerNames)) {
//                    mBuffer.append(speakerNames).append(". ");
//                }
//                if (!TextUtils.isEmpty(sessionAbstract)) {
//                    mBuffer.append(sessionAbstract);
//                }
//                snippetView.setText(mBuffer.toString());
//            }
        }

//        if (abstractView != null && !mIsSearchCursor) {
//            // render speaker names and abstracts into the abstract view
//            mBuffer.setLength(0);
//            if (!TextUtils.isEmpty(speakerNames)) {
//                mBuffer.append(speakerNames).append("\n\n");
//            }
//            if (!TextUtils.isEmpty(sessionAbstract)) {
//                mBuffer.append(sessionAbstract);
//            }
//            abstractView.setText(mBuffer.toString());
//        }

        // in expanded mode, the box background color follows the session color
        //if (useExpandedMode()) {
            boxView.setBackgroundColor(sessionColor);
        //}

        // show or hide the "in my schedule" indicator
        view.findViewById(R.id.indicator_in_schedule).setVisibility(starred ? View.VISIBLE
                : View.INVISIBLE);

        // if we are in condensed mode and this card is the hero card (big card at the top
        // of the screen), set up the message card if necessary.
        if (groupId == HERO_GROUP_ID) {
            // this is the hero view, so we might want to show a message card
            final boolean cardShown = setupMessageCard(view);

            // if this is the wide hero layout, show or hide the card or the session abstract
            // view, as appropriate (they are mutually exclusive).
            final View cardContainer = view.findViewById(R.id.message_card_container_wide);
            final View abstractContainer = view.findViewById(R.id.session_abstract);
            if (cardContainer != null && abstractContainer != null) {
                cardContainer.setVisibility(cardShown ? View.VISIBLE : View.GONE);
                abstractContainer.setVisibility(cardShown ? View.GONE : View.VISIBLE);
                abstractContainer.setBackgroundColor(sessionColor);
            }
        }

        // if this session is live right now, display the "LIVE NOW" icon on top of it
        View liveNowBadge = view.findViewById(R.id.live_now_badge);
        if (liveNowBadge != null) {
            liveNowBadge.setVisibility(happeningNow && hasLivestream ? View.VISIBLE : View.GONE);
        }

        // if this view is clicked, open the session details view
        final View finalPhotoView = photoView;
        sessionTargetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallbacks.onSessionSelected(sessionId, finalPhotoView);
            }
        });

        // animate this card
//        if (dataIndex > mMaxDataIndexAnimated) {
//            mMaxDataIndexAnimated = dataIndex;
//        }
    }


    private boolean setupMessageCard(View hero) {
        MessageCardView card = (MessageCardView) hero.findViewById(R.id.message_card);
        if (card == null) {
            LOGE(TAG, "Message card not found in UI (R.id.message_card).");
            return false;
        }
//        if (!PrefUtils.hasAnsweredLocalOrRemote(getActivity()) &&
//                !TimeUtils.hasConferenceEnded(getActivity())) {
//            // show the "in person" vs "remote" card
//            setupLocalOrRemoteCard(card);
//            return true;
//        } else if (WiFiUtils.shouldOfferToSetupWifi(getActivity(), true)) {
//            // show wifi setup card
//            setupWifiOfferCard(card);
//            return true;
//        } else if (PrefUtils.shouldOfferIOExtended(getActivity(), true)) {
//            // show the I/O extended card
//            setupIOExtendedCard(card);
//            return true;
//        } else {
            card.setVisibility(View.GONE);
            return false;
//        }
    }

    private void updateCollectionView() {

        if(mresult == null){
            return;
        }
        List<Venue> result = mresult;
        LOGD(TAG, "SessionsFragment updating CollectionView... " + (mSessionDataIsFullReload ?
                "(FULL RELOAD)" : "(light refresh)"));

        CollectionView.Inventory inv;
        if (result.size() == 0) {
            inv = new CollectionView.Inventory();
        } else {
            hideEmptyView();
            inv = prepareInventory();
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

    // Creates the CollectionView groups based on the cursor data.
    private CollectionView.Inventory prepareInventory() {

        if(mresult == null){
            return new CollectionView.Inventory();
        }
        List<Venue> result = mresult;

        LOGD(TAG, "Preparing collection view inventory.");

        ArrayList<CollectionView.InventoryGroup> inventoryGroups =
                new ArrayList<CollectionView.InventoryGroup>();

        CollectionView.InventoryGroup heroGroup = null;


        int nextGroupId = HERO_GROUP_ID + 1000; // to avoid conflict with the special hero group ID

        int dataIndex = -1;

        final boolean expandedMode = useExpandedMode();
        final int displayCols = getResources().getInteger(expandedMode ?
                R.integer.explore_2nd_level_grid_columns : R.integer.explore_1st_level_grid_columns);
        LOGD(TAG, "Using " + displayCols + " columns.");
        mPreloader.setDisplayCols(displayCols);

        for (Venue a : result) {

            dataIndex++;
            CollectionView.InventoryGroup group = null;

            if (heroGroup == null) {
                group = heroGroup = new CollectionView.InventoryGroup(HERO_GROUP_ID)
                        .setDisplayCols(1)  // hero item spans all columns
                        .setShowHeader(false);
            } else {
                if(inventoryGroups.size() == 0){
                    group = new CollectionView.InventoryGroup(nextGroupId++)
                            .setDisplayCols(displayCols)
                            .setShowHeader(false);
                    inventoryGroups.add(group);
                }else {
                    group = inventoryGroups.get(0);
                }
            }

            group.addItemWithCustomDataIndex(dataIndex);
        }

        // prepare the final groups list
        ArrayList<CollectionView.InventoryGroup> groups = new ArrayList<CollectionView.InventoryGroup>();
        if (heroGroup != null) {
            groups.add(heroGroup); // start with the hero
        }
        groups.addAll(inventoryGroups); // then all future events

        // finally, assemble the inventory and we're done
        CollectionView.Inventory inventory = new CollectionView.Inventory();
        for (CollectionView.InventoryGroup g : groups) {
            inventory.addGroup(g);
        }
        return inventory;
    }

    private boolean useExpandedMode() {
        return false;
    }

    private void hideEmptyView() {
        mEmptyView.setVisibility(View.GONE);
        mLoadingView.setVisibility(View.GONE);
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

    public void reloadFromArguments(Location location) {
//        if (arguments == null) {
//            arguments = new Bundle();
//        } else {
//            // since we might make changes, don't meddle with caller's copy
//            arguments = (Bundle) arguments.clone();
//        }
//
//        // save arguments so we can reuse it when reloading from content observer events
//        mArguments = arguments;

        AsyncListViewLoader loader = new AsyncListViewLoader();
        String url = "https://api.foursquare.com/v2/venues/explore?ll="
                + location.getLatitude() + "," + location.getLongitude()
                +"&limit=15&venuePhotos=1&client_id=PJ0YTLIJSKQVDYSJKD1TOH4SFAQUZYBD2PXIXVH3FOONWGZU"
                +"&client_secret=K41YXRJTWQE311IFRGDZ1CGUXP5GHIJECYGQK4QXGA5PWYGM"
                +"&v=20130815";

        loader.execute(url);

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
                photoDimens = new int[]{width, height};
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

    private class AsyncListViewLoader extends AsyncTask<String, Void, List<Venue>> {

        @Override
        protected void onPostExecute(List<Venue> result) {
            super.onPostExecute(result);
            mresult = result;
            updateCollectionView();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showEmptyView();
        }

        @Override
        protected List<Venue> doInBackground(String... params) {
            List<Venue> result = new ArrayList<Venue>();

            StringBuilder builder = new StringBuilder();

            HttpClient client = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(params[0]);

            try {
                HttpResponse response = client.execute(httpGet);
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode == 200) {
                    HttpEntity entity = response.getEntity();
                    InputStream content = entity.getContent();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(content));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }

                    JSONObject jsObj = new JSONObject(builder.toString());
                    JSONObject response1 = jsObj.getJSONObject("response");
                    JSONArray groups = response1.getJSONArray("groups");
                    JSONArray items = groups.getJSONObject(0).getJSONArray("items");

                    for (int i = 0; i < items.length(); i++) {

                        result.add(convertContact(items.getJSONObject(i)));

                    }

                    return result;

                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        private Venue convertContact(JSONObject obj) throws JSONException {
            JSONObject venue = obj.getJSONObject("venue");
            JSONObject photos = venue.getJSONObject("photos");
            JSONArray photosGroup = photos.getJSONArray("groups");

            String id = venue.getString("id");
            String name = venue.getString("name");
            JSONObject location = venue.getJSONObject("location");

            String address = null;
            if(location.has("address")){
                address = location.getString("address");
            }

            int distance = location.getInt("distance");

            String photoUrl = null;

            if(photosGroup.length() > 0){
                JSONObject photDetails = photosGroup.getJSONObject(0).getJSONArray("items").getJSONObject(0);
                String prefix = photDetails.getString("prefix");
                String suffix = photDetails.getString("suffix");
                photoUrl = prefix + "cap400" + suffix;
            }

            //return new Contact(name, surname, email, phoneNum);
            return new Venue(id, name, distance, address, photoUrl);
        }

    }


}
