#include "recognition.h"
#include "mel_frontend.h"
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cstdint>
#include <limits>
#include <unistd.h>
#include <memory>
#include <cmath>
#include <stdexcept>
struct Handle{void*map=MAP_FAILED;size_t mapped=0;std::unique_ptr<tangowiz::Engine> engine;~Handle(){engine.reset();if(map!=MAP_FAILED)munmap(map,mapped);}};
static void fail(JNIEnv*e,const char*s){if(!e->ExceptionCheck())e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),s);}
namespace {
struct FileDescriptor {
 int value;
 explicit FileDescriptor(int fd):value(fd){}
 ~FileDescriptor(){if(value>=0)close(value);}
 FileDescriptor(const FileDescriptor&)=delete;
 FileDescriptor& operator=(const FileDescriptor&)=delete;
};
struct JavaPath {
 JNIEnv* env;
 jstring value;
 const char* bytes;
 JavaPath(JNIEnv* e,jstring s):env(e),value(s),bytes(e->GetStringUTFChars(s,nullptr)){}
 ~JavaPath(){if(bytes)env->ReleaseStringUTFChars(value,bytes);}
};
// ResourceStore verifies SHA256 and atomically installs immutable resource bundles.
// This loader never modifies files; all validation precedes publication of a handle.
std::unique_ptr<Handle> openCatalogFile(const char* path){
 FileDescriptor fd(open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW|O_NONBLOCK));
 if(fd.value<0)throw std::runtime_error("Cannot open catalog resource file");
 struct stat info{};
 if(fstat(fd.value,&info)!=0||!S_ISREG(info.st_mode))throw std::runtime_error("Catalog resource must be a regular file");
 constexpr uint64_t maxCatalogBytes=8ULL*1024*1024*1024;
 if(info.st_size<4096||static_cast<uint64_t>(info.st_size)>maxCatalogBytes||static_cast<uint64_t>(info.st_size)>std::numeric_limits<size_t>::max())throw std::runtime_error("Catalog resource file size is invalid");
 auto h=std::make_unique<Handle>();
 h->mapped=static_cast<size_t>(info.st_size);
 h->map=mmap(nullptr,h->mapped,PROT_READ,MAP_PRIVATE,fd.value,0);
 if(h->map==MAP_FAILED)throw std::runtime_error("Cannot memory-map catalog resource file");
 h->engine=std::make_unique<tangowiz::Engine>(static_cast<const uint8_t*>(h->map),h->mapped);
 return h;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_org_tangowiz_app_NativeEngine_initFile(JNIEnv*e,jobject,jstring path){
 try{
  if(!path)throw std::runtime_error("Catalog resource path is required");
  JavaPath chars(e,path);
  if(!chars.bytes)return 0; // GetStringUTFChars has already raised an exception.
  if(!*chars.bytes)throw std::runtime_error("Catalog resource path is empty");
  return reinterpret_cast<jlong>(openCatalogFile(chars.bytes).release());
 }catch(const std::exception&x){fail(e,x.what());return 0;}
}

extern "C" JNIEXPORT jlong JNICALL Java_org_tangowiz_app_NativeEngine_init(JNIEnv*e,jobject,jobject manager){try{auto h=std::make_unique<Handle>();AAssetManager*m=AAssetManager_fromJava(e,manager);AAsset*a=AAssetManager_open(m,"catalog.bin",AASSET_MODE_RANDOM);if(!a)throw std::runtime_error("Bundled catalog missing");off64_t start,length;int fd=AAsset_openFileDescriptor64(a,&start,&length);AAsset_close(a);if(fd<0)throw std::runtime_error("Catalog must be packaged uncompressed");long page=sysconf(_SC_PAGESIZE);off64_t aligned=start/page*page,delta=start-aligned;h->mapped=length+delta;h->map=mmap(nullptr,h->mapped,PROT_READ,MAP_PRIVATE,fd,aligned);close(fd);if(h->map==MAP_FAILED)throw std::runtime_error("Cannot memory-map catalog");h->engine=std::make_unique<tangowiz::Engine>((uint8_t*)h->map+delta,length);return reinterpret_cast<jlong>(h.release());}catch(const std::exception&x){fail(e,x.what());return 0;}}
extern "C" JNIEXPORT jstring JNICALL Java_org_tangowiz_app_NativeEngine_search(JNIEnv*e,jobject,jlong handle,jfloatArray input,jint count,jfloat speed,jfloat offset){try{if(!handle||count<1||count>64||e->GetArrayLength(input)!=count*128)throw std::runtime_error("Invalid native search arguments");std::vector<float> values(count*128);e->GetFloatArrayRegion(input,0,values.size(),values.data());std::string result=((Handle*)handle)->engine->search(values.data(),count,speed,offset);auto bytes=e->NewByteArray(result.size());e->SetByteArrayRegion(bytes,0,result.size(),reinterpret_cast<const jbyte*>(result.data()));auto cls=e->FindClass("java/lang/String");auto ctor=e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");auto encoding=e->NewStringUTF("UTF-8");auto text=(jstring)e->NewObject(cls,ctor,bytes,encoding);e->DeleteLocalRef(bytes);e->DeleteLocalRef(encoding);return text;}catch(const std::exception&x){fail(e,x.what());return nullptr;}}
extern "C" JNIEXPORT void JNICALL Java_org_tangowiz_app_NativeEngine_reset(JNIEnv*,jobject,jlong handle){if(handle)((Handle*)handle)->engine->reset();}
extern "C" JNIEXPORT void JNICALL Java_org_tangowiz_app_NativeEngine_close(JNIEnv*,jobject,jlong handle){delete (Handle*)handle;}
extern "C" JNIEXPORT jfloatArray JNICALL Java_org_tangowiz_app_NativeEngine_features(JNIEnv*e,jobject,jfloatArray pcm){try{if(e->GetArrayLength(pcm)!=8000)throw std::runtime_error("Frontend requires8000samples");float in[8000],out[8448];e->GetFloatArrayRegion(pcm,0,8000,in);tangowiz::computeMel(in,out);auto a=e->NewFloatArray(8448);e->SetFloatArrayRegion(a,0,8448,out);return a;}catch(const std::exception&x){fail(e,x.what());return nullptr;}}
extern "C" JNIEXPORT jfloatArray JNICALL Java_org_tangowiz_app_NativeEngine_resample(JNIEnv*e,jobject,jfloatArray pcm,jdouble start,jdouble speed){try{int n=e->GetArrayLength(pcm);if(n<8000||n>4800000||!std::isfinite(start)||!std::isfinite(speed)||speed<.5||speed>2||start<0||start+7999*speed>=n)throw std::runtime_error("Invalid resampling window");std::vector<float> in(n);float out[8000];e->GetFloatArrayRegion(pcm,0,n,in.data());tangowiz::resampleWindow(in.data(),in.size(),start,speed,out);auto a=e->NewFloatArray(8000);e->SetFloatArrayRegion(a,0,8000,out);return a;}catch(const std::exception&x){fail(e,x.what());return nullptr;}}

extern "C" JNIEXPORT jdouble JNICALL Java_org_tangowiz_app_NativeEngine_recent(JNIEnv*e,jobject,jlong handle,jint count){try{if(!handle)throw std::runtime_error("Invalid engine handle");return ((Handle*)handle)->engine->recent(count);}catch(const std::exception&x){fail(e,x.what());return 0;}}
