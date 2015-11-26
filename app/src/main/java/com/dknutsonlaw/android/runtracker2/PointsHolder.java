package com.dknutsonlaw.android.runtracker2;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by dck on 11/26/15.
 *
 * This class was created to allow passing a large LatLng List from a RunFragment to a RunMapFragment.
 * In particularly prolonged Runs, the mPoints LatLng List combined with the mBounds LatLngBounds
 * object can be too large to pass with an Intent.
 */
public class PointsHolder {
    private static PointsHolder sPointsHolder;
    private Context mAppContext;

    Map<Long, WeakReference<List<LatLng>>> mPoints = new HashMap<Long, WeakReference<List<LatLng>>>();

    public static PointsHolder get(Context context){
        if (sPointsHolder == null){
            sPointsHolder = new PointsHolder(context.getApplicationContext());
        }
        return sPointsHolder;
    }

    private PointsHolder(Context context){
        mAppContext = context;
    }

    public void save(Long runId, List<LatLng> list){
        mPoints.put(runId, new WeakReference<List<LatLng>>(list));
    }

    public List<LatLng> retrieve(Long runId){
        WeakReference<List<LatLng>>  weakReference = mPoints.get(runId);
        return weakReference.get();
    }
}
