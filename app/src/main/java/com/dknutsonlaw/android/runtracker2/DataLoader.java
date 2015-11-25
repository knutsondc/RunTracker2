package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 */

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

/**
 * Created by dck on 1/15/15.
 * This is taken from the Big Nerd Ranch Book. Subclasses implement asynchronous loading of
 * arbitrary data types, not just cursors, from SQLite data tables. In this package, Run
 * objects are the data involved. Subclasses need to override the loadInBackground() method of
 * AsyncTaskLoader to specify the database query needed to find the data we're interested in, as
 * well as provide an appropriate constructor.
 *
 *
 * 8/12/15 - Added use of subclass LastLocationLoader in RunFragment
 */

abstract class DataLoader<D> extends AsyncTaskLoader<D> {
    private D mData;

    DataLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStartLoading() {
        if (mData != null) {
            deliverResult(mData);
        } else {
            forceLoad();
        }
    }

    @Override
    public void deliverResult(D data) {
        mData = data;
        if (isStarted())
            super.deliverResult(data);
    }
}
