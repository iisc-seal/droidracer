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

#include "common.h"

std::string abcLogFile;
std::string binaryLogFile;
std::string binaryLogStringHelperFile;
FILE *abcFp;
std::map<std::string, int> argStringToNumKeyMap;
int abcStringKey = 1;


void stopAbcModelChecker(){
    if(gDvm.isRunABC){
        //close binary log file
        //fclose(abcFp);
        gDvm.isRunABC = false;
    }
}

void serializeOperationIntoFile(int opType, int arg1, u4 arg2, int arg3, int arg4,
    int arg5, int tid, int taskId){
    
    OpLog* op = (OpLog*)malloc(sizeof(OpLog));
    op->opType = opType;
    op->arg1 = arg1;
    op->arg2 = arg2;
    op->arg3 = arg3;
    op->arg4 = arg4;
    op->arg5 = arg5;
    op->tid = tid;
    op->taskId = taskId;

    //write this datastructure into log file
    abcFp = fopen (binaryLogFile.c_str(),"ab");
    fwrite(op, sizeof(OpLog), 1, abcFp);
    fclose(abcFp);
}


bool isHbEdge(int src, int dest){
    return adjGraph[src-1][dest-1];
}

//takes two operation ids as input
void addEdgeToHBGraph(int op1, int op2){
    assert(op1 < abcOpCount && op2 < abcOpCount && op1 != -1 && op2 != -1);
  //  LOGE("ABC: check-add hb edge between %d and %d", op1, op2);
    if(adjGraph[op1 - 1][op2 - 1] == false){
        adjGraph[op1-1][op2-1] = true;    
        //add edge to worklist
        WorklistElem* elem = (WorklistElem*)malloc(sizeof(WorklistElem));
        elem->src = op1;
        elem->dest = op2;
        elem->prev = worklist;
        worklist = elem;
    
        //add this edge to adjMap
        std::map<int, std::pair<Destination*, Source*> >::iterator iter1 = adjMap.find(op1);
        std::map<int, std::pair<Destination*, Source*> >::iterator iter2 = adjMap.find(op2);
    
        if(iter1 != adjMap.end() && iter2 != adjMap.end()){
            Destination* destNode = (Destination*)malloc(sizeof(Destination));
            destNode->dest = op2;
            destNode->prev = iter1->second.first;
            iter1->second.first = destNode;
    
            Source* srcNode = (Source*)malloc(sizeof(Source));
            destNode->dest = op2;
            srcNode->src = op1;
            srcNode->prev = iter2->second.second;
            iter2->second.second = srcNode;
        }else{
            LOGE("ABC-MISSING: adjMap has no entry for %d or %d operations", op1, op2);
        }

/*        std::ofstream outfile;
        outfile.open(abcLogFile.c_str(), std::ios_base::app);
        outfile << "( " << op1 << ", " << op2  << " ) \n";
        outfile.close();  */
    }
 //   LOGE("ABC: exit HB graph");
}

AbcAsync* getAsyncBlockFromId(int asyncId){
    AbcAsync* async = NULL;
    std::map<int, AbcAsync*>::iterator itAsync = abcAsyncMap.find(asyncId);
    if(itAsync != abcAsyncMap.end()){
        async = itAsync->second;
    }

    return async;
}

int getAsyncIdOfOperation(int opId){
    int asyncId = -1;
    std::map<int, AbcOp*>::iterator it = abcTrace.find(opId);
    if(it != abcTrace.end()){
        asyncId = it->second->asyncId;
    }
    return asyncId;
}


