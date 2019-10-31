package com.example.myapplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
public class MainActivity extends Activity implements SensorEventListener,
        OnClickListener {
    /** Called when the activity is first created. */
    //Create a LOG label
    private Button mWriteButton, mStopButton;
    private boolean doWrite = false;
    private SensorManager sm;//传感器
    private float lowX = 0, lowY = 0, lowZ = 0;  //x、y、z三个方向
    private final float FILTERING_VALAUE = 0.1f;
    private TextView AT,ACT,STEP;
    private double[] var=new double[3];
    int ct=0;
    int steps=0;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AT = (TextView)findViewById(R.id.AT);
        ACT = (TextView)findViewById(R.id.onAccuracyChanged);
        STEP=(TextView)findViewById(R.id.STEP);
//Create a SensorManager to get the system’s sensor service  创建一个传感器去得到系统的传感器服务
        sm =
                (SensorManager)getSystemService(Context.SENSOR_SERVICE);

/*
 *使用最常用的方法注册事件
 * *参数1:sensoreventlistener detectophone
  **参数2：传感器一个服务可以有多个传感器
 * 实现。这里，我们使用getDefaultSensor来获取默认的传感器
 * *参数3：模式我们可以选择数据变化
* */
// Register the acceleration sensor 得到速度传感器
        sm.registerListener(this,
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),   //获得传感器速度服务
                SensorManager.SENSOR_DELAY_FASTEST);//High sampling rate高采样率；.SENSOR_DELAY_NORMAL means a lower sampling rate
        try {
            FileOutputStream fout = openFileOutput("acc.txt",
                    Context.MODE_PRIVATE); //打开acc.txt
            fout.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mWriteButton = (Button) findViewById(R.id.Button_Write);
        mWriteButton.setOnClickListener(this);
        mStopButton = (Button) findViewById(R.id.Button_Stop);
        mStopButton.setOnClickListener(this);
    }

    public void onPause(){
        super.onPause();
        sm.unregisterListener(this);
    }

    public void onClick(View v) {
        if (v.getId() == R.id.Button_Write) {
            doWrite = true;
        }
        if (v.getId() == R.id.Button_Stop) {
            doWrite = false;
        }
    }

    //传感器的监听事件
    //精确度
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        ACT.setText("onAccuracyChanged is detonated");
    }


    private final static String TAG = "StepDetector";
    private float  mLimit = 10;
    private float  mLastValues[] = new float[3*2];
    private float  mScale[] = new float[2];
    private float  mYOffset;
    private float  mLastDirections[] = new float[3*2];
    private float  mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float  mLastDiff[] = new float[3*2];
    private int  mLastMatch = -1;

    // 最后加速度方向
    public MainActivity(){
        int h = 480; // TODO: remove this constant
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));       //标准重力
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));   //地球最大磁场
    }

    //传感器改变
    public void onSensorChanged(SensorEvent event) {
        String message = new String();
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float X = event.values[0];
            float Y = event.values[1];
            float Z = event.values[2];
//Low-Pass Filter
            lowX = X * FILTERING_VALAUE + lowX * (1.0f -
                    FILTERING_VALAUE);
            lowY = Y * FILTERING_VALAUE + lowY * (1.0f -
                    FILTERING_VALAUE);
            lowZ = Z * FILTERING_VALAUE + lowZ * (1.0f -
                    FILTERING_VALAUE);
//High-pass filter
            float highX = X - lowX;
            float highY = Y - lowY;
            float highZ = Z - lowZ;
            double highA = Math.sqrt(highX * highX + highY * highY + highZ
                    * highZ);
            DecimalFormat df = new DecimalFormat("#,##0.000");
            message = df.format(highX) + " ";
            message += df.format(highY) + " ";
            message += df.format(highZ) + " ";
            message += df.format(highA) + "\n";

            AT.setText(message);
            float vSum = 0;
            for (int i=0 ; i<3 ; i++) {
                // 计算各轴速度
                final float v = mYOffset + event.values[i] * mScale[1];
                // 计算速度总和
                vSum += v;
            }
            //计算平均速度
            int k = 0;
            float v = vSum / 3;

            float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
            if (direction == - mLastDirections[k]) {
                // Direction changed
                // 改变方向，判断大于还是小于
                int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                mLastExtremes[extType][k] = mLastValues[k];
                float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);   // 两个极值点之差
                // 如果波动大于设定的阈值（灵敏度）
                if (diff > mLimit) {
                    boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                    boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                    boolean isNotContra = (mLastMatch != 1 - extType);
                    // 当以上三个条件均满足的时候，步数加1
                    if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                        steps += 1;
                        mLastMatch = extType;
                    }
                    else {
                        mLastMatch = -1;
                    }
                }
                // 记录上一次波动的值，也就是波动大小
                mLastDiff[k] = diff;
            }
            mLastDirections[k] = direction;
            mLastValues[k] = v;
            //Log.e("msg", message);
            STEP.setText(steps + "");
            if (doWrite) {
                write2file(message);
            }
        }
    }
    private void write2file(String a){
        try {
            File file = new File("sdcard/acc.txt");//write the result into/sdcard/acc.txt
            if (!file.exists()){
                file.createNewFile();
            }
// Open a random access file stream for reading and writing
            RandomAccessFile randomFile = new
                    RandomAccessFile("/sdcard/acc.txt", "rw");
// The length of the file (the number of bytes)
            long fileLength = randomFile.length();
// Move the file pointer to the end of the file
            randomFile.seek(fileLength);
            randomFile.writeBytes(a);
            randomFile.close();
        } catch (IOException e) {
// TODO Auto-generated catch block
            e.printStackTrace();
        }
    }










}