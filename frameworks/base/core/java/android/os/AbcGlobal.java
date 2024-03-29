package android.os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

//Main class for Android bug-checker's JAVA component

public class AbcGlobal {
	public static final int ABC_WRITE = 1;
    public static final int ABC_READ = 2;
    
    public static final int ABC_PAUSE = 1;              //Activity
    public static final int ABC_RESUME = 2;             //Activity
    public static final int ABC_LAUNCH = 3;             //Activity
    public static final int ABC_BIND = 4;               //Application
    public static final int ABC_RELAUNCH = 5;           //Activity
    public static final int ABC_DESTROY = 6;            //Activity
    public static final int ABC_CHANGE_CONFIG = 7;    
    public static final int ABC_STOP = 8;               //Activity
    public static final int ABC_RESULT = 9;             //Activity
    public static final int ABC_CHANGE_ACT_CONFIG = 10;
    public static final int ABC_CREATE_SERVICE = 11;
    public static final int ABC_STOP_SERVICE = 12;
    public static final int ABC_BIND_SERVICE = 13;
    public static final int ABC_UNBIND_SERVICE = 14;
    public static final int ABC_SERVICE_ARGS = 15;
    public static final int ABC_APPBIND_DONE = 16;
    public static final int ABC_CONNECT_SERVICE = 17;
    public static final int ABC_RUN_TIMER_TASK = 18;
    
    public static boolean isPrevEventBackPress = false;
    public static boolean isPrevEventStartActivity = false;
    public static boolean isStartActivityForResult = false;
    public static boolean isBindService = false;
    public static boolean isStartService = false;
    //to detect any being destroyed without going through back
    public static boolean isPauseFinishing = false;
    
    public static String abcLogFile = null;

	private static int abcUniqueMsgId = 5;
	
	private static long traceStartTime = -1;
	private static long traceEndTime = -1;
	private static long raceDetectionStartTime = -1;
	private static long raceDetectionEndTime = -1;
	
	public static HashSet<Integer> abcFinishingActivities = 
			new HashSet<Integer>();
	
	public static void setIsPrevEventStartActivity(boolean state){
		isPrevEventStartActivity = state;
	}
	
	public static void abcCheckAndSetAppToTest(String appName){
		//this should be set for all processes
		if(Looper.mcd != null){
	        Looper.mcd.setPackageName(appName);
        }
		
		//this should be set only for appUT
		File f = new File("/mnt/sdcard/Download/abc.txt");
	    if(f.exists()) {
	    	String appUT = null;
	    	int event_depth = 0;
	    	int initDelay = 0;
			try {
				BufferedReader br = new BufferedReader(new FileReader(
						"/mnt/sdcard/Download/abc.txt"));
				appUT = br.readLine();
				
				//read the depth for event to be executed (should be greater than one)
				//it is in 3rd line of abc.txt
			   	br.readLine(); //skip second line
			   	event_depth = Integer.valueOf(br.readLine());
			    initDelay = Integer.valueOf(br.readLine());
				br.close();	
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(appName.equals(appUT)){
				Looper.mcd.appUT = appName; 
				abcLogFile = "/data/data/" + appUT + "/abc_log.txt";
				ModelCheckingDriver.DEPTH_LIMIT = event_depth;
				ModelCheckingDriver.initDelay = initDelay;
				Log.e("abc", "abcFile: " + abcLogFile + " event-depth-limit:" 
						+ event_depth + " init-delay:" + initDelay);
				Looper.mcd.abcSilentReturn = false;
				
				//collect stats
				abcSetTraceStartTime(SystemClock.uptimeMillis());
			}
        }
	}
	
	public static void abcInitialization(Context appContext){
		 if(Looper.mcd != null && Looper.mcd.getContext() == null){
	        	if(Looper.mcd.getPackageName().equals(Looper.mcd.appUT)){
			        Looper.mcd.setContext(appContext);
			        if(Looper.mcd.getContext() != null){
	                    //database creation
			        	File dbFile = Looper.mcd.getContext().getDatabasePath("abc.db");
	                    if(!dbFile.exists())
	                    { 
	                    	McdDB mcdDB = new McdDB(Looper.mcd.getContext());
					        SQLiteDatabase database = mcdDB.getWritableDatabase();
					        
					        //some initializations 
					        
					        //ui_env for an event without a corresponding UI
					        ContentValues values = new ContentValues();
					        values.put(McdDB.COLUMN_SCREEN_ID, -1);
					        values.put(McdDB.COLUMN_ACTIVITY_ID, -1);
					        values.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, -1);
					        database.insert(McdDB.TABLE_UI_ENV, null,
					                values);
					        
					        Looper.mcd.initKeyPressEventsAndRotateScreen(database);
					        //add entries into event priority table
					        Looper.mcd.initUIEventPriority(database);
					        
					        //initialize text data
					        Looper.mcd.initializeTextAddress(database);
					        Looper.mcd.initializeTextAddressCaps(database);
					        Looper.mcd.initializeTextAllCaps(database);
					        Looper.mcd.initializeTextDate(database);
					        Looper.mcd.initializeTextDefault(database);
					        Looper.mcd.initializeTextEmail(database);
					        Looper.mcd.initializeTextName(database);
					        Looper.mcd.initializeTextNameCaps(database);
					        Looper.mcd.initializeTextDateTime(database);
					        Looper.mcd.initializeTextNumber(database);
					        Looper.mcd.initializeTextNumberFloat(database);
					        Looper.mcd.initializeTextNumberPassword(database);
					        Looper.mcd.initializeTextPassword(database);
					        Looper.mcd.initializeTextPhone(database);
					        Looper.mcd.initializeTextTime(database);
					        Looper.mcd.initializeTextUri(database);
					        
					        try{
						        if(database != null && database.isOpen()){
						        	database.close();
							        mcdDB.close();
						        	database = null;
						        	mcdDB = null;
						        }
						        }catch(SQLiteException e){
						        	Log.e(ModelCheckingDriver.TAG, 
						        			"sqliteException when closing database hit");
						        }
						        catch(NullPointerException e){
						        	Log.e(ModelCheckingDriver.TAG, 
						        			"nullPointerException when closing database hit");
						        }
	                    }else{
	                    	McdDB mcdDB = new McdDB(Looper.mcd.getContext());
	                        SQLiteDatabase database = mcdDB.getReadableDatabase();
	                        
	                        Looper.mcd.getKeyPressEventsAndRotateScreenIds(database);
	                        database.close();
	                        mcdDB.close();
	                        
	                    }
			        	Looper.mcd.initTextDataViewSet();
			        
			        }else{
			        	Log.e("model_checking","context is null!");
			        }
	        	}
	        }
	}
	
	public static synchronized int getCurrentMsgId(){
		return abcUniqueMsgId++;
	}
	
	//if there is already an enabled event entry for this view and event
	//return silently.
	public static void abcCheckAndAddEnableEventToTrace(View v, int eventType){
		if(v.getVisibility() == View.VISIBLE && v.isEnabled()){
		    switch(eventType){
		    case ModelCheckingDriver.EVENT_CLICK: 
		    	if(v.isClickable()){
		    		/*File file = new File(abcLogFile);
		    		String msgTxt = "ENABLE EVENT  view:" + v.toString() + "  hash:"
		    				+ v.hashCode() + "  event:" + String.valueOf(eventType) + "\n";
		    	
		    		try {
		    			byte[] contentInBytes = msgTxt.getBytes();
		    			FileOutputStream fop = new FileOutputStream(file, true);
		    			fop.write(contentInBytes);
		    			fop.flush();
		    			fop.close();
		    		} catch (FileNotFoundException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		} catch (IOException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		}*/
		    		
		    		Thread.currentThread().abcAddEnableEventForView(
		    				v.hashCode(), eventType);
		    		abcEnableSpecialEvents(v, false);
		    	}else{
		    		abcRemoveAllEventsOfView(v, ModelCheckingDriver.EVENT_LONG_CLICK);
		    	}
		    	break;
		    case ModelCheckingDriver.EVENT_LONG_CLICK:
		    	if(v.isLongClickable()){
		    		/*File file = new File(abcLogFile);
		    		String msgTxt = "ENABLE EVENT  view:" + v.toString() + "  hash:"
		    				+ v.hashCode() + "  event:" + String.valueOf(eventType) + "\n";
		    	
		    		try {
		    			byte[] contentInBytes = msgTxt.getBytes();
		    			FileOutputStream fop = new FileOutputStream(file, true);
		    			fop.write(contentInBytes);
		    			fop.flush();
		    			fop.close();
		    		} catch (FileNotFoundException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		} catch (IOException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		}*/
		    		
		    		Thread.currentThread().abcAddEnableEventForView(
		    				v.hashCode(), eventType);
		    	}else{
		    		abcRemoveAllEventsOfView(v, ModelCheckingDriver.EVENT_CLICK);
		    	}
		    	break;
		    }
		}else{
			abcRemoveAllEventsOfView(v, -1);
		}
	}
	
	//add special events like set-text, toggle etc. to enable
	//some of these special events you also need to check isClickable
	//E.g., toggle
	public static void abcEnableSpecialEvents(View v, boolean forceAdd){
		try {
			int viewType = Looper.mcd.getSimplifiedClassOfView(v.getClass());
			HashSet<Integer> splEvents = Looper.mcd.
					getSpecialEventsForView(viewType);
			for(Integer eventType : splEvents){
				if(forceAdd){
					abcForceAddEnableEvent(v, eventType.intValue());
				}else{
					/*File file = new File(abcLogFile);
		    		String msgTxt = "ENABLE EVENT  view:" + v.toString() + "  hash:"
		    				+ v.hashCode() + "  event:" + String.valueOf(eventType) + "\n";
		    	
		    		try {
		    			byte[] contentInBytes = msgTxt.getBytes();
		    			FileOutputStream fop = new FileOutputStream(file, true);
		    			fop.write(contentInBytes);
		    			fop.flush();
		    			fop.close();
		    		} catch (FileNotFoundException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		} catch (IOException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		}*/
		    		
					Thread.currentThread().abcAddEnableEventForView(
							v.hashCode(), eventType.intValue());
				}
			}
			if(EditText.class.isInstance(v)){
				if(forceAdd){
					abcForceAddEnableEvent(v, ModelCheckingDriver.EVENT_SET_TEXT);
				}else{
					/*File file = new File(abcLogFile);
		    		String msgTxt = "ENABLE EVENT  view:" + v.toString() + "  hash:"
		    				+ v.hashCode() + "  event:" + String.valueOf(ModelCheckingDriver.EVENT_SET_TEXT) + "\n";
		    	
		    		try {
		    			byte[] contentInBytes = msgTxt.getBytes();
		    			FileOutputStream fop = new FileOutputStream(file, true);
		    			fop.write(contentInBytes);
		    			fop.flush();
		    			fop.close();
		    		} catch (FileNotFoundException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		} catch (IOException e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    		}*/
		    		
					Thread.currentThread().abcAddEnableEventForView(
							v.hashCode(), ModelCheckingDriver.EVENT_SET_TEXT);
				}
			}
		} catch (McdException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	//this is triggered when a listener gets attached to a view. 
	//This checks if view is enabled and visible. If yes, removes any
	//previous enable events for this view for this event and adds itself.
	//If view is not visible or enabled then it does nothing. In such a case
	//the event is indicated to be enabled only when the view is enabled
	//and becomes visible. We do a force add because the code corresponding
	//to this listener could have been triggered only after this point.
	public static void abcForceAddEnableEvent(View v, int eventCode){
		/*view = 0 indicates the event to be BACK PRESS / MENU CLICK / ROTATE-SCREEN
		 *we useonly force-add for these 3 events and these are never disabled
		 *currently we enable these 3 events after resumeActivity is executed 
		 *by app code 
		 */
		
		int viewHash = 0;
		if(v != null) //for BACK, MENU, ROTATE events
			viewHash = v.hashCode();
		
		/*File file = new File(abcLogFile);
		String msgTxt = "FORCE-ENABLE EVENT  view:" + ((v != null)? v.toString() : "") + "  hash:"
				+ viewHash + "  event:" + String.valueOf(eventCode) + "\n";
	
		try {
			byte[] contentInBytes = msgTxt.getBytes();
			FileOutputStream fop = new FileOutputStream(file, true);
			fop.write(contentInBytes);
			fop.flush();
			fop.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		Thread.currentThread().abcForceAddEnableEvent(viewHash, eventCode);
	}
	

	//remove all events of view - happens when a view
	//is made invisible / disabled etc. do not remove event-type
	//specified in ignore event. If igNoreEvent is long-click
	//then remove click event from list of events of the view
	//and also special events on the view
	//on a high level only 2 types of UI events are possible i.e
	//click and long-click
	public static void abcRemoveAllEventsOfView(View v, int ignoreEvent){
		/*File file = new File(abcLogFile);
		String msgTxt = "REMOVE-EVENT  view:" + v.toString() + "  hash:"
				+ v.hashCode() + "  IgnoreEvent:" + String.valueOf(ignoreEvent);
		msgTxt += "  tid:" + String.valueOf(Thread.currentThread().getId()) 
				+ "\n";
		try {
			byte[] contentInBytes = msgTxt.getBytes();
			FileOutputStream fop = new FileOutputStream(file, true);
			fop.write(contentInBytes);
			fop.flush();
			fop.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		Thread.currentThread().abcRemoveAllEventsOfView(v.hashCode(), ignoreEvent);
	}
	
	public static void abcRemoveEventsEnabledForRootView(View v){
		if(v != null){
			if(v.isEnabled() && v.getVisibility() == View.VISIBLE &&
			        (v.isClickable() || v.isLongClickable())){
				abcRemoveAllEventsOfView(v, -1);
			}
			
			if(ViewGroup.class.isInstance(v)){
				for(int i=0; i < ((ViewGroup)v).getChildCount(); i++){
					abcRemoveEventsEnabledForRootView(((ViewGroup)v).getChildAt(i));
				}
			}
		}
	}
	
	public static void abcForceEnableEvnetsForRootView(View v){
		if(v != null){			
            if(v.isEnabled() && v.getVisibility() == View.VISIBLE){
    			if(v.isClickable()){
    				abcForceAddEnableEvent(v, 
    			    		ModelCheckingDriver.EVENT_CLICK);
    			    abcEnableSpecialEvents(v, true);
    			}
    			if(v.isLongClickable())
    				abcForceAddEnableEvent(v, 
    			    		ModelCheckingDriver.EVENT_LONG_CLICK);
    		}
    				
    		if(ViewGroup.class.isInstance(v)){
    			for(int i=0; i < ((ViewGroup)v).getChildCount(); i++){
    				abcForceEnableEvnetsForRootView(((ViewGroup)v).getChildAt(i));
				}
    		}
		}
	}
	
	public static void abcTriggerEvent(View v, int eventType){
		int viewHash = 0;
		if(v != null) //for BACK, MENU, ROTATE events
			viewHash = v.hashCode();
		
	/*	File file = new File(abcLogFile);
		
		String msgTxt = "TRIGGER EVENT  view:" + ((v != null)? v.toString() : "") + "  hash:"
				+ viewHash + "  event:" + String.valueOf(eventType) + "\n";
		if(v instanceof Button)
			msgTxt += " btnText:" + ((Button)v).getText() + "\n";
		try {
			byte[] contentInBytes = msgTxt.getBytes();
			FileOutputStream fop = new FileOutputStream(file, true);
			fop.write(contentInBytes);
			fop.flush();
			fop.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		Thread.currentThread().abcTriggerEvent(viewHash, eventType);
	}

	public static long abcGetTraceStartTime() {
		return traceStartTime;
	}

	public static void abcSetTraceStartTime(long traceStartTime) {
		AbcGlobal.traceStartTime = traceStartTime;
		Log.e("ABC", "traceStartTime: " + traceStartTime);
	}

	public static long abcGetTraceEndTime() {
		return traceEndTime;
	}

	public static void abcSetTraceEndTime(long traceEndTime) {
		AbcGlobal.traceEndTime = traceEndTime;
		Log.e("ABC", "traceEndTime: " + traceEndTime);
	}

	public static long abcGetRaceDetectionStartTime() {
		return raceDetectionStartTime;
	}

	public static void abcSetRaceDetectionStartTime(long raceDetectionStartTime) {
		AbcGlobal.raceDetectionStartTime = raceDetectionStartTime;
		Log.e("ABC", "raceDetectionStartTime: " + raceDetectionStartTime);
	}

	public static long abcGetRaceDetectionEndTime() {
		return raceDetectionEndTime;
	}

	public static void abcSetRaceDetectionEndTime(long raceDetectionEndTime) {
		AbcGlobal.raceDetectionEndTime = raceDetectionEndTime;
		Log.e("ABC", "raceDetectionEndTime: " + raceDetectionEndTime);
	}
	
}
