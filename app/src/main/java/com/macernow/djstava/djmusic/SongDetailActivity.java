package com.macernow.djstava.djmusic;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.macernow.djstava.djmusic.R;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.datatype.Artwork;

import java.io.File;


public class SongDetailActivity extends ActionBarActivity {
    private static final String TAG = SongDetailActivity.class.getSimpleName();
    private TextView textView_header,textView_content;
    private ImageView imageView;

    private MP3File mp3File;
    private Intent intent;
    private String songPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_detail);

        //初始化UI控件
        initUIComponent();

        intent = getIntent();
        songPath = intent.getStringExtra("songPath");

        showSongInfo(songPath);
    }

    private void initUIComponent() {
        textView_content = (TextView)findViewById(R.id.mp3_content);
        textView_header = (TextView)findViewById(R.id.mp3_header);
        imageView = (ImageView)findViewById(R.id.mp3_img);
    }

    private void showSongInfo(final String songPath) {
        getHeader(songPath);
        getContent();
    }

    private void getHeader(final String name) {
        try {
            File file = new File(name);
            mp3File = (MP3File) AudioFileIO.read(file);

            MP3AudioHeader header = mp3File.getMP3AudioHeader();
            StringBuffer sbf = new StringBuffer();
            sbf.append("长度: " + header.getTrackLength() + "\n");
            sbf.append("比特率: " + header.getBitRate() + "\n");
            sbf.append("编码器: " + header.getEncoder()+"\n");
            sbf.append("格式: " + header.getFormat() + "\n");
            sbf.append("声道: " + header.getChannels() + "\n");
            sbf.append("采样率: " +header.getSampleRate() + "\n");
            sbf.append("MPEG: " + header.getMpegLayer() + "\n");
            sbf.append("MP3起始字节: "+header.getMp3StartByte() + "\n");
            sbf.append("精确的长度: "+header.getPreciseTrackLength() + "\n");
            sbf.append("帧数: " +header.getNumberOfFrames()+ "\n");
            sbf.append("编码类型: "+header.getEncodingType()+ "\n");
            sbf.append("MPEG版本 :"+header.getMpegVersion()+ "\n");
            textView_header.setText(sbf);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*ID3是mp3格式的音频文件metadata的容器,v1版本在末尾128字节，v2版本在文件开头*/
    private void getContent() {
        try {
            if (mp3File.hasID3v1Tag()) {
                Log.e(TAG,"ID3v1Tag");
                Tag tag = mp3File.getTag();
                StringBuffer sbf = new StringBuffer();
                sbf.append("歌手: "+tag.getFirst(FieldKey.ARTIST) + "\n");
                sbf.append("专辑: "+tag.getFirst(FieldKey.ALBUM) + "\n");
                sbf.append("歌名: "+tag.getFirst(FieldKey.TITLE) + "\n");
                sbf.append("发行时间: "+tag.getFirst(FieldKey.YEAR));

                Artwork artwork = tag.getFirstArtwork();
                byte[] byteArray = artwork.getBinaryData();
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                imageView.setImageBitmap(bitmap);
                textView_content.setText(sbf);
            }else if (mp3File.hasID3v2Tag()) {
                Log.e(TAG,"ID3v2Tag");
                Tag tag = mp3File.getID3v2Tag();
                StringBuffer stringBuffer = new StringBuffer();

                stringBuffer.append("歌手: " + tag.getFirst(FieldKey.ARTIST) + "\n");
                stringBuffer.append("专辑: " + tag.getFirst(FieldKey.ALBUM) + "\n");
                stringBuffer.append("歌名: " + tag.getFirst(FieldKey.TITLE) + "\n");
                stringBuffer.append("发行时间: " + tag.getFirst(FieldKey.YEAR) + "\n");
                textView_content.setText(stringBuffer);

                Artwork artwork=tag.getFirstArtwork();
                byte[] byteArray=artwork.getBinaryData();
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                imageView.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_song_detail, menu);
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
}
