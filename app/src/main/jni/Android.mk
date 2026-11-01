LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := coarafast
LOCAL_SRC_FILES := coara_fast.cpp
LOCAL_CPPFLAGS := -std=c++17 -O2 -fno-exceptions -fno-rtti -ffp-contract=off -fvisibility=hidden -Wall -Werror
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDLIBS :=
include $(BUILD_SHARED_LIBRARY)
