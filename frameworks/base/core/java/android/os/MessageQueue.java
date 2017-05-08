/*
 * Copyright (C) 2006 The Android Open Source Project
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
 * The source copyrighted and licensed as above has been modified to add 
 * Android instrumentation code for DroidRacer. Code within the blocks 
 * delimited by "Android bug-checker" are copyrighted and licensed as follows:
 *
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

package android.os;

import android.database.sqlite.SQLiteDatabase;
import android.util.AndroidRuntimeException;
import android.util.Log;

import java.util.ArrayList;

/**
 * Low-level class holding the list of messages to be dispatched by a
 * {@link Looper}.  Messages are not added directly to a MessageQueue,
 * but rather through {@link Handler} objects associated with the Looper.
 * 
 * <p>You can retrieve the MessageQueue for the current thread with
 * {@link Looper#myQueue() Looper.myQueue()}.
 */
public class MessageQueue {
    Message mMessages;
    private final ArrayList<IdleHandler> mIdleHandlers = new ArrayList<IdleHandler>();
    private IdleHandler[] mPendingIdleHandlers;
    private boolean mQuiting;
    boolean mQuitAllowed = true;

    // Indicates whether next() is blocked waiting in pollOnce() with a non-zero timeout.
    private boolean mBlocked;

    @SuppressWarnings("unused")
    private int mPtr; // used by native code
    
    private native void nativeInit();
    private native void nativeDestroy();
    private native void nativePollOnce(int ptr, int timeoutMillis);
    private native void nativeWake(int ptr);

    /**
     * Callback interface for discovering when a thread is going to block
     * waiting for more messages.
     */
    public static interface IdleHandler {
        /**
         * Called when the message queue has run out of messages and will now
         * wait for more.  Return true to keep your idle handler active, false
         * to have it removed.  This may be called if there are still messages
         * pending in the queue, but they are all scheduled to be dispatched
         * after the current time.
         */
        boolean queueIdle();
    }

    /**
     * Add a new {@link IdleHandler} to this message queue.  This may be
     * removed automatically for you by returning false from
     * {@link IdleHandler#queueIdle IdleHandler.queueIdle()} when it is
     * invoked, or explicitly removing it with {@link #removeIdleHandler}.
     * 
     * <p>This method is safe to call from any thread.
     * 
     * @param handler The IdleHandler to be added.
     */
    public final void addIdleHandler(IdleHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Can't add a null IdleHandler");
        }
        synchronized (this) {
        	/*Android bug-checker*/
        	if(AbcGlobal.abcLogFile != null){
        		int msgId = AbcGlobal.getCurrentMsgId();
        		AbcGlobal.abcIdleHandlerToEventMap.put(new AbcPairKey(this.hashCode(), handler.hashCode()), msgId);
        		Thread.currentThread().abcLogIdlePostMsg(msgId, this.hashCode());
//        		Thread.currentThread().abcLogAddIdleHandler(handler.hashCode(), this.hashCode());
        	}
        	/*Android bug-checker*/
            mIdleHandlers.add(handler);
        }
    }

    /**
     * Remove an {@link IdleHandler} from the queue that was previously added
     * with {@link #addIdleHandler}.  If the given object is not currently
     * in the idle list, nothing is done.
     * 
     * @param handler The IdleHandler to be removed.
     */
    public final void removeIdleHandler(IdleHandler handler) {
        synchronized (this) {
            mIdleHandlers.remove(handler);
        }
    }
    
    MessageQueue() {
        nativeInit();
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            nativeDestroy();
        } finally {
            super.finalize();
        }
    }

    final Message next() {
        int pendingIdleHandlerCount = -1; // -1 only during first iteration
        int nextPollTimeoutMillis = 0;

        for (;;) {
            if (nextPollTimeoutMillis != 0) {
                Binder.flushPendingCommands();
            }
            nativePollOnce(mPtr, nextPollTimeoutMillis);

            synchronized (this) {
                // Try to retrieve the next message.  Return if found.
                final long now = SystemClock.uptimeMillis();
                final Message msg = mMessages;
                if (msg != null) {
                    final long when = msg.when;
                    if (now >= when) {
                        mBlocked = false;
                        mMessages = msg.next;
                        msg.next = null;
                        if (false) Log.v("MessageQueue", "Returning message: " + msg);
                        msg.markInUse();
                        return msg;
                    } else {
                    	nextPollTimeoutMillis = (int) Math.min(when - now, Integer.MAX_VALUE);
                    	
                    	/*Android bug-checker*/
                    	if(Looper.getMainLooper() != null &&
                    			Looper.getMainLooper().getThread() == Thread.currentThread())
                        {
                    	 if(Looper.mcd != null && Looper.mcd.getPackageName() != null &&
                    			 Looper.mcd.getPackageName().equals(Looper.mcd.appUT)
                       		  && Looper.mcd.getVisibleActivity() != null &&
                       		  (Looper.mcd.activityResumed || Looper.mcd.isPreviousBackClickEvent)){
                    		 
                         		boolean fireEvent = false;
                         		boolean backtrack = false;

                         		if(Looper.mcd.getLastPausedActivity() == null)
                         			fireEvent = true;
                         		else if(Looper.mcd.getVisibleActivity().hashCode() != 
                         				Looper.mcd.getLastPausedActivity().hashCode())
                 					fireEvent = true;
                 				else if(Looper.mcd.resumedTime > Looper.mcd.pausedTime)
                 					fireEvent = true;
                 				else if(Looper.mcd.isPreviousBackClickEvent && (Looper.mcd.getLastPausedActivity().
                 						hashCode()  == Looper.mcd.getLastDestroyedActivity().hashCode())
                 						&& Looper.mcd.destroyTime > Looper.mcd.pausedTime)
                 					backtrack = true;	 //pressing BACK has taken control outside the app under test
                         		
                         		if(fireEvent){
//                         			ViewRootImpl[] viewRoots = WindowManagerImpl.getDefault().getViewRoots();
//                        			for(int i=0; i<viewRoots.length;i++){
//                        				if(viewRoots[i].getView() instanceof ViewGroup){
//                        					Looper.mcd.traverseGUI((ViewGroup)viewRoots[i].getView());
//                        				}else{
//                        					View v = viewRoots[i].getView();
//                        					Log.v("model_checking", "non viewgroup viewroot " + v.toString() + " detected");
//                        				}
//                        			}
		                    		try {
										Looper.mcd.androidBugChecker();
									} catch (McdException e) {
										// TODO Auto-generated catch block
										Log.e("ABC exception", "Model checking exception raised. check stack trace");
										e.printStackTrace();
									}
                         		}else if(backtrack){
                         			McdDB mcdDB = new McdDB(Looper.mcd.getContext());
                        			SQLiteDatabase database = mcdDB.getWritableDatabase();
                        			Looper.mcd.backtrack(database, mcdDB, ModelCheckingDriver.FLAG_NO_ERROR);
                         		}
                         		
                           }
                    	 
                       }
                       /*Android bug-checker*/                        
                   } 
                } else {
                	nextPollTimeoutMillis = -1;
                	
                	/*Android bug-checker*/
                	if(Looper.getMainLooper() != null &&
                			Looper.getMainLooper().getThread() == Thread.currentThread()){
                    	
                      if(Looper.mcd != null && Looper.mcd.getPackageName() != null &&
                    		 Looper.mcd.getPackageName().equals(Looper.mcd.appUT)){
                    	  
                    	  if(Looper.mcd.getVisibleActivity() != null &&
                    		  Looper.mcd.activityResumed){
                         
                      		boolean fireEvent = false;
                      		boolean backtrack = false;

                      		if(Looper.mcd.getLastPausedActivity() == null)
                      			fireEvent = true;
                      		else if(Looper.mcd.getVisibleActivity().hashCode() != 
                      				Looper.mcd.getLastPausedActivity().hashCode())
              					fireEvent = true;
              				else if(Looper.mcd.resumedTime > Looper.mcd.pausedTime)
              					fireEvent = true;
              				else if(Looper.mcd.getLastDestroyedActivity() != null && Looper.mcd.isPreviousBackClickEvent 
              						&& (Looper.mcd.getLastPausedActivity().hashCode() == 
              						Looper.mcd.getLastDestroyedActivity().hashCode())
             						&& Looper.mcd.destroyTime > Looper.mcd.pausedTime)
             					backtrack = true;	 //pressing BACK has taken control outside the app under test
                      		
                      		if(fireEvent){
		                    	try {
									Looper.mcd.androidBugChecker();
								} catch (McdException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
                      		}else if(backtrack){
                     			McdDB mcdDB = new McdDB(Looper.mcd.getContext());
                    			SQLiteDatabase database = mcdDB.getWritableDatabase();
                    			Looper.mcd.backtrack(database, mcdDB, ModelCheckingDriver.FLAG_NO_ERROR);
                     		}
                    	  }
                    	  //dont allow main thread of app being tested to go to native sleep
	                      nextPollTimeoutMillis = 0;                    	
                      }
                }
                	/*Android bug-checker*/
                }

                // If first time, then get the number of idlers to run.
                if (pendingIdleHandlerCount < 0) {
                    pendingIdleHandlerCount = mIdleHandlers.size();
                }
                if (pendingIdleHandlerCount == 0) {
                    // No idle handlers to run.  Loop and wait some more.
                    mBlocked = true;
                    continue;
                }

                if (mPendingIdleHandlers == null) {
                    mPendingIdleHandlers = new IdleHandler[Math.max(pendingIdleHandlerCount, 4)];
                }
                mPendingIdleHandlers = mIdleHandlers.toArray(mPendingIdleHandlers);
            }

            // Run the idle handlers.
            // We only ever reach this code block during the first iteration.
            for (int i = 0; i < pendingIdleHandlerCount; i++) {
                final IdleHandler idler = mPendingIdleHandlers[i];
                mPendingIdleHandlers[i] = null; // release the reference to the handler
                
                /*Android bug-checker*/
                /* dequeue the dummy idle handler queued when addIdleHandler was performed.
                 * This is done so to be compatible with HB semantics presented in literature.
                 */               
//                int abcQueueIdleMsgId = 0;
                AbcPairKey apk = new AbcPairKey(this.hashCode(), idler.hashCode());
                Integer msgId = AbcGlobal.abcIdleHandlerToEventMap.get(apk);
                if(AbcGlobal.abcLogFile != null){
//                    abcQueueIdleMsgId = AbcGlobal.getCurrentMsgId();
//                    Thread.currentThread().abcPrintPostMsg(abcQueueIdleMsgId, 0, 0);  
                	if(msgId != null)
                		Thread.currentThread().abcPrintCallMsg(msgId.intValue());
                	else{
                		Log.e(ModelCheckingDriver.TAG, "An idle handler was not added to abcIdleHandlerToEventMap.");
                	}
//                    Thread.currentThread().abcLogQueueIdle(idler.hashCode(), this.hashCode());
                }
                /*Android bug-checker*/
                
                boolean keep = false;
                try {
                    keep = idler.queueIdle();
                } catch (Throwable t) {
                    Log.wtf("MessageQueue", "IdleHandler threw exception", t);
                }
                /*Android bug-checker*/
//                if(AbcGlobal.abcLogFile != null){
//                	Thread.currentThread().abcPrintRetMsg(abcQueueIdleMsgId);
//                }
                /*Android bug-checker*/
                if (!keep) {
                    synchronized (this) {
                    	/*Android bug-checker*/
                        if(AbcGlobal.abcLogFile != null){
//                        	Thread.currentThread().abcLogRemoveIdleHandler(idler.hashCode(), this.hashCode());
                        	if(msgId != null){
                        		AbcGlobal.abcIdleHandlerToEventMap.remove(apk);
                        		Thread.currentThread().abcPrintRetMsg(msgId.intValue());
                        	}else{
                            	Log.e(ModelCheckingDriver.TAG, "An idle handler was not added to abcIdleHandlerToEventMap.");
                            }
                        }
                        mIdleHandlers.remove(idler);
                    }
                }
                /*Android bug-checker*/
                else{
                	if(AbcGlobal.abcLogFile != null){
                		if(msgId != null){
                			int newMsgId = AbcGlobal.getCurrentMsgId();
                			AbcGlobal.abcIdleHandlerToEventMap.remove(apk);
                			AbcGlobal.abcIdleHandlerToEventMap.put(apk, newMsgId);
                			Thread.currentThread().abcLogIdlePostMsg(newMsgId, this.hashCode());
                			Thread.currentThread().abcPrintRetMsg(msgId.intValue());
                		}else{
                			Log.e(ModelCheckingDriver.TAG, "An idle handler was not added to abcIdleHandlerToEventMap.");
                		}
                    }
                }
                /*Android bug-checker*/
            }
                        
            // Reset the idle handler count to 0 so we do not run them again.
            pendingIdleHandlerCount = 0;

            // While calling an idle handler, a new message could have been delivered
            // so go back and look again for a pending message without waiting.
            nextPollTimeoutMillis = 0;
        }
    }

    final boolean enqueueMessage(Message msg, long when) {
        if (msg.isInUse()) {
            throw new AndroidRuntimeException(msg
                    + " This message is already in use.");
        }
        if (msg.target == null && !mQuitAllowed) {
            throw new RuntimeException("Main thread not allowed to quit");
        }
        final boolean needWake;
        synchronized (this) {
            if (mQuiting) {
                RuntimeException e = new RuntimeException(
                    msg.target + " sending message to a Handler on a dead thread");
                Log.w("MessageQueue", e.getMessage(), e);
                return false;
            } else if (msg.target == null) {
                mQuiting = true;
            }
            
            /*Android bug-checker
            if(when > 0) {        
               	Thread.currentThread().abcPrintPostMsg(msg.abcMsgId, this.hashCode(), 
            			when - SystemClock.uptimeMillis(), 0, 0);
            }else if(when < 0){
            	//we do not use this when info. passing it in case of any future use on collected trace.
            	Thread.currentThread().abcPrintPostMsg(msg.abcMsgId, this.hashCode(), when, 0, 1); 
            }else { 
            	Thread.currentThread().abcPrintPostMsg(msg.abcMsgId, this.hashCode(), 0, 1, 0);
            }	
            Android bug-checker*/

            msg.when = when;
            //Log.d("MessageQueue", "Enqueing: " + msg);
            Message p = mMessages;
            if (p == null || when == 0 || when < p.when) {
                msg.next = p;
                mMessages = msg;
                needWake = mBlocked; // new head, might need to wake up
            } else {
                Message prev = null;
                while (p != null && p.when <= when) {
                    prev = p;
                    p = p.next;
                }
                msg.next = prev.next;
                prev.next = msg;
                needWake = false; // still waiting on head, no need to wake up
            }
        }
        if (needWake) {
            nativeWake(mPtr);
        }
             
        return true;
    }

    final boolean removeMessages(Handler h, int what, Object object,
            boolean doRemove) {
        synchronized (this) {
            Message p = mMessages;
            boolean found = false;

            // Remove all messages at front.
            while (p != null && p.target == h && p.what == what
                   && (object == null || p.obj == object)) {
                if (!doRemove) return true;
                found = true;
            	/*Android bug-checker*/
            	printRemoveMessage(p);
            	/*Android bug-checker*/
                Message n = p.next;
                mMessages = n;
                p.recycle();
                p = n;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next; 
                if (n != null) {
                    if (n.target == h && n.what == what
                        && (object == null || n.obj == object)) {
                        if (!doRemove) return true;
                        found = true;
		            	/*Android bug-checker*/
            	        printRemoveMessage(n);
		            	/*Android bug-checker*/
                        Message nn = n.next;
                        n.recycle();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
            
            return found;
        }
    }

    final void removeMessages(Handler h, Runnable r, Object object) {
        if (r == null) {
            return;
        }

        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h && p.callback == r
                   && (object == null || p.obj == object)) {
            	/*Android bug-checker*/
    	        printRemoveMessage(p);
            	/*Android bug-checker*/
                Message n = p.next;
                mMessages = n;
                p.recycle();
                p = n;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && n.callback == r
                        && (object == null || n.obj == object)) {
		            	/*Android bug-checker*/
		    	        printRemoveMessage(n);
		            	/*Android bug-checker*/
                        Message nn = n.next;
                        n.recycle();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    final void removeCallbacksAndMessages(Handler h, Object object) {
        synchronized (this) {
            Message p = mMessages;

            // Remove all messages at front.
            while (p != null && p.target == h
                    && (object == null || p.obj == object)) {
            	/*Android bug-checker*/
    	        printRemoveMessage(p);
            	/*Android bug-checker*/
                Message n = p.next;
                mMessages = n;
                p.recycle();
                p = n;
            }

            // Remove all messages after front.
            while (p != null) {
                Message n = p.next;
                if (n != null) {
                    if (n.target == h && (object == null || n.obj == object)) {
                    	/*Android bug-checker*/
		    	        printRemoveMessage(n);
                    	/*Android bug-checker*/
                        Message nn = n.next;
                        n.recycle();
                        p.next = nn;
                        continue;
                    }
                }
                p = n;
            }
        }
    }

    /*Android bug-checker*/
    public void printRemoveMessage(Message msg){ 
        Thread.currentThread().abcPrintRemoveMessage(msg.abcMsgId);  
  	}
    /*Android bug-checker*/
    
    /*
    private void dumpQueue_l()
    {
        Message p = mMessages;
        System.out.println(this + "  queue is:");
        while (p != null) {
            System.out.println("            " + p);
            p = p.next;
        }
    }
    */
}
