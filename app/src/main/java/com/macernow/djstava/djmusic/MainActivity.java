package com.macernow.djstava.djmusic;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import com.macernow.djstava.djmusic.MusicService;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getCanonicalName();

    final private String[] mAudioExtensions = {"mp3", "wav", "ape", "flac"};

    //当前目录下的文件列表
    private ArrayList<File> mFilesList = new ArrayList<File>();
    private ArrayList<File> mMusicFilesList = new ArrayList<File>();
    private LruCache<String, Bitmap> mBitmapsCache;

    private FilesListAdapter filesListAdapter;
    private HashMap<String, Integer> mListPositioins = new HashMap<String, Integer>();

    //字母升序排列
    private int mOptSortType = FilePicker.SORT_NAME_ASC;

    //文件显示的初始路径
    private File startPath;
    private File mCurrentDirectory;

    //显示文件及文件夹，包括隐藏文件
    private int mOptChoiceType = FilePicker.CHOICE_TYPE_ALL;

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
    private TextView textView_music_name, textView_duration, textView_current_time, textView_mode;

    private ImageView imageView_info;

    private int currentPosition = 0;
    private int currentMax;
    private int currentMode = 1;

    private View.OnClickListener clickListener;

    private static int currentIndex = 0;
    private ProgressReceiver progressReceiver;

    private MusicService.MusicBinder musicBinder;

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
            } else {
                playPause.setImageResource(R.drawable.play);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        //初始化UI
        initUIComponent();

        //设置监听器
        initUIComponentListener();

        //注册广播
        registerLocalReceiver();

        setListView();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        mBitmapsCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return getBitmapSize(bitmap) / 1024;
            }
        };


        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            startPath = Environment.getExternalStorageDirectory();
        } else {
            startPath = new File("/");
        }

        readDirectory(startPath);

    }

    private void setListView() {
        filesListAdapter = new FilesListAdapter(this, R.layout.music_list_item);
        listView.setAdapter(filesListAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (position < mFilesList.size()) {
                    File file = mFilesList.get(position);

                    if (file.isDirectory()) {
                        int currentPosition = listView.getFirstVisiblePosition();
                        mListPositioins.put(mCurrentDirectory.getAbsolutePath(), currentPosition);
                        readDirectory(file);
                    } else {

                        /*
                        * 查找当前点击的文件在mMusicFileList中的位置
                        * */
                        currentIndex = mMusicFilesList.indexOf(mFilesList.get(position));

                        if (musicBinder != null) {
                            musicBinder.startPlay(currentIndex, 0);
                            if (musicBinder.isPlaying()) {
                                playPause.setImageResource(R.drawable.pause);
                            }
                        }

                    }

                    filesListAdapter.notifyDataSetChanged();
                }
            }
        });

    }

    /**
     * 给文件、目录排序
     */
    private void sort() {
        Collections.sort(mFilesList, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                boolean isDirectory1 = file1.isDirectory();
                boolean isDirectory2 = file2.isDirectory();

                if (isDirectory1 && !isDirectory2) {
                    return -1;
                }

                if (!isDirectory1 && isDirectory2) {
                    return 1;
                }

                switch (mOptSortType) {
                    case FilePicker.SORT_NAME_DESC:
                        return file2.getName().toLowerCase(Locale.getDefault()).compareTo(file1.getName().toLowerCase(Locale.getDefault()));
                    case FilePicker.SORT_SIZE_ASC:
                        return Long.valueOf(file1.length()).compareTo(Long.valueOf(file2.length()));
                    case FilePicker.SORT_SIZE_DESC:
                        return Long.valueOf(file2.length()).compareTo(Long.valueOf(file1.length()));
                    case FilePicker.SORT_DATE_ASC:
                        return Long.valueOf(file1.lastModified()).compareTo(Long.valueOf(file2.lastModified()));
                    case FilePicker.SORT_DATE_DESC:
                        return Long.valueOf(file2.lastModified()).compareTo(Long.valueOf(file1.lastModified()));
                }

                return file1.getName().toLowerCase(Locale.getDefault()).compareTo(file2.getName().toLowerCase(Locale.getDefault()));
            }

        });

        /*
        * 收集当前目录下的所有音乐文件
        * */
        for (int i = 0; i < mFilesList.size(); i++) {
            if (mFilesList.get(i).isFile()) {
                mMusicFilesList.add(mFilesList.get(i));
            }
        }

        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();

        /*
        * start music service
        * */
        connectToMusicService();
    }

    private int getBitmapSize(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT >= 14) {
            return new OldApiHelper().getBtimapSize(bitmap);
        }
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    private class OldApiHelper {
        @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
        private int getBtimapSize(Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    }

    private void initUIComponent() {
        listView = (ListView) findViewById(R.id.listview);

        seekBar = (SeekBar) findViewById(R.id.seekbar);

        textView_music_name = (TextView) findViewById(R.id.textView_music_name);
        textView_duration = (TextView) findViewById(R.id.textView_duration);
        textView_current_time = (TextView) findViewById(R.id.textView_current_time);
        textView_mode = (TextView) findViewById(R.id.textView_mode);
        textView_mode.setText(R.string.music_mode_all_loop);
        currentMode = MODE_ALL_LOOP;

        playPause = (ImageButton) findViewById(R.id.play);
        previous = (ImageButton) findViewById(R.id.previous);
        next = (ImageButton) findViewById(R.id.next);

        imageView_info = (ImageView) findViewById(R.id.imageView_song_info);
    }

    private void initUIComponentListener() {
        clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {

                    case R.id.textView_mode:
                        currentMode = (currentMode + 1) % 4;
                        if (musicBinder != null) {
                            musicBinder.setMode(currentMode);
                            if (currentMode == MODE_ONE_LOOP) {
                                textView_mode.setText(R.string.music_mode_single_loop);
                                Toast.makeText(MainActivity.this, R.string.music_mode_single_loop, Toast.LENGTH_SHORT).show();
                            } else if (currentMode == MODE_ALL_LOOP) {
                                textView_mode.setText(R.string.music_mode_all_loop);
                                Toast.makeText(MainActivity.this, R.string.music_mode_all_loop, Toast.LENGTH_SHORT).show();
                            } else if (currentMode == MODE_RANDOM) {
                                textView_mode.setText(R.string.music_mode_all_random);
                                Toast.makeText(MainActivity.this, R.string.music_mode_all_random, Toast.LENGTH_SHORT).show();
                            } else if (currentMode == MODE_SEQUENCE) {
                                textView_mode.setText(R.string.music_mode_sequence);
                                Toast.makeText(MainActivity.this, R.string.music_mode_sequence, Toast.LENGTH_SHORT).show();
                            }
                        }

                        break;

                    case R.id.play:
                        if (musicBinder != null) {
                            play(currentIndex);
                        }

                        break;

                    case R.id.previous:
                        if (musicBinder != null) {
                            musicBinder.toPrevious();
                        }

                        break;

                    case R.id.next:
                        if (musicBinder != null) {
                            musicBinder.toNext();
                        }

                        break;

                    case R.id.imageView_song_info:
                        Intent intent = new Intent(MainActivity.this, SongDetailActivity.class);
                        intent.putExtra("songPath", mMusicFilesList.get(currentIndex).getAbsolutePath());
                        startActivity(intent);
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
                    currentPosition = progress * 1000;
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

    private void registerLocalReceiver() {
        progressReceiver = new ProgressReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MusicService.ACTION_UPDATE_PROGRESS);
        intentFilter.addAction(MusicService.ACTION_UPDATE_DURATION);
        intentFilter.addAction(MusicService.ACTION_UPDATE_CURRENT_MUSIC);
        registerReceiver(progressReceiver, intentFilter);
    }

    private void connectToMusicService() {
        Intent intent = new Intent(MainActivity.this, MusicService.class);

        //向MusicService传递当前目录下的歌曲列表
        Bundle bundle = new Bundle();
        bundle.putSerializable("musicFileList", mMusicFilesList);
        intent.putExtras(bundle);

        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void play(int index) {
        if (musicBinder.isPlaying()) {
            musicBinder.stopPlay();
            playPause.setImageResource(R.drawable.play);
        } else {
            musicBinder.startPlay(index, currentPosition);
            playPause.setImageResource(R.drawable.pause);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e(TAG, "onPause.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e(TAG, "onStart.");
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
        }

        unregisterReceiver(progressReceiver);
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
            if (MusicService.ACTION_UPDATE_PROGRESS.equals(action)) {
                int progress = intent.getIntExtra(MusicService.ACTION_UPDATE_PROGRESS, 0);
                if (progress > 0) {
                    currentPosition = progress; // Remember the current position
                    seekBar.setProgress(progress / 1000);
                    textView_current_time.setText(formatTime(progress));
                }
            } else if (MusicService.ACTION_UPDATE_CURRENT_MUSIC.equals(action)) {
                currentIndex = intent.getIntExtra(MusicService.ACTION_UPDATE_CURRENT_MUSIC, 0);
                textView_music_name.setText("正在播放: " + mMusicFilesList.get(currentIndex).getName());
            } else if (MusicService.ACTION_UPDATE_DURATION.equals(action)) {
                //Receive the duration and show under the progress bar
                //Why do this ? because from the ContentResolver, the duration is zero.
                currentMax = intent.getIntExtra(MusicService.ACTION_UPDATE_DURATION, 0);
                seekBar.setMax(currentMax / 1000);
                textView_duration.setText(formatTime(currentMax));
            }
        }

    }

    /**
     * 将毫秒转换成分，格式min:second
     *
     * @param millisecond
     * @return
     */
    private String formatTime(int millisecond) {
        if (millisecond <= 0) {
            return "0:00";
        }

        int min = (millisecond / 1000) / 60;
        int second = (millisecond / 1000) % 60;

        String m, s;
        m = String.valueOf(min);
        if (second >= 10) {
            s = String.valueOf(second);
        } else {
            s = "0" + String.valueOf(second);
        }

        return m + ":" + s;
    }

    /**
     * @param path
     * @desc 读取当前目录下的文件、文件夹,排序并过滤音乐文件
     */
    private void readDirectory(File path) {
        mCurrentDirectory = path;
        mFilesList.clear();
        mMusicFilesList.clear();
        File[] files = path.listFiles();

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (mOptChoiceType == FilePicker.CHOICE_TYPE_DIRECTORIES && !files[i].isDirectory()) {
                    continue;
                } else if ((files[i].isFile()) && !Arrays.asList(mAudioExtensions).contains(getFileExtension(files[i].getName()))) {
                    continue;
                }

                mFilesList.add(files[i]);
            }
        }

        sort();
    }

    /**
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            File parentFile = mCurrentDirectory.getParentFile();
            if (parentFile != null) {
                readDirectory(parentFile);

                String path = mCurrentDirectory.getAbsolutePath();
                if (mListPositioins.containsKey(path)) {
                    listView.setSelection(mListPositioins.get(path));
                    mListPositioins.remove(path);
                }
            }

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * @param fileName
     * @return
     */
    @SuppressLint("DefaultLocale")
    private String getFileExtension(String fileName) {
        int index = fileName.lastIndexOf(".");

        if (index == -1) {
            return "";
        }

        return fileName.substring(index + 1, fileName.length()).toLowerCase(Locale.getDefault());
    }

    /**
     * @param key
     * @param bitmap
     */
    private void addBitmapToCache(String key, Bitmap bitmap) {
        if (getBitmapFromCache(key) == null) {
            mBitmapsCache.put(key, bitmap);
        }
    }

    /**
     * @param key
     * @return
     */
    private Bitmap getBitmapFromCache(String key) {
        return mBitmapsCache.get(key);
    }

    /*
    * 文件浏览列表适配器
    * */
    class FilesListAdapter extends BaseAdapter {
        private Context mContext;
        private int mResource;

        public FilesListAdapter(Context context, int resource) {
            mContext = context;
            mResource = resource;
        }

        @Override
        public int getCount() {
            return mFilesList.size();
        }

        @Override
        public Object getItem(int position) {
            return mFilesList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            File file = mFilesList.get(position);

            convertView = LayoutInflater.from(mContext).inflate(mResource, parent, false);
            ImageView thumbnail = (ImageView) convertView.findViewById(R.id.thumbnail);

            if (file.isDirectory()) {
                thumbnail.setImageResource(R.drawable.icon_folder);
            } else {
                if (Arrays.asList(mAudioExtensions).contains(getFileExtension(file.getName()))) {
                    Bitmap bitmap = getBitmapFromCache(file.getAbsolutePath());
                    if (bitmap == null) new ThumbnailLoader(thumbnail).execute(file);
                    else thumbnail.setImageBitmap(bitmap);
                } else {
                    thumbnail.setImageResource(R.drawable.icon_file);
                }

            }

            TextView filename = (TextView) convertView.findViewById(R.id.filename);
            filename.setText(file.getName());

            TextView filesize = (TextView) convertView.findViewById(R.id.filesize);
            if (filesize != null) {
                if (file.isFile()) filesize.setText(getHumanFileSize(file.length()));
                else {
                    filesize.setText("");
                }
            }

            return convertView;
        }

        String getHumanFileSize(long size) {
            String[] units = getResources().getStringArray(R.array.file_size_units);
            for (int i = units.length - 1; i >= 0; i--) {
                if (size >= Math.pow(1024, i)) {
                    return Math.round((size / Math.pow(1024, i))) + " " + units[i];
                }
            }
            return size + " " + units[0];
        }

        class ThumbnailLoader extends AsyncTask<File, Void, Bitmap> {
            private final WeakReference<ImageView> imageViewReference;

            public ThumbnailLoader(ImageView imageView) {
                imageViewReference = new WeakReference<ImageView>(imageView);
            }

            @TargetApi(Build.VERSION_CODES.ECLAIR)
            @Override
            protected Bitmap doInBackground(File... arg0) {
                Bitmap thumbnailBitmap = null;
                File file = arg0[0];
                if (file != null) {
                    try {
                        ContentResolver crThumb = getContentResolver();
                        if (Arrays.asList(mAudioExtensions).contains(getFileExtension(file.getName()))) {
                            Cursor cursor = crThumb.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Video.Media._ID}, MediaStore.Video.Media.DATA + "='" + file.getAbsolutePath() + "'", null, null);
                            if (cursor != null) {
                                if (cursor.getCount() > 0) {
                                    cursor.moveToFirst();
                                    thumbnailBitmap = MediaStore.Video.Thumbnails.getThumbnail(crThumb, cursor.getInt(0), MediaStore.Video.Thumbnails.MICRO_KIND, null);
                                }
                                cursor.close();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } catch (Error e) {
                        e.printStackTrace();
                    }
                }
                if (thumbnailBitmap != null)
                    addBitmapToCache(file.getAbsolutePath(), thumbnailBitmap);
                return thumbnailBitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (imageViewReference != null) {
                    final ImageView imageView = imageViewReference.get();
                    if (imageView != null) {
                        if (bitmap == null) imageView.setImageResource(R.drawable.icon_file);
                        else imageView.setImageBitmap(bitmap);
                    }
                }
            }

        }

    }

}
