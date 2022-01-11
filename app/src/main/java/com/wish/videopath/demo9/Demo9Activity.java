package com.wish.videopath.demo9;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.SeekBar;

import com.wish.videopath.databinding.ActivityDemo9Binding;

import androidx.appcompat.app.AppCompatActivity;

/**
 * openGL的使用
 * 使用openGl 为cameraX预览的数据添加滤镜
 */
public class Demo9Activity extends AppCompatActivity {

    private ActivityDemo9Binding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDemo9Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initSeek();
    }

    private void initSeek() {
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                float a = (6000f - 4000f) * i / 100.0f + 4000f;
                binding.glView.setProgress(a);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        binding.seekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                float a = (200f + 200f) * progress / 100.0f - 200f;
                binding.glView.setTint(a);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }


}