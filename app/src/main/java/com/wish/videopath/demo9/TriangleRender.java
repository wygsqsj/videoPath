package com.wish.videopath.demo9;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 绘制三角形,
 * openGL坐标系以图像中间为远点，右上角为1，1，左上 -1，1 左下 -1，-1 右下 1，-1
 * z轴表示深度，绘出三维图形
 * <p>
 * 通过gl语言传递给GPU进行绘制
 */
public class TriangleRender implements GLSurfaceView.Renderer {
    //gl语言，顶点程序
    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "void main() {" +
                    " gl_Position = vPosition;" +
                    "}";

    //片元程序
    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    " gl_FragColor = vColor;" +
                    "}";


    //三角形3个顶点数据
    float[] triangle = {
            0.5f, 0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
            0.5f, -0.5f, 0.0f,
    };

    //颜色数据 红色
    float[] color = {0.6f, 0f, 0f, 1.0f};

    private FloatBuffer floatBuffer;
    private int mProgram;


    /**
     * 初始化操作，在渲染前
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        //清空之前的数据 类似caves.restore
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        //将cpu数据传递给gpu;*4是因为gpu接收是以4个字节为单位
        ByteBuffer buffer = ByteBuffer.allocateDirect(triangle.length * 4);
        //gpu整理内存
        buffer.order(ByteOrder.nativeOrder());
        //将GPU返回的数据存入FloatBuffer,FloatBuffer是cpu和gpu之间传递信息的载体
        floatBuffer = buffer.asFloatBuffer();

        floatBuffer.put(triangle);
        floatBuffer.position(0);

        //创建一个顶点程序
        int shader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        //将源码传递给gpu
        GLES20.glShaderSource(shader, vertexShaderCode);
        //编译
        GLES20.glCompileShader(shader);


        //创建一个片元程序
        int colorShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        //将源码传递给gpu
        GLES20.glShaderSource(colorShader, fragmentShaderCode);
        //编译
        GLES20.glCompileShader(colorShader);

        //将我们创建得程序添加到GL中才能进行链接编译等后续操作
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, shader);
        GLES20.glAttachShader(mProgram, colorShader);
        //链接程序，生成可执行得程序
        GLES20.glLinkProgram(mProgram);
    }

    //宽高改变，手机横屏
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    /*
     * 类似与onDraw
     * 使用我们生成得GPU程序
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glUseProgram(mProgram);
        //找到顶点程序里得gl_Position，填充数据
        int mPositionHandler = GLES20.glGetAttribLocation(mProgram, "vPosition");
        //允许 cpu 往gpu里写数据
        GLES20.glEnableVertexAttribArray(mPositionHandler);
        //写数据 参数1，引用值地址 参数2 顶点个数 参数3 类型 参数4 一般false 参数5 顶点的数据量*4（float 1个字节；*4是转成gpu用的4个字节）
        //参数6 真实的数据；onCreate中我们的顶点数据已经放入了Buffer
        GLES20.glVertexAttribPointer(mPositionHandler, 3, GLES20.GL_FLOAT, false, 3 * 4, floatBuffer);

        //通知gpu渲染,往片元着色器里添加数据
        int mColorHandler = GLES20.glGetUniformLocation(mProgram, "vColor");
        //设置颜色 参数1 地址值 参数2 颜色数量 参数3 颜色数据 参数4 偏移量
        GLES20.glUniform4fv(mColorHandler, 1, color, 0);

        //绘制三角形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);

        //禁止顶点数组句柄使用
        GLES20.glDisableVertexAttribArray(mPositionHandler);
    }
}
