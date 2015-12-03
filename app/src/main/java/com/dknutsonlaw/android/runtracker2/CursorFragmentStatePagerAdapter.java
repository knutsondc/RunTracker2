package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15. An adapter that feeds fragments to a ViewPager based upon data taken
 * from a database cursor.
 */
import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.util.SparseIntArray;
import android.view.ViewGroup;

import java.util.HashMap;

@SuppressWarnings("ALL")
abstract class CursorFragmentStatePagerAdapter extends FragmentStatePagerAdapter {

    private boolean mDataValid;
    private Cursor mCursor;
    private Context mContext;
    private SparseIntArray mItemPositions;
    private HashMap<Object, Integer> mObjectMap;
    private int mRowIDColumn;

    public CursorFragmentStatePagerAdapter(Context context, FragmentManager fm, Cursor cursor) {
        super(fm);

        init(context, cursor);
    }

    private void init(Context context, Cursor c) {
        //noinspection Convert2Diamond
        mObjectMap = new HashMap<Object, Integer>();
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
        mContext = context;
        mRowIDColumn = cursorPresent ? c.getColumnIndexOrThrow("_id") : -1;
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
        mObjectMap.remove(object);

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
        mObjectMap.put(obj, rowId);

        return obj;
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
            //mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
            mRowIDColumn = newCursor.getColumnIndexOrThrow(BaseColumns._ID);
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mRowIDColumn = -1;
            mDataValid = false;
        }

        setItemPositions();
        /*if (mDataValid){
            notifyDataSetChanged();
        }*/


        return oldCursor;
    }

}