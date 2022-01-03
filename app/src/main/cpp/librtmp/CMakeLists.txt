#关闭ssl验证，不让rtmp进行加密验证
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO")

#将后缀名是.c结尾的代码放到 rtmp_source 中
file(GLOB rtmp_source *.c)

#将remp_source的代码 生成 rtmp 静态库
add_library(
        rtmp
        STATIC
        ${rtmp_source}
)