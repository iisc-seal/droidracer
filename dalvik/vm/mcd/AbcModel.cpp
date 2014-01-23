/* This is a native library to model check Android apps to detect
 * concurrency bugs. This file has functions to model Android app 
 * components
 * Author: Pallavi Maiya
 */


#include "AbcModel.h"

std::map<int, int> AbcInstanceIntentMap;
