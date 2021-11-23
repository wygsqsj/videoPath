package com.wish.videopath.demo6;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 类名称：SocketThread
 * 类描述：通过Socket建立连接，在C/S之间传递录屏数据
 * <p>
 * 创建时间：2021/11/12
 */
public class SocketThread extends Thread {

    private Socket cSocket;

    public SocketThread() {

    }

    @Override
    public void run() {
        super.run();
        try {
            //创建Socket,指定主机名和端口号
            Socket client = new Socket("127.0.0.1", 10000);
            //获取输出流
            OutputStream os = client.getOutputStream();
            //写出数据
            os.write("你好！服务端！".getBytes());
            //获取输入流，里面有服务端反馈的数据
            InputStream is = client.getInputStream();
            int len = 0;
            byte[] buf = new byte[1024];
            if ((len = is.read(buf)) != -1) {
                System.out.println(new String(buf, 0, len));
            }
            //关闭资源
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void putData() {

    }
}
