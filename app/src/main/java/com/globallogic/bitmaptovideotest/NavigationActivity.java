package com.globallogic.bitmaptovideotest;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.mapbox.mapboxsdk.Mapbox;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NavigationActivity extends AppCompatActivity {

    public BitmapToVideoEncoder bitmapToVideoEncoder;
    private static final String TAG = "NavigationActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_navigation);

        Button btn = (Button) findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!bitmapToVideoEncoder.isEncodingStarted()){
                    bitmapToVideoEncoder.startEncoding(644, 500, getOutFile());
                }else{
                    bitmapToVideoEncoder.stopEncoding();
                }
            }
        });
        bitmapToVideoEncoder = new BitmapToVideoEncoder(new BitmapToVideoEncoder.IBitmapToVideoEncoderCallback() {
            @Override
            public void onEncodingComplete(File outputFile) {

                Log.w(TAG, "onEncodingComplete: "+outputFile.getAbsolutePath() );
            }
        });
    }

    private File getOutFile() {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/saved_images");
        myDir.mkdirs();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fname = "mySnapshotVideo_"+ timeStamp +".mp4";
        return new File(myDir, fname);
    }




}
