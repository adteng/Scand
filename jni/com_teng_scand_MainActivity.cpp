#include "com_teng_scand_MainActivity.h"
#include "MarkingImg.h"

JavaVM *g_jvm;
jobject g_obj;
bool myAttachCurrentThread(void** env );
void ShowData(const unsigned char *pData,int iDataLen,int iWidth,int iHeight);
void ShowMsg(const unsigned char *pData,int iDataLen);

JNIEXPORT void JNICALL Java_com_teng_scand_MainActivity_setJNIEnv
  (JNIEnv *env, jobject obj,jstring strDir)
{
        env->GetJavaVM(&g_jvm);
        setShowImgFun(&ShowData);
        setShowMsgFun(&ShowMsg);
        g_obj = env->NewGlobalRef(obj);
        const char *dir = env->GetStringUTFChars(strDir, 0);
        //loadfile(dir);
        env->ReleaseStringUTFChars(strDir, dir); 
}

JNIEXPORT jstring JNICALL Java_com_teng_scand_MainActivity_scandFrame
  (JNIEnv * env, jobject obj, jint width, jint height, jbyteArray yuv)
{
	jbyte* _yuv =env->GetByteArrayElements(yuv,0);
   	//const char *dir = env->GetStringUTFChars(strDir, 0);
   	 //g_obj = env->NewGlobalRef(obj);
    string strWord = MarkingImg(width,height,(uchar *)_yuv,"0");
    LOGI("word:%s",strWord.c_str());
   	env->ReleaseByteArrayElements(yuv,_yuv,JNI_FALSE);
   	//env->ReleaseStringUTFChars(strDir, dir); 
   	//env->DeleteGlobalRef(g_obj);
   	jstring str = env->NewStringUTF(strWord.c_str());
   	return str;
}

bool myAttachCurrentThread(void** env )
{
    int status = 0;
    status = g_jvm->GetEnv(env,JNI_VERSION_1_4);
    if(status<0)
    {
        g_jvm->AttachCurrentThread((JNIEnv**)env, NULL);
        return true;
    }
    return false;
}

void ShowData(const unsigned char *pData,int iDataLen,int iWidth,int iHeight)
{
//      LOGI("helloooo");
        JNIEnv *env;
        /*
        if(g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK)
        {
                LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
                return;
        }*/
        bool bAttach = myAttachCurrentThread((void**)&env);
        //jbyteArray b = env->NewByteArray(iDataLen);
        //env->SetByteArrayRegion(b,0,iDataLen,(jbyte *)pData);
       	jintArray carr = env->NewIntArray(iDataLen);
       	//jint *pColor = (jint *)calloc(iDataLen, sizeof(jint));
       	jint *pColor = env->GetIntArrayElements(carr, NULL);
        int k = 0;
       	for(int i = 0; i< iWidth * iHeight; i++)
        {
			pColor[i] = ( (uint32_t)pData[k] | (uint32_t)(pData[k+1] << 8) | (uint32_t)(pData[k+2]<<16) | (uint32_t)(255 << 24) );
			k+=iDataLen/(iWidth * iHeight);
		}
        //env->SetIntArrayRegion(carr,0,iDataLen,pColor);
        jclass cls = env->GetObjectClass(g_obj);
        jmethodID mDraw = env->GetMethodID(cls,"drawImage","([III)V");
        env->CallVoidMethod(g_obj,mDraw,carr,iWidth,iHeight);
        env->DeleteLocalRef(cls);
        env->ReleaseIntArrayElements(carr, pColor, 0);
        env->DeleteLocalRef(carr);
        //free(pColor);
          
        if(bAttach)
        if(g_jvm->DetachCurrentThread() != JNI_OK)
        {
                LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
        }
}

void ShowMsg(const unsigned char *pData,int iDataLen)
{
	JNIEnv *env;
	bool bAttach = myAttachCurrentThread((void**)&env);	
	jbyteArray b = env->NewByteArray(iDataLen);
    env->SetByteArrayRegion(b,0,iDataLen,(jbyte *)pData);
	jclass cls = env->GetObjectClass(g_obj);
	jmethodID mDraw = env->GetMethodID(cls,"showMsg","([BI)V");
 	env->CallVoidMethod(g_obj,mDraw,b,iDataLen);
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(b);
    if(bAttach)
        if(g_jvm->DetachCurrentThread() != JNI_OK)
        {
                LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
        }
}



