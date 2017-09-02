package com.dknutsonlaw.android.runtracker2;

/*
  Created by dck on 9/6/15. An adapter that feeds fragments to a ViewPager based upon data taken
  from a database cursor.
 */
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.ViewGroup;

import java.util.HashMap;

@SuppressWarnings("ALL")
public abstract class CursorFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

    protected boolean mDataValid;
    protected Cursor mCursor;
    protected Context mContext;
    protected SparseArray mRegisteredFragments;
    protected SparseIntArray mItemPositions;
    protected SparseArray mRunIdToFragment;
    protected HashMap<Object, Integer> mObjectMap;
    protected int mRowIDColumn;

    public CursorFragmentStatePagerAdapter(Context context, FragmentManager fm, Cursor cursor) {
        super(fm);

        init(context, cursor);
    }

    private void init(Context context, Cursor c) {
        mObjectMap = new HashMap<>();
        mRegisteredFragments = new SparseArray<>();
        mRunIdToFragment = new SparseArray<>();
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
        mContext = context;
        mRowIDColumn = cursorPresent ? c.getColumnIndexOrThrow(BaseColumns._ID) : -1;
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public int getItemPosition(Object object) {
        Integer rowId = mObjectMap.get(object);
        if (rowId != null && mItemPositions != null) {
            return mItemPositions.get(rowId, POSITION_NONE);
        }
        return POSITION_NONE;
    }
    //Create a SparseArray to associate item ID with its position in the adapter
    public void setItemPositions() {
        mItemPositions = null;

        if (mDataValid) {
            int count = mCursor.getCount();
            mItemPositions = new SparseIntArray(count);
            mCursor.moveToPosition(-1);
            while (mCursor.moveToNext()) {
                int rowId = mCursor.getInt(mRowIDColumn);
                int cursorPos = mCursor.getPosition();
                mItemPositions.append(rowId, cursorPos);
            }
        }
    }

    @Override
    public Fragment getItem(int position) {
        if (mDataValid) {
            mCursor.moveToPosition(position);
            return getItem(mContext, mCursor);
        } else {
            return null;
        }
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        int rowId = mObjectMap.get(object);
        mObjectMap.remove(object);
        mRegisteredFragments.remove(position);
        mRunIdToFragment.remove(rowId);
        super.destroyItem(container, position, object);
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        int rowId = mCursor.getInt(mRowIDColumn);
        Object obj = super.instantiateItem(container, position);
        //instantiate mappings of object to ID number and object (fragment) to adapter position
        mObjectMap.put(obj, rowId);
        mRegisteredFragments.put(position, obj);
        mRunIdToFragment.put(rowId, obj);


        return obj;
    }
    //Get fragment instance from specified position in adapter
    public Object getRegisteredFragment(int position){
        return mRegisteredFragments.get(position);
    }
    //Get fragment instance associated with RunId
    public Object getFragmentFromRunId(long runId){
        return mRunIdToFragment.get((int)runId);
    }

    public abstract Fragment getItem(@SuppressWarnings("UnusedParameters") Context context, Cursor cursor);

    @Override
    public int getCount() {
        if (mDataValid) {
            return mCursor.getCount();
        } else {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) {
            old.close();
        }
    }

    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }
        Cursor oldCursor = mCursor;
        mCursor = newCursor;
        if (newCursor != null) {
            mRowIDColumn = newCursor.getColumnIndexOrThrow(BaseColumns._ID);
            mDataValid = true;
        } else {
            mRowIDColumn = -1;
            mDataValid = false;
        }

        setItemPositions();
        if (mDataValid){
            notifyDataSetChanged();
        }


        return oldCursor;
    }

}