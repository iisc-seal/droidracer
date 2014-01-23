/* This is a native library to model check Android apps to detect
 * concurrency bugs. This file has functions to model Android app 
 * components
 * Author: Pallavi Maiya
 */


#include "AbcModel.h"

std::map<u4, int> AbcInstanceIntentMap;
std::map<u4, AbcOpWithId*> ActivityStateMap;


/*method acting as Activity state machine and also update 
 *component state in ActivityStateMap
 */
void checkAndUpdateComponentState(int opId, AbcOp* op){
    AbcOpWithId* prevOperation = NULL;
    if(op->arg1 != ABC_LAUNCH){
        if(ActivityStateMap.find(op->arg2->id) != ActivityStateMap.end()){
            prevOperation = ActivityStateMap.find(op->arg2->id)->second;
        }else{
            gDvm.isRunABC = false;
            LOGE("ABC-MISSING: Activity state machine error. %d seen before instantiation", op->arg1);
            return;
        }
    }
    switch(op->arg1){
      case ABC_LAUNCH:
        break;
      case ABC_RESUME:
        break;	  
      case ABC_ACTIVITY_START:
        break;
      case ABC_PAUSE:
        break;
      case ABC_STOP:
        break;
      case ABC_DESTROY:
        break;
      case ABC_RESULT:
        break;
      case ABC_NEW_INTENT:
        break;
    } 
}
