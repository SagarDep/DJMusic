package com.macernow.djstava.djmusic;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getCanonicalName();

    //歌曲搜索目录
    private final String MUSIC_PATH = "/mnt/sdcard/djstava";

    //音乐播放模式
    public static final int MODE_ONE_LOOP = 0;
    public static final int MODE_ALL_LOOP = 1;
    public static final int MODE_RANDOM = 2;
    public static final int MODE_SEQUENCE = 3;

    private ListView listView;
    private SeekBar seekBar;
    private ImageButton playPause;
    private ImageButton previous;
    private ImageButton next;
    private TextView textView_music_name,textView_duration,textView_current_time,textView_mode;

    private ImageView imageView_info;

    private int currentPosition = 0;
    private int currentMax;
    private int currentMode = 1;

    private View.OnClickListener clickListener;

    private List<String> fileList,fileListPath;
    private String[] files,filesPath;
    private static int currentIndex;
    private ProgressReceiver progressReceiver;

    private AdapterTextview adapterTextview;

    private com.macernow.djstava.djmusic.MusicService.MusicBinder musicBinder;

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBinder = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (musicBinder == null) {
                musicBinder = (MusicService.MusicBinder) service;
            }

            if (musicBinder.isPlaying()) {
                playPause.setImageResource(R.drawable.pause);
                musicBinder.notifyActivity();
            }else {
                playPause.setImageResource(R.drawable.play);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        Log.e(TAG, "onCreate.");
        connectToMusicService();

        //初始化UI
        initUIComponent();

        //设置监听器
        initUIComponentListener();

        //扫描歌曲
        scanMp3Files();

        //注册广播
        registerLocalReceiver();

        //填充listview
        adapterTextview = new AdapterTextview(this, files);
        listView.setAdapter(adapterTextview);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //开始播放选中歌曲
                currentIndex = position;
                musicBinder.startPlay(currentIndex, 0);
                if (musicBinder.isPlaying()) {
                    playPause.setImageResource(R.drawable.pause);
                }

            }
        });
    }

    private void initUIComponent() {
        listView = (ListView)findViewById(R.id.listview);

        seekBar = (SeekBar)findViewById(R.id.seekbar);

        textView_music_name = (TextView)findViewById(R.id.textView_music_name);
        textView_duration = (TextView)findViewById(R.id.textView_duration);
        textView_current_time = (TextView)findViewById(R.id.textView_current_time);
        textView_mode = (TextView)findViewById(R.id.textView_mode);
        textView_mode.setText(R.string.music_mode_all_loop);
        currentMode = MODE_ALL_LOOP;

        playPause = (ImageButton)findViewById(R.id.play);
        previous = (ImageButton)findViewById(R.id.previous);
        next = (ImageButton)findViewById(R.id.next);

        imageView_info = (ImageView)findViewById(R.id.imageView_song_info);

    }

    private void initUIComponentListener() {
        clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.textView_mode:
                        currentMode = (currentMode + 1) % 4;
                        musicBinder.setMode(currentMode);
                        if (currentMode == MODE_ONE_LOOP) {
                            textView_mode.setText(R.string.music_mode_single_loop);
                            Toast.makeText(MainActivity.this,R.string.music_mode_single_loop,Toast.LENGTH_SHORT).show();
                        } else if (currentMode == MODE_ALL_LOOP) {
                            textView_mode.setText(R.string.music_mode_all_loop);
                            Toast.makeText(MainActivity.this,R.string.music_mode_all_loop,Toast.LENGTH_SHORT).show();
                        } else if (currentMode == MODE_RANDOM) {
                            textView_mode.setText(R.string.music_mode_all_random);
                            Toast.makeText(MainActivity.this,R.string.music_mode_all_random,Toast.LENGTH_SHORT).show();
                        } else if (currentMode == MODE_SEQUENCE) {
                            textView_mode.setText(R.string.music_mode_sequence);
                            Toast.makeText(MainActivity.this,R.string.music_mode_sequence,Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case R.id.play:
                        play(currentIndex,R.id.play);

                        break;

                    case R.id.previous:
                        musicBinder.toPrevious();

                        break;

                    case R.id.next:
                        musicBinder.toNext();

                        break;

                    case R.id.imageView_song_info:
                        Intent intent = new Intent(MainActivity.this,SongDetailActivity.class);
                        intent.putExtra("songPath",filesPath[currentIndex]);
                        startActivity(intent);
                        break;

                    default:
                        break;
                }
            }
        };

        textView_mode.setOnClickListener(clickListener);

        playPause.setOnClickListener(clickListener);
        previous.setOnClickListener(clickListener);
        next.setOnClickListener(clickListener);

        imageView_info.setOnClickListener(clickListener);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    musicBinder.changeProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void registerLocalReceiver(){
        progressReceiver = new ProgressReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MusicService.ACTION_UPDATE_PROGRESS);
        intentFilter.addAction(MusicService.ACTION_UPDATE_DURATION);
        intentFilter.addAction(MusicService.ACTION_UPDATE_CURRENT_MUSIC);
        registerReceiver(progressReceiver, intentFilter);
    }

    private void connectToMusicService(){
        Intent intent = new Intent(MainActivity.this, MusicService.class);

        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void play(int position,int resId) {
        if (musicBinder.isPlaying()) {
            musicBinder.stopPlay();
            playPause.setImageResource(R.drawable.play);
        }else {
            musicBinder.startPlay(position,currentPosition);
            playPause.setImageResource(R.drawable.pause);
        }
    }

    private void scanMp3Files() {
        fileList = new ArrayList<String>();
        fileListPath = new ArrayList<String>();

        final File[] file = new File(MUSIC_PATH).listFiles();

        readFile(file);
        files = fileList.toArray(new String[1]);
        filesPath = fileListPath.toArray(new String[1]);
    }

    private void readFile(final File[] file) {
        for (int i = 0;(file != null) && (i < file.length);i++) {
            if (file[i].isFile() && (file[i].getName().endsWith("mp3"))) {

                fileList.add(file[i].getName());
                fileListPath.add(file[i].getPath());
            }else if (file[i].isDirectory()) {
                final File[] tempFileList = new File(file[i].getAbsolutePath()).listFiles();
                readFile(tempFileList);
            }

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e(TAG, "onPause.");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.e(TAG, "onResume.");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.e(TAG, "onStop.");
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Log.e(TAG, "onRestart.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy.");

        if (musicBinder != null) {
            unbindService(serviceConnection);
            unregisterReceiver(progressReceiver);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    class ProgressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(MusicService.ACTION_UPDATE_PROGRESS.equals(action)){
                int progress = intent.getIntExtra(MusicService.ACTION_UPDATE_PROGRESS, 0);
                //Log.e(TAG,"Recevie: " + progress);
                if(progress > 0){
                    currentPosition = progress; // Remember the current position
                    seekBar.setProgress(progress / 1000);
                    textView_current_time.setText(formatTime(progress));
                }
            }else if(MusicService.ACTION_UPDATE_CURRENT_MUSIC.equals(action)){
                //Retrive the current music and get the title to show on top of the screen.
                currentIndex = intent.getIntExtra(MusicService.ACTION_UPDATE_CURRENT_MUSIC, 0);
                //Log.e(TAG,"Receive: " + files[currentIndex]);
                textView_music_name.setText("正在播放: " + files[currentIndex]);
            }else if(MusicService.ACTION_UPDATE_DURATION.equals(action)){
                //Receive the duration and show under the progress bar
                //Why do this ? because from the ContentResolver, the duration is zero.
                currentMax = intent.getIntExtra(MusicService.ACTION_UPDATE_DURATION, 0);
                //Log.e(TAG, "[Main ProgressReciver] Receive duration : " + currentMax);
                seekBar.setMax(currentMax / 1000);
                textView_duration.setText(formatTime(currentMax));
            }
        }

    }

    //将毫秒转换成分，格式min:second
    private String formatTime(int millisecond) {
        if (millisecond <= 0) {
            return "0:00";
        }

        int min = (millisecond / 1000) / 60;
        int second = (millisecond / 1000) % 60;

        String m,s;
        m = String.valueOf(min);
        if (second >= 10) {
            s = String.valueOf(second);
        }else {
            s = "0" + String.valueOf(second);
        }

        return m + ":" + s;
    }

}
