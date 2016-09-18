package com.dknutsonlaw.android.runtracker2;

/**
 * Created by dck on 9/6/15.
 *  * Created by dck on 1/14/15.
 *
 * The basic object used to hold data concerning each particular run we've tracked or are tracking.
 *
 * 2/15/2015
 * Implemented Parcelable interface to allow a Run to be passed to TrackingLocationIntentService.
 *
 * 5/1/2015
 * Added mDistance and mDuration fields for live updating in RunRecyclerListFragment
 *
 * 8/14/2015
 * Added mStartAddress and mEndAddress fields
 */
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;
import java.util.Locale;

public class Run implements Parcelable {
    @SuppressWarnings("unused")
    private static final String TAG = "run";

    private long mId;
    private Date mStartDate;
    private String mStartAddress;
    private double mDistance;
    private long mDuration;
    private String mEndAddress;

    public Run() {
        mId = -1;
        mStartDate = new Date();
        mStartAddress = "";
        mDistance = 0.0;
        mDuration = 0;
        mEndAddress = "";
    }

    double getDistance() {
        return mDistance;
    }

    void setDistance(double distance) {
        mDistance = distance;
    }

    long getDuration() {
        return mDuration;
    }

    void setDuration(long duration) {
        mDuration = duration;
    }

    long getId() {
        return mId;
    }

    void setId(long id) {
        mId = id;
    }

    Date getStartDate() {
        return mStartDate;
    }

    void setStartDate(Date startDate) {
        mStartDate = startDate;
    }

    String getStartAddress() {
        return mStartAddress;
    }

    void setStartAddress(String startAddress) {
        mStartAddress = startAddress;
    }

    String getEndAddress() {
        return mEndAddress;
    }

    void setEndAddress(String endAddress) {
        mEndAddress = endAddress;
    }

    public static final Parcelable.Creator<Run> CREATOR = new Creator<Run>() {
        public Run createFromParcel(Parcel source) {
            Run run = new Run();
            run.mId = source.readLong();
            run.mStartDate = new Date(source.readLong());
            run.mStartAddress = source.readString();
            run.mDistance = source.readDouble();
            run.mDuration = source.readLong();
            run.mEndAddress = source.readString();
            return run;
        }

        public Run[] newArray(int size) {
            return new Run[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(mId);
        parcel.writeLong(mStartDate.getTime());
        parcel.writeString(mStartAddress);
        parcel.writeDouble(mDistance);
        parcel.writeLong(mDuration);
        parcel.writeString(mEndAddress);
    }

    public String toString() {
        return  "RunID =" + mId + "\n" +
                "Start Date = " + mStartDate.toString() + "\n" +
                "Start Address = " + mStartAddress + "\n" +
                "Distance = " + (mDistance * Constants.METERS_TO_MILES) + "\n" +
                "Duration = " + formatDuration((int)mDuration) + "\n" +
                "End Address = " + mEndAddress;
    }

    static String formatDuration(int durationSeconds) {
        int seconds = durationSeconds % 60;
        int minutes = ((durationSeconds - seconds) / 60) % 60;
        int hours = (durationSeconds - (minutes * 60) - seconds) / 3600;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
