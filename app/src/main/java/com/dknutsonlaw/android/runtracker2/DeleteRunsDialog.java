package com.dknutsonlaw.android.runtracker2;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

/**
 * Created by dck on 11/9/16. A simple AlertDialog asking the user to confirm deletion of a Run or Runs.
 * Takes two fragment arguments: which Activity the request for the dialog came from; and how many Runs
 * are marked for deletion. The former is needed so that only the Activity that made the request will
 * act when the dialog calls back (all the Activities implement the interface that sets up the
 * callbacks so there has to be a way to prevent those that didn't invoke the dialog from trying to
 * act on it); the latter is needed by the dialog to make the dialog's message fit the number of Runs
 * marked for deletion.
 */

@SuppressWarnings("Convert2Lambda")
public class DeleteRunsDialog extends DialogFragment {

    private int mNumberOfRuns;
    private int mWhichFragment;

    //Interface to communicate to relevant UI fragment confirmation of whether to delete Runs.
    public interface DeleteRunsDialogListener {
        void onDeleteRunsDialogPositiveClick(int which);
        void onDeleteRunsDialogNegativeClick(int which);
    }
    //Use this instance of interface to communicate with UI fragments
    private DeleteRunsDialogListener mListener;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        mNumberOfRuns = bundle.getInt(Constants.NUMBER_OF_RUNS);
        mWhichFragment = bundle.getInt(Constants.FRAGMENT);
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        //verify that the Activity implements the necessary interface
        try {
            mListener = (DeleteRunsDialogListener) context;
        } catch (ClassCastException e){
            throw new ClassCastException(context.toString() +
                    " must implement DeleteRunsDialogListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog (@NonNull Bundle bundle){
        //Build the Dialog and set up the button click handlers
        Resources r = getActivity().getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(r.getQuantityString(R.plurals.deletion_dialog_message, mNumberOfRuns, mNumberOfRuns))
                .setPositiveButton(r.getText(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Send the positive button result back to the host activity
                        mListener.onDeleteRunsDialogPositiveClick(mWhichFragment);
                    }
                })
                .setNegativeButton(r.getText(android.R.string.no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Send the negative button result back to the host activity
                        mListener.onDeleteRunsDialogNegativeClick(mWhichFragment);
                    }
                });
        return builder.create();
    }
}
