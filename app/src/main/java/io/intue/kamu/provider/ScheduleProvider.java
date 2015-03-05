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

package io.intue.kamu.provider;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import io.intue.kamu.util.SelectionBuilder;

import static io.intue.kamu.util.LogUtils.LOGV;
import static io.intue.kamu.util.LogUtils.makeLogTag;

public class ScheduleProvider extends ContentProvider{

    private static final String TAG = makeLogTag(ScheduleProvider.class);

    private static final UriMatcher sUriMatcher = buildUriMatcher();

    private static final int SESSIONS = 400;

    private ScheduleDatabase mOpenHelper;
    @Override
    public boolean onCreate() {
        mOpenHelper = new ScheduleDatabase(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        final SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        String tagsFilter = uri.getQueryParameter(ScheduleContract.Sessions.QUERY_PARAMETER_TAG_FILTER);
        final int match = sUriMatcher.match(uri);

        // avoid the expensive string concatenation below if not loggable
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            LOGV(TAG, "uri=" + uri + " match=" + match + " proj=" + Arrays.toString(projection) +
                    " selection=" + selection + " args=" + Arrays.toString(selectionArgs) + ")");
        }


        switch (match) {
            default: {
                // Most cases are handled with simple SelectionBuilder
                final SelectionBuilder builder = buildExpandedSelection(uri, match);

//                // If a special filter was specified, try to apply it
//                if (!TextUtils.isEmpty(tagsFilter)) {
//                    addTagsFilter(builder, tagsFilter);
//                }

                boolean distinct = !TextUtils.isEmpty(
                        uri.getQueryParameter(ScheduleContract.QUERY_PARAMETER_DISTINCT));

                Cursor cursor = builder
                        .where(selection, selectionArgs)
                        .query(db, distinct, projection, sortOrder, null);
//                Context context = getContext();
//                if (null != context) {
//                    cursor.setNotificationUri(context.getContentResolver(), uri);
//                }
                return cursor;
            }
//            case SEARCH_SUGGEST: {
//                final SelectionBuilder builder = new SelectionBuilder();
//
//                // Adjust incoming query to become SQL text match
//                selectionArgs[0] = selectionArgs[0] + "%";
//                builder.table(Tables.SEARCH_SUGGEST);
//                builder.where(selection, selectionArgs);
//                builder.map(SearchManager.SUGGEST_COLUMN_QUERY,
//                        SearchManager.SUGGEST_COLUMN_TEXT_1);
//
//                projection = new String[] {
//                        BaseColumns._ID,
//                        SearchManager.SUGGEST_COLUMN_TEXT_1,
//                        SearchManager.SUGGEST_COLUMN_QUERY
//                };
//
//                final String limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT);
//                return builder.query(db, false, projection, ScheduleContract.SearchSuggest.DEFAULT_SORT, limit);
//            }
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }

    /**
     * Build and return a {@link UriMatcher} that catches all {@link Uri}
     * variations supported by this {@link ContentProvider}.
     */
    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = ScheduleContract.CONTENT_AUTHORITY;
        matcher.addURI(authority, "sessions", SESSIONS);
        return matcher;
    }

    /**
     * Build an advanced {@link SelectionBuilder} to match the requested
     * {@link Uri}. This is usually only used by {@link #query}, since it
     * performs table joins useful for {@link Cursor} data.
     */
    private SelectionBuilder buildExpandedSelection(Uri uri, int match) {
        final SelectionBuilder builder = new SelectionBuilder();
        switch (match) {

            case SESSIONS: {
                // We query sessions on the joined table of sessions with rooms and tags.
                // Since there may be more than one tag per session, we GROUP BY session ID.
                // The starred sessions ("my schedule") are associated with a user, so we
                // use the current user to select them properly
                return builder.table(Tables.SESSIONS);
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    interface Tables {
        String SESSIONS = "sessions";
    }
}