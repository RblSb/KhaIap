#pragma once
#include <Kore/Android.h>
#include <Kore/Log.h>
#include <hx/CFFI.h>
#include <hxcpp.h>

namespace Billing {
	JNIEnv* attachThread();
	void detachThread();
	void init(void (*callback)(), void (*error)(), void (*purchase)(int status, String data));
	void setCallbacks(void (*callback)(), void (*error)(), void (*purchase)(int status, String data));
	void getProducts(String hxids, void (*callback)(int status, String data));
	void purchase(String hxid);
	void consume(String hxid, void (*callback)(int status, String data));

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onInitComplete(JNIEnv* env, jobject jCaller);
	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onInitError(JNIEnv* env, jobject jCaller);
	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onGetProducts(JNIEnv* env, jobject jCaller, jint status, jstring jdata);
	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onPurchase(JNIEnv* env, jobject jCaller, jint status, jstring jdata);
	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onConsume(JNIEnv* env, jobject jCaller, jint status, jstring jdata);

	// References
	// https://github.com/openfl/openfl-native/issues/216
	// https://github.com/haxenme/nme/blob/master/project/src/android/AndroidCommon.h#L19
	// https://github.com/haxenme/nme/blob/master/project/src/android/AndroidFrame.cpp#L754
	struct AutoHaxe {
		int base;
		const char *message;
		AutoHaxe(const char *inMessage) {
			// HX_TOP_OF_STACK
			base = 0;
			message = inMessage;
			gc_set_top_of_stack(&base, true);
			// Kore::log(Kore::Info, "Enter %s %p", message, pthread_self());
		}
		~AutoHaxe() {
			// Kore::log(Kore::Info, "Leave %s %p", message, pthread_self());
			gc_set_top_of_stack(0, true);
		}
	};
}
