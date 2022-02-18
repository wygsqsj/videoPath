package com.wish.videopath.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtil {

    public static String copyAssFileToSD(Context context, String name) {
        try {
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            File file = new File(dir, name);
            if (file.exists()) {
                return file.getAbsolutePath();
            } else {
                file.createNewFile();
                OutputStream os = new FileOutputStream(file);
                InputStream is = context.getAssets().open(name);
                byte[] buffer = new byte[1024];
                int bufferRead = 0;
                while ((bufferRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bufferRead);
                }
                os.flush();
                is.close();
                os.close();
                return file.getAbsolutePath();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
