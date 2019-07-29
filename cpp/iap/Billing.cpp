#include "Billing.h"

namespace Billing {

	void (*onInitComplete)();
	void (*onInitError)();
	void (*onPurchase)(int status, String data);
	void (*onGetProducts)(int status, String data);
	void (*onConsume)(int status, String data);
	bool attached = false;

	JNIEnv* attachThread() {
		JNIEnv* env;
		ANativeActivity* kactivity = KoreAndroid::getActivity();
		KoreAndroid::getActivity()->vm->AttachCurrentThread(&env, NULL);
		attached = true;

		int getEnvStat = kactivity->vm->GetEnv((void**)&env, JNI_VERSION_1_4);
		if (getEnvStat == JNI_EDETACHED) {
			attached = true;
			if (kactivity->vm->AttachCurrentThread(&env, 0) != 0)
				throw std::runtime_error("Failed to attach");
			// pthread_setspecific (s_thread_key, &env);
		} else attached = false;

		return env;
	}

	void detachThread() {
		if (attached) KoreAndroid::getActivity()->vm->DetachCurrentThread();
	}

	void init(void (*complete)(), void (*error)(), void (*callback)(int status, String data)) {
		// Kore::log(Kore::Info, "iap init");
		onInitComplete = complete;
		onInitError = error;
		onPurchase = callback;
		JNIEnv* env = attachThread();

		jclass cls = KoreAndroid::findClass(env, "iap.Billing");
		jmethodID methodId = env->GetStaticMethodID(cls, "init", "()V");
		env->CallStaticVoidMethod(cls, methodId);
		detachThread();
	}

	void setCallbacks(void (*complete)(), void (*error)(), void (*callback)(int status, String data)) {
		onInitComplete = complete;
		onInitError = error;
		onPurchase = callback;
	}

	void getProducts(String hxids, void (*callback)(int status, String data)) {
		const char *ids = hxids.c_str();
		onGetProducts = callback;
		JNIEnv* env = attachThread();

		jclass cls = KoreAndroid::findClass(env, "iap.Billing");
		jstring jids = env->NewStringUTF(ids);
		jmethodID methodId = env->GetStaticMethodID(cls, "getProducts", "(Ljava/lang/String;)V");
		env->CallStaticVoidMethod(cls, methodId, jids);

		env->DeleteLocalRef(jids);
		detachThread();
	}

	void purchase(String hxid) {
		const char *id = hxid.c_str();
		JNIEnv* env = attachThread();

		jclass cls = KoreAndroid::findClass(env, "iap.Billing");
		jstring jid = env->NewStringUTF(id);
		jmethodID methodId = env->GetStaticMethodID(cls, "purchase", "(Ljava/lang/String;)V");
		env->CallStaticVoidMethod(cls, methodId, jid);

		env->DeleteLocalRef(jid);
		detachThread();
	}

	void consume(String hxid, void (*callback)(int status, String data)) {
		const char *id = hxid.c_str();
		onConsume = callback;
		JNIEnv* env = attachThread();

		jclass cls = KoreAndroid::findClass(env, "iap.Billing");
		jstring jid = env->NewStringUTF(id);
		jmethodID methodId = env->GetStaticMethodID(cls, "consume", "(Ljava/lang/String;)V");
		env->CallStaticVoidMethod(cls, methodId, jid);

		env->DeleteLocalRef(jid);
		detachThread();
	}

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onInitComplete(JNIEnv* env, jobject jCaller) {
		AutoHaxe haxe("Java_iap_Billing_onInitComplete");
		onInitComplete();
	}

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onInitError(JNIEnv* env, jobject jCaller) {
		AutoHaxe haxe("Java_iap_Billing_onInitError");
		onInitError();
	}

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onGetProducts(JNIEnv* env, jobject jCaller, jint status, jstring jdata) {
		const char *data = env->GetStringUTFChars(jdata, NULL);
		AutoHaxe haxe("Java_iap_Billing_onGetProducts");
		String hxdata = String(data);
		env->ReleaseStringUTFChars(jdata, data);
		onGetProducts((int)status, hxdata);
	}

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onPurchase(JNIEnv* env, jobject jCaller, jint status, jstring jdata) {
		const char *data = env->GetStringUTFChars(jdata, NULL);
		AutoHaxe haxe("Java_iap_Billing_onPurchase");
		String hxdata = String(data);
		env->ReleaseStringUTFChars(jdata, data);
		onPurchase((int)status, hxdata);
	}

	extern "C" JNIEXPORT
	void JNICALL Java_iap_Billing_onConsume(JNIEnv* env, jobject jCaller, jint status, jstring jdata) {
		const char *data = env->GetStringUTFChars(jdata, NULL);
		AutoHaxe haxe("Java_iap_Billing_onConsume");
		String hxdata = String(data);
		env->ReleaseStringUTFChars(jdata, data);
		onConsume((int)status, hxdata);
	}

}
