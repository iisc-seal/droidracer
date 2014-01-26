/* This is a native library to model check Android apps to detect
 * concurrency bugs. This file has functions to model Android app 
 * components
 * Author: Pallavi Maiya
 */


#include "AbcModel.h"

//a mapping from activity instance to intentID
std::map<u4, int> AbcInstanceIntentMap;
//a mapping from activity instance (or intentID temporarily) 
//to a TRIGGER op
std::map<u4, AbcOpWithId*> ActivityStateMap;
//activity instance is the key
std::map<u4, AbcActivityResult*> ActivityResultMap;

int blankEnableResumeOpid = -1;


void abcMapInstanceWithIntentId(u4 instance, int intentId){
    AbcInstanceIntentMap.insert(std::make_pair(instance, intentId));
    //change key from intentId to instance now that you have activity instance info
    if(ActivityStateMap.find(intentId) != ActivityStateMap.end()){
        AbcOpWithId* tmpOp = ActivityStateMap.find(intentId)->second;
        ActivityStateMap.erase(intentId);
        ActivityStateMap.insert(std::make_pair(instance, tmpOp));
    }else{
        gDvm.isRunABC = false;
        LOGE("ABC-MISSING: Activity state machine error. instance supplied without intentId entry in stateMap");
        return;
    }
}

void checkAndAddToMapIfActivityResult(int opId, AbcOp* op){
    if(op->arg1 == ABC_RESULT){
        AbcActivityResult* ar;
        if(ActivityResultMap.find(op->arg2->id) != ActivityResultMap.end()){
            ar = ActivityResultMap.find(op->arg2->id)->second;
            if(ar->enable2 == NULL){
                ar->enable2 = (AbcOpWithId*)malloc(sizeof(AbcOpWithId));
                ar->enable2->opId = opId;
                ar->enable2->opPtr = op;
            }else{
                ar->enable2->opId = opId;
                ar->enable2->opPtr = op;
            }
        }else{
            ar = (AbcActivityResult*)malloc(sizeof(AbcActivityResult));
            ar->enable2 = NULL;
            ar->enable1 = (AbcOpWithId*)malloc(sizeof(AbcOpWithId));
            ar->enable1->opId = opId;
            ar->enable1->opPtr = op;
            ActivityResultMap.insert(std::make_pair(op->arg2->id, ar));
        }
    }
}

//add edges: ENABLE -> TRIGGER and ENABLE -> postOf(TRIGGER)
//ENABLE to callOf(TRIGGER) should get derived conditional-transitively
void connectEnableAndTriggerLifecycleEvents(int enableOpid, int triggerOpid, AbcOp* triggerOp){
    addEdgeToHBGraph(enableOpid, triggerOpid);    
    if(triggerOp->asyncId != -1){
        AbcAsync* triggerAsync = getAsyncBlockFromId(triggerOp->asyncId);
        if(triggerAsync != NULL){
            addEdgeToHBGraph(enableOpid, triggerAsync->postId);
        }  
        
        //check if trigger is for send_result
        if(triggerOp->arg1 == ABC_RESULT){
            if(ActivityResultMap.find(triggerOp->arg2->id) != ActivityResultMap.end()){
                AbcActivityResult* ar = ActivityResultMap.find(triggerOp->arg2->id)->second;
                addEdgeToHBGraph(ar->enable1->opId, triggerOpid);
                addEdgeToHBGraph(ar->enable2->opId, triggerOpid);
                if(triggerAsync != NULL){
                    addEdgeToHBGraph(ar->enable1->opId, triggerAsync->postId);
                    addEdgeToHBGraph(ar->enable2->opId, triggerAsync->postId);
                }
                //update corresponding AbcActivityResult struct
                ar->trigger = (AbcOpWithId*)malloc(sizeof(AbcOpWithId));
                ar->trigger->opId = triggerOpid;
                ar->trigger->opPtr = triggerOp;
            }
        }
    }
}

bool isEventActivityEvent(int eventId){
    bool isActivityEvent = false;
    switch(eventId){
      case ABC_LAUNCH:
      case ABC_RESUME:
      case ABC_ACTIVITY_START:
      case ABC_PAUSE:
      case ABC_STOP:
      case ABC_DESTROY:
      case ABC_RESULT:
      case ABC_NEW_INTENT:
      case ABC_START_NEW_INTENT:
      case ABC_RELAUNCH: isActivityEvent = true;
           break;
      default: isActivityEvent = false;
    }

    return isActivityEvent;
}

/*method acting as Activity state machine and also update 
 *component state in ActivityStateMap
 */
bool checkAndUpdateComponentState(int opId, AbcOp* op){
    bool updated = false;
    AbcOpWithId* prevOperation = NULL;
    u4 instance = op->arg2->id;
    if(op->arg1 != ABC_LAUNCH){
        if(ActivityStateMap.find(instance) != ActivityStateMap.end()){
            prevOperation = ActivityStateMap.find(instance)->second;
        }else if(!isEventActivityEvent(op->arg1)){
            return updated;
        }else{
            gDvm.isRunABC = false;
            LOGE("ABC-MISSING: Activity state machine error. %d state seen before instantiation", op->arg1);
            return updated;
        }
    }
    switch(op->arg1){
      case ABC_LAUNCH:
        //if any instance with same id remaining clear it
        //should not happen usually. but not considering as a serious error...
        ActivityStateMap.erase(instance); 
 
        //add an entry to activity state tracking map
        /*for LAUNCH instance is actually intentId. Instance will be sent later
         *and this will be rewritten
         */
        prevOperation = (AbcOpWithId*)malloc(sizeof(AbcOpWithId));
        prevOperation->opId = opId;
        prevOperation->opPtr = op;
        ActivityStateMap.insert(std::make_pair(instance, prevOperation));
        updated = true;
        break;
      case ABC_RESUME:
        updated = true;
        break;	  
      case ABC_ACTIVITY_START:
        updated = true;
        break;
      case ABC_PAUSE:
        updated = true;
        break;
      case ABC_STOP:
        updated = true;
        break;
      case ABC_DESTROY:
        updated = true;
        break;
      case ABC_RESULT:
        updated = true;
        break;
      case ABC_NEW_INTENT:
        updated = true;
        break;
      case ABC_START_NEW_INTENT:
        updated = true;
        break;
      case ABC_RELAUNCH:
        updated = true;
        break;
    } 

    return updated;
}
