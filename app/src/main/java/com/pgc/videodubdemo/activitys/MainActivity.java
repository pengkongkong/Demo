package com.pgc.videodubdemo.activitys;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.pgc.videodubdemo.R;
import com.pgc.videodubdemo.utils.VideoHelper;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final String videoUrl = "http://qnfile.yc-learning.com/2020122410015149.mp4";//此视频为网络链接，可能会出现丢失现象
    //这个是音频每一句开始时间，根据这个时间，将音频切断成对应的音频片段
    private static final long[] times={0,1060,2620,4240,8680,9860,11520,13920,17530,19150,20510,22020,25330,27770,30030,33110,34950,39730,43570,-1};
    @BindView(R.id.state_tv)
    TextView stateTv;
    @BindView(R.id.start_bt)
    Button startBt;
    private VideoHelper videoHelper;
    private String dirPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        dirPath=getExternalFilesDir("audio").getAbsolutePath();
        videoHelper = new VideoHelper(handler);
    }

    @OnClick(R.id.start_bt)
    public void onViewClicked() {
        new Thread(()->{
            try {
                videoHelper.initDecodeVideoToAudio(dirPath,videoUrl,"test",false,times);
            } catch (IOException e) {
                e.printStackTrace();
                handler.sendEmptyMessage(0);
            }
        }).start();
    }


    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            StringBuilder stringBuilder=new StringBuilder();
            switch (msg.what) {
                case 0:
                    stringBuilder.append("解析错误");
                    break;
                case 1:
                    stringBuilder.append("解析开始");
                    break;
                case 2:
                    stringBuilder.append("解析完成");
                    break;
                case 3:
                    stringBuilder.append("解析准备");
                    break;
            }
            stringBuilder.append("\n");
            stateTv.setText(stringBuilder);
        }
    };
}
