/* This is a native library to model check Android apps to detect
 * concurrency bugs. This is a header for modelling functions 
 * Author: Pallavi Maiya
 */

#ifndef ABCMODEL_H_
#define ABCMODEL_H_

#include <map>
#include "common.h"

#define ABC_BIND 4
#define ABC_APPBIND_DONE 16
#define ABC_PAUSE 1
#define ABC_RESUME 2
#define ABC_LAUNCH 3
#define ABC_RELAUNCH 5
#define ABC_DESTROY 6
#define ABC_CHANGE_CONFIG 7
#define ABC_STOP 8
#define ABC_RESULT 9
#define ABC_CHANGE_ACT_CONFIG 10
#define ABC_CREATE_SERVICE 11
#define ABC_STOP_SERVICE 12
#define ABC_BIND_SERVICE 13
#define ABC_UNBIND_SERVICE 14
#define ABC_SERVICE_ARGS 15
#define ABC_CONNECT_SERVICE 17
#define ABC_RUN_TIMER_TASK 18
#define ABC_REQUEST_START_SERVICE 19
#define ABC_REQUEST_BIND_SERVICE 20
#define ABC_REQUEST_STOP_SERVICE 21
#define ABC_REQUEST_UNBIND_SERVICE 22
#define ABC_ACTIVITY_START 23
#define ABC_NEW_INTENT 24

//a mapping from activity instance to intentID
extern std::map<u4, int> AbcInstanceIntentMap;

//a mapping from activity instance (or intentID temporarily) 
//to a TRIGGER op
extern std::map<u4, AbcOpWithId*> ActivityStateMap;

void checkAndUpdateComponentState(int opId, AbcOp* op);
#endif  // ABCMODEL_H_
