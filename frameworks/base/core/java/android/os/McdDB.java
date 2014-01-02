package android.os;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class McdDB extends SQLiteOpenHelper{
	
	public static final String TABLE_UI_ENV = "UiEnv_Tab";
	public static final String TABLE_SCREEN = "Screen_Tab";
	public static final String TABLE_ACTIVITY = "Activity_Tab";
	public static final String TABLE_UNEXPLORED_EVENTS = "UnexploredEvents_Tab";
	public static final String TABLE_IGNORE_EVENT = "IgnoreEvents_Tab";
	public static final String TABLE_UI_EVENT = "UiEvent_Tab";
	public static final String TABLE_INTENT = "Intent_Tab";
	public static final String TABLE_INTENT_CATEGORY = "IntentCategory_Tab";
	public static final String TABLE_UNEXPLORED_INTENTS = "UnExploredIntents_Tab";
	public static final String TABLE_POPUP = "Popup_Tab";
	public static final String TABLE_VIEW_NODE = "ViewNode_Tab";
	public static final String TABLE_PATH_NODE = "PathNode_Tab";
	public static final String TABLE_PATH = "Path_Tab";
	public static final String TABLE_ERROR_PATH = "ErrorPath_Tab";
	public static final String TABLE_PATH_NODE_DATA = "PatNodeData_Tab";
	public static final String TABLE_FIELD_SPECIFIC_DATA = "FieldSpecificData_Tab";
	public static final String TABLE_INPUT_SPECIFIC_DATA = "InputSpecificData_Tab";
	public static final String TABLE_QUICK_NODE_DATA_MAP = "QuickNodeDataMap_Tab";
	public static final String TABLE_UI_EVENT_RELATIVE_PRIORITY = "UIEventRelativePriority_Tab"; 
	public static final String TABLE_POPUPS_SCREEN = "PopupsScreen_Tab";
	
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_SCREEN_ID = "screenID";
	public static final String COLUMN_ACTIVITY_ID = "activityID";
	//hash value of activity object, has to be updated in every run
	public static final String COLUMN_TMP_ACTIVITY_HASH = "tmpActivityHash";
	public static final String COLUMN_ACTIVITY_NAME = "activityName";
	public static final String COLUMN_SCREEN_HASH = "screenHash";
	public static final String COLUMN_EVENT_ID = "eventID";
	public static final String COLUMN_EVENT_TYPE = "eventType";
	public static final String COLUMN_UI_EVENT_TYPE = "eventType";
	public static final String COLUMN_INTENT_TYPE = "intentType";
	public static final String COLUMN_INTENT_ID = "intentID";
	//public static final String COLUMN_INTENT_CATEGORY_ID = "intentCategoryID";
	public static final String COLUMN_INTENT_ACTION = "intentAction";
	public static final String COLUMN_INTENT_CATEGORY = "intentCategory";
	public static final String COLUMN_INTENT_COMPONENT = "intentComponent";
	public static final String COLUMN_INTENT_DATA = "intentDataUri";
	public static final String COLUMN_INTENT_AVAIL_FLAG = "intentAvailabilityFlag";
	public static final String COLUMN_VIEW_ID = "viewID";
	public static final String COLUMN_NODE_ID = "nodeID";
	public static final String COLUMN_VIEWROOT_LEVEL = "viewRootLevel";
	public static final String COLUMN_VIEW_TYPE = "viewType";
	public static final String COLUMN_GUI_ID = "guiID";
	public static final String COLUMN_RELATIVE_ID = "relativeID";
	public static final String COLUMN_PARENT_ID = "parentID";
	public static final String COLUMN_IS_CLICKABLE = "isClickable";
	public static final String COLUMN_IS_LONG_CLICKABLE = "isLongClickable";
	public static final String COLUMN_TRAIT = "trait";
	public static final String COLUMN_POPUP_ID = "popupID";
	public static final String COLUMN_UI_ENV_ID = "uiEnvID";
	public static final String COLUMN_IS_ANCESTOR_DATA_WIDGET = "isAncestorDataWidget";
	public static final String COLUMN_SCREEN_ACTIVITY_HASH = "screenActivityHash";
	public static final String COLUMN_EXECUTION_STATUS = "executionStatus";
	public static final String COLUMN_IS_CUSTOMIZED = "isDataCustomized";
	public static final String COLUMN_DATA_ID = "dataID";
	public static final String COLUMN_DATA = "data";
	public static final String COLUMN_ORDER = "eventOrder";
	public static final String COLUMN_EXPLORE_DEFAULT = "exploreDefault"; 
	public static final String COLUMN_INPUT_TYPE = "inputType"; 
	public static final String COLUMN_EVENT_PRIORITY = "eventPriority";
	public static final String COLUMN_NEXT_EVENT = "nextEvent";
	public static final String COLUMN_IS_FIRST_NODE = "isFirstNode";
	public static final String COLUMN_HAS_MENU = "activityHasMenu";

	private static final String DATABASE_NAME = "abc.db"; //android bug checker db
	private static final int DATABASE_VERSION = 1;

	  // Database creation sql statement
	  private static final String TABLE_UI_ENV_CREATE = "create table "
	      + TABLE_UI_ENV + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_SCREEN_ID
	      + " integer not null, " + COLUMN_ACTIVITY_ID + " integer not null, "
	      + COLUMN_SCREEN_ACTIVITY_HASH + " integer not null,"
	      + " FOREIGN KEY ("+ COLUMN_SCREEN_ID +") REFERENCES "+ TABLE_SCREEN
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL,"
	      + " FOREIGN KEY ("+ COLUMN_ACTIVITY_ID +") REFERENCES "+ TABLE_ACTIVITY 
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);"; 
	      
	  //we may not use the screen_hash field as it does not seem very useful or safe
	  //as of now keep the screen has as 0 for all
	  private static final String TABLE_SCREEN_CREATE = "create table "
	      + TABLE_SCREEN + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_ACTIVITY_NAME
	      + " text not null, " + COLUMN_SCREEN_HASH
	      + " integer not null);";
	  
	  private static final String TABLE_ACTIVITY_CREATE = "create table "
	      + TABLE_ACTIVITY + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_TMP_ACTIVITY_HASH
	      + " integer, " + COLUMN_ACTIVITY_NAME
	      + " text not null);";
	  
	  //popupID is not set as foreign key: 
	  //if popupID == -1
	  //	then the view is not in a popup window
	  //else
	  //	popupID is a valid popup window's id in popup table &
	  //	the view is on a popup window
	  
	  //root view node on a screen has no parent. Hence we dont make parentID
	  //as foreign key but enforce the constraint programmatically
      //the clicks possible on the view is tracked by UI_EVENT table and hence
	  //we do not store it in VIEW_NODE table. Also, based on if the view is 
	  //disabled or not, or visible or not these properties can change.
	  
	  //IS_ANCESTOR_DATA_WIDGET - tells if view ancestor of the view is a "maybe
	  //data widget". Ex., spinner, radioButton, list item, editText etc.
	  private static final String TABLE_VIEW_NODE_CREATE = "create table "
	      + TABLE_VIEW_NODE + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_VIEW_TYPE
	      + " integer not null, " + COLUMN_GUI_ID
	      + " integer not null, " + COLUMN_RELATIVE_ID
	      + " integer not null, " + COLUMN_IS_ANCESTOR_DATA_WIDGET
	      + " integer not null, " + COLUMN_SCREEN_ID
	      + " integer not null, " + COLUMN_POPUP_ID    
	      + " integer default -1, " + COLUMN_PARENT_ID
	      + " integer not null, " + COLUMN_TRAIT
	      + " text null," 
	      + " FOREIGN KEY ("+ COLUMN_SCREEN_ID +") REFERENCES "+ TABLE_SCREEN 
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
      //if the popup window has been created due to an intent and not ui-event
      //on a view, then column_view_id is set to -1 and event_type refers to 
      //intent id. incorporate this logic when coding
      //hence we do not make column_view_id as a foreign key
	  
	  //note: column ACTIVITY_ID looks like a dumb thing to have as it may not
	  //serve any purpose. Since popup windows are freshly created everytime
	  //just check for identical popup windows in database for that activity and
	  //the action which created the popup and explore only if such a popup is not
	  //already present. 
	  //Remove Activity_ID after you cleanly implement the above logic.
	  
	  //viewRootLevel refers to the number that has to be subtracted from the count of viewRoots
      private static final String TABLE_POPUP_CREATE = "create table "
	      + TABLE_POPUP + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_VIEW_ID
	      + " integer not null, " + COLUMN_EVENT_TYPE
	      + " integer not null, " + COLUMN_ACTIVITY_ID
	      + " integer null," 
	      + " FOREIGN KEY ("+ COLUMN_ACTIVITY_ID +") REFERENCES "+ TABLE_ACTIVITY
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
	  //a table to group popups and refer to a screen
      private static final String TABLE_POPUPS_SCREEN_CREATE = "create table "
    	      + TABLE_POPUPS_SCREEN + "(" + COLUMN_ID
    	      + " integer primary key autoincrement, " + COLUMN_POPUP_ID
    	      + " integer not null, " + COLUMN_SCREEN_ID
    	      + " integer not null, " + COLUMN_VIEWROOT_LEVEL
    	      + " integer not null," 
    	      + " FOREIGN KEY ("+ COLUMN_POPUP_ID +") REFERENCES "+ TABLE_POPUP
    	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL," 
    	      + " FOREIGN KEY ("+ COLUMN_SCREEN_ID +") REFERENCES "+ TABLE_SCREEN
    	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
      //ui_event_type - 0 : single click, 1: long click, 2:text input, 
      //3 onwards are for view specific events
      //for back button press, scrren_rotate and menu button click associated
      //viewID is -1
      private static final String TABLE_UI_EVENT_CREATE = "create table "
	      + TABLE_UI_EVENT + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_VIEW_ID
	      + " integer not null, " + COLUMN_UI_EVENT_TYPE
	      + " integer not null);"; 
//	      + " FOREIGN KEY ("+ COLUMN_VIEW_ID +") REFERENCES "+ TABLE_VIEW_NODE
//	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
	private static final String TABLE_INTENT_CREATE = "create table " + TABLE_INTENT + "(" + COLUMN_ID
			+ " integer primary key autoincrement, " + COLUMN_INTENT_ID + " integer not null, " + COLUMN_INTENT_TYPE
			+ " integer not null, " + COLUMN_INTENT_COMPONENT + " string not null, " + COLUMN_INTENT_AVAIL_FLAG
			+ " integer not null, " + COLUMN_INTENT_DATA + " string null);";

	private static final String TABLE_UNEXPLORED_INTENTS_CREATE = "create table " + TABLE_UNEXPLORED_INTENTS + "(" + COLUMN_ID
			+ " integer primary key autoincrement, " + COLUMN_INTENT_ID + " integer not null, " + COLUMN_INTENT_TYPE
			+ " integer not null, " + COLUMN_INTENT_COMPONENT + " string not null, " + COLUMN_INTENT_AVAIL_FLAG
			+ " integer not null, " + COLUMN_INTENT_DATA + " string null);";

	private static final String TABLE_INTENT_CATEGORY_CREATE = "create table " + TABLE_INTENT_CATEGORY + "("
			+ COLUMN_ID + " integer primary key autoincrement, " + COLUMN_INTENT_ID + " integer not null, "
			+ COLUMN_INTENT_CATEGORY + " string not null," + " FOREIGN KEY (" + COLUMN_INTENT_ID + ") REFERENCES "
			+ TABLE_INTENT + "(" + COLUMN_INTENT_ID + ") ON DELETE SET NULL ON UPDATE SET NULL);";


      //event_id refers to a ui event or intent. the table to refer to is decided 
      //by event type: 0 - ui-event, 1 - intent
      private static final String TABLE_PATH_NODE_CREATE = "create table "
	      + TABLE_PATH_NODE + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_EVENT_ID
	      + " integer not null, " + COLUMN_EVENT_TYPE
	      + " integer not null, " + COLUMN_UI_ENV_ID
	      + " integer not null, " 
	      + " FOREIGN KEY ("+ COLUMN_UI_ENV_ID +") REFERENCES "+ TABLE_UI_ENV
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
      //event_id refers to a ui event or intent. the table to refer to is decided 
      //by event type: 0 - ui-event, 1 - intent
      //nodeID refers to the pathnode at which the ignore event was added
      private static final String TABLE_IGNORE_EVENT_CREATE = "create table "
	      + TABLE_IGNORE_EVENT + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_EVENT_ID
	      + " integer not null, " + COLUMN_EVENT_TYPE
	      + " integer not null, " + COLUMN_UI_ENV_ID
	      + " integer not null, " + COLUMN_NODE_ID
	      + " integer not null,"
	      + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL,"
	      + " FOREIGN KEY ("+ COLUMN_UI_ENV_ID +") REFERENCES "+ TABLE_UI_ENV
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
      private static final String TABLE_UNEXPLORED_EVENTS_CREATE = "create table "
	      + TABLE_UNEXPLORED_EVENTS + "(" + COLUMN_ID
	      + " integer primary key autoincrement, " + COLUMN_NODE_ID
	      + " integer not null, " + COLUMN_EVENT_TYPE
	      + " integer not null, " + COLUMN_EVENT_ID
	      + " integer not null, " + COLUMN_EVENT_PRIORITY
	      + " integer not null," 
	      + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
	      + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
      
      //executionStatus = 1 (execute event corresponding to the node), 
      //				 -1 (ignore this node)
      //whenever path has to be re-executed the first path node is set to 1 before exiting
      //current run and after first finishes it sets the 2nd node and so on..
      //if no node is set to 1 or 2 then scan current app state and perform the next event
      private static final String TABLE_PATH_CREATE = "create table "
	       + TABLE_PATH + "(" + COLUMN_ID
	       + " integer primary key autoincrement, " + COLUMN_NODE_ID
	       + " integer not null, " + COLUMN_EXECUTION_STATUS
	       + " integer not null,"
	       + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
	       + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
	      
      
	  //nextEvent refers to an _id in errorPath table or -1 if its the last event in path
      private static final String TABLE_ERROR_PATH_CREATE = "create table "
   	       + TABLE_ERROR_PATH + "(" + COLUMN_ID
   	       + " integer primary key autoincrement, " + COLUMN_EVENT_ID
   	       + " integer not null, " + COLUMN_EVENT_TYPE
   	       + " integer not null, " + COLUMN_DATA
   	       + " text null, " + COLUMN_NEXT_EVENT 
   	       + " integer not null, " + COLUMN_IS_FIRST_NODE
   	       + " integer not null);";
      
	
     //<pathNodeID, id, isCustomized (0:refers to table (1), 1: refers table (2) >
	 //remove a pathnodeID entry from the table if a non-text input entry is explored
	 //at this pathnode on backtrack, or else update
     private static final String TABLE_PATH_NODE_DATA_CREATE = "create table "
	       + TABLE_PATH_NODE_DATA + "(" + COLUMN_ID
	       + " integer primary key autoincrement, " + COLUMN_IS_CUSTOMIZED
	       + " integer not null, " + COLUMN_DATA_ID
	       + " integer not null, " + COLUMN_NODE_ID 
	       + " integer not null," 
	       + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
	       + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
     
     
     private static final String TABLE_QUICK_NODE_DATA_MAP_CREATE = "create table "
  	       + TABLE_QUICK_NODE_DATA_MAP + "(" + COLUMN_ID
  	       + " integer primary key autoincrement, " + COLUMN_DATA
  	       + " text not null, " + COLUMN_NODE_ID + " integer not null,"
  	       + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
  	       + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
     
     // all data corresponding to guid or inputType is explored in ascending order of
  	 //ORDER. if exploreDefault field is set to 1 then both table 0 & table 1 is explored
  	 // for that data view. 
  	 //later we will put a mechanism to fill these tables by parsing some input file
     //either viewID or gui_id != -1...viewID not being -1 is used to handle default text in 
     //editText fields which do not have guiID
     //nodeID field is added to indicate if the data added was present as default text in the text 
     //field, in which case remove it from field_specific list after exploring all events for that
     //screen. nodeID  = -1 means it was not a default text
     private static final String TABLE_FIELD_SPECIFIC_DATA_CREATE = "create table "
	       + TABLE_FIELD_SPECIFIC_DATA + "(" + COLUMN_ID
	       + " integer primary key autoincrement, " + COLUMN_GUI_ID
	       + " integer not null, " + COLUMN_VIEW_ID 
	       + " integer default -1, " + COLUMN_NODE_ID
	       + " integer default -1, " + COLUMN_DATA
	       + " text not null, " + COLUMN_ACTIVITY_NAME 
	       + " text not null, " + COLUMN_ORDER 
	       + " integer not null, " + COLUMN_EXPLORE_DEFAULT
	       + " integer not null,"
     	   + " FOREIGN KEY ("+ COLUMN_NODE_ID +") REFERENCES "+ TABLE_PATH_NODE
           + "("+ COLUMN_ID +") ON DELETE SET NULL ON UPDATE SET NULL);";
     
     private static final String TABLE_INPUT_SPECIFIC_DATA_CREATE = "create table "
  	       + TABLE_INPUT_SPECIFIC_DATA + "(" + COLUMN_ID
  	       + " integer primary key autoincrement, " + COLUMN_DATA
  	       + " text not null, " + COLUMN_ORDER 
  	       + " integer not null, " + COLUMN_INPUT_TYPE
  	       + " integer not null);";
     
     //<-1, 0> is orientation change event
     //priority 2 > priority 1 ...
     private static final String TABLE_UI_EVENT_RELATIVE_PRIORITY_CREATE = "create table "
    	       + TABLE_UI_EVENT_RELATIVE_PRIORITY + "(" + COLUMN_ID
    	       + " integer primary key autoincrement, " + COLUMN_VIEW_TYPE
    	       + " integer not null, " + COLUMN_UI_EVENT_TYPE 
    	       + " integer not null, " + COLUMN_EVENT_PRIORITY
    	       + " integer not null);";


        
	  public McdDB(Context context) {
	    super(context, DATABASE_NAME, null, DATABASE_VERSION);
	  }
     

	  @Override
	  public void onCreate(SQLiteDatabase database) {
		database.execSQL(TABLE_ACTIVITY_CREATE);
		database.execSQL(TABLE_SCREEN_CREATE);
		database.execSQL(TABLE_UI_ENV_CREATE);
		database.execSQL(TABLE_VIEW_NODE_CREATE);
		database.execSQL(TABLE_POPUP_CREATE);
		database.execSQL(TABLE_UI_EVENT_CREATE);
		database.execSQL(TABLE_INTENT_CREATE);
		database.execSQL(TABLE_UNEXPLORED_INTENTS_CREATE);
		database.execSQL(TABLE_INTENT_CATEGORY_CREATE);
		database.execSQL(TABLE_UNEXPLORED_EVENTS_CREATE);
		database.execSQL(TABLE_IGNORE_EVENT_CREATE);
		database.execSQL(TABLE_PATH_NODE_CREATE);
		database.execSQL(TABLE_PATH_CREATE);
		database.execSQL(TABLE_PATH_NODE_DATA_CREATE);
		database.execSQL(TABLE_FIELD_SPECIFIC_DATA_CREATE);
		database.execSQL(TABLE_INPUT_SPECIFIC_DATA_CREATE);
		database.execSQL(TABLE_QUICK_NODE_DATA_MAP_CREATE);
		database.execSQL(TABLE_UI_EVENT_RELATIVE_PRIORITY_CREATE);
		database.execSQL(TABLE_POPUPS_SCREEN_CREATE);
		database.execSQL(TABLE_ERROR_PATH_CREATE);
	    
	    if (!database.isReadOnly()) {
	        // Enable foreign key constraints
	    	database.execSQL("PRAGMA foreign_keys=ON;");
	      }
	  }

	  @Override
	  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(McdDB.class.getName(),
			"Upgrading database from version " + oldVersion + " to "
				+ newVersion + ", which will destroy all old data");
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACTIVITY);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_SCREEN);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_UI_ENV);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_VIEW_NODE);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_POPUP);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_UI_EVENT);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTENT);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_UNEXPLORED_INTENTS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTENT_CATEGORY);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_UNEXPLORED_EVENTS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_IGNORE_EVENT);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATH_NODE);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATH);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PATH_NODE_DATA);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_FIELD_SPECIFIC_DATA);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_INPUT_SPECIFIC_DATA);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUICK_NODE_DATA_MAP);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_UI_EVENT_RELATIVE_PRIORITY);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_POPUPS_SCREEN);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_ERROR_PATH);
		onCreate(db);
	  }

}


