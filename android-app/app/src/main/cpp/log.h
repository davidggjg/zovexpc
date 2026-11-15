#pragma once
#include <android/log.h>

#define ZLOG_TAG "ZovexNative"
#define ZLOGI(...) __android_log_print(ANDROID_LOG_INFO,  ZLOG_TAG, __VA_ARGS__)
#define ZLOGW(...) __android_log_print(ANDROID_LOG_WARN,  ZLOG_TAG, __VA_ARGS__)
#define ZLOGE(...) __android_log_print(ANDROID_LOG_ERROR, ZLOG_TAG, __VA_ARGS__)
