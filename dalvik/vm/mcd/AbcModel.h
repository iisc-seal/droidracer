/* This is a native library to model check Android apps to detect
 * concurrency bugs. This is a header for modelling functions 
 * Author: Pallavi Maiya
 */

#ifndef ABCMODEL_H_
#define ABCMODEL_H_

#include <map>

//a mapping from activity instance to intentID
extern std::map<int, int> AbcInstanceIntentMap;

#endif  // ABCMODEL_H_
