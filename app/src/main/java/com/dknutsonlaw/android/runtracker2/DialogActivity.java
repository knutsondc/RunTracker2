package com.dknutsonlaw.android.runtracker2;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class DialogActivity extends AppCompatActivity {

    public final static String TAG = "DialogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_dialog);
        Resources r = getResources();
        int errorCode = getIntent().getIntExtra(Constants.EXTRA_ERROR_CODE, -1);
        TextView textView = findViewById(R.id.error_textview);
        textView.setText(r.getString(R.string.error_number, errorCode));
        Button button = findViewById(R.id.ok_button);
        button.setOnClickListener(view -> finish());
    }

}
