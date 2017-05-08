/*
 * Copyright 2014 Pallavi Maiya and Aditya Kanade, Indian Institute of Science
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* This is a native library to log and process information obtained by
 * Android instrumentation added by DroidRacer. 
 *
 */

#include "abc.h"

struct AbcGlobals* gAbc;


// This maps the threadid to its corresponding stackentries.
// Lifetime: During Trace generation. Should be completely freed by 
// analysis time.
std::map<int, AbcMethodStack*> abcThreadStackMap;

// This maps the threadid to its corresponding entry app method in stack.
// Lifetime: During trace generation. Should be completely freed by 
// analysis time.
std::map<int, AbcMethod*> abcThreadBaseMethodMap;

// required during trace-generation
std::map<int, AbcCurAsync*> abcThreadCurAsyncMap;

// maps a thread-id to caller objects.
// needed only during trace generation.
// This is required to track java library calls.
std::map<int, AbcMethObj*> abcLibCallerObjectMap;

// Required only during trace generation.
// There will be as many as the number of unique messages.
std::map<u4, AbcMsg*> abcUniqueMsgMap;
//std::map<u4, int> abcUniqueMsgMap;
std::map<u4, int> abcQueueToThreadMap;

// Cleanup  this strcuture completely.
std::map<int, AbcThreadIds*> abcLogicalThreadIdMap;

// Cleanup  this strcuture completely.
std::map<std::string, AbcDbAccess*> abcDbAccessMap;

// Cleanup  this strcuture completely.
//std::set<int> abcThreadIdSet;



// maps a thread-id to some meta-data related to the thread.
// Lifetime: Required during analysis.
// Remove all the fields in this except the first two.
// Not many objects in an app. Just as many as the number of createc
// threads.
std::map<int, AbcThread*> abcThreadMap;

// maps a global integer number generation to the sequence of
// operations. This is the main program trace. This is required
// throughout the program analysis. 
std::map<int, AbcOp*> abcTrace;

// Required throughout analysis
// There will be as many as the number of messages.
std::map<int, AbcAsync*> abcAsyncMap;

// Required throughout analysis
// There will be as many as the number of read-writes.
std::map<int, AbcRWAccess*> abcRWAccesses;

// Required throughout analysis
// There will be as many as the number of read-write sets.
// Each one inturn points to a list of integers which indicate a read-write access index
// generated in abcRWAccesses.
std::map<int, std::pair<int, std::list<int> > > abcRWAbstractionMap;


// required throughout
std::map<std::pair<Object*, u4>, std::pair<std::set<int>, std::set<int> > > abcObjectAccessMap;
std::map<std::string, std::pair<std::set<int>, std::set<int> > > abcDatabaseAccessMap;
std::map<std::pair<const char*, u4>, std::pair<std::set<int>, std::set<int> > > abcStaticAccessMap;


//needed only during trace generation to track async-blocks to be deleted.
//clean this up before graph closure
std::map<int, std::pair<bool,bool> > abcAsyncStateMap;

//tracks next access set id for read-write operations performed by thread.
//needed only during trace generation 
std::map<int, int> abcThreadAccessSetMap;

//tracks lock-unlock performed on an object. 
//gets filled during analysis stage
std::map<Object*, AbcLock*> abcLockMap;

//used to track nested synchronize locks on same object.
//needed only during trace generation
std::map<Object*, AbcLockCount*> abcLockCountMap;

//needed only during trace generation
std::map<u4, std::set<int> > abcViewEventMap;

//a map from asyncId to its corresponding enable event id if the async block is due to it
std::map<int, int> abcAsyncEnableMap;



//initialize during startup
std::set<std::string> UiWidgetSet;

int abcNativeTid = -1;
int abcThreadCount = 1;
int abcMsgCount = 1;
int abcOpCount = 1;
int abcAccessSetCount = 1;
int abcRWCount = 1;
int abcEventCount = 0;
int abcTraceLengthLimit = -1;

int abcStartOpId;
bool ** adjGraph;
WorklistElem* worklist = NULL; //a list 
std::map<int, std::pair<Destination*, Source*> > adjMap; 

//some fields for race detection related statistics collection
int abcForkCount = 1; 
int abcMQCount = 0; //since attach-q of main thread happens after ABC starts it gets logged
int abcEventTriggerCount = 0;

std::set<u4> eventTriggeringTasks;
std::set<int> nativeThreadSet;
std::list<int> nativeThreadIdListForPOR;
std::map<int, OpInfo*> porTmpTrace;
std::map<int, OpInfo*> porTrace;
std::list<std::pair<int, int> > porHBList;
std::map<u4, AbcOp*> msgCallOpMap;
std::map<Object*, AbcOp*> porLockUnlockMap;
std::set<u4> messagesPostedByNativeOrUiThreads;
std::map<int, int> nativeOrUiPostToNopMap;

    

void cleanupBeforeExit(){
    //free abcThreadMap
    if(abcThreadMap.size() > 0){
        std::map<int, AbcThread*>::iterator delIter = abcThreadMap.begin();
        while(delIter != abcThreadMap.end()){
            AbcThread* ptr = delIter->second;
            abcThreadMap.erase(delIter++);
            free(ptr);
        }    
    }

    //free abstraction map
    if(abcRWAbstractionMap.size() > 0){
        std::map<int, std::pair<int, std::list<int> > >::iterator delIter = abcRWAbstractionMap.begin();
        while(delIter != abcRWAbstractionMap.end()){
            delIter->second.second.clear();
            abcRWAbstractionMap.erase(delIter++);
        }
    }

    //free object-database-static access map
    if(abcObjectAccessMap.size() > 0){
        std::map<std::pair<Object*, u4>, std::pair<std::set<int>, std::set<int> > >::iterator delIter = abcObjectAccessMap.begin();
        while(delIter != abcObjectAccessMap.end()){
            delIter->second.first.clear();
            delIter->second.second.clear();
            abcObjectAccessMap.erase(delIter++);
        }
    }

    if(abcDatabaseAccessMap.size() > 0){
        std::map<std::string, std::pair<std::set<int>, std::set<int> > >::iterator delIter = abcDatabaseAccessMap.begin();
        while(delIter != abcDatabaseAccessMap.end()){
            delIter->second.first.clear();
            delIter->second.second.clear();
            abcDatabaseAccessMap.erase(delIter++);
        }
    }

    if(abcStaticAccessMap.size() > 0){
        std::map<std::pair<const char*, u4>, std::pair<std::set<int>, std::set<int> > >::iterator delIter = abcStaticAccessMap.begin();
        while(delIter != abcStaticAccessMap.end()){
            delIter->second.first.clear();
            delIter->second.second.clear();
            abcStaticAccessMap.erase(delIter++);
        }
    }

    //free read writes
    if(abcRWAccesses.size() > 0){
        std::map<int, AbcRWAccess*>::iterator delIter = abcRWAccesses.begin();
        while(delIter != abcRWAccesses.end()){
            AbcRWAccess* ptr = delIter->second;
            abcRWAccesses.erase(delIter++);
            free(ptr->dbPath);
            free(ptr->field);
            free(ptr);
        }
    }


    //free abcLockMap
    if(abcLockMap.size() > 0){
        std::map<Object*, AbcLock*>::iterator delIter = abcLockMap.begin();
        while(delIter != abcLockMap.end()){
            AbcLock* ptr = delIter->second;
            abcLockMap.erase(delIter++);

            std::map<int, UnlockList*>::iterator iter = ptr->unlockMap.begin();
            while(iter != ptr->unlockMap.end()){
                UnlockList* lockPtr = iter->second;
                ptr->unlockMap.erase(iter++);
                free(lockPtr);
            }            
            
            free(ptr);
        }
    }    

    //clearing async map
    if(abcAsyncMap.size() > 0){
        std::map<int, AbcAsync*>::iterator delIter = abcAsyncMap.begin();
        while(delIter != abcAsyncMap.end()){
            AbcAsync* ptr = delIter->second;
            abcAsyncMap.erase(delIter++);
            free(ptr);
        }
    }

    //clearing abcTrace
    if(abcTrace.size() > 0){
        std::map<int, AbcOp*>::iterator delIter = abcTrace.begin();
        while(delIter != abcTrace.end()){
            AbcOp* ptr = delIter->second;
            abcTrace.erase(delIter++);
            free(ptr->arg2);
            free(ptr->arg5);
            free(ptr);
        }
    }

}

void abcAddLockOpToTrace(Thread* self, Object* obj){
    /*lock/unlock should be tracked even when shouldABCTrack = false
     *and a app method based thread stack exists for a thread. 
     *In such tracking when shouldABCTrack = false, we only record
     *those lock/unlocks which have any non-lock or non-unlock operation
     *in between.
     */
    if(self->shouldABCTrack == true){
    //    if(abcThreadCurAsyncMap.find(self->threadId)->second->shouldRemove == false){
            abcLockMutex(self, &gAbc->abcMainMutex);
            if(gDvm.isRunABC == true){
                bool addToTrace = checkAndIncrementLockCount(obj, self->abcThreadId);
                if(addToTrace){
                    AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(self->abcThreadId)->second;
                    if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                        addLockToTrace(abcOpCount++, self->abcThreadId, obj);
                    }else{
                        LOGE("ABC-DONT-LOG: found a lock operation in deleted async block. not logging it");
                    }
                }
            }
            abcUnlockMutex(&gAbc->abcMainMutex);
    /*    }else{
            LOGE("Trace has a LOCK for a async block forced to be deleted which is not addressed by "
                " implementation. Cannot continue further");
            gDvm.isRunABC = false;
            return;
        } */

    }else if(abcLibCallerObjectMap.find(self->threadId)
                    != abcLibCallerObjectMap.end()){
        if(isObjectInThreadAccessMap(self->threadId,obj)){
            abcLockMutex(self, &gAbc->abcMainMutex);
            if(gDvm.isRunABC == true){
                bool addToTrace = checkAndIncrementLockCount(obj, self->abcThreadId);
                if(addToTrace){
                    AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(self->abcThreadId)->second;
                    if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                        addLockToTrace(abcOpCount++, self->abcThreadId, obj);
                    }else{
                        LOGE("ABC-DONT-LOG: found a lock operation in deleted async block. not logging it");
                    }
                }
            }
            abcUnlockMutex(&gAbc->abcMainMutex);
        }
    }
}

void abcAddUnlockOpToTrace(Thread* self, Object* obj){
    if(self->shouldABCTrack == true){
            abcLockMutex(self, &gAbc->abcMainMutex);
            if(gDvm.isRunABC == true){
                bool addToTrace = checkAndDecrementLockCount(obj, self->abcThreadId);
                if(addToTrace){
                    AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(self->abcThreadId)->second;
                    if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                        addUnlockToTrace(abcOpCount++, self->abcThreadId, obj);
                    }else{
                        LOGE("ABC-DONT-LOG: found a unlock operation in deleted async block. not logging it");
                    }
                }
            }
            abcUnlockMutex(&gAbc->abcMainMutex);

    }else if(abcLibCallerObjectMap.find(self->threadId)
                    != abcLibCallerObjectMap.end()){
        if(isObjectInThreadAccessMap(self->threadId,obj)){
            abcLockMutex(self, &gAbc->abcMainMutex);
            if(gDvm.isRunABC == true){
                bool addToTrace = checkAndDecrementLockCount(obj, self->abcThreadId);
                if(addToTrace){
                    AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(self->abcThreadId)->second;
                    if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                        addUnlockToTrace(abcOpCount++, self->abcThreadId, obj);
                    }else{
                        LOGE("ABC-DONT-LOG: found a unlock operation in deleted async block. not logging it");
                    }
                }
            }
            abcUnlockMutex(&gAbc->abcMainMutex);
        }
    }
}

void abcAddCallerObjectForLibMethod(int abcTid, const Method* meth, Object* obj){
    if(strcmp(meth->name,"<clinit>") != 0 && strcmp(meth->name,"<init>") != 0 && 
          strcmp(meth->clazz->descriptor,
                "Ljava/lang/ClassLoader;") != 0 && strcmp(meth->clazz->descriptor,
                    "Ldalvik/system/PathClassLoader;") != 0 && strcmp(
                     meth->clazz->descriptor,"Ljava/lang/BootClassLoader") != 0){
        std::map<int,AbcMethObj*>::iterator it = abcLibCallerObjectMap.find(abcTid);
        AbcMethObj* mo = (AbcMethObj*)malloc(sizeof(AbcMethObj));
        mo->method = meth;
        mo->obj = obj;
        mo->prev = NULL;
        if(it != abcLibCallerObjectMap.end()){
            mo->prev = it->second;
            it->second = mo;
        }else{
            abcLibCallerObjectMap.insert(std::make_pair(abcTid,mo));
        }
   }
}

void abcRemoveCallerObjectForLibMethod(int abcTid, const Method* meth){
    std::map<int,AbcMethObj*>::iterator it = abcLibCallerObjectMap.find(abcTid);
    if(strcmp(meth->name,"<clinit>") != 0 && strcmp(meth->name,"<init>") != 0 &&
          strcmp(meth->clazz->descriptor,
                "Ljava/lang/ClassLoader;") != 0 && strcmp(meth->clazz->descriptor,
                    "Ldalvik/system/PathClassLoader;") != 0 && strcmp(
                     meth->clazz->descriptor,"Ljava/lang/BootClassLoader") != 0 && 
                     it != abcLibCallerObjectMap.end()){
        if(it->second != NULL && it->second->method == meth){ 
            if(it->second->prev != NULL){
                AbcMethObj* tmpPtr = it->second;
                it->second = it->second->prev;
                free(tmpPtr);
            }else{
                AbcMethObj* tmpPtr = it->second;
                abcLibCallerObjectMap.erase(abcTid);
                free(tmpPtr);
            }
        }else{
         //   LOGE("ABC-MISSING: caller object for method %s has been suspiciously removed for thread %d", meth->name, abcTid);
            abcLibCallerObjectMap.erase(abcTid);
        }
    }
}

bool isObjectInThreadAccessMap(int abcThreadId, Object* obj){
    std::map<int, AbcMethObj*>::iterator it;
    bool retVal = false;
    AbcMethObj* tmp;
    if((it = abcLibCallerObjectMap.find(abcThreadId)) != abcLibCallerObjectMap.end()){
        tmp = it->second;
        while(tmp != NULL){
            if(tmp->obj == obj){
                retVal = true;
                break;
            }else{
                tmp = tmp->prev;
            }                
        }
    }
    return retVal;
}

int abcGetRecursiveLockCount(Object* lockObj){
    std::map<Object*, AbcLockCount*>::iterator it = abcLockCountMap.find(lockObj);
    if(it == abcLockCountMap.end()){
        return 0;
    }else{
        return it->second->count;
    }
}

bool checkAndIncrementLockCount(Object* lockObj, int threadId){
    bool addToTrace = false;
    std::map<Object*, AbcLockCount*>::iterator it = abcLockCountMap.find(lockObj);
    if(it == abcLockCountMap.end()){
        AbcLockCount* alc = (AbcLockCount*)malloc(sizeof(AbcLockCount));
        alc->threadId = threadId;
        alc->count = 1;
        abcLockCountMap.insert(std::make_pair(lockObj, alc));
        addToTrace = true;
    }else{
        if(it->second->threadId == threadId){
            it->second->count++;
        }else{
            LOGE("ABC: A thread %d is taking a lock %p when another thread %d holds the lock."
                 " The trace seems to have missed an unlock. Aborting trace generation.", 
                 threadId, lockObj, it->second->threadId);
            stopAbcModelChecker();
        }
    }
    return addToTrace;
}

bool checkAndDecrementLockCount(Object* lockObj, int threadId){
    bool addToTrace = false;
    std::map<Object*, AbcLockCount*>::iterator it = abcLockCountMap.find(lockObj);
    if(it == abcLockCountMap.end()){
        LOGE("ABC: Unlock seen by trace without a prior lock. We might have missed a "
             "lock statement. Aborting trace generation. tid:%d  lock: %p", threadId, lockObj);
        stopAbcModelChecker();
    }else{
        if(it->second->threadId == threadId){
            it->second->count--;
            if(it->second->count == 0){
                addToTrace = true;
                abcLockCountMap.erase(lockObj);
            }
        }else{
            LOGE("ABC: A thread %d is performing an unlock when another thread holds the lock %p."
                 " The trace seems to have missed an unlock / lock. Aborting trace generation.", threadId, lockObj);
            stopAbcModelChecker();
        }
    }
    return addToTrace;
}

bool isThreadStackEmpty(int abcThreadId){
    bool retVal = true;
    std::map<int, AbcMethodStack*>::iterator it;
    if((it = abcThreadStackMap.find(abcThreadId)) !=  abcThreadStackMap.end()){
        if(it->second != NULL)
            retVal = false;
    }
    return retVal;
}

void abcPushMethodForThread(int threadId, const Method* method){
    std::map<int, AbcMethodStack*>::iterator it;
    AbcMethodStack* mStack = (AbcMethodStack *)malloc(sizeof(AbcMethodStack));
    mStack->method = method;
    mStack->prev = NULL;
    if((it = abcThreadStackMap.find(threadId)) !=  abcThreadStackMap.end()){
        mStack->prev = it->second;
        it->second = mStack;
    }else{
        abcThreadStackMap.insert(std::make_pair(threadId, mStack));
    }
}

void abcStoreBaseMethodForThread(int threadId, const Method* method) {
	if(abcThreadBaseMethodMap.find(threadId) == abcThreadBaseMethodMap.end()){
            /*allocate the struct on heap so that its accessible even after 
             *method exit */
            AbcMethod* am = (AbcMethod *)malloc(sizeof(AbcMethod));
            am->method = method;
            abcThreadBaseMethodMap.insert(std::make_pair(threadId, am));
        }else{
            LOGE("ABC: there is already a base method associated with the thread.");
        } 
}

/*returns NULL if no base-app-method for the specified thread id*/
const Method* abcGetBaseMethodForThread(int threadId){
    std::map<int, AbcMethod*>::iterator it;
    if((it = abcThreadBaseMethodMap.find(threadId)) == abcThreadBaseMethodMap.end()){
        return NULL;
    }else{
        return abcThreadBaseMethodMap.find(threadId)->second->method;
    }
}

void abcRemoveBaseMethodForThread(int threadId){
    std::map<int, AbcMethod*>::iterator it;
    if((it = abcThreadBaseMethodMap.find(threadId)) != abcThreadBaseMethodMap.end()){
        AbcMethod* tmpPtr = it->second;
        abcThreadBaseMethodMap.erase(it->first);
        free(tmpPtr);
    }else{
        LOGE("ABC: did not find the base method to be remove!");
    }
}

void abcRemoveThreadFromStackMap(int threadId){
    std::map<int, AbcMethodStack*>::iterator it;
    if((it = abcThreadStackMap.find(threadId)) !=  abcThreadStackMap.end()){
        AbcMethodStack* tmpPtr = it->second; 
        abcThreadStackMap.erase(it->first);
        free(tmpPtr);
    }
}

const Method* abcGetLastMethodInThreadStack(int threadId){
    std::map<int, AbcMethodStack*>::iterator it;
    if((it = abcThreadStackMap.find(threadId)) !=  abcThreadStackMap.end()){
        if(it->second == NULL)
            return NULL;
        else
            return it->second->method;
    }else{
        return NULL;
    }
}

/*erases the last method entry in stack and returns the method*/
const Method* abcPopLastMethodInThreadStack(int threadId){
    std::map<int, AbcMethodStack*>::iterator it;
    if((it = abcThreadStackMap.find(threadId)) !=  abcThreadStackMap.end()){
       const Method* meth = it->second->method;
       if(it->second->prev == NULL){
           AbcMethodStack* tmpPtr = it->second;
           abcThreadStackMap.erase(it->first);
           free(tmpPtr);
       }else{
           AbcMethodStack* tmpPtr = it->second;
           it->second = it->second->prev;
           free(tmpPtr); 
       }
       return meth;
    }else{
        return NULL;
    }
}

void abcAddThreadToMap(Thread* self, const char* name){
    AbcThread* abcThread =  (AbcThread *)malloc(sizeof(AbcThread));
    abcThread->threadId = self->threadId;
    abcThread->isOriginUntracked = false;

    /*initialize the conditional variable associated by ABC with a thread
    pthread_cond_init(&self->abcCond, NULL);
    abcThread->abcCond = &self->abcCond; */
    
    abcThreadMap.insert(std::make_pair(self->abcThreadId, abcThread));
}

void addThreadToCurAsyncMap(int threadId){
    AbcCurAsync* async = (AbcCurAsync*)malloc(sizeof(AbcCurAsync));
    async->asyncId = -1;
    async->shouldRemove = false;
    async->hasMQ = false;
    abcThreadCurAsyncMap.insert(std::make_pair(threadId, async));
}

void abcAddLogicalIdToMap(int threadId, int abcThreadId){
    std::map<int, AbcThreadIds*>::iterator it = abcLogicalThreadIdMap.find(threadId);  
    AbcThreadIds* ati = (AbcThreadIds *)malloc(sizeof(AbcThreadIds));
    ati->abcThreadId = abcThreadId;
    if(it != abcLogicalThreadIdMap.end()){
        ati->prevAbcId = it->second;
        it->second = ati;
    }else{
        ati->prevAbcId = NULL;
        abcLogicalThreadIdMap.insert(std::make_pair(threadId, ati));
    }
}

int abcGetAbcThreadId(int threadId){
    int abcId = -1;
    std::map<int, AbcThreadIds*>::iterator it = abcLogicalThreadIdMap.find(threadId);
    if(it != abcLogicalThreadIdMap.end()){
        abcId = it->second->abcThreadId;
    }
    return abcId;
}

void abcRemoveThreadFromLogicalIdMap(int threadId){
    std::map<int, AbcThreadIds*>::iterator it = abcLogicalThreadIdMap.find(threadId);
    if(it != abcLogicalThreadIdMap.end()){
        abcLogicalThreadIdMap.erase(it);
    }
}

bool abcIsThreadOriginUntracked(int abcThreadId){
    bool isOriginUntracked = true;
    std::map<int, AbcThread*>::iterator it = abcThreadMap.find(abcThreadId);
    if(it != abcThreadMap.end()){
        isOriginUntracked = it->second->isOriginUntracked;
    }
    return isOriginUntracked;
}

void abcAddDbAccessInfo(std::string dbPath, int trId, int accessType, int abcThreadId){
    
    std::map<std::string, AbcDbAccess*>::iterator it = abcDbAccessMap.find(dbPath);
    if(it == abcDbAccessMap.end()){
        AbcDbAccess* ada = (AbcDbAccess *)malloc(sizeof(AbcDbAccess));

        ada->dbAccessForThread = (AbcDbAccessType *)malloc(sizeof(AbcDbAccessType));
        ada->dbAccessForThread->accessType = accessType;
        ada->dbAccessForThread->transitionId = trId;
        ada->dbAccessForThread->prevAccess = NULL;
        
        ada->abcThreadId = abcThreadId;
        ada->nextThreadDb = NULL;
                
        abcDbAccessMap.insert(std::make_pair(dbPath, ada));
    }else{
        AbcDbAccess *tmpDba = it->second;
        while(tmpDba != NULL){
            if(tmpDba->abcThreadId == abcThreadId){
                AbcDbAccessType *adt = (AbcDbAccessType *)malloc(sizeof(AbcDbAccessType));
                adt->accessType = accessType;
                adt->transitionId = trId;
                adt->prevAccess = tmpDba->dbAccessForThread;
                tmpDba->dbAccessForThread = adt;
                
                break;
            }
            if(tmpDba->nextThreadDb == NULL){
                AbcDbAccess* ada = (AbcDbAccess *)malloc(sizeof(AbcDbAccess));
                ada->dbAccessForThread = (AbcDbAccessType *)malloc(sizeof(AbcDbAccessType));
                ada->dbAccessForThread->accessType = accessType;
                ada->dbAccessForThread->transitionId = trId;
                ada->dbAccessForThread->prevAccess = NULL;

                ada->abcThreadId = abcThreadId;
                ada->nextThreadDb = NULL;
                tmpDba->nextThreadDb = ada;
                break;
            }            
            tmpDba = tmpDba->nextThreadDb;
        }
    }
}

/*print stack trace associated with a thread*/
void abcPrintThreadStack(int threadId){
    std::map<int, AbcMethodStack*>::iterator it;
    if((it = abcThreadStackMap.find(threadId)) != abcThreadStackMap.end()){
        AbcMethodStack* ams = it->second;
        while(ams != NULL){
            LOGE("ABC: method in stack - %s", ams->method->name);
            ams = ams->prev; 
        }
     }
}

void abcLockMutex(Thread* self, pthread_mutex_t* pMutex){   

    ThreadStatus oldStatus;
    
    /*if (self == NULL)       // try to get it from TLS 
        self = dvmThreadSelf();

    if (self != NULL) {
        oldStatus = self->status;
        self->status = THREAD_VMWAIT;
    } else {
        // happens during VM shutdown 
        oldStatus = THREAD_UNDEFINED;  // shut up gcc
    }*/ 

    oldStatus = dvmChangeStatus(self,THREAD_VMWAIT);
    dvmLockMutex(pMutex); 
    dvmChangeStatus(self,oldStatus);
    
    /*if (self != NULL){
        self->status = oldStatus;
    }*/
    
}

void abcUnlockMutex(pthread_mutex_t* pMutex){
    dvmUnlockMutex(pMutex);
}

void addStartToTrace(int opId){
    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = ABC_START;
    op->tid = dvmThreadSelf()->abcThreadId;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/
    
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " START" << "\n";
    outfile.close(); 
}

void startAbcModelChecker(){
    gAbc = new AbcGlobals;
    pthread_cond_init(&gAbc->abcMainCond, NULL);
    dvmInitMutex(&gAbc->abcMainMutex);

    binaryLogFile = std::string("/data/data/") + gDvm.app_for_ABC
           + "/droidracer.blog";       

    binaryLogStringHelperFile = std::string("/data/data/") + gDvm.app_for_ABC
           + "/droidracerStringHelper.blog";

    std::string timeTickActionString("android.intent.action.TIME_TICK");
    argStringToNumKeyMap.insert(std::make_pair(timeTickActionString, abcStringKey++));


    abcStartOpId = abcOpCount;
    addStartToTrace(abcOpCount++);
    addThreadInitToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId); 

    addEnableLifecycleToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, "", 0, ABC_BIND);


    /* below initialization needed only for race detection
    //initialize UI widget class set
    UiWidgetSet.insert("Landroid/view/View;");
    UiWidgetSet.insert("Landroid/widget/TextView;");
    UiWidgetSet.insert("Landroid/widget/ImageView;");
    UiWidgetSet.insert("Landroid/widget/Button;");
    UiWidgetSet.insert("Landroid/widget/AutoCompleteTextView;");
    UiWidgetSet.insert("Landroid/widget/ToggleButton;");
    UiWidgetSet.insert("Landroid/widget/RadioButton;");
    UiWidgetSet.insert("Landroid/widget/EditText;");
    UiWidgetSet.insert("Landroid/webkit/WebView;");
    UiWidgetSet.insert("Landroid/webkit/WebTextView;");
    UiWidgetSet.insert("Landroid/widget/Chronometer;");
    UiWidgetSet.insert("Landroid/widget/Switch;");
    UiWidgetSet.insert("Landroid/widget/CheckBox;");
    UiWidgetSet.insert("Landroid/widget/CheckedTextView;");
    UiWidgetSet.insert("Landroid/widget/DigitalClock;");
    UiWidgetSet.insert("Landroid/widget/AnalogClock;");
    UiWidgetSet.insert("Landroid/widget/ExpandableListView;");
    UiWidgetSet.insert("Landroid/widget/ListView;");
    UiWidgetSet.insert("Landroid/widget/Spinner;");
    UiWidgetSet.insert("Landroid/widget/Gallery;");
    UiWidgetSet.insert("Landroid/widget/GridLayout;");
    UiWidgetSet.insert("Landroid/widget/GridView;");
    UiWidgetSet.insert("Landroid/widget/TableRow;");
    UiWidgetSet.insert("Landroid/widget/TableLayout;");
    UiWidgetSet.insert("Landroid/widget/FrameLayout;");
    UiWidgetSet.insert("Landroid/widget/LinearLayout;");
    UiWidgetSet.insert("Landroid/widget/RelativeLayout;");
    UiWidgetSet.insert("Landroid/widget/ImageButton;");
    UiWidgetSet.insert("Landroid/widget/ViewAnimator;");
    UiWidgetSet.insert("Landroid/widget/ViewFlipper;");
    UiWidgetSet.insert("Landroid/widget/MediaController;");
    UiWidgetSet.insert("Landroid/widget/NumberPicker;");
    UiWidgetSet.insert("Landroid/widget/CalendarView;");
    UiWidgetSet.insert("Landroid/widget/DatePicker;");
    UiWidgetSet.insert("Landroid/widget/TimePicker;");
    UiWidgetSet.insert("Landroid/widget/DateTimeView;");
    UiWidgetSet.insert("Landroid/widget/SeekBar;");
    UiWidgetSet.insert("Landroid/widget/RadioGroup;");
    UiWidgetSet.insert("Landroid/widget/RatingBar;");
    UiWidgetSet.insert("Landroid/widget/ScrollView;");
    UiWidgetSet.insert("Landroid/widget/SearchView;");
    UiWidgetSet.insert("Landroid/widget/TabHost;");
    UiWidgetSet.insert("Landroid/widget/TabWidget;");
    UiWidgetSet.insert("Landroid/widget/Toast;");
    */
}


std::string getLifecycleForCode(int code, std::string lifecycle){
    switch(code){
        case 1: lifecycle = "PAUSE-ACT" ; break;
        case 2: lifecycle = "RESUME-ACT" ; break;
        case 3: lifecycle = "LAUNCH-ACT" ; break;
        case 4: lifecycle = "BIND-APP" ; break;
        case 5: lifecycle = "RELAUNCH-ACT" ; break;
        case 6: lifecycle = "DESTROY-ACT" ; break;
        case 7: lifecycle = "CHANGE-CONFIG" ; break;
        case 8: lifecycle = "STOP-ACT" ; break;
        case 9: lifecycle = "RESULT-ACT" ; break;
        case 10: lifecycle = "CHANGE-ACT-CONFIG" ; break;
        case 11: lifecycle = "CREATE-SERVICE" ; break;
        case 12: lifecycle = "STOP-SERVICE" ; break;
        case 13: lifecycle = "BIND-SERVICE" ; break;
        case 14: lifecycle = "UNBIND-SERVICE" ; break;
        case 15: lifecycle = "SERVICE-ARGS" ; break;
        case 16: lifecycle = "BINDAPP-DONE"; break;
        case 17: lifecycle = "SERVICE_CONNECT"; break;
        case 18: lifecycle = "RUN_TIMER_TASK"; break;
        case 19: lifecycle = "REQUEST_START_SERVICE"; break;
        case 20: lifecycle = "REQUEST_BIND_SERVICE"; break;
        case 21: lifecycle = "REQUEST_STOP_SERVICE"; break;
        case 22: lifecycle = "REQUEST_UNBIND_SERVICE"; break;
        case 23: lifecycle = "START-ACT"; break;
        case 24: lifecycle = "NEW_INTENT"; break;
        case 25: lifecycle = "START_NEW_INTENT"; break;
        case 26: lifecycle = "ABC_REGISTER_RECEIVER";break;
        case 27: lifecycle = "ABC_SEND_BROADCAST";break;
        case 28: lifecycle = "ABC_SEND_STICKY_BROADCAST";break;
        case 29: lifecycle = "ABC_ONRECEIVE";break;
        case 30: lifecycle = "ABC_UNREGISTER_RECEIVER";break;
        case 31: lifecycle = "ABC_REMOVE_STICKY_BROADCAST";break;
        case 32: lifecycle = "ABC_TRIGGER_ONRECIEVE_LATER";break;
        default: lifecycle = "UNKNOWN";
    }
    
    return lifecycle;
}

bool addIntermediateReadWritesToTrace(int opId, int tid){
    bool accessSetAdded = false;
    std::map<int, int>::iterator accessIt = abcThreadAccessSetMap.find(tid);
    if(accessIt != abcThreadAccessSetMap.end()){
        addAccessToTrace(opId, tid, accessIt->second);
        abcThreadAccessSetMap.erase(accessIt);
        accessSetAdded = true;
    }

    return accessSetAdded;
}

void abcAddWaitOpToTrace(int opId, int tid, int waitingThreadId, bool timed){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add WAIT to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));

    if(timed){
        op->opType = ABC_TIMED_WAIT;
    }else{
        op->opType = ABC_WAIT;
    }
    op->arg1 = waitingThreadId;
    op->tid = tid;
    op->arg2 = NULL;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    if(timed){
        outfile << opId << " TIMED-WAIT tid:" << waitingThreadId <<"\n";
    }else{
        outfile << opId << " WAIT tid:" << waitingThreadId <<"\n";
    }
    outfile.close();

    if(timed){
        serializeOperationIntoFile(ABC_TIMED_WAIT, waitingThreadId, 0, 0, 0, 0, tid, -1);
    }else{
        serializeOperationIntoFile(ABC_WAIT, waitingThreadId, 0, 0, 0, 0, tid, -1);
    }

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void abcAddNotifyToTrace(int opId, int tid, int notifiedTid){
    int opType = -1;

    if(tid == abcNativeTid){
        opType = ABC_NATIVE_NOTIFY;
    }else{
        opType = ABC_NOTIFY;
    }

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add NOTIFY to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = opType;
    op->arg1 = notifiedTid;
    op->tid = tid;
    op->arg2 = NULL;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " NOTIFY tid:" << tid << " notifiedTid:" << notifiedTid << "\n";
    outfile.close();

    serializeOperationIntoFile(opType, notifiedTid, 0, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addAccessToTrace(int opId, int tid, u4 accessId){
    LOGE("%d ABC:Entered - Add ACCESS to trace", opId);
    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = accessId;

    op->opType = ABC_ACCESS;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::map<int, std::pair<int, std::list<int> > >::iterator 
        absIter = abcRWAbstractionMap.find(accessId);
    if(absIter != abcRWAbstractionMap.end()){
        absIter->second.first = opId;
    }else{
        LOGE("ABC-MISSING: accessId %d entry missing in abcRWAbstractionMap" 
             " when adding ACCESS to trace", accessId);
    }
    
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ACCESS tid:" << tid << "\t accessId:"
       << accessId << "\n";
    outfile.close(); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addTriggerServiceLifecycleToTrace(int opId, int tid, const char* component, u4 componentId, int state){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add TRIGGER-SERVICE to trace tid:%d  state: %d", opId, tid, state);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = componentId;

    op->opType = ABC_TRIGGER_SERVICE;
    op->arg1 = state;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->arg5 = strdup(component);
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::string lifecycle("");
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " TRIGGER-SERVICE tid:" << tid << " component:" << component
        << " id:" << componentId << " state:" << getLifecycleForCode(state, lifecycle) <<"\n";
    outfile.close();

    std::string arg5Str(component);
    int arg5 = -1;
    std::map<std::string, int>::iterator strIt = argStringToNumKeyMap.find(arg5Str);
    if(strIt == argStringToNumKeyMap.end()){
        arg5 = abcStringKey++;
        argStringToNumKeyMap.insert(std::make_pair(arg5Str, arg5));

        OpArgHelper *opStr = (OpArgHelper*)malloc(sizeof(OpArgHelper));
        opStr->key = arg5;
        strncpy(opStr->str, arg5Str.c_str(), strlen(opStr->str));
        opStr->str[sizeof(opStr->str)-1] = '\0';

        FILE* fp = fopen (binaryLogStringHelperFile.c_str(),"ab");
        fwrite(opStr, sizeof(OpArgHelper), 1, fp);
        fclose(fp);
    }else{
        arg5 = strIt->second;
    }
    serializeOperationIntoFile(ABC_TRIGGER_SERVICE, state, componentId, 0, 0, arg5, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addTriggerBroadcastLifecycleToTrace(int opId, int tid, const char* action, u4 componentId, 
        int intentId, int state, int delayTriggerOpid){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add TRIGGER-BROADCAST to trace tid:%d  state: %d", opId, tid, state);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = componentId;

    op->opType = ABC_TRIGGER_RECEIVER;
    op->arg1 = state;
    op->arg2 = arg2;
    op->arg3 = intentId;
    op->arg4 = delayTriggerOpid;
    op->arg5 = strdup(action);
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;
    abcTrace.insert(std::make_pair(opId, op));*/

    std::string arg5Str(action);
    int arg5 = -1;
    std::map<std::string, int>::iterator strIt = argStringToNumKeyMap.find(arg5Str);
    if(strIt == argStringToNumKeyMap.end()){
        arg5 = abcStringKey++;
        argStringToNumKeyMap.insert(std::make_pair(arg5Str, arg5));

        OpArgHelper *opStr = (OpArgHelper*)malloc(sizeof(OpArgHelper));
        opStr->key = arg5;
        strncpy(opStr->str, arg5Str.c_str(), strlen(opStr->str));
        opStr->str[sizeof(opStr->str)-1] = '\0';

        FILE* fp = fopen (binaryLogStringHelperFile.c_str(),"ab");
        fwrite(opStr, sizeof(OpArgHelper), 1, fp);
        fclose(fp);
    }else{
        arg5 = strIt->second;
    }

    std::string lifecycle("");
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " TRIGGER-BROADCAST tid:" << tid << " action:" << action <<" action-key:" << arg5 
        << " component:" << componentId << " intent:"<< intentId << " onRecLater:" << delayTriggerOpid
        << " state:" << getLifecycleForCode(state, lifecycle) <<"\n";
    outfile.close();


    serializeOperationIntoFile(ABC_TRIGGER_RECEIVER, state, componentId, intentId, delayTriggerOpid, arg5, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addEnableLifecycleToTrace(int opId, int tid, const char* component, u4 componentId, int state){
    int opType = -1;
    if(state == ABC_RUN_TIMER_TASK){
        opType = ENABLE_TIMER_TASK;
    }else{
        opType = ABC_ENABLE_LIFECYCLE;
    }

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add ENABLE-LIFECYCLE to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = componentId;

    op->opType = opType;
    op->arg1 = state;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::string lifecycle("");
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ENABLE-LIFECYCLE tid:" << tid << " component:" << component
        << " id:" << componentId << " state:" << getLifecycleForCode(state, lifecycle) <<"\n";
    outfile.close(); 

    serializeOperationIntoFile(opType, state, componentId, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addTriggerLifecycleToTrace(int opId, int tid, const char* component, u4 componentId, int state){
    int opType = -1;
    if(state == ABC_RUN_TIMER_TASK){
        opType = TRIGGER_TIMER_TASK;
    }else{
        opType = ABC_TRIGGER_LIFECYCLE;
    }

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add TRIGGER-LIFECYCLE to trace tid:%d  state: %d", opId, tid, state);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = componentId;

    op->opType = opType;
    op->arg1 = state;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::string lifecycle("");
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " TRIGGER-LIFECYCLE tid:" << tid << " component:" << component
        << " id:" << componentId << " state:" << getLifecycleForCode(state, lifecycle) <<"\n";
    outfile.close(); 

    serializeOperationIntoFile(opType, state, componentId, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addInstanceIntentMapToTrace(int opId, int tid, u4 instance, int intentId){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add INSTANCE-INTENT to trace tid:%d ", opId, tid);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = instance;

    op->opType = ABC_INSTANCE_INTENT;
    op->arg1 = intentId;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::string lifecycle("");
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " INSTANCE-INTENT tid:" << tid << " instance:" << instance
        << " intentId:" << intentId <<"\n";
    outfile.close();

    serializeOperationIntoFile(ABC_INSTANCE_INTENT, intentId, instance, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addEnableEventToTrace(int opId, int tid, u4 view, int event){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add ENABLE-EVENT to trace", opId);
   
    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = view;

    op->opType = ABC_ENABLE_EVENT;
    op->arg1 = event;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ENABLE-EVENT tid:" << tid << " view:" << view 
        << " event:" << event <<"\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_ENABLE_EVENT, event, view, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addTriggerEventToTrace(int opId, int tid, u4 view, int event){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add TRIGGER-EVENT to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = view;

    op->opType = ABC_TRIGGER_EVENT;
    op->arg1 = event;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " TRIGGER-EVENT tid:" << tid << " view:" << view
        << " event:" << event <<"\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_TRIGGER_EVENT, event, view, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addEnableWindowFocusChangeEventToTrace(int opId, int tid, u4 windowHash){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add ENABLE-WINDOW-FOCUS to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = windowHash;

    op->opType = ENABLE_WINDOW_FOCUS;
    op->arg1 = -1;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ENABLE-WINDOW-FOCUS tid:" << tid 
        << " windowHash:" << windowHash <<"\n";
    outfile.close();

    serializeOperationIntoFile(ENABLE_WINDOW_FOCUS, 0, windowHash, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addTriggerWindowFocusChangeEventToTrace(int opId, int tid, u4 windowHash){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Enter - Add TRIGGER-WINDOW-FOCUS to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = windowHash;

    op->opType = TRIGGER_WINDOW_FOCUS;
    op->arg1 = -1;
    op->arg2 = arg2;
    op->arg3 = -1;
    op->arg4 = -1;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " TRIGGER-WINDOW-FOCUS tid:" << tid 
        << " windowHash:" << windowHash <<"\n";
    outfile.close();

    serializeOperationIntoFile(TRIGGER_WINDOW_FOCUS, 0, windowHash, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

int addPostToTrace(int opId, int srcTid, u4 msg, int destTid, s8 delay, int isFoqPost, int isNegPost, int isAtTimePost){
    int opType = -1;
    if(srcTid == abcNativeTid){
        if(isFoqPost == 1){
            opType = ABC_NATIVE_POST_FOQ;
        }else if(isNegPost == 1){
            opType = ABC_POST_NEG;
        }else if(isAtTimePost == 1){
            opType = ABC_AT_TIME_POST;
        }else{
            opType = ABC_NATIVE_POST;
        }
    }else{
        if(isFoqPost == 1){
            opType = ABC_POST_FOQ;
        }else if(isNegPost == 1){
            opType = ABC_POST_NEG;
        }else if(isAtTimePost == 1){
            opType = ABC_AT_TIME_POST;
        }else{
            opType = ABC_POST;
        }
    }

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, srcTid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add POST to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msg;
 
    op->opType = opType;
    op->arg1 = srcTid;
    op->arg2 = arg2;
    op->arg3 = destTid;
    op->arg4 = delay;
    op->tid = srcTid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " POST src:" << srcTid << " msg:" << msg << " dest:" << destTid 
        << " delay:" << delay << " foq:" << isFoqPost << " neg:" << isNegPost << " atTime:" << isAtTimePost << "\n";
    outfile.close(); 

    serializeOperationIntoFile(opType, srcTid, msg, destTid, delay, 0, srcTid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }

    return opId;
}

void addCallToTrace(int opId, int tid, u4 msg){
    LOGE("%d ABC:Entered - Add CALL to trace", opId);
    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msg;

    op->opType = ABC_CALL;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " CALL tid:" << tid << "\t msg:" << msg  << "\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_CALL, tid, msg, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }

}

void addRetToTrace(int opId, int tid, u4 msg){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add RET to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msg;
    
    op->opType = ABC_RET;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " RET tid:" << tid << "\t msg:" << msg  << "\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_RET, tid, msg, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addRemoveToTrace(int opId, int tid, u4 msg){
    LOGE("ABC:Entered - Add REMOVE to trace");
    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msg;

    op->opType = ABC_REM;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    serializeOperationIntoFile(ABC_REM, tid, msg, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

int addIdlePostToTrace(int opId, int srcTid, u4 msg, int destTid){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, srcTid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add POST to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msg;

    op->opType = ABC_IDLE_POST;
    op->arg1 = srcTid;
    op->arg2 = arg2;
    op->arg3 = destTid;
    op->arg4 = 0;
    op->tid = srcTid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " IDLE-POST src:" << srcTid << " msg:" << msg << " dest:" << destTid << "\n";
    outfile.close();

    serializeOperationIntoFile(ABC_IDLE_POST, srcTid, msg, destTid, 0, 0, srcTid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }

    return opId;
}

void addAttachQToTrace(int opId, int tid, u4 msgQ){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add ATTACHQ to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msgQ;

    op->opType = ABC_ATTACH_Q;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ATTACH-Q tid:" << tid << "\t queue:" << msgQ <<"\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_ATTACH_Q, tid, msgQ, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addLoopToTrace(int opId, int tid, u4 msgQ){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add LOOP to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msgQ;

    op->opType = ABC_LOOP;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " LOOP tid:" << tid << "\t queue:" << msgQ <<"\n";
    outfile.close();

    serializeOperationIntoFile(ABC_LOOP, tid, msgQ, 0, 0, 0, tid, -1); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addLoopExitToTrace(int opId, int tid, u4 msgQ){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add LOOP-EXIT to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = msgQ;

    op->opType = ABC_LOOP_EXIT;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " LOOP-EXIT tid:" << tid << "\t queue:" << msgQ <<"\n";
    outfile.close();

    serializeOperationIntoFile(ABC_LOOP_EXIT, tid, msgQ, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addQueueIdleToTrace(int opId, u4 idleHandlerHash, int queueHash, int tid){
    LOGE("%d ABC:Entered - Add QUEUE_IDLE to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->id = idleHandlerHash;
    arg2->obj = NULL;

    op->opType = ABC_QUEUE_IDLE;
    op->arg1 = queueHash;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " QUEUE_IDLE tid:" << tid << " idler:" << idleHandlerHash << " queue:" << queueHash <<"\n";
    outfile.close();

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addIdleHandlerToTrace(int opId, u4 idleHandlerHash, int queueHash, int tid){
    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add ADD_IDLE_HANDLER to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->id = idleHandlerHash;
    arg2->obj = NULL;

    op->opType = ABC_ADD_IDLE_HANDLER;
    op->arg1 = queueHash;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " ADD_IDLE_HANDLER idler:" << idleHandlerHash << " queue:" << queueHash <<"\n";
    outfile.close();

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addRemoveIdleHandlerToTrace(int opId, u4 idleHandlerHash, int queueHash, int tid){
    LOGE("%d ABC:Entered - Add REMOVE_IDLE_HANDLER to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->id = idleHandlerHash;
    arg2->obj = NULL;

    op->opType = ABC_REMOVE_IDLE_HANDLER;
    op->arg1 = queueHash;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " REMOVE_IDLE_HANDLER idler:" << idleHandlerHash << " queue:" << queueHash <<"\n";
    outfile.close();

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addLockToTrace(int opId, int tid, Object* lockObj){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add LOCK to trace obj:%p tid:%d", opId, lockObj, tid);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = lockObj;
    arg2->id = 0;

    op->opType = ABC_LOCK;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " LOCK" << " tid:" << tid << "\t lock-obj:" << lockObj << "\n";
    outfile.close(); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addUnlockToTrace(int opId, int tid, Object* lockObj){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add UNLOCK to trace obj:%p tid:%d", opId, lockObj, tid);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = lockObj;
    arg2->id = 0;

    op->opType = ABC_UNLOCK;
    op->arg1 = tid;
    op->arg2 = arg2;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " UNLOCK" << " tid:" << tid << "\t lock-obj:" << lockObj << "\n";
    outfile.close(); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addForkToTrace(int opId, int parentTid, int childTid){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, parentTid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add FORK to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    AbcArg* arg2 = (AbcArg*)malloc(sizeof(AbcArg));
    arg2->obj = NULL;
    arg2->id = childTid;

    op->opType = ABC_FORK;
    op->arg1 = parentTid;
    op->arg2 = arg2;
    op->tid = parentTid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " FORK par-tid:" << parentTid << "\t child-tid:"
        << childTid << "\n";
    outfile.close(); 

    //we will serialize childTid as int instead of u4. Hence, we use arg3 for to store childTid
    serializeOperationIntoFile(ABC_FORK, parentTid, 0, childTid, 0, 0, parentTid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addThreadInitToTrace(int opId, int tid){
    LOGE("%d ABC:Entered - Add THREADINIT to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = ABC_THREADINIT;
    op->arg1 = tid;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " THREADINIT tid:" << tid << "\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_THREADINIT, tid, 0, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addThreadExitToTrace(int opId, int tid){

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }
    LOGE("%d ABC:Entered - Add THREADEXIT to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = ABC_THREADEXIT;
    op->arg1 = tid;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " THREADEXIT tid:" << tid << "\n";
    outfile.close(); 

    serializeOperationIntoFile(ABC_THREADEXIT, tid, 0, 0, 0, 0, tid, -1);

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addNativeEntryToTrace(int opId, int tid){
    LOGE("%d ABC:Entered - Add NATIVE_ENTRY to trace", opId);

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = ABC_NATIVE_ENTRY;
    op->arg1 = tid;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/

    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " NATIVE-ENTRY tid:" << tid << "thread-name:" << dvmGetThreadName(dvmThreadSelf()).c_str() << "\n";
    outfile.close(); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addNativeExitToTrace(int opId, int tid){
    LOGE("%d ABC:Entered - Add NATIVE_EXIT to trace", opId);

    bool accessSetAdded = addIntermediateReadWritesToTrace(opId, tid);
    if(accessSetAdded){
        opId = abcOpCount++;
    }

    /*AbcOp* op = (AbcOp*)malloc(sizeof(AbcOp));
    op->opType = ABC_NATIVE_EXIT;
    op->arg1 = tid;
    op->tid = tid;
    op->tbd = false;
    op->asyncId = -1;

    abcTrace.insert(std::make_pair(opId, op));*/
    
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << opId << " NATIVE-EXIT tid:" << tid << "thread-name:" << dvmGetThreadName(dvmThreadSelf()).c_str() << "\n";
    outfile.close(); 

    if(abcTraceLengthLimit != -1 && opId >= abcTraceLengthLimit){
        stopAbcModelChecker();
        LOGE("Trace truncated as hit trace limit set by user");
    }
}

void addReadWriteToTrace(int rwId, int accessType, const char* clazz, std::string field, u4 fieldIdx, Object* obj, std::string dbPath, int tid){ 
    AbcRWAccess* access = (AbcRWAccess*)malloc(sizeof(AbcRWAccess));
    access->accessType = accessType;
    access->obj = obj;
    access->dbPath = strdup(dbPath.c_str());
    access->clazz = clazz;
    access->field = strdup(field.c_str());
    access->fieldIdx = fieldIdx;
    access->tid = tid;
 
    std::map<int, int>::iterator accessIt = abcThreadAccessSetMap.find(tid);
    std::map<int, std::pair<int, std::list<int> > >::iterator rwIt;
    if(accessIt == abcThreadAccessSetMap.end()){
        //if this is the first read/write seen after a non access operation
        abcThreadAccessSetMap.insert(std::make_pair(tid, abcAccessSetCount++)); 
        std::list<int> tmpList; 
        abcRWAbstractionMap.insert(make_pair(abcAccessSetCount-1, std::make_pair(-1, tmpList)));
        rwIt = abcRWAbstractionMap.find(abcAccessSetCount-1);
    }else{
        rwIt = abcRWAbstractionMap.find(accessIt->second);
    }
    rwIt->second.second.push_back(rwId);

    access->accessId = rwIt->first;
    abcRWAccesses.insert(std::make_pair(rwId, access));


    //insert this operation to appropriate access tracker map
    if(obj != NULL){
        std::pair<Object*, u4> tmpPair = std::make_pair(obj, fieldIdx);
        std::map<std::pair<Object*, u4>, std::pair<std::set<int>, std::set<int> > >::iterator it 
            = abcObjectAccessMap.find(tmpPair);
        if(it != abcObjectAccessMap.end()){
            if(accessType == ABC_READ)
                it->second.first.insert(rwId);
            else
                it->second.second.insert(rwId);
        }else{
            std::set<int> readSet;
            std::set<int> writeSet;
            if(accessType == ABC_READ)
                readSet.insert(rwId);
            else
                writeSet.insert(rwId);
            abcObjectAccessMap.insert(std::make_pair(tmpPair, std::make_pair(readSet, writeSet)));
        }
    }else if(dbPath != ""){
        std::map<std::string, std::pair<std::set<int>, std::set<int> > >::iterator it  
            = abcDatabaseAccessMap.find(dbPath);
        if(it != abcDatabaseAccessMap.end()){
            if(accessType == ABC_READ)
                it->second.first.insert(rwId);
            else
                it->second.second.insert(rwId);
        }else{  
            std::set<int> readSet;
            std::set<int> writeSet;
            if(accessType == ABC_READ)
                readSet.insert(rwId);
            else
                writeSet.insert(rwId);
            abcDatabaseAccessMap.insert(std::make_pair(std::string(access->dbPath),std::make_pair(readSet, writeSet)));
        }  
    }else{
        std::pair<const char*, u4> tmpPair = std::make_pair(clazz, fieldIdx);
        std::map<std::pair<const char*, u4>, std::pair<std::set<int>, std::set<int> > >::iterator it
            = abcStaticAccessMap.find(tmpPair);
        if(it != abcStaticAccessMap.end()){
            if(accessType == ABC_READ)
                it->second.first.insert(rwId);
            else
                it->second.second.insert(rwId);
        }else{
            std::set<int> readSet;
            std::set<int> writeSet;
            if(accessType == ABC_READ)
                readSet.insert(rwId);
            else
                writeSet.insert(rwId);
            abcStaticAccessMap.insert(std::make_pair(tmpPair,std::make_pair(readSet, writeSet)));
        }
    }
}


/*
bool checkAndAbortIfAssumtionsFailed(int opId, AbcOp* op, AbcThreadBookKeep* threadBK){
    bool shouldAbort = false;
    if(threadBK->loopId != -1 && op->asyncId == -1){
        switch(op->opType){
            case ABC_ACCESS:
            case ABC_LOCK:
            case ABC_UNLOCK:
            case ABC_FORK:
            case ABC_JOIN: 
                shouldAbort = true; 
                LOGE("Trace has an unusual sequence op operations outside async blocks, which is not addressed by "
                "implementation. Cannot continue further");
                 break;
        }
    }
    return shouldAbort;
}


void abcComputeMemoryUsedByRaceDetector(){
    std::ofstream outfile;
    outfile.open(abcLogFile.c_str(), std::ios_base::app);
    outfile << "\n\nMemory used for race detection\n" << "\n";
    outfile << "AbcTrace         : " << abcTrace.size() << " * " << sizeof(AbcOp) << "\n"; 
    outfile << "AbcAsyncMap      : " << abcAsyncMap.size() << " * " << sizeof(AbcAsync) << "\n";
    outfile << "ReadWriteMap     : " << abcRWAccesses.size() << " * " << sizeof(AbcRWAccess) << "\n";
    outfile << "ThreadBookKeepMap: " << abcThreadBookKeepMap.size() << " * " << sizeof(AbcThreadBookKeep) << "\n";
    outfile << "LockMap          : " << abcLockMap.size() << " * " << sizeof(AbcLock) << "\n";   
    outfile << "Graph size       : " << abcTrace.size() << " * " << abcTrace.size() << " * " << sizeof(bool) << "\n";


    outfile.close();
}
*/
