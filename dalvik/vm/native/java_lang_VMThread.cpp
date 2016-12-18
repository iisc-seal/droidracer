/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * java.lang.VMThread
 */
#include "Dalvik.h"
#include "native/InternalNativePriv.h"

/*Android bug-checker*/
#include "mcd/abc.h"
#include <inttypes.h>
/*Android bug-checker*/


/*Android bug-checker*/

static void Dalvik_java_lang_VMThread_abcStopTraceGeneration(const u4* args,
    JValue* pResult){
    stopAbcModelChecker();
   // gDvm.isRunABC = false;
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcGetTraceLength(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetThreadCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetMessageQueueCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetAsyncBlockCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetEventTriggerCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetFieldCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetMultiThreadedRaceCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetAsyncRaceCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetDelayPostRaceCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetCrossPostRaceCount(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetCoEnabledEventUiRaces(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcGetCoEnabledEventNonUiRaces(const u4* args,
    JValue* pResult){
    RETURN_INT(0);
}

static void Dalvik_java_lang_VMThread_abcPrintRacesDetectedToFile(const u4* args,
    JValue* pResult){
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPerformRaceDetection(const u4* args,
    JValue* pResult){
    abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
    stopAbcModelChecker();
    abcUnlockMutex(&gAbc->abcMainMutex);
    /* uncomment this to detect data races
    bool success = abcPerformRaceDetection();
    
    if(success){
        RETURN_INT(1);
    }else{
        RETURN_INT(0);
    }*/

    RETURN_INT(1);
}

static void Dalvik_java_lang_VMThread_abcComputeMemoryUsedByRaceDetector(const u4* args,
    JValue* pResult){
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcMapInstanceWithIntentId(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 instance = args[1];
        int intentId = args[2];

        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        addInstanceIntentMapToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, instance, intentId);
        abcUnlockMutex(&gAbc->abcMainMutex);
    }    
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcTriggerServiceLifecycle(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        StringObject* compStr = (StringObject*) args[1];
        char *component = dvmCreateCstrFromString(compStr);
        u4 componentId = args[2];
        int state = args[3];
        Thread* selfThread = dvmThreadSelf();

        std::map<int, AbcCurAsync*>::iterator asyncIt = abcThreadCurAsyncMap.find(selfThread->abcThreadId);
        if(asyncIt != abcThreadCurAsyncMap.end()){
            AbcCurAsync* curAsync = asyncIt->second;
            if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                abcLockMutex(selfThread, &gAbc->abcMainMutex);
                addTriggerServiceLifecycleToTrace(abcOpCount++, selfThread->abcThreadId, component, componentId, state);
                abcUnlockMutex(&gAbc->abcMainMutex);
            }else{
                LOGE("ABC-DONT-LOG: trigger service lifecycle found in a deleted async block. aborting trace creation");
                std::ofstream outfile;
                outfile.open(abcLogFile.c_str(), std::ios_base::app);
                outfile << "ABC: ABORT " << "\n";
                outfile.close();
                stopAbcModelChecker();
            }
        }else{
            LOGE("ABC-DONT-LOG: trigger service lifecycle found on an untracked thread. Truncating trace collection.");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }

        free(component);
    }
}

static void Dalvik_java_lang_VMThread_abcEnableLifecycleEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        StringObject* compStr = (StringObject*) args[1];
        char *component = dvmCreateCstrFromString(compStr);        
        u4 componentId = args[2];
        int state = args[3];
        Thread* selfThread = dvmThreadSelf();
        
        std::map<int, AbcCurAsync*>::iterator asyncIt = abcThreadCurAsyncMap.find(selfThread->abcThreadId);
        if(asyncIt != abcThreadCurAsyncMap.end()){
            AbcCurAsync* curAsync = asyncIt->second;
            if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                abcLockMutex(selfThread, &gAbc->abcMainMutex);
                addEnableLifecycleToTrace(abcOpCount++, selfThread->abcThreadId, component, componentId, state);
                abcUnlockMutex(&gAbc->abcMainMutex);
            }else{
                LOGE("ABC-DONT-LOG: enable lifecycle found in a deleted async block. aborting trace creation");
                std::ofstream outfile;
                outfile.open(abcLogFile.c_str(), std::ios_base::app);
                outfile << "ABC: ABORT " << "\n";
                outfile.close();
                stopAbcModelChecker();
            }
        }else{
            LOGE("ABC-DONT-LOG: enable activity lifecycle found on an untracked thread. Truncating trace collection.");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }
        
        free(component);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcTriggerLifecycleEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        StringObject* compStr = (StringObject*) args[1];
        char *component = dvmCreateCstrFromString(compStr);
        u4 componentId = args[2];
        int state = args[3];

        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        addTriggerLifecycleToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, component, componentId, state);
        abcUnlockMutex(&gAbc->abcMainMutex);
 
        free(component);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcTriggerBroadcastLifecycle(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        StringObject* actionStr = (StringObject*) args[1];
        char *action = dvmCreateCstrFromString(actionStr);
        u4 componentId = args[2];
        int intentId = args[3];
        int state = args[4];
        int delayTriggerOpid = -1;

        Thread* selfThread = dvmThreadSelf();

     /*   AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(dvmThreadSelf()->threadId)->second;
        if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
            abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
            addTriggerBroadcastLifecycleToTrace(abcOpCount++, dvmThreadSelf()->threadId, action, componentId, intentId, state);
            abcUnlockMutex(&gAbc->abcMainMutex);
        }else{
            LOGE("ABC-DONT-LOG: trigger service lifecycle found in a deleted async block. aborting trace creation");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            gDvm.isRunABC = false;
        }*/
        
        std::map<int, AbcCurAsync*>::iterator asyncIt = abcThreadCurAsyncMap.find(selfThread->abcThreadId);
        if(asyncIt != abcThreadCurAsyncMap.end()){
            abcLockMutex(selfThread, &gAbc->abcMainMutex);
            if(state != ABC_TRIGGER_ONRECIEVE_LATER){
                addTriggerBroadcastLifecycleToTrace(abcOpCount++, selfThread->abcThreadId, action, 
                    componentId, intentId, state, delayTriggerOpid);
            }else{
                //do nothing. ONRECEIVE_LATER was earlier needed but now is handled more elegantly by better instrumentation.
                //onReceive can be posted from native thread (we have a trigger there and hence should be tracked).
            }
            abcUnlockMutex(&gAbc->abcMainMutex);
        }else{
            LOGE("ABC-DONT-LOG: trigger broadcast receiver found on an untracked thread. Truncating trace collection.");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }

        free(action);
    }
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcTriggerEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 view = args[1];
        int event = args[2];

        //view = 0 indicates the event to be BACK PRESS / MENU CLICK / ROTATE-SCREEN
        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        std::map<u4, std::set<int> >::iterator it = abcViewEventMap.find(view);
        if(it == abcViewEventMap.end()){
            LOGE("ABC: triggering an event for which an enable is not seen. Something is missing."
                 "Aborting");
            stopAbcModelChecker();
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "NO-TRIGGER  viewHash:" << view << "  event:" << event << "\n";
            outfile.close();
        }else{
            if(it->second.find(event) == it->second.end()){
                LOGE("ABC: triggering an event for which view exists but an enable is not seen."
                     "Something is missing. Aborting");
                stopAbcModelChecker();
                std::ofstream outfile;
                outfile.open(abcLogFile.c_str(), std::ios_base::app);
                outfile << "NO-TRIGGER  viewHash:" << view << "  event:" << event << "\n";
                outfile.close();
            }else{
                addTriggerEventToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, view, event);
            }
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcForceAddEnableEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 view = args[1];
        int event = args[2];

        //view = 0 indicates the event to be BACK PRESS / MENU CLICK / ROTATE-SCREEN
        AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(dvmThreadSelf()->abcThreadId)->second;
        if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){

            abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
            std::map<u4, std::set<int> >::iterator it = abcViewEventMap.find(view);
            if(it == abcViewEventMap.end()){
                std::set<int> eventSet;
                eventSet.insert(event);
                abcViewEventMap.insert(std::make_pair(view, eventSet));
                addEnableEventToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, view, event);
            }else{
                if(it->second.find(event) == it->second.end()){
                    it->second.insert(event);
                } 
                addEnableEventToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, view, event);
            }
            abcUnlockMutex(&gAbc->abcMainMutex);

        }else{
            LOGE("ABC-DONT-LOG: force-enable event found in a deleted async block. aborting trace creation");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcRemoveAllEventsOfView(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 view = args[1];
        int ignoreEvent = args[2];
  
        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        
        std::map<u4, std::set<int> >::iterator it = abcViewEventMap.find(view);
        if(it != abcViewEventMap.end()){
            if(ignoreEvent == EVENT_CLICK){
                //this means the only event to be removed is the long-click event
                it->second.erase(EVENT_LONG_CLICK);
            }else{
                for(std::set<int>::iterator setIt = it->second.begin(); setIt != it->second.end(); ){
                    if(*setIt != ignoreEvent){
                        it->second.erase(setIt++);
                    }else{
                        ++setIt;
                    }
                }
            }
            
            if(it->second.size() == 0){
                abcViewEventMap.erase(view);
            }
        }

        abcUnlockMutex(&gAbc->abcMainMutex);
    }
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcAddEnableEventForView(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 view = args[1];
        int event = args[2];

        AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(dvmThreadSelf()->abcThreadId)->second;
        if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){

        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        std::map<u4, std::set<int> >::iterator it = abcViewEventMap.find(view);
        if(it == abcViewEventMap.end()){
            std::set<int> eventSet;
            eventSet.insert(event);
            abcViewEventMap.insert(std::make_pair(view, eventSet));
            addEnableEventToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, view, event);
        }else{
            if(it->second.find(event) == it->second.end()){
                it->second.insert(event);
                addEnableEventToTrace(abcOpCount++, dvmThreadSelf()->abcThreadId, view, event);
            }    
        }
        abcUnlockMutex(&gAbc->abcMainMutex);

        }else{
            LOGE("ABC-DONT-LOG: enable event found in a deleted async block. aborting trace creation");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcEnableWindowFocusChangeEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 windowHash = args[1];
 
        Thread* selfThread = dvmThreadSelf();
        std::map<int, AbcCurAsync*>::iterator asyncIt = abcThreadCurAsyncMap.find(selfThread->abcThreadId);
        if(asyncIt != abcThreadCurAsyncMap.end()){
            AbcCurAsync* curAsync = asyncIt->second;
            if(!curAsync->hasMQ || curAsync->asyncId != -1){
                abcLockMutex(selfThread, &gAbc->abcMainMutex);
                addEnableWindowFocusChangeEventToTrace(abcOpCount++, selfThread->abcThreadId, windowHash);
                abcUnlockMutex(&gAbc->abcMainMutex);
            }else{
                LOGE("ABC-ABORT: ENABLE-WINDOW-FOCUS found outside async block in a thread with message queue. aborting trace creation");
                std::ofstream outfile;
                outfile.open(abcLogFile.c_str(), std::ios_base::app);
                outfile << "ABC: ABORT " << "\n";
                outfile.close();
                stopAbcModelChecker();
            }
        }else{
            LOGE("ABC-DONT-LOG: enable window-focus found on an untracked thread. Truncating trace collection.");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }
    }
    
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcTriggerWindowFocusChangeEvent(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        u4 windowHash = args[1];

        Thread* selfThread = dvmThreadSelf();
        AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(selfThread->abcThreadId)->second;
        if(!curAsync->hasMQ || curAsync->asyncId != -1){
            abcLockMutex(selfThread, &gAbc->abcMainMutex);
            addTriggerWindowFocusChangeEventToTrace(abcOpCount++, selfThread->abcThreadId, windowHash);
            abcUnlockMutex(&gAbc->abcMainMutex);
        }else{
            LOGE("ABC-ABORT: TRIGGER-WINDOW-FOCUS found outside async block in a thread with message queue. aborting trace creation");
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
            outfile << "ABC: ABORT " << "\n";
            outfile.close();
            stopAbcModelChecker();
        }
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcSendDbAccessInfo(const u4* args, JValue* pResult){
    //uncomment below code to log database read-writes
    /*
    if(gDvm.isRunABC == true && abcThreadBaseMethodMap.find(dvmThreadSelf()->threadId)
            != abcThreadBaseMethodMap.end()){
    AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(dvmThreadSelf()->abcThreadId)->second;
        if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){

    StringObject* dbPathStr = (StringObject*) args[1];
    if(dbPathStr != NULL){
        char *dbPath = dvmCreateCstrFromString(dbPathStr);
        int accessType = args[2];
        std::string access;
        if(accessType == ABC_WRITE)
            access = "WRITE";
        else if(accessType == ABC_READ)
            access = "READ";
        else{
            LOGE("ABC: invalid access type");
            return;
        }

        std::string pathStr(dbPath);
        abcLockMutex(dvmThreadSelf(), &gAbc->abcMainMutex);
        if(gDvm.isRunABC == true){
            addReadWriteToTrace(abcRWCount++, accessType, NULL, "", 0,
                NULL, pathStr, dvmThreadSelf()->abcThreadId);
            std::ofstream outfile;
            outfile.open(abcLogFile.c_str(), std::ios_base::app);
                        outfile << "rwId:" << abcRWCount-1 << " " << access << " tid:" << dvmThreadSelf()->abcThreadId
                            << " database:" << dbPath << "\n";
            outfile.close(); 
        }
        abcUnlockMutex(&gAbc->abcMainMutex);

    }
    }else{
       LOGE("ABC-DONT-LOG: found a read/write to database in deleted async block. not logging it");
       return;
    }
    }
    */

    RETURN_VOID();
}


static void Dalvik_java_lang_VMThread_abcPrintPostMsg(const u4* args, 
    JValue* pResult){
    if(gDvm.isRunABC == true){

        Thread* selfThread = dvmThreadSelf();
        u4 msgHash = args[1];
        u4 queueHash = args[2];
        int flag = args[3]; 
        s8 delay = GET_ARG_LONG(args,4);
        int isFoQPost = 0, isNegPost = 0;
        if(flag == 0){
            isFoQPost = 0;
            isNegPost = 0;
        }else if(flag == 1){
            isFoQPost = 1;
            isNegPost = 0;
        }else if(flag == 2){
            isFoQPost = 0;
            isNegPost = 1;
        }

//        int nativeEntryId = -1;
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        /*if(it == abcThreadMap.end()){
            abcLockMutex(selfThread, &gAbc->abcMainMutex);
            
            selfThread->abcThreadId = abcThreadCount++;
            abcAddThreadToMap(selfThread, dvmGetThreadName(selfThread).c_str());
            it = abcThreadMap.find(selfThread->abcThreadId);
            if(it != abcThreadMap.end()){
                it->second->isOriginUntracked = true;
            }else{
                LOGE("ABC: error in model checking. A native thread not added to map!");
                gDvm.isRunABC = false;
                abcUnlockMutex(&gAbc->abcMainMutex);
                return;
            }
            addThreadToCurAsyncMap(selfThread->abcThreadId);
              
            abcUnlockMutex(&gAbc->abcMainMutex);
        }*/ //we will not be logging native entry - exit hence wont track native threads


        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        
        /*if(it->second->isOriginUntracked){
            nativeEntryId = abcOpCount++;
        }*/ //commented this as we do not log native enntr-exit

        AbcMsg* msg = (AbcMsg*)malloc(sizeof(AbcMsg));
        msg->msgId = abcMsgCount++;
        msg->postId = abcOpCount++;
        std::map<u4,AbcMsg*>::iterator msgIter = abcUniqueMsgMap.find(msgHash);
        if(msgIter != abcUniqueMsgMap.end()){
            //this can happen only when a posted message got removed in which case the entry in abcUniqueMsgMap will remaiin.
            //so just go ahead and re-assign the key to a new value.
            msgIter->second = msg;   
        }else{
            abcUniqueMsgMap.insert(std::make_pair(msgHash, msg));
        }

        //abcUniqueMsgMap.insert(std::make_pair(msgHash,abcMsgCount++));
        std::map<u4,int>::iterator queueIt = abcQueueToThreadMap.find(queueHash);
        int destThread = -1;
        if(queueIt != abcQueueToThreadMap.end()){
            destThread = queueIt->second;
        }else{
            LOGE("ABC: error in trace generation. An event is posted to a thread whose attachQ is not tracked!");
            stopAbcModelChecker();
	    abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }

             
        abcAsyncStateMap.insert(std::make_pair(msg->msgId, std::make_pair(false, false)));
        //if(it->second->isOriginUntracked == true){
        if(it == abcThreadMap.end()){
            if(gDvm.isRunABC == true){
                //addNativeEntryToTrace(nativeEntryId, selfThread->abcThreadId);
                //msg->postId = addPostToTrace(msg->postId, selfThread->abcThreadId, msg->msgId, destThread, delay, isFoQPost, isNegPost);
                msg->postId = addPostToTrace(msg->postId, abcNativeTid, msg->msgId, destThread, delay, isFoQPost, isNegPost);
                //addNativeExitToTrace(abcOpCount++, selfThread->abcThreadId);
            }
        }else{
            if(gDvm.isRunABC == true){
                msg->postId = addPostToTrace(msg->postId, selfThread->abcThreadId, msg->msgId, destThread, delay, isFoQPost, isNegPost);
            }
        }
        //}
        abcUnlockMutex(&gAbc->abcMainMutex);
    }
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintCallMsg(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 msgHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a CALL on native thread which is not addressed by "
               " implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        } 
        int curTid = selfThread->abcThreadId;

        if(gDvm.isRunABC == true){
            std::map<u4, AbcMsg*>::iterator msgIter = abcUniqueMsgMap.find(msgHash); 
            std::map<int, std::pair<bool,bool> >::iterator msgState = abcAsyncStateMap.find(msgIter->second->msgId);
            AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(curTid)->second;
            curAsync->asyncId = msgIter->second->msgId;
            curAsync->shouldRemove = msgState->second.first;
            if(!curAsync->shouldRemove){
                addCallToTrace(abcOpCount++, curTid, msgIter->second->msgId);
            }

        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintRetMsg(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 msgHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        if(gDvm.isRunABC == true){

            std::map<u4,AbcMsg*>::iterator msgIter = abcUniqueMsgMap.find(msgHash);
            if(abcThreadCurAsyncMap.find(selfThread->abcThreadId)->second->shouldRemove == false){
                addRetToTrace(abcOpCount++, selfThread->abcThreadId, msgIter->second->msgId);
            }else{
                //no need to maintain info of an async block which is deleted
                abcAsyncStateMap.erase(msgIter->second->msgId);
            }
            AbcMsg* tmpPtr = msgIter->second;
            abcUniqueMsgMap.erase(msgHash);
            free(tmpPtr);

            AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(selfThread->abcThreadId)->second;
            curAsync->asyncId = -1;
            curAsync->shouldRemove = false;

        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintRemoveMsg(const u4* args, JValue* pResult){

    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        //int curTid = selfThread->threadId;
        u4 msgHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        if(gDvm.isRunABC == true){
            std::map<u4,AbcMsg*>::iterator msgIter = abcUniqueMsgMap.find(msgHash);
            if(msgIter != abcUniqueMsgMap.end()){
                addRemoveToTrace(abcOpCount++, selfThread->abcThreadId, msgIter->second->msgId);
            } 
            //dont remove from abcUniqueMsgMap. This is problematic if this message is dequeued but has not yet finished execution.
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
        
    }
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcLogIdlePostMsg(const u4* args, JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 msgHash = args[1];
        u4 queueHash = args[2];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);

        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a post idle-handler on native thread which is not addressed by "
               " implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }
        
        AbcMsg* msg = (AbcMsg*)malloc(sizeof(AbcMsg));
        msg->msgId = abcMsgCount++;
        msg->postId = abcOpCount++;
        //due to the way we generate msgHash for simulated post for IdleHandler, this msgHash is unique.
        //Hence, no need to check and insert unlike a normal post where a msg gets reused and can thus have same hash.
        abcUniqueMsgMap.insert(std::make_pair(msgHash, msg));

        std::map<u4,int>::iterator queueIt = abcQueueToThreadMap.find(queueHash);
        int destThread = -1;
        if(queueIt != abcQueueToThreadMap.end()){
            destThread = queueIt->second;
        }else{
            LOGE("ABC: error in trace generation. An event is posted to a thread whose attachQ is not tracked!");
            stopAbcModelChecker();
	    abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }

        msg->postId = addIdlePostToTrace(msg->postId, selfThread->abcThreadId, msg->msgId, destThread);

        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintAttachQueue(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 queueHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a ATTACH-Q on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }

        if(gDvm.isRunABC == true){
            abcQueueToThreadMap.insert(std::make_pair(queueHash, selfThread->abcThreadId));
            addAttachQToTrace(abcOpCount++, selfThread->abcThreadId, queueHash);
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintLoop(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 queueHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a LOOP on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }

        if(gDvm.isRunABC == true){
            abcThreadCurAsyncMap.find(selfThread->abcThreadId)->second->hasMQ = true;
            addLoopToTrace(abcOpCount++, selfThread->abcThreadId, queueHash);
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcPrintExitLoop(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 queueHash = args[1];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a EXIT LOOP on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }

        if(gDvm.isRunABC == true){
            std::map<int, AbcCurAsync*>::iterator curAsyncIt = abcThreadCurAsyncMap.find(selfThread->abcThreadId);
            if(curAsyncIt != abcThreadCurAsyncMap.end() && curAsyncIt->second->hasMQ){
                curAsyncIt->second->hasMQ = false;
                addLoopExitToTrace(abcOpCount++, selfThread->abcThreadId, queueHash);
            }else{
                LOGE("Seeing a LOOP exit on a thread for which LOOP-ON-Q has not been logged.");
                stopAbcModelChecker();
                abcUnlockMutex(&gAbc->abcMainMutex);
                return;
            }
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcLogQueueIdle(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 idleHandlerHash = args[1];
        u4 queueHash = args[2];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a QUEUE_IDLE on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }else{
            addQueueIdleToTrace(abcOpCount++, idleHandlerHash, queueHash, selfThread->abcThreadId);
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }
    
    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcLogAddIdleHandler(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 idleHandlerHash = args[1];
        int queueHash = args[2];

        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a ADD_IDLE_HANDLER on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            return;
        }else{
            AbcCurAsync* curAsync = abcThreadCurAsyncMap.find(selfThread->abcThreadId)->second;
            if(curAsync->shouldRemove == false && (!curAsync->hasMQ || curAsync->asyncId != -1)){
                abcLockMutex(selfThread, &gAbc->abcMainMutex);
                addIdleHandlerToTrace(abcOpCount++, idleHandlerHash, queueHash, selfThread->abcThreadId);
                abcUnlockMutex(&gAbc->abcMainMutex);
            }else{
                LOGE("ABC-DONT-LOG: found a ADD_IDLE_HANDLER in deleted async block. not logging it");
            }
        }
    }

    RETURN_VOID();
}

static void Dalvik_java_lang_VMThread_abcLogRemoveIdleHandler(const u4* args,
    JValue* pResult){
    if(gDvm.isRunABC == true){
        Thread* selfThread = dvmThreadSelf();
        u4 idleHandlerHash = args[1];
        u4 queueHash = args[2];

        abcLockMutex(selfThread, &gAbc->abcMainMutex);
        std::map<int, AbcThread*>::iterator it = abcThreadMap.find(selfThread->abcThreadId);
        if(it == abcThreadMap.end() || it->second->isOriginUntracked == true){
            LOGE("Trace has a REMOVE_IDLE_HANDLER on native thread which is not addressed by "
                "implementation. Cannot continue further");
            stopAbcModelChecker();
            abcUnlockMutex(&gAbc->abcMainMutex);
            return;
        }else{
            addRemoveIdleHandlerToTrace(abcOpCount++, idleHandlerHash, queueHash, selfThread->abcThreadId);
        }
        abcUnlockMutex(&gAbc->abcMainMutex);
    }

    RETURN_VOID();
}

/*Android bug-checker*/

/*
 * static void create(Thread t, long stacksize)
 *
 * This is eventually called as a result of Thread.start().
 *
 * Throws an exception on failure.
 */
static void Dalvik_java_lang_VMThread_create(const u4* args, JValue* pResult)
{
    Object* threadObj = (Object*) args[0];
    s8 stackSize = GET_ARG_LONG(args, 1);

    /* copying collector will pin threadObj for us since it was an argument */
    dvmCreateInterpThread(threadObj, (int) stackSize);
    RETURN_VOID();
}

/*
 * static Thread currentThread()
 */
static void Dalvik_java_lang_VMThread_currentThread(const u4* args,
    JValue* pResult)
{
    UNUSED_PARAMETER(args);

    RETURN_PTR(dvmThreadSelf()->threadObj);
}

/*
 * void getStatus()
 *
 * Gets the Thread status. Result is in VM terms, has to be mapped to
 * Thread.State by interpreted code.
 */
static void Dalvik_java_lang_VMThread_getStatus(const u4* args, JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    Thread* thread;
    int result;

    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    if (thread != NULL)
        result = thread->status;
    else
        result = THREAD_ZOMBIE;     // assume it used to exist and is now gone
    dvmUnlockThreadList();

    RETURN_INT(result);
}

/*
 * boolean holdsLock(Object object)
 *
 * Returns whether the current thread has a monitor lock on the specific
 * object.
 */
static void Dalvik_java_lang_VMThread_holdsLock(const u4* args, JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    Object* object = (Object*) args[1];
    Thread* thread;

    if (object == NULL) {
        dvmThrowNullPointerException("object == null");
        RETURN_VOID();
    }

    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    int result = dvmHoldsLock(thread, object);
    dvmUnlockThreadList();

    RETURN_BOOLEAN(result);
}

/*
 * void interrupt()
 *
 * Interrupt a thread that is waiting (or is about to wait) on a monitor.
 */
static void Dalvik_java_lang_VMThread_interrupt(const u4* args, JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    Thread* thread;

    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    if (thread != NULL)
        dvmThreadInterrupt(thread);
    dvmUnlockThreadList();
    RETURN_VOID();
}

/*
 * static boolean interrupted()
 *
 * Determine if the current thread has been interrupted.  Clears the flag.
 */
static void Dalvik_java_lang_VMThread_interrupted(const u4* args,
    JValue* pResult)
{
    Thread* self = dvmThreadSelf();
    bool interrupted;

    UNUSED_PARAMETER(args);

    interrupted = self->interrupted;
    self->interrupted = false;
    RETURN_BOOLEAN(interrupted);
}

/*
 * boolean isInterrupted()
 *
 * Determine if the specified thread has been interrupted.  Does not clear
 * the flag.
 */
static void Dalvik_java_lang_VMThread_isInterrupted(const u4* args,
    JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    Thread* thread;
    bool interrupted;

    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    if (thread != NULL)
        interrupted = thread->interrupted;
    else
        interrupted = false;
    dvmUnlockThreadList();

    RETURN_BOOLEAN(interrupted);
}

/*
 * void nameChanged(String newName)
 *
 * The name of the target thread has changed.  We may need to alert DDMS.
 */
static void Dalvik_java_lang_VMThread_nameChanged(const u4* args,
    JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    StringObject* nameStr = (StringObject*) args[1];
    Thread* thread;
    int threadId = -1;

    /* get the thread's ID */
    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    if (thread != NULL)
        threadId = thread->threadId;
    dvmUnlockThreadList();

    dvmDdmSendThreadNameChange(threadId, nameStr);
    //char* str = dvmCreateCstrFromString(nameStr);
    //LOGI("UPDATE: threadid=%d now '%s'", threadId, str);
    //free(str);

    RETURN_VOID();
}

/*
 * void setPriority(int newPriority)
 *
 * Alter the priority of the specified thread.  "newPriority" will range
 * from Thread.MIN_PRIORITY to Thread.MAX_PRIORITY (1-10), with "normal"
 * threads at Thread.NORM_PRIORITY (5).
 */
static void Dalvik_java_lang_VMThread_setPriority(const u4* args,
    JValue* pResult)
{
    Object* thisPtr = (Object*) args[0];
    int newPriority = args[1];
    Thread* thread;

    dvmLockThreadList(NULL);
    thread = dvmGetThreadFromThreadObject(thisPtr);
    if (thread != NULL)
        dvmChangeThreadPriority(thread, newPriority);
    //dvmDumpAllThreads(false);
    dvmUnlockThreadList();

    RETURN_VOID();
}

/*
 * static void sleep(long msec, int nsec)
 */
static void Dalvik_java_lang_VMThread_sleep(const u4* args, JValue* pResult)
{
    dvmThreadSleep(GET_ARG_LONG(args,0), args[2]);
    RETURN_VOID();
}

/*
 * public void yield()
 *
 * Causes the thread to temporarily pause and allow other threads to execute.
 *
 * The exact behavior is poorly defined.  Some discussion here:
 *   http://www.cs.umd.edu/~pugh/java/memoryModel/archive/0944.html
 */
static void Dalvik_java_lang_VMThread_yield(const u4* args, JValue* pResult)
{
    UNUSED_PARAMETER(args);

    sched_yield();

    RETURN_VOID();
}

const DalvikNativeMethod dvm_java_lang_VMThread[] = {
    { "abcComputeMemoryUsedByRaceDetector","()V",
        Dalvik_java_lang_VMThread_abcComputeMemoryUsedByRaceDetector },
    { "abcPrintRacesDetectedToFile","()V",
        Dalvik_java_lang_VMThread_abcPrintRacesDetectedToFile },
    { "abcGetCoEnabledEventNonUiRaces","()I",
        Dalvik_java_lang_VMThread_abcGetCoEnabledEventNonUiRaces },
    { "abcGetCoEnabledEventUiRaces","()I",
        Dalvik_java_lang_VMThread_abcGetCoEnabledEventUiRaces },
    { "abcGetCrossPostRaceCount","()I",
        Dalvik_java_lang_VMThread_abcGetCrossPostRaceCount },
    { "abcGetDelayPostRaceCount","()I",
        Dalvik_java_lang_VMThread_abcGetDelayPostRaceCount },
    { "abcGetAsyncRaceCount","()I",
        Dalvik_java_lang_VMThread_abcGetAsyncRaceCount },
    { "abcGetMultiThreadedRaceCount","()I",
        Dalvik_java_lang_VMThread_abcGetMultiThreadedRaceCount },
    { "abcGetFieldCount","()I",
        Dalvik_java_lang_VMThread_abcGetFieldCount },
    { "abcGetEventTriggerCount","()I",
        Dalvik_java_lang_VMThread_abcGetEventTriggerCount },
    { "abcGetAsyncBlockCount","()I",
        Dalvik_java_lang_VMThread_abcGetAsyncBlockCount },
    { "abcGetMessageQueueCount","()I",
        Dalvik_java_lang_VMThread_abcGetMessageQueueCount },
    { "abcGetThreadCount","()I",
        Dalvik_java_lang_VMThread_abcGetThreadCount },
    { "abcGetTraceLength","()I",
        Dalvik_java_lang_VMThread_abcGetTraceLength },
    { "abcStopTraceGeneration","()V",
        Dalvik_java_lang_VMThread_abcStopTraceGeneration },
    { "abcMapInstanceWithIntentId","(II)V",
        Dalvik_java_lang_VMThread_abcMapInstanceWithIntentId },
    { "abcTriggerServiceLifecycle","(Ljava/lang/String;II)V",
        Dalvik_java_lang_VMThread_abcTriggerServiceLifecycle },
    { "abcTriggerLifecycleEvent","(Ljava/lang/String;II)V",
        Dalvik_java_lang_VMThread_abcTriggerLifecycleEvent },
    { "abcEnableLifecycleEvent","(Ljava/lang/String;II)V",
        Dalvik_java_lang_VMThread_abcEnableLifecycleEvent },
    { "abcTriggerBroadcastLifecycle","(Ljava/lang/String;III)V",
        Dalvik_java_lang_VMThread_abcTriggerBroadcastLifecycle },
    { "abcPerformRaceDetection","()I",
        Dalvik_java_lang_VMThread_abcPerformRaceDetection },
    { "abcTriggerEvent","(II)V",
        Dalvik_java_lang_VMThread_abcTriggerEvent },
    { "abcEnableWindowFocusChangeEvent","(I)V",
        Dalvik_java_lang_VMThread_abcEnableWindowFocusChangeEvent },
    { "abcTriggerWindowFocusChangeEvent","(I)V",
        Dalvik_java_lang_VMThread_abcTriggerWindowFocusChangeEvent },
    { "abcAddEnableEventForView","(II)V",
        Dalvik_java_lang_VMThread_abcAddEnableEventForView },
    { "abcRemoveAllEventsOfView","(II)V",
        Dalvik_java_lang_VMThread_abcRemoveAllEventsOfView },
    { "abcForceAddEnableEvent","(II)V",
        Dalvik_java_lang_VMThread_abcForceAddEnableEvent },
    { "abcPrintRemoveMsg","(I)V",
        Dalvik_java_lang_VMThread_abcPrintRemoveMsg },
    { "abcSendDbAccessInfo", "(Ljava/lang/String;I)V", 
        Dalvik_java_lang_VMThread_abcSendDbAccessInfo},
    { "abcPrintAttachQueue","(I)V",
        Dalvik_java_lang_VMThread_abcPrintAttachQueue },
    { "abcPrintLoop","(I)V",
        Dalvik_java_lang_VMThread_abcPrintLoop },
    { "abcPrintExitLoop","(I)V",
        Dalvik_java_lang_VMThread_abcPrintExitLoop },
    { "abcLogIdlePostMsg","(II)V",
        Dalvik_java_lang_VMThread_abcLogIdlePostMsg },
    { "abcLogRemoveIdleHandler","(II)V",
        Dalvik_java_lang_VMThread_abcLogRemoveIdleHandler },
    { "abcLogAddIdleHandler","(II)V",
        Dalvik_java_lang_VMThread_abcLogAddIdleHandler },
    { "abcLogQueueIdle","(II)V",
        Dalvik_java_lang_VMThread_abcLogQueueIdle },
    { "abcPrintRetMsg","(I)V",
        Dalvik_java_lang_VMThread_abcPrintRetMsg },
    { "abcPrintCallMsg","(I)V",
        Dalvik_java_lang_VMThread_abcPrintCallMsg },
    { "abcPrintPostMsg","(IIIJ)V",
        Dalvik_java_lang_VMThread_abcPrintPostMsg },
    { "create",         "(Ljava/lang/Thread;J)V",
        Dalvik_java_lang_VMThread_create },
    { "currentThread",  "()Ljava/lang/Thread;",
        Dalvik_java_lang_VMThread_currentThread },
    { "getStatus",      "()I",
        Dalvik_java_lang_VMThread_getStatus },
    { "holdsLock",      "(Ljava/lang/Object;)Z",
        Dalvik_java_lang_VMThread_holdsLock },
    { "interrupt",      "()V",
        Dalvik_java_lang_VMThread_interrupt },
    { "interrupted",    "()Z",
        Dalvik_java_lang_VMThread_interrupted },
    { "isInterrupted",  "()Z",
        Dalvik_java_lang_VMThread_isInterrupted },
    { "nameChanged",    "(Ljava/lang/String;)V",
        Dalvik_java_lang_VMThread_nameChanged },
    { "setPriority",    "(I)V",
        Dalvik_java_lang_VMThread_setPriority },
    { "sleep",          "(JI)V",
        Dalvik_java_lang_VMThread_sleep },
    { "yield",          "()V",
        Dalvik_java_lang_VMThread_yield },
    { NULL, NULL, NULL },
};
