cmake_minimum_required(VERSION 3.10.2)
#添指向librtmp文件夹，编译该文件夹下的文件
add_subdirectory(librtmp)

message("----------------")
message(${CMAKE_BUILD_TYPE})
message(${ANDROID_ABI})
message(${CMAKE_SOURCE_DIR})
#导入x264头文件
include_directories(${CMAKE_SOURCE_DIR}/include/x264)
#导入faac头文件
include_directories(${CMAKE_SOURCE_DIR}/include/faac)
#导入ffmpeg头文件
include_directories(${CMAKE_SOURCE_DIR}/include/ffmpeg)
#导入x264静态库 、faac静态库、ffmpeg
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${CMAKE_SOURCE_DIR}/lib/${ANDROID_ABI}")

#导入当前的cpp
file(GLOB x264_soures *.cpp)
file(GLOB opse music/*.cpp)

add_library(
        native-lib
        SHARED
        ${x264_soures}
        ${opse})

find_library(
        log-lib
        log)

#添加动态链接库
target_link_libraries(
        native-lib
        rtmp
        x264
        faac
        avcodec
        avfilter
        avformat
        avutil
        swscale
        swresample
        android
        OpenSLES
        ${log-lib})