package com.dknutsonlaw.android.runtracker2;

import android.content.Context;

import com.google.android.gms.maps.model.LatLngBounds;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dck on 11/26/15.
 * This class is used to pass a LatLngBounds object from a RunFragment to a RunMapFragment. In particularly long Runs, this object
 * combined with the mPoints List of LatLngs can be too large to pass using an Intent.
 */
public class BoundsHolder {
    private static BoundsHolder sBoundsHolder;
    private Context mAppContext;

    Map<Long, WeakReference<LatLngBounds>> mBoundsMap = new HashMap<Long, WeakReference<LatLngBounds>>();

    public static BoundsHolder get(Context context){
        if (sBoundsHolder == null){
            //Use Application Context to avoid leaking activities
            sBoundsHolder = new BoundsHolder(context.getApplicationContext());
        }
        return sBoundsHolder;
    }

    private BoundsHolder(Context context){
        mAppContext = context;
    }

    public void save(Long runId, LatLngBounds bounds) {
        mBoundsMap.put(runId, new WeakReference<LatLngBounds>(bounds));
    }

    public LatLngBounds retrieve(Long runId){
        WeakReference<LatLngBounds> latLngBoundsWeakReference = mBoundsMap.get(runId);
        return latLngBoundsWeakReference.get();
    }
}
