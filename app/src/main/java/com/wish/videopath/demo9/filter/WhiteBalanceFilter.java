package com.wish.videopath.demo9.filter;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.wish.videopath.R;
import com.wish.videopath.util.OpenGLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 动态滤镜控制
 */
public class WhiteBalanceFilter implements GLFilter {

    private int vPosition;
    private int vCoord;
    private int vTexture;
    private int vMatrix;
    private int vTemperature;
    private int vTint;
    private float[] mtx = new float[16];
    private int mWidth = 0;
    private int mHeight = 0;

    //cpu传递gpu数据的桥梁
    private FloatBuffer textureBuffer;
    private FloatBuffer vertexBuffer;

    private float temperature = 5000f;
    private float tint = 0.9f;

    //顶点坐标，贴满整个视图;原点在中间
    private float[] VERTEX = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    //纹理坐标;原点在左下角
    private float[] TEXTURE = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    private int program;

    /**
     * 坐标获取ByteBuffer --> 获取着色器 --> 为openGl代码中定义的变量赋值，得到句柄
     */
    public WhiteBalanceFilter(Context context) {
        //顶点着色器，生成数据传递桥梁，大小计算方式：坐标数 * 2（每个坐标两个点） * float字节数4
        vertexBuffer = ByteBuffer.allocateDirect(4 * 4 * 2)
                .order(ByteOrder.nativeOrder()) //内存重排序
                .asFloatBuffer();

        vertexBuffer.clear();
        vertexBuffer.put(VERTEX);

        //片元着色器，生成数据传递桥梁，大小计算方式：坐标数 * 2（每个坐标两个点） * float字节数4
        textureBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        textureBuffer.clear();
        textureBuffer.put(TEXTURE);

        //从raw中通过IO读取出代码，方便后续的 编译、链接、运行
        String vertexShader = OpenGLUtils.readRawTextFile(context, R.raw.camera_vert);
        String textureShader = OpenGLUtils.readRawTextFile(context, R.raw.white_blance_frag);

        //创建顶点着色器和片元着色器，program是gpu存储顶点和片元的索引
        program = OpenGLUtils.loadProgram(vertexShader, textureShader);
        //对应我们raw下camera_vert中定义的openGl变量，获取这些变量对应的句柄，用于我们的cpu逻辑操作
        //gpu中的顶点vPosition
        vPosition = GLES20.glGetAttribLocation(program, "vPosition");
        //纹理坐标
        vCoord = GLES20.glGetAttribLocation(program, "vCoord");
        //采样器
        vTexture = GLES20.glGetUniformLocation(program, "vTexture");
        //矩阵变换用于获取当前渲染的视图
        vMatrix = GLES20.glGetUniformLocation(program, "vMatrix");

        vTemperature = GLES20.glGetUniformLocation(program, "temperature");
        vTint = GLES20.glGetUniformLocation(program, "tint");
    }


    /**
     * java层Camera获取到数据后，已经通过setTransformMatrix将矩阵数据传递给openGl
     *
     * @param textureId
     * @return
     */
    @Override
    public int onDrawFrame(int textureId) {
        //设置窗口大小
        GLES20.glViewport(0, 0, mWidth, mHeight);
        // 2.使用着色器程序
        GLES20.glUseProgram(program);
        // 3.给着色器程序中传值
        // 3.1 给顶点坐标数据传值
        vertexBuffer.position(0);
        //将顶点数据从cpu传递给gpu
        GLES20.glVertexAttribPointer(vPosition,//理解为顶点坐标的索引
                2,//每个顶点的个数
                GLES20.GL_FLOAT,//类型，默认GL_FLOAT; GL_FLOAT GL_BYTE GL_SHORT....
                false,// 标志话和非标准化,设置默认false，true的话代表顶点坐标非-1到1的范围转换成-1到1
                0,//offset
                vertexBuffer);//顶点数据
        // 激活生效
        GLES20.glEnableVertexAttribArray(vPosition);

        // 3.2 给纹理坐标数据传值
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        // 3.3 变化矩阵传值；矩阵的数据来源是CameraX获取的数据
        GLES20.glUniformMatrix4fv(vMatrix, 1, false, mtx, 0);


        if (this.temperature < 5000) {
            GLES20.glUniform1f(vTemperature, (float) (0.0004 * (this.temperature - 5000.0)));
        } else {
            GLES20.glUniform1f(vTemperature, (float) (0.00006 * (this.temperature - 5000.0)));
        }

        GLES20.glUniform1f(vTint, this.tint / 100.0f);

        // 3.4 给片元着色器中的 采样器绑定
        // 激活图层
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        // 获取一个采样器，进行绑定，采样器里是我们camera获取到的数据
        GLES20.glBindTexture(GLES11Ext.GL_SAMPLER_EXTERNAL_OES, textureId);
        // 传递参数，0指第0个图层，因为我们目前只用了一个图层
        GLES20.glUniform1i(vTexture, 0);

        //参数传递完毕,通知 opengl开始画画，从0开始有 4个坐标
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 解绑
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return textureId;
    }

    @Override
    public void setTransformMatrix(float[] mtx) {
        this.mtx = mtx;
    }

    @Override
    public void onReady(int width, int height) {
        mWidth = width;
        mHeight = height;
    }


    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public void setTint(float tint) {
        this.tint = tint;
    }

    void release() {
        GLES20.glDeleteProgram(program);
    }
}
