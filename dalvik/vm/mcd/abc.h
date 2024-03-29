/* This is a native library to model check Android apps to detect
 * concurrency bugs. 
 * Author: Pallavi Maiya
 */

/*todo:
 *(1)if this class works fine, after initalizing along with gDvm,
 *then consider storing custom fields currently in gDvm here iself
 *This will pack all ABC related things in one place unless
 *unavoidable.
*/

/* todo: have a tag to identify threads started by Thread.start() and when 
 * shouldAbcTrack set to true and the rest (threads started by native method
 * or library and later hitting app code). 
 */

#ifndef ABC_H_
#define ABC_H_

#include "Dalvik.h"
#include <map>
#include <set>
#include <string>
#include <iostream>
#include <fstream>
#include <list>

#define ABC_WRITE 1 
#define ABC_READ 2

#define ABC_POST 3
#define ABC_CALL 4
#define ABC_RET 5
#define ABC_ATTACH_Q 6
#define ABC_FORK 7
#define ABC_JOIN 8
#define ABC_THREADINIT 9
#define ABC_THREADEXIT 10
#define ABC_LOCK 11
#define ABC_UNLOCK 12
#define ABC_NATIVE_ENTRY 13
#define ABC_NATIVE_EXIT 14
#define ABC_START 15
#define ABC_REM 16
#define ABC_ACCESS 17
#define ABC_LOOP 18
#define ABC_ENABLE_EVENT 19
#define ABC_TRIGGER_EVENT 20
#define ABC_ENABLE_LIFECYCLE 21
#define ABC_TRIGGER_LIFECYCLE 22
#define ABC_REGISTER_RECEIVER 23
#define ABC_TRIGGER_RECEIVER 24

#define EVENT_CLICK 0
#define EVENT_LONG_CLICK 1	
#define EVENT_SET_TEXT 2
#define EVENT_BACK 3
#define EVENT_MENU_CLICK 4
#define EVENT_ROTATE_SCREEN 5
#define EVENT_SET_DATE_ANIMATE 6
#define EVENT_UPDATE_DATE 7
#define EVENT_SET_CURRENT_HOUR_MIN 8
#define EVENT_SET_VALUE 9
#define EVENT_SET_PROGRESS 10
#define EVENT_SET_RATING 11
#define EVENT_SET_QUERY 12
#define EVENT_TOGGLE 13
#define EVENT_SHOW_NEXT_OR_PREV 14

#define ABC_BIND 4
#define ABC_APPBIND_DONE 16 

enum abcEventType{
    ABC_UI_EVENT = 1,
    ABC_INTENT_EVENT = 2
};

struct AbcGlobals{
//    pthread_t abcMainThread;
    pthread_cond_t abcMainCond;
    pthread_mutex_t abcMainMutex;
//    pthread_cond_t cond1;
//    pthread_cond_t cond2;
};

struct ArgumentStruct{
    Object* obj; //lock
    int id; //thread-id/msg-id/access-id
};
typedef struct ArgumentStruct AbcArg;

struct operationStruct{
    int opType;
    int arg1;
    AbcArg* arg2;
    int arg3;
    int arg4; //delay argument for post (-1 indicates front of queue)
   // int followingOpId;
    char* arg5; //holds actionof broadcast receiver 
    int tid; //thread executing the operation
    int asyncId; //async-block from ehich the operation is posted; -1 if posted from non MQ thread
    bool tbd; //true if node to be deleted
};
typedef struct operationStruct AbcOp;

struct asyncStruct{
  //  int msg;
    int tid;
    int postId;
    int callId;
    int retId;
    int parentAsyncId; //-1 if posted by a non-queue thread
    int delay; //stored here only to reduce lookup
   
    //fields needed to collect stats for nature of async races
    int recentTriggerOpId; //tracks the most recent trigger that led to this async block being called
    int recentCrossPostAsync; //tracks the most recent ancestor that was posted from another thread
    int recentDelayAsync; //tracks most recent ancestor that was posted with a delay
};
typedef struct asyncStruct AbcAsync;

struct sharedVarAccessStruct{
    int accessType;
    Object* obj;
    const char* clazz;
    char* field;
    u4 fieldIdx;
    char * dbPath;
    int tid; //thread accessing the shared variable
    int accessId; //accessID to which this read/write belongs
};
typedef struct sharedVarAccessStruct AbcRWAccess;

struct raceFieldInfoStruct{
    const char* clazz;
    u4 field;
    Object* obj;
    char* dbPath;    
};
typedef struct raceFieldInfoStruct RaceFieldInfo;

struct threadBookKeepingStruct{
    int curAsyncId; //abcAsyncMap key
    int prevOpId; //abcTrace key
    int attachqId; //abcTrace key if thread has a message queue
    int loopId; //not -1 only after Looper.loop() starts
    int forkId; //abcTraceId for fork operation; -1 for native and main thread
};
typedef struct threadBookKeepingStruct AbcThreadBookKeep;

struct CurAsyncStruct{
    int asyncId;
    bool shouldRemove; //set to true if any of the ancestors is a delay msg or front-of-Q post msg
    bool hasMQ; //true if loop action has been called on this thread
};
typedef struct CurAsyncStruct AbcCurAsync;

/*struct LockStruct{
    std::map<int, std::set<int> > unlockMap;
    int lastUnlockTid;
    *this is needed to not derive HB edge b/w two operations
     *on the same thread (due to lock-unlock), if an edge does not 
     *exist due to program order or asynchronous relation
     *
    std::set<int> prevUnlocksSeen;
};*/
struct unlockListStruct{
    int unlockOp;
    struct unlockListStruct *prev;
};
typedef struct unlockListStruct UnlockList;

struct LockStruct{
    int lastUnlockTid;
    std::map<int, UnlockList*> unlockMap;
    UnlockList* prevUnlocksSeen;
};
typedef struct LockStruct AbcLock;

struct MsgStruct{
    int msgId;
    int postId;
};
typedef struct MsgStruct AbcMsg;

struct receiverStruct{
    char* component;
    char* action;
};
typedef struct receiverStruct AbcReceiver;

struct ThreadLockCountStruct{
    int threadId;
    int count;
};
typedef struct ThreadLockCountStruct AbcLockCount;

/* We use this struct to associate all logical IDs 
 * corresponding to a threadID (I think threadIDs are recycled 
 * after a thread dies)...so the current logical id associated 
 * with a threadID is the one with the highest value
 */

struct AbcThreadIdStruct{
    int abcThreadId;
    struct AbcThreadIdStruct *prevAbcId;
};
typedef struct AbcThreadIdStruct AbcThreadIds;

/*A struct to store a stack of method calls
 */
struct MethodStack{
    const Method * method;
    struct MethodStack* prev;
};
typedef struct MethodStack AbcMethodStack;

struct MethodStruct{
    const Method *method;
};
typedef struct MethodStruct AbcMethod;

struct AbcEventStruct{
    int eventId;
    int eventType;
};
typedef struct AbcEventStruct AbcEvent;

/*
struct AbcLockStruct{
    Object *obj;
    struct AbcLockStruct *prevLock;
};
typedef struct AbcLockStruct AbcLock;
*/

struct AbcThreadStruct{
//    int abcThreadId;
    int threadId; //android given thread ID
//    int threadState;
//    bool isStartedInApp;
//    AbcLock* lockList;
//    struct AbcThreadStruct* parentThread;
//    const char* name;
    bool isOriginUntracked;
//    AbcEvent* event;
    /*thread condition variable assigned by ABC
    pthread_cond_t *abcCond;*/
};
typedef struct AbcThreadStruct AbcThread;

struct DbAccessTypeStruct{
    int accessType;
    /* this will be taken from database and is the 
     * transition that caused the DB access
     */
    int transitionId;
    struct DbAccessTypeStruct* prevAccess;
};
typedef struct DbAccessTypeStruct AbcDbAccessType;

/*struct DbAccessStruct{
    std::map<int, AbcDbAccessType*> dbAccessMap;
};
typedef struct DbAccessStruct AbcDbAccess;*/

struct DbAccessStruct{
    int abcThreadId;
    AbcDbAccessType *dbAccessForThread;
    struct DbAccessStruct *nextThreadDb;
};
typedef struct DbAccessStruct AbcDbAccess;

extern struct AbcGlobals* gAbc; 

struct MethObjStruct{
    const Method* method;
    Object* obj;
    struct MethObjStruct* prev;
};
typedef struct MethObjStruct AbcMethObj;

struct WorklistElemStruct{
    int src;
    int dest;
    struct WorklistElemStruct * prev;
};
typedef struct WorklistElemStruct WorklistElem;

struct DestinationStruct{
    int dest;
    struct DestinationStruct * prev;
};
typedef struct DestinationStruct Destination;

struct SourceStruct{
    int src;
    struct SourceStruct * prev;
};
typedef struct SourceStruct Source;

/*globals for a process*/
/*a map from threadId -> MethodStack to store stack of
 *method calls with an app method at the base. This implmentation
 *may change with refinement to the model checker scope.
 *If an app method is called with another app method as base,
 *the new app method is just added to the stack and not considered
 *as a base method.
 *
 *threadId - can be logical thread id as threadIds can be repeated
 *after a thread exits.
 */
extern std::map<int, AbcMethodStack*> abcThreadStackMap;

/*threadId to base app method map */
extern std::map<int, AbcMethod*> abcThreadBaseMethodMap;

extern std::map<int, AbcThread*> abcThreadMap; 

/*map from actual thread id to thread id assigned by ABC*/
//extern std::map<int, AbcThreadIds*> abcLogicalThreadIdMap;

//extern std::set<int> abcThreadIdSet;

//extern std::map<std::string, AbcDbAccess*> abcDbAccessMap;

/*map to temporarily store the caller object of a library
 *method called from an app method
 */
extern std::map<int, AbcMethObj*> abcLibCallerObjectMap;

/*program trace stored as hashmap with key being the index 
 *of operation in the trace*/
extern std::map<int, AbcOp*> abcTrace;

/* a mapping from message hashcode to a unique id which is used to
 * index the msg's async block
 */
extern std::map<u4,AbcMsg*> abcUniqueMsgMap;

/*async block map with msg as key*/
//extern std::map<int, AbcAsync*> abcAsyncMap;

/*map storing all read-write operations*/
//extern std::map<int, AbcRWAccess*> abcRWAccesses; 

// <accessId, <opId, list-of-read-writes> >
//extern std::map<int, std::pair<int, std::list<int> > > abcRWAbstractionMap;

/*mappings from object locations to set of R/W accesses etc.*/
//extern std::map<std::pair<Object*, u4>, std::pair<std::set<int>, std::set<int> > > abcObjectAccessMap;
//extern std::map<std::string, std::pair<std::set<int>, std::set<int> > > abcDatabaseAccessMap;
//extern std::map<std::pair<const char*, u4>, std::pair<std::set<int>, std::set<int> > > abcStaticAccessMap;

/*a thread boom keeping map needed for initial HB graph construction*/
//extern std::map<int, AbcThreadBookKeep*> abcThreadBookKeepMap;

extern std::map<int, AbcCurAsync*> abcThreadCurAsyncMap;

/*bool,bool is set to false,true for those msgs whose async block should not be deleted
 *(maybe the post was a delayed post or front of queue post)
 *and the async block enters app method (second component of pair).
 */
extern std::map<int, std::pair<bool,bool> > abcAsyncStateMap;

//extern std::map<int, int> abcThreadAccessSetMap;

//extern std::map<Object*, AbcLock*> abcLockMap;

//extern std::map<Object*, AbcLockCount*> abcLockCountMap;

//a map maintained during trace generation to track enabled events on views
extern std::map<int, std::set<int> > abcViewEventMap;

// <<view, event>, enable-event-opId> only abc.cpp modifies it and hence need not be extern
//extern std::map<std::pair<int, int>, int> abcEnabledEventMap;
//extern std::map<std::pair<int, int>, int> abcEnabledLifecycleMap;

//some maps to handle broadcast receivers
extern std::map<int, AbcReceiver*> abcDelayedReceiverTriggerThreadMap;
extern std::map<int, AbcReceiver*> abcDelayedReceiverTriggerMsgMap;


extern int abcThreadCount;
extern int abcMsgCount;
extern int abcOpCount;
extern int abcAccessSetCount;
extern int abcRWCount;
extern int abcEventCount;
extern int abcEventLimit;


/*Android bug-checker library methods*/
void abcAddLockOpToTrace(Thread* self, Object* obj);

void abcAddUnlockOpToTrace(Thread* self, Object* obj);

void abcAddCallerObjectForLibMethod(int abcTid, const Method* meth, Object* obj);

void abcRemoveCallerObjectForLibMethod(int abcTid, const Method* meth);

bool isObjectInThreadAccessMap(int abcThreadId, Object* obj);

bool checkAndIncrementLockCount(Object* lockObj, int threadId);

bool checkAndDecrementLockCount(Object* lockObj, int threadId);

bool isThreadStackEmpty(int abcThreadId);

void abcPushMethodForThread(int threadId, const Method* method);

void abcStoreBaseMethodForThread(int threadId, const Method* method);

void abcRemoveBaseMethodForThread(int threadId);

void abcRemoveThreadFromStackMap(int threadId);

/*returns NULL if no base-app-method for the specified thread id*/
const Method* abcGetBaseMethodForThread(int threadId);

const Method* abcGetLastMethodInThreadStack(int threadId);

const Method* abcPopLastMethodInThreadStack(int threadId);

bool abcIsThreadOriginUntracked(int abcThreadId);

//void abcAddDbAccessInfo(std::string dbPath, int trId, int accessType, int abcThreadId);

void abcPrintThreadStack(int threadId);

void abcCondWait(pthread_cond_t *cond, pthread_mutex_t *pMutex);

void abcLockMutex(Thread* self, pthread_mutex_t* pMutex);

void abcUnlockMutex(pthread_mutex_t* pMutex);

void abcAddThreadToMap(Thread *self, const char* name);

void addThreadToCurAsyncMap(int threadId);

void abcAddLockToList(int abcThreadId, Object *lockObj);

void abcRemoveLockFromList(int abcThreadId, Object *lockObj);

/*we dont need these thread constructs now as we dont track these fields*/
//void abcSetThreadState(int abcThreadId, int threadState);
//void abcSetIsStartedInAppForThread(int abcThreadId, bool isStartedInApp);
//void abcSetParentThread(int abcThreadId, int parentAbcId); 

/*we do not have to maintain logical thread ids when we are not controlling thread interleaving*/
//void abcAddLogicalIdToMap(int threadId, int abcThreadId);
//int abcGetAbcThreadId(int threadId);
//void abcRemoveThreadFromLogicalIdMap(int threadId);

void startAbcModelChecker();

bool abcPerformRaceDetection();

void addAccessToTrace(int opId, int tid, int accessId);

int addPostToTrace(int opId, int srcTid, int msg, int destTid, s8 delay);

void addCallToTrace(int opId, int tid, int msg);

void addRetToTrace(int opId, int tid, int msg);

void addRemoveToTrace(int opId, int tid, int msg);

void addAttachQToTrace(int opId, int tid, int msgQ);

void addLoopToTrace(int opId, int tid, int msgQ);

void addLockToTrace(int opId, int tid, Object* lockObj);

void addUnlockToTrace(int opId, int tid, Object* lockObj);

void addReadWriteToTrace(int rwId, int accessType, const char* clazz, std::string field, u4 fieldIdx,
    Object* obj, std::string dbPath, int tid);

void addForkToTrace(int opId, int parentTid, int childTid);

void addThreadInitToTrace(int opId, int tid);

void addThreadExitToTrace(int opId, int tid);

void addNativeEntryToTrace(int opId, int tid);

void addNativeExitToTrace(int opId, int tid);

void addEnableEventToTrace(int opId, int tid, int view, int event);

void addTriggerEventToTrace(int opId, int tid, int view, int event);

void addEnableLifecycleToTrace(int opId, int tid, char* component, int componentId, int state);

void addTriggerLifecycleToTrace(int opId, int tid, char* component, int componentId, int state);

void addRegisterBroadcastReceiverToTrace(int opId, int tid, char* component, char* action);

void addTriggerBroadcastReceiverToTrace(int opId, int tid, char* component, char* action);

int abcGetTraceLength();

int abcGetThreadCount();

int abcGetMessageQueueCount();

int abcGetAsyncBlockCount();

int abcGetEventTriggerCount();

int abcGetFieldCount();

int abcGetMultiThreadedRaceCount();

int abcGetAsyncRaceCount();

int abcGetDelayPostRaceCount();

int abcGetCrossPostRaceCount();

int abcGetCoEnabledEventUiRaces();

int abcGetCoEnabledEventNonUiRaces();

void abcPrintRacesDetectedToFile();

void abcComputeMemoryUsedByRaceDetector();
#endif  // ABC_H_
