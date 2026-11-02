#include "recognition.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <memory>
#include <string>
#include <stdexcept>

struct Handle {
 void* map=MAP_FAILED;size_t bytes=0;std::unique_ptr<tangowiz::Engine> engine;
 ~Handle(){engine.reset();if(map!=MAP_FAILED)munmap(map,bytes);}
};
static thread_local std::string result,error;
extern "C" {
const char* phone_error(){return error.c_str();}
void* phone_open(const char*path){try{
 int fd=open(path,O_RDONLY);struct stat st{};
 if(fd<0||fstat(fd,&st)){if(fd>=0)close(fd);throw std::runtime_error("Catalog unavailable");}
 auto h=std::make_unique<Handle>();h->bytes=st.st_size;h->map=mmap(nullptr,h->bytes,PROT_READ,MAP_PRIVATE,fd,0);close(fd);
 if(h->map==MAP_FAILED)throw std::runtime_error("Catalog mapping failed");
 h->engine=std::make_unique<tangowiz::Engine>((const uint8_t*)h->map,h->bytes);return h.release();
}catch(const std::exception&e){error=e.what();return nullptr;}}
void phone_close(void*h){delete (Handle*)h;}
void phone_reset(void*h){((Handle*)h)->engine->reset();}
const char* phone_search(void*h,const float*fp,float speed,float offset){try{
 result=((Handle*)h)->engine->search(fp,1,speed,offset);return result.c_str();
}catch(const std::exception&e){error=e.what();return nullptr;}}
double phone_recent(void*h,int n){return ((Handle*)h)->engine->recent(n);}
#ifdef TANGO_RECOVERY_API
const char* phone_recent_result(void*h,int n){try{
 result=((Handle*)h)->engine->recentResult(n);return result.c_str();
}catch(const std::exception&e){error=e.what();return nullptr;}}
int phone_retain_recent(void*h,int n){try{
 ((Handle*)h)->engine->retainRecent(n);return 1;
}catch(const std::exception&e){error=e.what();return 0;}}
#endif
}
