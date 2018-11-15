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

/* This is the main class driving UI exploration of Android apps
 */

package android.os;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.widget.AbsListView;
import android.widget.AbsSpinner;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.CheckedTextView;
import android.widget.NumberPicker;
import android.widget.RatingBar;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.SlidingDrawer;
import android.widget.StackView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.PopupWindow;
import android.widget.TimePicker;

public class ModelCheckingDriver {
	
	//event type constants
	
	/* Logic
	 * as of now for widgets like number picker, and maybe datepicker too we'll go with default values
	 * We have not handled Popup Menu, PopupWindow, ProgressBar(a subclass of View) specially , which is not a subclass of View
	 * 
	 * Spinners are not like lists. They support only listeners that a View supports. nothing more
	 * 
	 * TextView maintains a list of TextWatcher listeners
	 * Toast widget is read only. So ignore them
	 */
	
	//write a logic to decrement this in case a popup window is created
	//and destroyed withing the same event. maybe you will nedd a 
	//map kind of structure to track this
	public int popupsCreatedOnLastEvent = 0;
	public static HashMap<String, Integer> viewClassMap = new HashMap<String, Integer>();
	
	public static final int FLAG_ERROR = 0;
	public static final int FLAG_NO_ERROR = 1;
	public static final int FLAG_STOP = 2;
	public static final String TAG = "Android-Bug-Checker";
	public static final String TAG_DEBUG = "ABC-DEBUG";
	
	//events like BACK button click, screen rotation etc. will be classified as UI 
	//events with viewID = -1 and some uiEventType
	public static final int UI_EVENT = 0;
	public static final int INTENT_EVENT = 1;
	
	public boolean viewClickDone = false;
	public boolean viewLongClickDone = false;
	public int previousEventActivity = -1;
	public boolean isCorrectViewOnTop = false;
	public boolean clipBoardSet = false;
	
	public static final int PLAY = 2;
	public static final int EXPLORE = 1;
	
	public static final int MODE = EXPLORE; //todo: this should be read from a file
	
	int playNextEvent = -1;
			
	Activity visibleActivity = null;
	Activity lastPausedActivity = null;
	Activity lastDestroyedActivity = null;
	public long resumedTime = 0;
	public long pausedTime = 0;
	public long destroyTime = 0;
	String packageName = null;
	public String appUT = "";
	public String sampleAppClass = "";
	Context context = null;
	public List<PopupWindow> popupWindows = new ArrayList<PopupWindow>();
	
	public boolean isPreviousBackClickEvent = false;
	public boolean isPreviousMenuClickEvent = false;
	
	//set to true when app is waiting to be killed by Android bug-checker server
	public boolean abcSilentReturn = false;
//	public boolean hasMenu = true;
	public boolean activityResumed = false;
	
	private final Handler mcdHandler = new Handler();
	
	private int abcHangCounter = -1;
	//counts the period of inactivity after an event trigger and continues normally after the limit is reached
	private static int HANG_LIMIT = 15; 
	
	HashSet<Integer> textDataViewSet = new HashSet<Integer>();
	
	public int BACK_CLICK_EVENT_ID = -1;
	public int MENU_CLICK_EVENT_ID = -1;
	public int ROTATE_SCREEN_EVENT_ID = -1;
	
	//special event list
	public static final int EVENT_CLICK = 0;
	public static final int EVENT_LONG_CLICK = 1;
	public static final int EVENT_SET_TEXT = 2;
	public static final int EVENT_BACK = 3;
	public static final int EVENT_MENU_CLICK = 4;
	public static final int EVENT_ROTATE_SCREEN = 5;
	
	public static final int KEY_PRESS_EVENT_PRIORITY = 5;
	
	//setDate(long, ture, true) for calendarView; find min-max before settin date and 
	//set a date which is mid
	public static final int EVENT_SET_DATE_ANIMATE = 6; 
	
	//(int year, int month, int day) for datePicker, get min and max and convert this no. to day/month/year
	public static final int EVENT_UPDATE_DATE = 7;	
	
	//setCurrentMinute(int 0-59), setCurrentHour(int 0-23) for TimePicker
	public static final int EVENT_SET_CURRENT_HOUR_MIN = 8;
	
	//setValue(int) for NumberPicker perform getMax and getMin before performing the event
	public static final int EVENT_SET_VALUE = 9;
	
	//setProgress(int) for SeekBar , perform getMax()
	public static final int EVENT_SET_PROGRESS = 10;
	
	//setRating(float) for RatingBar perform getNumStars before setting
	public static final int EVENT_SET_RATING = 11;
	
	//setQuery(String, true) to set a query. use data given by retrieveText as query
	public static final int EVENT_SET_QUERY = 12;
	
	//toggle() for slidingDrawer. ask isOpened() before toggling
	public static final int EVENT_TOGGLE = 13;
		
	//showNext() or showPrev() for StackView depending on getChildCount() and
	// getDisplayedChild() before the event
	public static final int EVENT_SHOW_NEXT_OR_PREV = 14;
	
	/* List of View types 
	 * Used to classify a given view object and apply data view heuristic
	 */
	public static final int VIEW_VIEW = 0;
	public static final int VIEW_WEB_VIEW = 1;
	public static final int VIEW_WEB_TEXT_VIEW = 2;
	public static final int VIEW_AUTO_COMPLETE_TEXT_VIEW = 4;
	public static final int VIEW_BUTTON = 5;
	
	//chronometer is a read only object, it can be set-reset-paused-started if user has provided 
	//other buttons to interact with chronometer. So, dont call start(), stop(),setBase() methods
	//of Chronomete. if the root view of chronometer is clickable
	//do so and do not store its child view etc.
	public static final int VIEW_CHRONOMETER = 9;
	
	//for CheckedTextView, CheckBox, ToggleButton and Switch ask isChecked() and then call toggle()
	//but calling toggle() is same as clicking it..so no need to explicitly call toggle, if you find 
	//behaviour mismatch only then call toggle
	public static final int VIEW_TOGGLE_BUTTON = 10;
	public static final int VIEW_SWITCH = 11;	
	public static final int VIEW_CHECK_BOX = 7;
	public static final int VIEW_CHECKED_TEXT_VIEW = 8;
	public static final int VIEW_RADIO_BUTTON = 29;

	//Note: DigitalClock and AnalogClock widgets are only meant to show system time. If a view 
	//is of this type, dont store its children, perform click/long click on parent if applicable
	public static final int VIEW_DIGITAL_CLOCK = 14;
	public static final int VIEW_ANALOG_CLOCK = 3;
	
	public static final int VIEW_EDIT_TEXT = 15;
	public static final int VIEW_EXPANDABLE_LIST_VIEW = 16;
	public static final int VIEW_GALLERY = 17;
	public static final int VIEW_GRID_LAYOUT = 18;
	public static final int VIEW_TABLE_ROW = 37;
	public static final int VIEW_TABLE_LAYOUT = 38;
	public static final int VIEW_GRID_VIEW = 19;
	public static final int VIEW_FRAME_LAYOUT = 20;
	public static final int VIEW_LINEAR_LAYOUT = 21;
	public static final int VIEW_RELATIVE_LAYOUT = 39;
	public static final int VIEW_IMAGE_BUTTON = 22;
	public static final int VIEW_IMAGE_VIEW = 23;
	
	//ViewAnimator - can have many child views which are reached by programmatically calling
	//showNext(), showPrevious() (check you dont go beyond boundary), or by performing
	//click, fling etc. which will call showPrev() etc. As of now we will use clicks to go
	//to next view. Later on we can perform gestures too (fling etc.)
	//ViewFlipper - periodically flips between Views
	//We do not apply any heuristic on ViewAnimator or Flipper as of now
	public static final int VIEW_VIEW_ANIMATOR = 24;
	public static final int VIEW_VIEW_FLIPPER = 25;
	
	public static final int VIEW_LIST_VIEW = 26;
	public static final int VIEW_SPINNER = 36;
	public static final int VIEW_ABS_LIST_VIEW = 50;
	public static final int VIEW_ABS_SPINNER = 51;
	
	
	public static final int VIEW_MEDIA_CONTROLLER = 27;

	//Use setValue() to set  a number, perform getMinValue() and getMaxValue() before setting
	public static final int VIEW_NUMBER_PICKER = 28;
	public static final int VIEW_CALENDAR_VIEW = 6;
	public static final int VIEW_DATE_PICKER = 12;
	public static final int VIEW_TIME_PICKER = 42;
	public static final int VIEW_DATE_TIME_VIEW = 13;
	
	//Progress bar's progress cant be set by the user..if its clickable or long clickable perform it
	public static final int VIEW_PROGRESS_BAR = 47;
	//ask getMax() and then set a mid-value with setProgress()
	public static final int VIEW_SEEK_BAR = 34;
	
	public static final int VIEW_RADIO_GROUP = 30;
	
	//RatingBar - use methods like setRating and getNumStars to set valid rating
	public static final int VIEW_RATING_BAR = 31;
	
	//Use scrollTo() to perform scroll. 
	//or perform view.requestFocus() and only then perform any event on the view
	public static final int VIEW_SCROLL_VIEW = 32;
	
	//setQuery (find inputType before searching)
	public static final int VIEW_SEARCH_VIEW = 33;
	
	//SlidingDrawer - perform toggle() and ask isOpened() to know the state.
	public static final int VIEW_SLIDING_DRAWER = 48;
	
	//StackView - do getChildCount() for safety and then perform showNext() or showPrevios()
	public static final int VIEW_STACK_VIEW = 35;
	
	public static final int VIEW_TAB_HOST = 37; //this is just a container of tabs and frameViews
	public static final int VIEW_TAB_WIDGET = 40;
	
	public static final int VIEW_TEXT_VIEW = 41;
	public static final int VIEW_TOAST = 43;
	
	//ZoomControls, ZoomButton (R.id.zoomIn, R.id,zoomOut)
	public static final int VIEW_ZOOM_BUTTON = 44;
	public static final int VIEW_ZOOM_CONTROLS = 45;
	
	public static final int TYPE_TEXT_DATE = 1;
	public static final int TYPE_TEXT_TIME = 2;
	public static final int TYPE_TEXT_DATE_TIME = 3;
	public static final int TYPE_TEXT_EMAIL = 4;
	public static final int TYPE_TEXT_PASSWORD = 5;
	public static final int TYPE_TEXT_NAME_CAPS = 6;
	public static final int TYPE_TEXT_NAME = 7;
	public static final int TYPE_TEXT_ADDRESS_CAPS = 8;
	public static final int TYPE_TEXT_ADDRESS = 9;
	public static final int TYPE_TEXT_URI = 10;
	public static final int TYPE_TEXT_ALL_CAPS = 11;
	public static final int TYPE_TEXT_PHONE = 12;
	public static final int TYPE_TEXT_NUMBER_FLOAT = 13;
	public static final int TYPE_TEXT_NUMBER_PASSWORD = 14;
	public static final int TYPE_TEXT_NUMBER_NORMAL = 15;
	public static final int TYPE_TEXT_DEFAULT = 16;
	
	public static int DEPTH_LIMIT = 0; //this should be supplied by a file and set in the beginning
	public static int initDelay = 0;
	public static int abcPort = 0;
	public static String email = null;
	public static String password = null;
	public static String phone = null;
	public static String default_text = null;
	
	public void initKeyPressEventsAndRotateScreen(SQLiteDatabase db){
		ContentValues values;
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_ID, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_BACK);
		Looper.mcd.BACK_CLICK_EVENT_ID = (int) db.insert(McdDB.TABLE_UI_EVENT, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_ID, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_MENU_CLICK);
		Looper.mcd.MENU_CLICK_EVENT_ID = (int) db.insert(McdDB.TABLE_UI_EVENT, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_ID, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_ROTATE_SCREEN);
		Looper.mcd.ROTATE_SCREEN_EVENT_ID = (int) db.insert(McdDB.TABLE_UI_EVENT, null, values);
	}
	
	public void getKeyPressEventsAndRotateScreenIds(SQLiteDatabase db){
		Cursor cursor = db.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, McdDB.COLUMN_VIEW_ID + " = -1 AND " 
				+ McdDB.COLUMN_UI_EVENT_TYPE + " = ?", new String[]{String.valueOf(EVENT_BACK)}, null, null, null);
		cursor.moveToFirst();
		BACK_CLICK_EVENT_ID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));

		cursor.close();
		cursor = null;
		cursor = db.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, McdDB.COLUMN_VIEW_ID + " = -1 AND " 
				+ McdDB.COLUMN_UI_EVENT_TYPE + " = ?", new String[]{String.valueOf(EVENT_MENU_CLICK)}, null, null, null);
		cursor.moveToFirst();
		MENU_CLICK_EVENT_ID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));

		cursor.close();
		cursor = null;
		cursor = db.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, McdDB.COLUMN_VIEW_ID + " = -1 AND " 
				+ McdDB.COLUMN_UI_EVENT_TYPE + " = ?", new String[]{String.valueOf(EVENT_ROTATE_SCREEN)}, null, null, null);
		cursor.moveToFirst();
		ROTATE_SCREEN_EVENT_ID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
		cursor.close();

	}
	
	//greater the integer value higher the priority
	public void initUIEventPriority(SQLiteDatabase db){
		ContentValues values;
		
		//key press events and rotate screen event
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_BACK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, KEY_PRESS_EVENT_PRIORITY);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_MENU_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, KEY_PRESS_EVENT_PRIORITY);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, -1);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_ROTATE_SCREEN);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, KEY_PRESS_EVENT_PRIORITY);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		//long clicks
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SEARCH_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 10);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_EDIT_TEXT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 10);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_AUTO_COMPLETE_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 10);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_WEB_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 10);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_FRAME_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 11);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_LINEAR_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 11);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RELATIVE_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 11);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GRID_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 12);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TABLE_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 12);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TABLE_ROW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 12);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GRID_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 13);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SCROLL_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 13);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_WEB_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 13);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);

		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GALLERY);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 13);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TOAST);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 14);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHECK_BOX);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHECKED_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SWITCH);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TOGGLE_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RADIO_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RATING_BAR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 15);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_PROGRESS_BAR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 16);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RADIO_GROUP);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 16);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SEEK_BAR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 16);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHRONOMETER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 17);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DIGITAL_CLOCK);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 17);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ANALOG_CLOCK);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 17);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DATE_TIME_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 17);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DATE_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 18);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_NUMBER_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 18);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CALENDAR_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 18);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TIME_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 18);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW_ANIMATOR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 19);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW_FLIPPER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 19);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SLIDING_DRAWER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 19);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);

		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 20);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_STACK_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 21);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ZOOM_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 22);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ZOOM_CONTROLS);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 22);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_IMAGE_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 23);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_IMAGE_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 23);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 23);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 23); 
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 27); //27
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ABS_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 27); //27
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SPINNER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 27); //27
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ABS_SPINNER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 27); //27
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_EXPANDABLE_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 27); //27
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		
		//clicks and data events
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TAB_WIDGET);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 31);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 35);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_IMAGE_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 35);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_IMAGE_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 35);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 32);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_FRAME_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_LINEAR_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RELATIVE_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GRID_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TABLE_LAYOUT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TABLE_ROW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TAB_HOST);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GRID_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SCROLL_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_WEB_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RADIO_GROUP);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHRONOMETER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 37);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DATE_TIME_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 37);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ANALOG_CLOCK);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DIGITAL_CLOCK);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 30);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		//note: performing clicks may not be nexessary for the picker objects
		//as you are directly setting date
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DATE_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 38);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TIME_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 38);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_NUMBER_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 38);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CALENDAR_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 38);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_STACK_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 39);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW_ANIMATOR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 40);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_VIEW_FLIPPER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 40);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SLIDING_DRAWER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 41);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ZOOM_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 42);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ZOOM_CONTROLS);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 42);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TOAST);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 43);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 45);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ABS_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 45);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SPINNER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 45);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_ABS_SPINNER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 45);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_EXPANDABLE_LIST_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 45);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SEARCH_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_QUERY);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 59);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_GALLERY);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 47);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_MEDIA_CONTROLLER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 48);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_STACK_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SHOW_NEXT_OR_PREV);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 50);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CALENDAR_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_DATE_ANIMATE);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 51);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_DATE_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_UPDATE_DATE);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 51);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_NUMBER_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_VALUE);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 51);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TIME_PICKER);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_CURRENT_HOUR_MIN);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 51);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SEEK_BAR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_PROGRESS);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 52);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RATING_BAR);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 53);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_TOGGLE_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_TOGGLE);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 54);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHECK_BOX);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 54);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_CHECKED_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 54);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_RADIO_BUTTON);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 54);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_SWITCH);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 54);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_EDIT_TEXT);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_TEXT);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 61);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_AUTO_COMPLETE_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_TEXT);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 61);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
		
		values = new ContentValues();
		values.put(McdDB.COLUMN_VIEW_TYPE, VIEW_WEB_TEXT_VIEW);
		values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_TEXT);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, 61);
		db.insert(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, null, values);
	}
	
	
	public void initializeTextDate(SQLiteDatabase db){
		String data = ""; //yyyy-MM-dd
		ContentValues values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DATE);
//		values.put(McdDB.COLUMN_ORDER, 1);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
		data = "2012-11-11"; //yyyy-MM-dd
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DATE);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextTime(SQLiteDatabase db){
		String data = ""; //hh:mm:ss
		ContentValues values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_TIME);
//		values.put(McdDB.COLUMN_ORDER, 2);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
//		
//		data = "10:10:10"; //hh:mm:ss
//		values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_TIME);
//		values.put(McdDB.COLUMN_ORDER, 1);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
		data = "22:10:10"; //hh:mm:ss
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_TIME);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextDateTime(SQLiteDatabase db){
		String data = ""; //hh:mm:ss
		ContentValues values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DATE_TIME);
//		values.put(McdDB.COLUMN_ORDER, 2);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
//		
//		data = "2012-11-11 10:10:10"; //hh:mm:ss
//		values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DATE_TIME);
//		values.put(McdDB.COLUMN_ORDER, 1);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
		data = "2012-11-11 22:10:10"; //hh:mm:ss
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DATE_TIME);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextEmail(SQLiteDatabase db){
		String data = email;
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_EMAIL);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
        //password of a dummy account created for testing purpose
	public void initializeTextPassword(SQLiteDatabase db){
		String data = password; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_PASSWORD);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextNameCaps(SQLiteDatabase db){
		String data = "JANE DOE"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NAME_CAPS);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextName(SQLiteDatabase db){
		ContentValues values = new ContentValues();
		String data = "Jane Doe"; 
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NAME);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextAddressCaps(SQLiteDatabase db){
		String data = "INSTITUTE OF VERIFICATION, STREET TESTING, NV 12345"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_ADDRESS_CAPS);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextAddress(SQLiteDatabase db){
		String data = "INSTITUTE OF VERIFICATION, STREET TESTING, NV 12345"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_ADDRESS);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextUri(SQLiteDatabase db){
		String data = "http://www.google.com"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_URI);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextAllCaps(SQLiteDatabase db){
		String data = "TESTDATA"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_ALL_CAPS);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextPhone(SQLiteDatabase db){
		ContentValues values = new ContentValues();
		String data = phone;
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_PHONE);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextNumberFloat(SQLiteDatabase db){
		String data = ""; 
		ContentValues values = new ContentValues();
//		values.put(McdDB.COLUMN_DATA, data);
//		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NUMBER_FLOAT);
//		values.put(McdDB.COLUMN_ORDER, 1);
//		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
		data = "3.14"; 
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NUMBER_FLOAT);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextNumberPassword(SQLiteDatabase db){
		ContentValues values = new ContentValues();
		String data = "312456"; 
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NUMBER_PASSWORD);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextNumber(SQLiteDatabase db){
		String data = "2"; 
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_NUMBER_NORMAL);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	}
	
	public void initializeTextDefault(SQLiteDatabase db){
		ContentValues values = new ContentValues();
		
		String data = phone; 		
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DEFAULT);
		values.put(McdDB.COLUMN_ORDER, 2);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
	
		data = email; 		
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DEFAULT);
		values.put(McdDB.COLUMN_ORDER, 3);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
		data = default_text; 		
		values = new ContentValues();
		values.put(McdDB.COLUMN_DATA, data);
		values.put(McdDB.COLUMN_INPUT_TYPE, TYPE_TEXT_DEFAULT);
		values.put(McdDB.COLUMN_ORDER, 0);
		db.insert(McdDB.TABLE_INPUT_SPECIFIC_DATA, null, values);
		
	}
	
	
	static{
	    viewClassMap.put("View",VIEW_VIEW);
		viewClassMap.put("WebView",VIEW_WEB_VIEW);
		viewClassMap.put("WebTextView",VIEW_WEB_TEXT_VIEW);
		viewClassMap.put("AutoCompleteTextView",VIEW_AUTO_COMPLETE_TEXT_VIEW);
		viewClassMap.put("Button",VIEW_BUTTON);
		viewClassMap.put("Chronometer",VIEW_CHRONOMETER);
		viewClassMap.put("ToggleButton",VIEW_TOGGLE_BUTTON);
		viewClassMap.put("Switch",VIEW_SWITCH);
		viewClassMap.put("CheckBox",VIEW_CHECK_BOX);
		viewClassMap.put("CheckedTextView",VIEW_CHECKED_TEXT_VIEW);
		viewClassMap.put("RadioButton",VIEW_RADIO_BUTTON);
		viewClassMap.put("DigitalClock",VIEW_DIGITAL_CLOCK);
		viewClassMap.put("AnalogClock",VIEW_ANALOG_CLOCK);
		viewClassMap.put("EditText",VIEW_EDIT_TEXT);
		viewClassMap.put("ExpandableListView",VIEW_EXPANDABLE_LIST_VIEW);
		viewClassMap.put("Gallery",VIEW_GALLERY);
		viewClassMap.put("GridLayout",VIEW_GRID_LAYOUT);
		viewClassMap.put("TableRow",VIEW_TABLE_ROW);
		viewClassMap.put("TableLayout",VIEW_TABLE_LAYOUT);
		viewClassMap.put("GridView",VIEW_GRID_VIEW);
		viewClassMap.put("FrameLayout",VIEW_FRAME_LAYOUT);
		viewClassMap.put("LinearLayout",VIEW_LINEAR_LAYOUT);
		viewClassMap.put("RelativeLayout",VIEW_RELATIVE_LAYOUT);
		viewClassMap.put("ImageButton",VIEW_IMAGE_BUTTON);
		viewClassMap.put("ImageView",VIEW_IMAGE_VIEW);
		viewClassMap.put("ViewAnimator",VIEW_VIEW_ANIMATOR);
		viewClassMap.put("ViewFlipper",VIEW_VIEW_FLIPPER);
		viewClassMap.put("ListView",VIEW_LIST_VIEW);
		viewClassMap.put("Spinner",VIEW_SPINNER);
		viewClassMap.put("AbsListView",VIEW_ABS_LIST_VIEW);
		viewClassMap.put("AbsSpinner",VIEW_ABS_SPINNER);
		viewClassMap.put("MediaController",VIEW_MEDIA_CONTROLLER);
		viewClassMap.put("NumberPicker",VIEW_NUMBER_PICKER);
		viewClassMap.put("CalendarView",VIEW_CALENDAR_VIEW);
		viewClassMap.put("DatePicker",VIEW_DATE_PICKER);
		viewClassMap.put("TimePicker",VIEW_TIME_PICKER);
		viewClassMap.put("DateTimeView",VIEW_DATE_TIME_VIEW);
		viewClassMap.put("ProgressBar",VIEW_PROGRESS_BAR);
		viewClassMap.put("SeekBar",VIEW_SEEK_BAR);
		viewClassMap.put("RadioGroup",VIEW_RADIO_GROUP);
		viewClassMap.put("RatingBar",VIEW_RATING_BAR);
		viewClassMap.put("ScrollView",VIEW_SCROLL_VIEW);
		viewClassMap.put("SearchView",VIEW_SEARCH_VIEW);
		viewClassMap.put("SlidingDrawer",VIEW_SLIDING_DRAWER);
		viewClassMap.put("StackView",VIEW_STACK_VIEW);
		viewClassMap.put("TabHost",VIEW_TAB_HOST);
		viewClassMap.put("TabWidget",VIEW_TAB_WIDGET);
		viewClassMap.put("TextView",VIEW_TEXT_VIEW);
		viewClassMap.put("Toast",VIEW_TOAST);
		viewClassMap.put("ZoomButton",VIEW_ZOOM_BUTTON);
		viewClassMap.put("ZoomControls",VIEW_ZOOM_CONTROLS);
	}
	
	public void initTextDataViewSet(){
		//initiaize this from a file later
		textDataViewSet.add(EVENT_SET_TEXT);
		textDataViewSet.add(EVENT_SET_QUERY);
	}

	public void setPackageName(String packageName){
		this.packageName = packageName;
	}
	
	public void setVisibleActivity(Activity act){
		this.visibleActivity = act;
	}
	
	public Activity getVisibleActivity(){
		return this.visibleActivity;
	}
	
	public void setLastPausedActivity(Activity act){
		this.lastPausedActivity = act;
	}
	
	public void setLastDestroyedActivity(Activity act){
		this.lastDestroyedActivity = act;
	}
	
	public Activity getLastPausedActivity(){
		return this.lastPausedActivity;
	}
	
	public Activity getLastDestroyedActivity(){
		return this.lastDestroyedActivity;
	}
	
	public String getPackageName(){
		return this.packageName;
	}
	
	public void setContext(Context context){
		this.context = context;
	}
	
	public Context getContext(){
		return this.context;
	}
	
	public boolean isIgnoreChildrenViews(int clazz){
		boolean flag = false;
		switch(clazz){
		case VIEW_CHRONOMETER:
		case VIEW_DATE_PICKER:
		case VIEW_DATE_TIME_VIEW:
		case VIEW_TIME_PICKER:
		case VIEW_ANALOG_CLOCK:
		case VIEW_DIGITAL_CLOCK:
		case VIEW_SWITCH:
		case VIEW_TOGGLE_BUTTON: 
		case VIEW_CHECK_BOX:
		case VIEW_CHECKED_TEXT_VIEW:
		case VIEW_NUMBER_PICKER:
		case VIEW_PROGRESS_BAR:
		case VIEW_SEEK_BAR:
		case VIEW_RATING_BAR:
		case VIEW_SEARCH_VIEW:	flag = true;
		}
		
		return flag;
	}
	
	public HashSet<Integer> getSpecialEventsForView(int viewType){
		HashSet<Integer> eventCodeSet = new HashSet<Integer>();
		
		switch(viewType){
		case VIEW_CALENDAR_VIEW:eventCodeSet.add(EVENT_SET_DATE_ANIMATE);
			break;
		case VIEW_DATE_PICKER:
			eventCodeSet.add(EVENT_UPDATE_DATE);
			break;
		case VIEW_TIME_PICKER:
			eventCodeSet.add(EVENT_SET_CURRENT_HOUR_MIN);
			break;
		case VIEW_NUMBER_PICKER:
			eventCodeSet.add(EVENT_SET_VALUE);
			break;
		case VIEW_RATING_BAR:
			eventCodeSet.add(EVENT_SET_RATING);
			break;
		case VIEW_SEARCH_VIEW:
			eventCodeSet.add(EVENT_SET_QUERY);
			break;
		case VIEW_SEEK_BAR:
			eventCodeSet.add(EVENT_SET_PROGRESS);
			break;
		case VIEW_SLIDING_DRAWER:
			eventCodeSet.add(EVENT_TOGGLE);
			break;
		case VIEW_STACK_VIEW:
			eventCodeSet.add(EVENT_SHOW_NEXT_OR_PREV);
			break;
		}
		return eventCodeSet;
	}
	
	//this maps any given view class to a known view type (by recursively querying superclass).
	//If no matching found keeps it as View and does not classify it as a data widget
	public int getSimplifiedClassOfView(Class viewClass) throws McdException{
		int clazz = -1;
		Integer classCode = viewClassMap.get(viewClass.getSimpleName());
		if(classCode == null)
			clazz = getSimplifiedClassOfView(viewClass.getSuperclass());
		else
			clazz = classCode.intValue();
		if(clazz == -1){
			mcdRaiseException("Android Bug-checker returned an invalid class of" +
						" type -1", null);
		}
		return clazz;
	}
	
	public String getTraitOfView(View v){
		String trait = null;
		if(v instanceof Button){
			if(((Button)v).getText() != null)
				trait = ((Button)v).getText().toString();
		}
		else if(v instanceof EditText){
			if(((EditText)v).getHint() != null)
				trait = ((EditText)v).getHint().toString();
		}
		else if(v instanceof TextView){
			if(((TextView)v).getText() != null)
				trait = ((TextView)v).getText().toString();
		}
		else if(v instanceof RadioButton){
			if(((RadioButton)v).getText() != null)
				trait = ((RadioButton)v).getText().toString();
		}
		else if(v instanceof CheckBox){
			if(((CheckBox)v).getText() != null)
				trait = ((CheckBox)v).getText().toString();
		}
		else if(v instanceof CheckedTextView){
			if(((CheckedTextView)v).getText() != null)
				trait = ((CheckedTextView)v).getText().toString();
		}
		
		return trait;
	}
	
	//match given views of a popup window with existing one in the database one-on-one
	public boolean matchPopupViews(int popupID, View v, int parentID, int parentClass,
			String trait, int guiID, SQLiteDatabase database) throws McdException{
		boolean flag = true;
		int viewClass = getSimplifiedClassOfView(v.getClass());
		//compare trait and class
		if(parentClass == viewClass && guiID == v.getId() && ((guiID != -1)? true :
				(trait != null)?(trait.equals(getTraitOfView(v))):(getTraitOfView(v) == null))){
			if(!isIgnoreChildrenViews(viewClass)){
			Cursor cursor = database.query(McdDB.TABLE_VIEW_NODE, 
					  new String[] { McdDB.COLUMN_ID, McdDB.COLUMN_VIEW_TYPE, McdDB.COLUMN_TRAIT,
						McdDB.COLUMN_GUI_ID}, 
					  McdDB.COLUMN_POPUP_ID + " = ? AND " + 
					  McdDB.COLUMN_PARENT_ID + " = ?", 
					  new String[] {String.valueOf(popupID), String.valueOf(parentID)},
					  null, null, McdDB.COLUMN_RELATIVE_ID + " ASC");
			if(ViewGroup.class.isInstance(v)){
				if(((ViewGroup)v).getChildCount() == cursor.getCount()){
				  if(cursor.moveToFirst()){
					for(int i=0; i < cursor.getCount(); i++){
					  flag = matchPopupViews(popupID, ((ViewGroup)v).getChildAt(i), cursor.getInt(
							  cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID)), cursor.getInt(
									  cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE)), 
									  cursor.getString(
											  cursor.getColumnIndexOrThrow(McdDB.COLUMN_TRAIT)),
									  cursor.getInt(
													  cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE)),
									  database);
					  if(flag == false)
						  break;
					  
					  cursor.moveToNext();
					}
				  }
				}else
					flag = false;
			}else if(cursor.getCount() > 0)
				flag = false;
			
			cursor.close();
			}
		}else
			flag = false;
		
		return flag;
	}
	
	//we consider popups to be identical even if they are on different activity
	//objects. This can be changed later by passing activityID also as a param 
	//and checking it with the activityID of existing corresponding popup
	
	//we create entry for identical popups belonging to different activity objects
	//but do not explore as per current logic
	
	//perform one to one comparison between existing ViewGroups's children and database entry
	
	//viewID - view on which when event eventType is performed to get a popup whose 
	//         rootview is v
	//v is not null
	
	//returns a pair of matching popupID and a set of matching screenIds where viewRootLevel
	//of this popupWindow also matches
	public Pair<Integer, HashSet<Integer>> doIdenticalPopupsExist(int viewID, int eventType, 
			int viewRootLevel, View v, SQLiteDatabase database) throws McdException{
		boolean flag = false;
		Pair<Integer, HashSet<Integer>> matchingPopup = null;
		HashSet<Integer> matchingScreenIds = new HashSet<Integer>();
		
		Cursor cursor = database.query(McdDB.TABLE_POPUP, 
				  new String[] { McdDB.COLUMN_ID }, 
				  McdDB.COLUMN_VIEW_ID + " = ? AND " + 
				  McdDB.COLUMN_EVENT_TYPE + " = ?",
				  new String[] {String.valueOf(viewID), String.valueOf(eventType)}, 
				  null, null, null);
		
		if(cursor.moveToFirst()){
			for(int i=0; i < cursor.getCount(); i++){
				int popupID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				//get row corresponding to root view of popup window
				Cursor cursorTmp = database.query(McdDB.TABLE_VIEW_NODE, 
						  new String[] { McdDB.COLUMN_ID, McdDB.COLUMN_VIEW_TYPE, McdDB.COLUMN_GUI_ID},
						  McdDB.COLUMN_POPUP_ID + " = ? AND " + 
						  McdDB.COLUMN_PARENT_ID + " = ? AND " + 
						  McdDB.COLUMN_RELATIVE_ID + " = ?", 
						  new String[] {String.valueOf(popupID), String.valueOf(-1),
						  String.valueOf(0)}, null, null, null);
				if(cursorTmp.getCount() != 1){
					mcdRaiseException("Android Bug-checker found a screen/popup window " +
								"with more than one root view", database);
				}
				
				cursorTmp.moveToFirst();
				int tmpViewID = cursorTmp.getInt(
						cursorTmp.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				int viewType = cursorTmp.getInt(
						cursorTmp.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE));
				int guiID = cursorTmp.getInt(
						cursorTmp.getColumnIndexOrThrow(McdDB.COLUMN_GUI_ID));
				cursorTmp.close();
				cursorTmp = null;
				
				flag = matchPopupViews(popupID, v, tmpViewID, viewType, null,
								guiID, database);
				if(flag){ //found a matching popup
					Cursor popupScreens = database.query(McdDB.TABLE_POPUPS_SCREEN, new String[]{
							McdDB.COLUMN_SCREEN_ID}, 
							McdDB.COLUMN_POPUP_ID + " = ? AND " + 
							McdDB.COLUMN_VIEWROOT_LEVEL + " = ?", 
							new String[]{String.valueOf(popupID), String.valueOf(viewRootLevel)},
							null, null, null);
					if(popupScreens.moveToFirst()){
						for(int k=0; k<popupScreens.getCount(); k++){
							matchingScreenIds.add(popupScreens.getInt(
									popupScreens.getColumnIndexOrThrow(McdDB.COLUMN_SCREEN_ID)));
							popupScreens.moveToNext();
						}
					}else{
						mcdRaiseException("Android Bug-checker hit a popup window" +
									" belonging to no screen", database);
					}
					matchingPopup = new Pair<Integer, HashSet<Integer>>(popupID, matchingScreenIds);
					popupScreens.close();
					popupScreens = null;
					break;
				}
				
				cursor.moveToNext();
			}
			
			cursor.close();
		}
		return matchingPopup;
	}
	
	public boolean matchScreenViews(int viewID, View v, SQLiteDatabase database) throws McdException{
		boolean doesMatch = true;
		if(!isIgnoreChildrenViews(getSimplifiedClassOfView(v.getClass()))){
		Cursor cursor = database.query(McdDB.TABLE_VIEW_NODE, 
				  new String[] { McdDB.COLUMN_ID, McdDB.COLUMN_VIEW_TYPE, McdDB.COLUMN_GUI_ID, 
				  McdDB.COLUMN_TRAIT}, 
				  McdDB.COLUMN_POPUP_ID + " = ? AND " + 
				  McdDB.COLUMN_PARENT_ID + " = ?", 
				  new String[] { String.valueOf(-1), String.valueOf(viewID)},
				  null, null, McdDB.COLUMN_RELATIVE_ID + " ASC");
		if(ViewGroup.class.isInstance(v)){
			if(((ViewGroup)v).getChildCount() == cursor.getCount()){
				 if(cursor.moveToFirst()){
					for(int i=0; i < cursor.getCount(); i++){
					  String trait = cursor.getString(cursor.getColumnIndexOrThrow(McdDB.COLUMN_TRAIT));
					  View tmpView = ((ViewGroup)v).getChildAt(i);
					  if(cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE)) ==
							  getSimplifiedClassOfView(tmpView.getClass()) && 
							  cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_GUI_ID)) 
							  == tmpView.getId() && ((tmpView.getId() != -1)?true:
									  (trait != null)?(trait.equals(getTraitOfView(tmpView))):
										  (getTraitOfView(tmpView) == null))){
						  
						  doesMatch = matchScreenViews(cursor.getInt(cursor.getColumnIndexOrThrow(
								  McdDB.COLUMN_ID)), ((ViewGroup)v).getChildAt(i), database);
					  
						  if(doesMatch == false)
							  break;
					   }else{
						   doesMatch = false;
						   break;
					   }
					   cursor.moveToNext();
					}
				 }
				 
			}else{
				doesMatch = false;
			}
		}else if(cursor.getCount() > 0)
			doesMatch = false;
		
		cursor.close();
		}
		return doesMatch;
	}
	
	public int findIdenticalActivityScreen(View v, String activity, SQLiteDatabase database) 
			throws McdException{
		int screenID = -1;
		Cursor cursor = database.query(McdDB.TABLE_SCREEN, 
				  new String[] { McdDB.COLUMN_ID }, 
				  McdDB.COLUMN_ACTIVITY_NAME + " = ?", 
				  new String[] {activity}, null, null, null);
		
		if(cursor.moveToFirst()){
			for(int i=0; i < cursor.getCount(); i++){
				Cursor tmpCursor = database.query(McdDB.TABLE_VIEW_NODE, 
						  new String[] { McdDB.COLUMN_ID, McdDB.COLUMN_VIEW_TYPE,
						  McdDB.COLUMN_GUI_ID }, 
						  McdDB.COLUMN_SCREEN_ID + " = ? AND " + McdDB.COLUMN_RELATIVE_ID
						  + " = ? AND " + McdDB.COLUMN_PARENT_ID + " = ? AND "
						  + McdDB.COLUMN_POPUP_ID + " = ?", 
						  new String[] {String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(
								  McdDB.COLUMN_ID))), String.valueOf(0), String.valueOf(-1), 
								  String.valueOf(-1)}, null, null, null);
				if(tmpCursor.getCount() != 1){
					try {
						throw new McdException("Android Bug-checker hit a screen with no root view or" +
								" more than one root view");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				tmpCursor.moveToFirst();
				int viewID = tmpCursor.getInt(tmpCursor.getColumnIndexOrThrow(
						  McdDB.COLUMN_ID));
				int viewType = tmpCursor.getInt(tmpCursor.getColumnIndexOrThrow(
						McdDB.COLUMN_VIEW_TYPE));
				int guid = tmpCursor.getInt(
						tmpCursor.getColumnIndexOrThrow(McdDB.COLUMN_GUI_ID));
				tmpCursor.close();
				tmpCursor = null;
				
				boolean matchFlag = false;
				if(viewType == getSimplifiedClassOfView(v.getClass()) && guid == v.getId()){
					  
					  matchFlag = matchScreenViews(viewID, v, database);
				}
				
				if(matchFlag){
					screenID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
					break;
				}
				cursor.moveToNext();
			}
		}
		cursor.close();
		
		return screenID;
	}

	public boolean isViewTypeDataWidget(int viewClass){
		boolean flag = false;
		switch(viewClass){
		case VIEW_LIST_VIEW:
		case VIEW_ABS_LIST_VIEW:
		case VIEW_CALENDAR_VIEW: 
		case VIEW_CHECK_BOX:
		case VIEW_CHECKED_TEXT_VIEW:
		case VIEW_AUTO_COMPLETE_TEXT_VIEW:
		case VIEW_DATE_PICKER:
		case VIEW_DATE_TIME_VIEW:
		case VIEW_EDIT_TEXT:
		case VIEW_EXPANDABLE_LIST_VIEW:
		case VIEW_GALLERY:
		case VIEW_NUMBER_PICKER:
		case VIEW_RADIO_BUTTON:
		case VIEW_SEARCH_VIEW:
		case VIEW_SEEK_BAR:
		case VIEW_PROGRESS_BAR:
		case VIEW_SPINNER:
		case VIEW_ABS_SPINNER:
		case VIEW_SWITCH:
		case VIEW_TIME_PICKER:
		case VIEW_TOGGLE_BUTTON:flag = true;
		}
		
		return flag;
	}
	
	public void storeChildrenViews(ViewGroup parent, long parentID, long screenID, int popupID,
			int isAncestorDataView, SQLiteDatabase database) throws McdException{
		ContentValues values;
		int tmpIsAncestorData;
		
		for(int i=0; i<parent.getChildCount(); i++){
			tmpIsAncestorData = 1; //if ancestor is not a data case is handled below
			int viewClass = getSimplifiedClassOfView(parent.getChildAt(i).getClass());
			
			if(isAncestorDataView == 0){
				tmpIsAncestorData = isViewTypeDataWidget(viewClass) ? 1 : 0;
			}
			
			values = new ContentValues();
	        values.put(McdDB.COLUMN_SCREEN_ID, screenID);
	        values.put(McdDB.COLUMN_GUI_ID, parent.getChildAt(i).getId());
	        values.put(McdDB.COLUMN_RELATIVE_ID, i);
	        values.put(McdDB.COLUMN_IS_ANCESTOR_DATA_WIDGET, tmpIsAncestorData);
	        values.put(McdDB.COLUMN_POPUP_ID, popupID);
	        values.put(McdDB.COLUMN_PARENT_ID, parentID);
	        values.put(McdDB.COLUMN_VIEW_TYPE, viewClass);
	        values.put(McdDB.COLUMN_TRAIT, getTraitOfView(parent.getChildAt(i)));
	        
	        long viewID = database.insert(McdDB.TABLE_VIEW_NODE, null, values);
	        if(!(viewID > -1)){
				mcdRaiseException("Android Bug-checker returned a database row for ViewBode" +
							" with primary key being -1", database);
	        }
	        
	        if(ViewGroup.class.isInstance(parent.getChildAt(i)) && 
	        		!isIgnoreChildrenViews(viewClass)){
	          storeChildrenViews((ViewGroup)parent.getChildAt(i), viewID, screenID, popupID, 
	        		  tmpIsAncestorData, database);
	        }
		}
	}
	
	//store views on a screen in the database 
	//returns id of ui_env row created. This can be used to store in the pathNode
	public int[] storeScreenElements(View rootView, int activityID, String activity, 
			int popupID, long hash, SQLiteDatabase database) throws McdException{
		int ui_envID = -1;
		int screenID = -1;
		ContentValues values = null; 
		int[] result = new int[2];
		//creation of screen entry and ui_env entry for popup windows is handled separately
		if(popupID == -1){
	        values = new ContentValues();
	        values.put(McdDB.COLUMN_ACTIVITY_NAME, activity);
	        values.put(McdDB.COLUMN_SCREEN_HASH, 0);
	        screenID = (int) database.insert(McdDB.TABLE_SCREEN, null,
	                values);
	        
	        if(!(screenID > -1)){
				mcdRaiseException("Android Bug-checker returned a datbase row for" +
							" Screen table with primary key -1", database);
	        }
	        
	        values = new ContentValues();
	        values.put(McdDB.COLUMN_SCREEN_ID, screenID);
	        values.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
	        values.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, hash);
	        ui_envID = (int) database.insert(McdDB.TABLE_UI_ENV, null, values);
		}
        
        	int viewClass = getSimplifiedClassOfView(rootView.getClass());
	        values = new ContentValues();
	        values.put(McdDB.COLUMN_SCREEN_ID, screenID);
	        values.put(McdDB.COLUMN_GUI_ID, rootView.getId());
	        values.put(McdDB.COLUMN_RELATIVE_ID, 0);
	        values.put(McdDB.COLUMN_IS_ANCESTOR_DATA_WIDGET, 0);
	        values.put(McdDB.COLUMN_POPUP_ID, popupID);
	        values.put(McdDB.COLUMN_PARENT_ID, -1);
	        values.put(McdDB.COLUMN_VIEW_TYPE, viewClass);
	        
	        long parentID = database.insert(McdDB.TABLE_VIEW_NODE, null, values);
	        
	        if(ViewGroup.class.isInstance(rootView) && !isIgnoreChildrenViews(viewClass)){
	          storeChildrenViews((ViewGroup)rootView, parentID, screenID, popupID, 0, database);
	        }
        
	    result[0] = ui_envID;
	    result[1] = screenID;
        return result;
	}
	
	//uiEnvId of current screen or corresponding to (-1,-1) if no ui
	public int createDummyPathNode(int uiEnvID, SQLiteDatabase database) throws McdException{
		int pathNodeID;
        ContentValues values = new ContentValues();
        values.put(McdDB.COLUMN_UI_ENV_ID, uiEnvID);
        values.put(McdDB.COLUMN_EVENT_ID, -1);
        values.put(McdDB.COLUMN_EVENT_TYPE, -1);
        pathNodeID = (int) database.insert(McdDB.TABLE_PATH_NODE, null, values);
        if(!(pathNodeID > -1)){
			mcdRaiseException("Android Bug-checker returned a database row for PathNode" +
						" table with primary key -1", database);
        }
        
        return pathNodeID;
	}
	
	public void updatePathNodeWithEvent(int pathNodeID, int eventID, int eventType, 
			SQLiteDatabase database){
        ContentValues values = new ContentValues();
        values.put(McdDB.COLUMN_EVENT_ID, eventID);
        values.put(McdDB.COLUMN_EVENT_TYPE, eventType);
        database.update(McdDB.TABLE_PATH_NODE, values, McdDB.COLUMN_ID + " = ?",
        	new String[]{String.valueOf(pathNodeID)});
        
	}
	
	//eventType: 0 - ui-event, 1 - intent
	public void addEventToUnexploredList(long eventID, int eventType, int pathNodeID, 
			int priority, SQLiteDatabase database) throws McdException{
		
		ContentValues values = new ContentValues();
		
		values.put(McdDB.COLUMN_EVENT_ID, eventID);
		values.put(McdDB.COLUMN_EVENT_TYPE, eventType);
		values.put(McdDB.COLUMN_NODE_ID, pathNodeID);
		values.put(McdDB.COLUMN_EVENT_PRIORITY, priority);
		
		long insertID = database.insert(McdDB.TABLE_UNEXPLORED_EVENTS, null, values);
		
		if(!(insertID > -1)){
			mcdRaiseException("Android Bug-checker returned a database row for" +
						" for UnexploredEvents table with primary key -1", database);
		}
	}
	
	public boolean isEditTextWritable(EditText et){
		boolean isWriteable = true;
		
		// write code to find out if editText has not been made read only. for this
		//some library instrumentation may be needed which has not yet been done.
		
		return isWriteable;
	}
	
	//store UI events in ui-event table if the event is not already present,
	//then add this event to unexplored events for the corresponding path node
	public int storeViewEvents(View v, int viewID, int pathNodeID, int parentViewType, 
			SQLiteDatabase database) throws McdException{
		ContentValues values;
		long eventID;
		Cursor cursor;
		int priority;
		int viewType = getSimplifiedClassOfView(v.getClass());		
				
		if(v.getVisibility() == View.VISIBLE && v.isEnabled()){
			boolean ignoreEvent = false;
			if(viewType == VIEW_SEARCH_VIEW){
				ignoreEvent = true; //dont trigger click/long-click on search view
			}else if(v instanceof EditText){
				if(((EditText)v).getText() == null || 
						((EditText)v).getText().toString() == ""){
					ignoreEvent = true; //trigger click/long-click only for non empty string
				}
			}
			
			if(!ignoreEvent){
			if(v.isClickable() && !(v instanceof EditText) &&
					!(parentViewType == VIEW_LIST_VIEW || parentViewType == VIEW_SPINNER || 
					parentViewType == VIEW_ABS_LIST_VIEW || parentViewType == VIEW_ABS_SPINNER ||
					parentViewType == VIEW_EXPANDABLE_LIST_VIEW)){
				cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
						McdDB.COLUMN_VIEW_ID + " = ? AND " + 
						McdDB.COLUMN_EVENT_TYPE + " = ?",
						new String[]{String.valueOf(viewID), String.valueOf(EVENT_CLICK)},
						null, null, null);
				if(!cursor.moveToFirst()){
					values = new ContentValues();
			        values.put(McdDB.COLUMN_VIEW_ID, viewID);
			        values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
		        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
					if(!(eventID > -1)){
						mcdRaiseException("Android Bug-checker returned a database row for" +
								" UiEvent table with primary key -1", database);
					}
				}else{
					eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				}
				cursor.close();
				cursor = null;
				//we do not add click and long click directly on list/spinner but on their children
				if(!(viewType == VIEW_LIST_VIEW || viewType == VIEW_SPINNER || 
						viewType == VIEW_ABS_LIST_VIEW || viewType == VIEW_ABS_SPINNER || 
						viewType == VIEW_EXPANDABLE_LIST_VIEW)){
				cursor = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
						McdDB.COLUMN_EVENT_PRIORITY}, 
						McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
						McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
						new String[]{String.valueOf(viewType), String.valueOf(EVENT_CLICK)},
						null, null, null);
				if(cursor.moveToFirst())
					priority = cursor.getInt(cursor.getColumnIndexOrThrow(
							McdDB.COLUMN_EVENT_PRIORITY));
				else
					priority = 5; //some default value
				
		        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
		        cursor.close();
		        cursor = null;
				}
			}else if(parentViewType == VIEW_LIST_VIEW || parentViewType == VIEW_SPINNER || 
					parentViewType == VIEW_ABS_LIST_VIEW || parentViewType == VIEW_ABS_SPINNER ||
					parentViewType == VIEW_EXPANDABLE_LIST_VIEW){
				Cursor tmp = database.rawQuery("SELECT " + McdDB.COLUMN_ID + " FROM " +
					McdDB.TABLE_UI_EVENT + " WHERE " + McdDB.COLUMN_UI_EVENT_TYPE + " = ? AND " +
						McdDB.COLUMN_VIEW_ID + " IN (SELECT " + McdDB.COLUMN_PARENT_ID + " FROM " +
					McdDB.TABLE_VIEW_NODE + " WHERE " + McdDB.COLUMN_ID + " = " + 
						String.valueOf(viewID) + ")", 
						new String[]{String.valueOf(EVENT_CLICK)});
				
				if(tmp.moveToFirst()){
					cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
							McdDB.COLUMN_VIEW_ID + " = ? AND " + 
							McdDB.COLUMN_EVENT_TYPE + " = ?",
							new String[]{String.valueOf(viewID), String.valueOf(EVENT_CLICK)},
							null, null, null);
					if(!cursor.moveToFirst()){
						values = new ContentValues();
				        values.put(McdDB.COLUMN_VIEW_ID, viewID);
				        values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_CLICK);
			        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
						if(!(eventID > -1)){
							mcdRaiseException("Android Bug-checker returned a database row for" +
										" UiEvent table with primary key -1", database);
						}
					}else{
						eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
					}
					
					Cursor tmp1 = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
							McdDB.COLUMN_EVENT_PRIORITY}, 
							McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
							McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
							new String[]{String.valueOf(parentViewType), 
							String.valueOf(EVENT_CLICK)},
							null, null, null);
					if(tmp1.moveToFirst())
						priority = tmp1.getInt(tmp1.getColumnIndexOrThrow(
								McdDB.COLUMN_EVENT_PRIORITY));
					else
						priority = 5; //some default value
					
			        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
			        cursor.close();
			        cursor = null;
			        tmp1.close();
			        tmp1 = null;
				}
				tmp.close();
				tmp = null;
			}
			
			if(v.isLongClickable() &&
					!(parentViewType == VIEW_LIST_VIEW || parentViewType == VIEW_SPINNER || 
					parentViewType == VIEW_ABS_LIST_VIEW || parentViewType == VIEW_ABS_SPINNER ||
					parentViewType == VIEW_EXPANDABLE_LIST_VIEW)){
				cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
					McdDB.COLUMN_VIEW_ID + " = ? AND " + 
					McdDB.COLUMN_EVENT_TYPE + " = ?",
					new String[]{String.valueOf(viewID), String.valueOf(EVENT_LONG_CLICK)},
					null, null, null);
				if(!cursor.moveToFirst()){
					values = new ContentValues();
			        values.put(McdDB.COLUMN_VIEW_ID, viewID);
			        values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
		        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
					if(!(eventID > -1)){
						mcdRaiseException("Android Bug-checker returned a database row for" +
									" UiEvent table with primary key -1", database);
					}
				}else{
					eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				}
				cursor.close();
				cursor = null;
				//we do not add click and long click directly on list/spinner but on their children
				if(!(viewType == VIEW_LIST_VIEW || viewType == VIEW_SPINNER || 
						viewType == VIEW_ABS_LIST_VIEW || viewType == VIEW_ABS_SPINNER ||
						viewType == VIEW_EXPANDABLE_LIST_VIEW)){
				cursor = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
						McdDB.COLUMN_EVENT_PRIORITY}, 
						McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
						McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
						new String[]{String.valueOf(viewType), String.valueOf(EVENT_LONG_CLICK)},
						null, null, null);
				if(cursor.moveToFirst())
					priority = cursor.getInt(cursor.getColumnIndexOrThrow(
							McdDB.COLUMN_EVENT_PRIORITY));
				else
					priority = 5; //some default value
				
		        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
		        cursor.close();
		        cursor = null;
				}
			}else if(parentViewType == VIEW_LIST_VIEW || parentViewType == VIEW_SPINNER || 
					parentViewType == VIEW_ABS_LIST_VIEW || parentViewType == VIEW_ABS_SPINNER ||
					parentViewType == VIEW_EXPANDABLE_LIST_VIEW){
				Cursor tmp = database.rawQuery("SELECT " + McdDB.COLUMN_ID + " FROM " +
						McdDB.TABLE_UI_EVENT + " WHERE " + McdDB.COLUMN_UI_EVENT_TYPE + " = ? AND " +
							McdDB.COLUMN_VIEW_ID + " IN (SELECT " + McdDB.COLUMN_PARENT_ID + " FROM " +
						McdDB.TABLE_VIEW_NODE + " WHERE " + McdDB.COLUMN_ID + " = " + 
							String.valueOf(viewID) + ")", 
							new String[]{String.valueOf(EVENT_LONG_CLICK)});
					
				if(tmp.moveToFirst()){
					cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
							McdDB.COLUMN_VIEW_ID + " = ? AND " + 
							McdDB.COLUMN_EVENT_TYPE + " = ?",
							new String[]{String.valueOf(viewID), 
							String.valueOf(EVENT_LONG_CLICK)},
							null, null, null);
					if(!cursor.moveToFirst()){
						values = new ContentValues();
				        values.put(McdDB.COLUMN_VIEW_ID, viewID);
				        values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_LONG_CLICK);
			        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
						if(!(eventID > -1)){
							mcdRaiseException("Android Bug-checker returned a database row for" +
										" UiEvent table with primary key -1", database);
						}
					}else{
						eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
					}
					
					Cursor tmp1 = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
							McdDB.COLUMN_EVENT_PRIORITY}, 
							McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
							McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
							new String[]{String.valueOf(parentViewType), 
							String.valueOf(EVENT_LONG_CLICK)},
							null, null, null);
					if(tmp1.moveToFirst())
						priority = tmp1.getInt(tmp1.getColumnIndexOrThrow(
								McdDB.COLUMN_EVENT_PRIORITY));
					else
						priority = 5; //some default value
					
			        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
			        cursor.close();
			        cursor = null;
			        tmp1.close();
			        tmp1 = null;
				}
				tmp.close();
				tmp = null;
			}
			
			}
				
			HashSet<Integer> splEvents = getSpecialEventsForView(viewType);
			if(splEvents.size() != 0){
				for(Integer eventCode : splEvents){
					cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
							McdDB.COLUMN_VIEW_ID + " = ? AND " + 
							McdDB.COLUMN_EVENT_TYPE + " = ?",
							new String[]{String.valueOf(viewID), String.valueOf(eventCode)},
							null, null, null);
						if(!cursor.moveToFirst()){
							values = new ContentValues();
					        values.put(McdDB.COLUMN_VIEW_ID, viewID);
					        values.put(McdDB.COLUMN_UI_EVENT_TYPE, eventCode);
				        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
							if(!(eventID > -1)){
								mcdRaiseException("Android Bug-checker returned a database row for" +
										" UiEvent table with primary key -1", database);
							}
						}else{
							eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						}
						cursor.close();
						cursor = null;
						cursor = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
								McdDB.COLUMN_EVENT_PRIORITY}, 
								McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
								McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
								new String[]{String.valueOf(viewType), String.valueOf(eventCode)},
								null, null, null);
						if(cursor.moveToFirst())
							priority = cursor.getInt(cursor.getColumnIndexOrThrow(
									McdDB.COLUMN_EVENT_PRIORITY));
						else
							priority = 5; //some default value
						
				        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
				        cursor.close();
				        cursor = null;
				}
			}else if(EditText.class.isInstance(v) && isEditTextWritable((EditText)v)){
				cursor = database.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
					McdDB.COLUMN_VIEW_ID + " = ? AND " + 
					McdDB.COLUMN_EVENT_TYPE + " = ?",
					new String[]{String.valueOf(viewID), String.valueOf(EVENT_SET_TEXT)},
					null, null, null);
				if(!cursor.moveToFirst()){
					values = new ContentValues();
			        values.put(McdDB.COLUMN_VIEW_ID, viewID);
			        values.put(McdDB.COLUMN_UI_EVENT_TYPE, EVENT_SET_TEXT);
		        	eventID = database.insert(McdDB.TABLE_UI_EVENT, null, values);
					if(!(eventID > -1)){
						mcdRaiseException("Android Bug-checker returned a database row for" +
									" UiEvent table with primary key -1", database);
					}
				}else{
					eventID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				}
				cursor.close();
				cursor = null;
				cursor = database.query(McdDB.TABLE_UI_EVENT_RELATIVE_PRIORITY, new String[]{
						McdDB.COLUMN_EVENT_PRIORITY}, 
						McdDB.COLUMN_VIEW_TYPE + " = ? AND " + 
						McdDB.COLUMN_UI_EVENT_TYPE + " = ?",
						new String[]{String.valueOf(viewType), String.valueOf(EVENT_SET_TEXT)},
						null, null, null);
				if(cursor.moveToFirst())
					priority = cursor.getInt(cursor.getColumnIndexOrThrow(
							McdDB.COLUMN_EVENT_PRIORITY));
				else
					priority = 5; //some default value
				
		        addEventToUnexploredList(eventID, UI_EVENT, pathNodeID, priority, database);
		        cursor.close();
		        cursor = null;
			}
		}
		
		return viewType;
	}
	
	public void storeUIEvents(View v, int viewID, int pathNodeID, int parentViewType,
			SQLiteDatabase database) throws McdException{
        int viewType = storeViewEvents(v, viewID, pathNodeID, parentViewType, database);
        
        if(ViewGroup.class.isInstance(v) && 
        		!isIgnoreChildrenViews(getSimplifiedClassOfView(v.getClass()))){
        	Cursor cursor = database.query(McdDB.TABLE_VIEW_NODE, new String[]{McdDB.COLUMN_ID}, 
					McdDB.COLUMN_PARENT_ID + " = ?",
					new String[]{String.valueOf(viewID)},
					null, null, McdDB.COLUMN_RELATIVE_ID + " ASC");
        	
        	if(cursor.getCount() != ((ViewGroup)v).getChildCount()){
				mcdRaiseException("Android Bug-checker hit a view whose children count did not" +
							" match that stored in database for that view", database);
        	}
        	
        	if(cursor.moveToFirst()){
        	for(int i=0; i<cursor.getCount(); i++){
        		storeUIEvents(((ViewGroup)v).getChildAt(i), cursor.getInt(
        				cursor.getColumnIndexOrThrow(McdDB.COLUMN_ID)), pathNodeID, viewType, 
        				database);
        		cursor.moveToNext();
        	}
        	}
        	
        	cursor.close();
        }
	}
	
	//store events corresponding to current screen in the database if not already present
	//and add it to the unexplored list corresponding to the pathNode 
	public void storeCurrentUIEvents(View rootView, int viewID, int pathNodeID, SQLiteDatabase db) 
			throws McdException{
		storeUIEvents(rootView, viewID, pathNodeID, -1, db);
	}
	
	//for screen hash, if an object has a GUID use that in summation otherwise the
	//hash value, because in many cases guid gets maintained on a refresh or revisit of a screen by
	//clicking BACK/ TAB widget etc. but not the hash value of the view. 
	//take care of this in your logic for possible data widgets
	public long computeScreenHash(ViewGroup vg, long sum) throws McdException{
	if(vg != null){
		for(int i=0; i < vg.getChildCount(); i++){
			View v = vg.getChildAt(i);
			if(v.getId() != -1)
				sum += v.getId();
			else
				sum += v.hashCode();
				
			if(ViewGroup.class.isInstance(v) && 
					!isIgnoreChildrenViews(getSimplifiedClassOfView(v.getClass()))){
				computeScreenHash((ViewGroup)v, sum);
			}
		}
	}
		return sum;
	}
	
	public int getMappedInputType(int rawType){
		int mappedType = -1; //-1 is default and if no matching type is found
		//switch case with the list of input types for which you have customized input
		if((rawType & 0x0000000f) == InputType.TYPE_CLASS_PHONE){
				//+91-9449970222
					mappedType = TYPE_TEXT_PHONE;
		}else if((rawType & 0x0000000f) == InputType.TYPE_CLASS_DATETIME){
			rawType = rawType ^ InputType.TYPE_CLASS_DATETIME;
			
			if(rawType == InputType.TYPE_DATETIME_VARIATION_DATE){
				
				mappedType = TYPE_TEXT_DATE;
				
			}else if(rawType == InputType.TYPE_DATETIME_VARIATION_TIME){
				
				mappedType = TYPE_TEXT_TIME;
				
			}else if(rawType == InputType.TYPE_DATETIME_VARIATION_NORMAL){
				
				mappedType = TYPE_TEXT_DATE_TIME;
			}
		}else if((rawType & 0x0000000f) == InputType.TYPE_CLASS_TEXT){
			rawType = rawType ^ InputType.TYPE_CLASS_TEXT;
			int tmpRawType = rawType & 0x000000f0; //to ignore flag fields of bit-mask
			if((tmpRawType == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)  ||
					(tmpRawType == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)){
				
				mappedType = TYPE_TEXT_EMAIL;
				
			}else if((tmpRawType == InputType.TYPE_TEXT_VARIATION_PASSWORD) ||
					(tmpRawType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) ||
					(tmpRawType == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)){
				
				mappedType = TYPE_TEXT_PASSWORD;
				
			}else if(tmpRawType == InputType.TYPE_TEXT_VARIATION_PERSON_NAME){
				if((rawType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0){
					mappedType = TYPE_TEXT_NAME_CAPS;
					
				}else{
					mappedType = TYPE_TEXT_NAME;
				}
			}else if(tmpRawType == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS){
				if((rawType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0){
					mappedType = TYPE_TEXT_ADDRESS_CAPS;
					
				}else{
					mappedType = TYPE_TEXT_ADDRESS;
				}
			}else if(tmpRawType == InputType.TYPE_TEXT_VARIATION_URI){
				
					mappedType = TYPE_TEXT_URI;
					
			}else{
				if((rawType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0){
					
					mappedType = TYPE_TEXT_ALL_CAPS;
					
				}else{
					//our default input is Testdata, a 8 digit no. and _a*$hj kind of 
					//word
					mappedType = TYPE_TEXT_DEFAULT;
				}
			}
		}else if((rawType & 0x0000000f) == InputType.TYPE_CLASS_NUMBER){
			rawType = rawType ^ InputType.TYPE_CLASS_NUMBER;
			if(rawType == InputType.TYPE_NUMBER_VARIATION_PASSWORD){
				//a 6 letter length numeric password
					mappedType = TYPE_TEXT_NUMBER_PASSWORD;
					
			}else if(rawType == InputType.TYPE_NUMBER_FLAG_DECIMAL){
				//number with decimal point
					mappedType = TYPE_TEXT_NUMBER_FLOAT;
			}else{
				// give a positive integer
					mappedType = TYPE_TEXT_NUMBER_NORMAL;
			}
		}else{
			//our default input is Testdata, a 8 digit no. and _a*$hj kind of 
			//word
					mappedType = TYPE_TEXT_DEFAULT;
		}
		
		return mappedType;
	}
	
	//if the event is to input text to an editText or enter search query
	//note: arguments for intent has to be handled by a separate method
	public String getTextDataStoredForPathNode(int pathNodeID, SQLiteDatabase database){
		String data = null;
		Cursor cursor = database.query(McdDB.TABLE_QUICK_NODE_DATA_MAP, new String[]{
				McdDB.COLUMN_DATA}, 
				McdDB.COLUMN_NODE_ID + " = ?",
				new String[]{String.valueOf(pathNodeID)},
				null, null, null);
		
		if(cursor.moveToFirst()){
			data = cursor.getString(cursor.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
		}
		cursor.close();
	
		return data;
	}
	
	//uses a storage to keep track of strings to be fed to an editText/searchView etc.
	//check the view's type 
	//pathNodeId is used to keep track of data already provided if there are options available for
	//the view and is useful during backtracking
	public String retrieveNextDataForView(View v, int viewID, String activity, int pathNodeId, 
			SQLiteDatabase database) throws McdException{
		String data = null;
		//later on add code to even check if the view is of type calendar view etc.
		//which can take user provided textual data
		if(v instanceof EditText || v instanceof SearchView){
			int inputType = -1;
			if(v instanceof SearchView) 
				inputType = ((SearchView)v).getSearchInputType();
			else
				inputType = ((EditText)v).getInputType();
			int guid = v.getId();
			int dataID = -1;
			int isCustomized = -1;
			
			Cursor cursor = database.query(McdDB.TABLE_PATH_NODE_DATA, new String[]{
					McdDB.COLUMN_DATA_ID, McdDB.COLUMN_IS_CUSTOMIZED}, 
					McdDB.COLUMN_NODE_ID + " = ?",
					new String[]{String.valueOf(pathNodeId)},
					null, null, null);
			if(cursor.moveToFirst()){
				dataID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_DATA_ID));
				isCustomized = cursor.getInt(cursor.getColumnIndexOrThrow(
						McdDB.COLUMN_IS_CUSTOMIZED));
			}
			cursor.close();
			cursor = null;
			
			if(dataID != -1){
				if(isCustomized == 0){
					Cursor typeData = database.rawQuery("SELECT "+ McdDB.COLUMN_DATA + ", " +
							McdDB.COLUMN_ID + 
							" FROM "+ McdDB.TABLE_INPUT_SPECIFIC_DATA + " WHERE " + 
							McdDB.COLUMN_INPUT_TYPE + " = ? AND " + McdDB.COLUMN_ORDER + 
							" = (SELECT "+ McdDB.COLUMN_ORDER +" FROM "+ 
							McdDB.TABLE_INPUT_SPECIFIC_DATA +" WHERE "+ McdDB.COLUMN_ID + 
							" = ?) + 1", 
							new String[]{String.valueOf(
									getMappedInputType(inputType)), String.valueOf(dataID)});
					if(!typeData.moveToFirst()){
						v = null; //to avoid any window leak
						mcdRaiseException("Android Bug-checker hit an invalid primary key" +
									" for InputSpecificData table", database);
					}
					ContentValues values = new ContentValues();
					values.put(McdDB.COLUMN_DATA_ID, typeData.getInt(
							typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
					database.update(McdDB.TABLE_PATH_NODE_DATA, values, McdDB.COLUMN_NODE_ID + 
							" = ?", new String[]{String.valueOf(pathNodeId)});
					
					dataID = typeData.getInt(
							typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID));
					isCustomized = 0;
					
					values.clear();
					data = typeData.getString(typeData.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
					values.put(McdDB.COLUMN_DATA, data);
					values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
					database.insert(McdDB.TABLE_QUICK_NODE_DATA_MAP, null, values);
					
					typeData.close();
					typeData = null;
				}else{
					
					int tmpViewID = -1;
					if(guid == -1)
						tmpViewID = viewID;
					
					Cursor fieldData = database.rawQuery("SELECT "+ McdDB.COLUMN_DATA + ", " +
							McdDB.COLUMN_ID + ", " + McdDB.COLUMN_EXPLORE_DEFAULT +
							" FROM "+ McdDB.TABLE_FIELD_SPECIFIC_DATA + " WHERE " + 
							McdDB.COLUMN_GUI_ID + " = ? AND " + McdDB.COLUMN_ACTIVITY_NAME + 
							" = ? AND " + McdDB.COLUMN_VIEW_ID + " = ? AND " + McdDB.COLUMN_ORDER + 
							" = (SELECT "+ McdDB.COLUMN_ORDER +" FROM "+ 
							McdDB.TABLE_FIELD_SPECIFIC_DATA +" WHERE "+ McdDB.COLUMN_ID + 
							" = ?) + 1", 
							new String[]{String.valueOf(guid), activity, String.valueOf(tmpViewID),
									String.valueOf(dataID)
									});
					
					ContentValues values;
					if(fieldData.moveToFirst()){
						values = new ContentValues();
						values.put(McdDB.COLUMN_DATA_ID, fieldData.getInt(
								fieldData.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
						database.update(McdDB.TABLE_PATH_NODE_DATA, values, McdDB.COLUMN_NODE_ID + 
								" = ?", new String[]{String.valueOf(pathNodeId)});
						
						dataID = fieldData.getInt(
								fieldData.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						isCustomized = 1;
						
						data = fieldData.getString(fieldData.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
					}else{
						Cursor cus = database.query(McdDB.TABLE_FIELD_SPECIFIC_DATA, new String[]{
								McdDB.COLUMN_EXPLORE_DEFAULT}, McdDB.COLUMN_ID + " = ?", 
								new String[]{String.valueOf(dataID)}, 
										null, null, null);
						
						cus.moveToFirst();
						if(!(cus.getInt(cus.getColumnIndexOrThrow
								(McdDB.COLUMN_EXPLORE_DEFAULT)) == 1)){
							v = null; //to avoid any window leak
							mcdRaiseException("Android Bug-checker hit aproblem for " +
										" FieldSpecificData table", database);
						}
						cus.close();
						cus = null;
						
						Cursor typeData = database.query(McdDB.TABLE_INPUT_SPECIFIC_DATA, new String[]{
								McdDB.COLUMN_DATA, McdDB.COLUMN_ID}, 
								McdDB.COLUMN_INPUT_TYPE + " = ? AND " + McdDB.COLUMN_ORDER + " = ?",
								new String[]{String.valueOf(getMappedInputType(inputType)), 
								String.valueOf(0)},	null, null, null);
						if(!(typeData.moveToFirst())){
							v = null; //to avoid any window leak
							mcdRaiseException("Android Bug-checker hit a problem for" +
										" InputSpecificData", database);
						}
						
						values = new ContentValues();
						values.put(McdDB.COLUMN_DATA_ID, typeData.getInt(
								typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
						values.put(McdDB.COLUMN_IS_CUSTOMIZED, 0);
						database.update(McdDB.TABLE_PATH_NODE_DATA, values, McdDB.COLUMN_NODE_ID + 
								" = ?", new String[]{String.valueOf(pathNodeId)});
						data = typeData.getString(typeData.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
						
						dataID = typeData.getInt(
								typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						isCustomized = 0;
						
						typeData.close();
						typeData = null;
					}
					values = new ContentValues();
					values.put(McdDB.COLUMN_DATA, data);
					values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
					database.insert(McdDB.TABLE_QUICK_NODE_DATA_MAP, null, values);
					
					fieldData.close();
					fieldData = null;
				}
			}else{
				int tmpViewID = -1;
				if(guid == -1)
					tmpViewID = viewID;
				
				Cursor fieldData = database.query(McdDB.TABLE_FIELD_SPECIFIC_DATA, new String[]{
						McdDB.COLUMN_DATA, McdDB.COLUMN_ID, McdDB.COLUMN_EXPLORE_DEFAULT}, 
						McdDB.COLUMN_GUI_ID + " = ? AND " + McdDB.COLUMN_ACTIVITY_NAME + " = ? AND "
						+ McdDB.COLUMN_VIEW_ID + " = ? AND " + McdDB.COLUMN_ORDER + " = ?",
						new String[]{String.valueOf(guid), activity, String.valueOf(tmpViewID),
						String.valueOf(0)},
						null, null, null);
				String fieldString = null;
				int fieldDataID = -1;
				int exploreDef = -1;
				
				if(fieldData.moveToFirst()){
					fieldString = fieldData.getString(fieldData.getColumnIndexOrThrow(
							McdDB.COLUMN_DATA));
					fieldDataID = fieldData.getInt(fieldData.getColumnIndexOrThrow(
							McdDB.COLUMN_ID));
					exploreDef = fieldData.getInt(fieldData.getColumnIndexOrThrow(
							McdDB.COLUMN_EXPLORE_DEFAULT));
				}
				fieldData.close();
				fieldData = null;
				
				ContentValues values;
				String defaultText = null;
				if(v instanceof SearchView){
					if(!(((SearchView)v).getQuery() == null || 
							((SearchView)v).getQuery().toString().equals(""))){
						defaultText = ((SearchView)v).getQuery().toString();
					}
				}else if(v instanceof EditText){
					if(!(((EditText)v).getText() == null || 
							((EditText)v).getText().toString().equals(""))){
						defaultText = ((EditText)v).getText().toString();
					}
				}
				
				if(fieldDataID != -1){
					//logic to explore default text
					if(defaultText != null){
					Cursor tmpRes = database.query(McdDB.TABLE_FIELD_SPECIFIC_DATA, new String[]{
							McdDB.COLUMN_ID}, 
							McdDB.COLUMN_GUI_ID + " = ? AND " + McdDB.COLUMN_ACTIVITY_NAME + 
							" = ? AND "	+ McdDB.COLUMN_DATA + " = ?",
							new String[]{String.valueOf(guid), activity, 
							defaultText},
							null, null, null);
					if(!tmpRes.moveToFirst()){
						Cursor order = database.rawQuery("SELECT MAX(" + McdDB.COLUMN_ORDER + 
								") FROM " + McdDB.TABLE_FIELD_SPECIFIC_DATA + " WHERE " + 
								McdDB.COLUMN_GUI_ID + " = ? AND " + McdDB.COLUMN_ACTIVITY_NAME + 
								" = ?", new String[]{String.valueOf(guid), activity});
						if(!(order.moveToFirst())){
							v = null; //to avoid any window leak
							mcdRaiseException("Android Bug-checker expected result for" +
										" FieldSpecificData table but was not present", database);
						}
						values = new ContentValues();
						values.put(McdDB.COLUMN_DATA, defaultText);
						values.put(McdDB.COLUMN_ACTIVITY_NAME, activity);
						values.put(McdDB.COLUMN_GUI_ID, guid);
						if(guid == -1)
							values.put(McdDB.COLUMN_VIEW_ID, viewID);
						values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
						values.put(McdDB.COLUMN_ORDER, order.getInt(0) + 1);
						values.put(McdDB.COLUMN_EXPLORE_DEFAULT, exploreDef);
						database.insert(McdDB.TABLE_FIELD_SPECIFIC_DATA, null, values);
						
						order.close();
						order = null;
					}
					tmpRes.close();
					tmpRes = null;
					}
					
					data = fieldString;
					values = new ContentValues();
					values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
					values.put(McdDB.COLUMN_DATA_ID, fieldDataID);
					values.put(McdDB.COLUMN_IS_CUSTOMIZED, 1);
					database.insert(McdDB.TABLE_PATH_NODE_DATA, null, values);
					
					dataID = fieldDataID;
					isCustomized = 1;
				}else{
					//logic to explore default text
					if(defaultText != null){
						values = new ContentValues();
						values.put(McdDB.COLUMN_DATA, defaultText);
						values.put(McdDB.COLUMN_ACTIVITY_NAME, activity);
						if(guid == -1)
							values.put(McdDB.COLUMN_VIEW_ID, viewID);
						values.put(McdDB.COLUMN_GUI_ID, guid);
						values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
						values.put(McdDB.COLUMN_ORDER, 0);
						values.put(McdDB.COLUMN_EXPLORE_DEFAULT, 1);
						int inserID = (int) database.insert(McdDB.TABLE_FIELD_SPECIFIC_DATA, null, values);
						
						data = defaultText;
						values = new ContentValues();
						values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
						values.put(McdDB.COLUMN_DATA_ID, inserID);
						values.put(McdDB.COLUMN_IS_CUSTOMIZED, 1);
						database.insert(McdDB.TABLE_PATH_NODE_DATA, null, values);
						
						dataID = inserID;
						isCustomized = 1;
					}else{
						Cursor typeData = database.query(McdDB.TABLE_INPUT_SPECIFIC_DATA, new String[]{
								McdDB.COLUMN_DATA, McdDB.COLUMN_ID}, 
								McdDB.COLUMN_INPUT_TYPE + " = ? AND " + McdDB.COLUMN_ORDER + " = ?",
								new String[]{String.valueOf(getMappedInputType(inputType)), 
								String.valueOf(0)},	null, null, null);
						if(!(typeData.moveToFirst())){
							v = null; //to avoid any window leak
							mcdRaiseException("Android Bug-checker expected result for " +
										"InputSpecificData table which was not present", database);
						}
						data = typeData.getString(typeData.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
						values = new ContentValues();
						values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
						values.put(McdDB.COLUMN_DATA_ID, typeData.getInt(
								typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
						values.put(McdDB.COLUMN_IS_CUSTOMIZED, 0);
						database.insert(McdDB.TABLE_PATH_NODE_DATA, null, values);
						
						dataID = typeData.getInt(
								typeData.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						isCustomized = 0;
						
						typeData.close();
						typeData = null;
						}
					}
				
				values = new ContentValues();
				database.delete(McdDB.TABLE_QUICK_NODE_DATA_MAP, McdDB.COLUMN_NODE_ID + " = ?", 
						new String[]{String.valueOf(pathNodeId)});
				values.put(McdDB.COLUMN_DATA, data);
				values.put(McdDB.COLUMN_NODE_ID, pathNodeId);
				database.insert(McdDB.TABLE_QUICK_NODE_DATA_MAP, null, values);
				
			}
			// code to remove this event if no text available next
			
			//dataID and isCustomized fields have values corresponding to the current updates
			
			if(isCustomized == 0){
				Cursor typeData = database.rawQuery("SELECT "+ McdDB.COLUMN_ID + 
						" FROM "+ McdDB.TABLE_INPUT_SPECIFIC_DATA + " WHERE " + 
						McdDB.COLUMN_INPUT_TYPE + " = ? AND " + McdDB.COLUMN_ORDER + 
						" = (SELECT "+ McdDB.COLUMN_ORDER +" FROM "+ 
						McdDB.TABLE_INPUT_SPECIFIC_DATA +" WHERE "+ McdDB.COLUMN_ID + 
						" = ?) + 1", 
						new String[]{String.valueOf(
								getMappedInputType(inputType)), 
								String.valueOf(dataID)
								});
				if(typeData.getCount() == 0){
					//delete node-data tracking field from the table
					database.delete(McdDB.TABLE_PATH_NODE_DATA, McdDB.COLUMN_NODE_ID + " = ?", 
							new String[]{String.valueOf(pathNodeId)});
					//delete the text input event
					database.execSQL("DELETE FROM " + McdDB.TABLE_UNEXPLORED_EVENTS + " WHERE " +
					McdDB.COLUMN_NODE_ID + " = " + String.valueOf(pathNodeId) + " AND " +
					McdDB.COLUMN_EVENT_ID + " IN (SELECT "+ McdDB.TABLE_PATH_NODE + "." + 
							McdDB.COLUMN_EVENT_ID + " FROM " + 
							McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.TABLE_PATH_NODE + "." + 
					McdDB.COLUMN_ID + " = " + String.valueOf(pathNodeId) + ");");
				}
				
				typeData.close();
				typeData = null;
			}else{
				int tmpViewID = -1;
				if(guid == -1)
					tmpViewID = viewID;
				
				Cursor fieldData = database.rawQuery("SELECT "+ McdDB.COLUMN_ID + ", " +
						McdDB.COLUMN_EXPLORE_DEFAULT +
						" FROM "+ McdDB.TABLE_FIELD_SPECIFIC_DATA + " WHERE " + 
						McdDB.COLUMN_GUI_ID + " = ? AND " + McdDB.COLUMN_ACTIVITY_NAME +
						" = ? AND " + McdDB.COLUMN_VIEW_ID + " = ? AND " + McdDB.COLUMN_ORDER + 
						" = (SELECT "+ McdDB.COLUMN_ORDER +" FROM "+ 
						McdDB.TABLE_FIELD_SPECIFIC_DATA +" WHERE "+ McdDB.COLUMN_ID + 
						" = ?) + 1", 
						new String[]{String.valueOf(guid), activity, String.valueOf(tmpViewID),
						String.valueOf(dataID)});
				
				if(fieldData.getCount() == 0){
					Cursor cus = database.query(McdDB.TABLE_FIELD_SPECIFIC_DATA, new String[]{
							McdDB.COLUMN_EXPLORE_DEFAULT}, McdDB.COLUMN_ID + " = ?", 
							new String[]{String.valueOf(dataID)}, 
									null, null, null);
					cus.moveToFirst();
					if(cus.getInt(cus.getColumnIndexOrThrow(
							McdDB.COLUMN_EXPLORE_DEFAULT)) == 1){
					Cursor typeData = database.query(McdDB.TABLE_INPUT_SPECIFIC_DATA, new String[]{
							McdDB.COLUMN_ID}, 
							McdDB.COLUMN_INPUT_TYPE + " = ? AND " + McdDB.COLUMN_ORDER + " = ?",
							new String[]{String.valueOf(getMappedInputType(inputType)), 
							String.valueOf(0)},	null, null, null);
					if(typeData.getCount() == 0){
						database.delete(McdDB.TABLE_PATH_NODE_DATA, McdDB.COLUMN_NODE_ID + " = ?", 
								new String[]{String.valueOf(pathNodeId)});
						//delete the text input event
						database.execSQL("DELETE FROM " + McdDB.TABLE_UNEXPLORED_EVENTS + " WHERE " +
						McdDB.COLUMN_NODE_ID + " = " + String.valueOf(pathNodeId) + " AND " +
						McdDB.COLUMN_EVENT_ID + " IN (SELECT "+ McdDB.TABLE_PATH_NODE + "." + 
								McdDB.COLUMN_EVENT_ID + " FROM " + 
								McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.TABLE_PATH_NODE + "." + 
						McdDB.COLUMN_ID + " = " + String.valueOf(pathNodeId) + ");");
					}
					
					typeData.close();
					typeData = null;
					}else{
						database.delete(McdDB.TABLE_PATH_NODE_DATA, McdDB.COLUMN_NODE_ID + " = ?", 
								new String[]{String.valueOf(pathNodeId)});
						//delete the text input event
						database.execSQL("DELETE FROM " + McdDB.TABLE_UNEXPLORED_EVENTS + " WHERE " +
						McdDB.COLUMN_NODE_ID + " = " + String.valueOf(pathNodeId) + " AND " +
						McdDB.COLUMN_EVENT_ID + " IN (SELECT "+ McdDB.TABLE_PATH_NODE + "." + 
								McdDB.COLUMN_EVENT_ID + " FROM " + 
								McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.TABLE_PATH_NODE + "." + 
						McdDB.COLUMN_ID + " = " + String.valueOf(pathNodeId) + ");");
					}
					
					cus.close();
					cus = null;
				}
				
				fieldData.close();
				fieldData = null;
			}
		}
		//check if viewtype is any of the text input types
		//then get the inputType of the view and then give an appropriate type
		
		return data;
	}
	
	public void clickDown(View v){
		long downTime = SystemClock.uptimeMillis();
    	long eventTime = SystemClock.uptimeMillis();
    	int metaState;
    	float x, y;
    	MotionEvent motionEvent;
    	
    	if(v.getParent() instanceof AbsListView || v.getParent() instanceof AbsSpinner){
     		x = (v.getLeft() + v.getRight())/2;
     		y = (v.getTop() + v.getBottom())/2;
     		v = (View) v.getParent();
     	}else{
	    	x = 0.0f;
	    	y = 0.0f;
     	}
    	metaState = 0;
    		
    	
    	motionEvent = MotionEvent.obtain(
    	    downTime, 
    	    eventTime, 
    	    MotionEvent.ACTION_DOWN, 
    	    x, 
    	    y, 
    	    metaState
    	);
    	
    	v.dispatchTouchEvent(motionEvent);
	}
	
	public void clickUp(View v){
		long downTime = SystemClock.uptimeMillis();
    	long eventTime = SystemClock.uptimeMillis();
    	int metaState;
    	float x, y;
    	MotionEvent motionEvent;
    	
    	if(v.getParent() instanceof AbsListView || v.getParent() instanceof AbsSpinner){
     		x = (v.getLeft() + v.getRight())/2;
     		y = (v.getTop() + v.getBottom())/2;
     		v = (View) v.getParent();
     	}else{
	    	x = 0.0f;
	    	y = 0.0f;
     	}
    	metaState = 0;
    		
    	
    	motionEvent = MotionEvent.obtain(
    	    downTime, 
    	    eventTime, 
    	    MotionEvent.ACTION_UP, 
    	    x, 
    	    y, 
    	    metaState
    	);
    	
    	v.dispatchTouchEvent(motionEvent);
	}
	
	void abcDelayExecution(int ms){
		try {
			Thread.currentThread().sleep(2500);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	//data field is non-null only if eventType is text input
	public void triggerUIEvent(final View v, final int eventType, final String data) throws McdException{
	        Runnable nextEvent = new Runnable() {
    	public void run() {
//			abcDelayExecution(1500);
    		Log.e("ABC", "triggered event " + eventType + "on view " + (v != null ? v.toString() : "key-press"));
    		//log trigger-event for race detection
    		if(v != null && (v.getParent() instanceof AbsListView || 
    				v.getParent() instanceof AbsSpinner)){
    			AbcGlobal.abcTriggerEvent((View)v.getParent(), eventType);
    		}else{
    			AbcGlobal.abcTriggerEvent(v, eventType);
    		}
    		
    		//trigger event
    		if(eventType == EVENT_BACK){
        		isPreviousBackClickEvent = true;
    	    	getVisibleActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
    	    	getVisibleActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
    	    	
    	    	//log enabling of lifecycle callback for race detection
    	    	Thread.currentThread().abcEnableLifecycleEvent(
        				getVisibleActivity().getLocalClassName(), 
        				getVisibleActivity().hashCode(), AbcGlobal.ABC_PAUSE);
//        		AbcGlobal.isPrevEventBackPress = true;
        		
    	    	//log this to a file later
    	    	if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: BACK press event");
    	    	}
        	}
//			        	menu click does not work programmatically like BACK button click as of now
        	else if(eventType == EVENT_MENU_CLICK){
        		isPreviousMenuClickEvent = true;
        		getVisibleActivity().openOptionsMenu();
        	
    	    	//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: MENU CLICK event");
    	    	}
        	}
        	else if(eventType == EVENT_ROTATE_SCREEN){
        		/*Android bug-checker*/
                Thread.currentThread().abcEnableLifecycleEvent(
                		getVisibleActivity().getLocalClassName(), 
                		getVisibleActivity().hashCode(), AbcGlobal.ABC_RELAUNCH);
                Thread.currentThread().abcEnableLifecycleEvent("", 0,
                		AbcGlobal.ABC_CHANGE_CONFIG);
                Thread.currentThread().abcEnableLifecycleEvent("", 0,
            			AbcGlobal.ABC_CHANGE_ACT_CONFIG);
                /*Android bug-checker*/
                
        		WindowManager mWindowManager =  (WindowManager) getContext().getSystemService(
        				getContext().WINDOW_SERVICE);
        	    if(mWindowManager.getDefaultDisplay().getRotation() == Surface.ROTATION_0)
        	    	getVisibleActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        	    else if(mWindowManager.getDefaultDisplay().getRotation() == 
        	    		Surface.ROTATION_90)
        	    	getVisibleActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        	    	
        	    if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SCREEN ROTATE event");
    	    	}
        	}else if(eventType == EVENT_CLICK){
        		viewClickDone = false;			    	        
     	        
        		clickDown(v); 	
    	    	clickUp(v);			    	        
    	       
    	      //log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: CLICK event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_LONG_CLICK){
    			viewLongClickDone = false;
    			
    			clickDown(v);	
    	    	
    	    	//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: LONG CLICK event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_SET_TEXT){
    			if(!EditText.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" EditText which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			
    			//this may lead to problems in case clicking on editText takes you to different screen
    			//but there is a deadlock bug in requestFocus. clicking and typing is a natural thing to do though
    			clickDown(v);
    			clickUp(v);
    			
    			((EditText)v).setText(data);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET TEXT event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId() + " text: " + data);
    	    	}
    		}else if(eventType == EVENT_SET_QUERY){
    			if(!SearchView.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" SearchView which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((SearchView)v).setQuery(data, true);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET QUERY event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId() + " query: " + data);
    	    	}
    		}else if(eventType == EVENT_SET_DATE_ANIMATE){
    			if(!CalendarView.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" CalendarView which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			//ERROR: report error if foll. assertion fails
    			if(!(((CalendarView)v).getMaxDate() >= ((CalendarView)v).getMinDate())){
    				try {
						throw new McdException("Android Bug-checker found a CalendarView with " +
									"mismatched date limits");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((CalendarView)v).setDate((((CalendarView)v).getMaxDate() + ((CalendarView)v).getMinDate())/2,
    					true, true);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET DATE event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    			
    		}else if(eventType == EVENT_SET_CURRENT_HOUR_MIN){
    			if(!TimePicker.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" TimePicker which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((TimePicker)v).setCurrentHour(12);
    			((TimePicker)v).setCurrentMinute(12);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET CURRENT_HOUR_MIN event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType ==  EVENT_UPDATE_DATE){
    			if((v instanceof DatePicker) == false){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" DatePicker which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			//ERROR: report error if foll. assertion fails
    			if(((DatePicker)v).getMaxDate() < ((DatePicker)v).getMinDate()){
    				try {
						throw new McdException("Android Bug-checker found a DatePicker with " +
									"mismatched limits for DatePicker view");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			Calendar cal = Calendar.getInstance();
    			cal.setTimeInMillis((((DatePicker)v).getMaxDate() + ((DatePicker)v).getMinDate())/2);
    			((DatePicker)v).updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 
    					cal.get(Calendar.DAY_OF_MONTH));
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: UPDATE DATE event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_SET_PROGRESS){
    			if(!SeekBar.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" SeekBar which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((SeekBar)v).setProgress(((SeekBar)v).getMax()/2);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET PROGRESS event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_SET_VALUE){
    			if(!NumberPicker.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" NumberPicker which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			//ERROR: report error if foll. assertion fails
    			if(!(((NumberPicker)v).getMaxValue() >= ((NumberPicker)v).getMinValue())){
    				try {
						throw new McdException("Android Bug-checker found a NumberPicker view" +
									" with mismatched limits");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((NumberPicker)v).setValue((((NumberPicker)v).getMaxValue() + 
    					((NumberPicker)v).getMaxValue())/2);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET VALUE (number) event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_SET_RATING){
    			if(!RatingBar.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" RatingBar which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((RatingBar)v).setRating(((RatingBar)v).getNumStars()/2);
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: SET RATING event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_TOGGLE){
    			if(!SlidingDrawer.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" SlidingDrawer which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			((SlidingDrawer)v).toggle();
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: TOGGLE event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}else if(eventType == EVENT_SHOW_NEXT_OR_PREV){
    			if(!StackView.class.isInstance(v)){
    				try {
						throw new McdException("Android Bug-checker hit a view expected to be" +
									" StackView which was not");
					} catch (McdException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			if(((StackView)v).getCount() > 1){
    				if(((StackView)v).getDisplayedChild() < (((StackView)v).getCount() - 1))
    					((StackView)v).showNext();
    				else
    					((StackView)v).showPrevious();
    			}
    			
    			//log this to a file later
        		if(ModelCheckingDriver.MODE == ModelCheckingDriver.PLAY){
    	    		Log.v(ModelCheckingDriver.TAG, "error: STACKVIEW SHOW NEXT event on view " + v.getClass() 
    	    				+ " viewID:" + v.getId());
    	    	}
    		}
    	} // end of run() method definition
    };// end of anonymous Runnable object instantiation
    mcdHandler.post(nextEvent);
    	
	}
	
	//returns list of <relativePos,viewType,parentId> of hierarchy leading upto list/spinner not
	//including the list/spinner id itself. this is the result provided the view is inside a 
	//list/spinner item
	public List<McdTriple> isViewInsideListOrSpinnerItem(int viewID, 
			SQLiteDatabase database) throws McdException{
		List<McdTriple> relativePositions = new ArrayList<McdTriple>();
		
		Cursor cursor = database.query(McdDB.TABLE_VIEW_NODE, new String[]{McdDB.COLUMN_PARENT_ID, 
				McdDB.COLUMN_VIEW_TYPE, McdDB.COLUMN_RELATIVE_ID}, 
				McdDB.COLUMN_ID + " = ?", new String[]{String.valueOf(viewID)}, 
				null, null, null);
		if(!(cursor.moveToFirst())){
			mcdRaiseException("Android Bug-checker found an invalid key in ViewNode table", database);
		}
		int viewType = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE));
		int parentID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_PARENT_ID));
		int relativePos = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_RELATIVE_ID));
		while(!((viewType == VIEW_LIST_VIEW || viewType == VIEW_ABS_LIST_VIEW || viewType == 
				VIEW_SPINNER || viewType == VIEW_ABS_SPINNER || viewType == 
				VIEW_EXPANDABLE_LIST_VIEW) || parentID == -1)){
			
			McdTriple triple = new McdTriple();
			triple.first = relativePos;
			triple.second = viewType;
			triple.third = parentID;
			relativePositions.add(triple);
			cursor.close();
			cursor = null;
			
			cursor = database.query(McdDB.TABLE_VIEW_NODE, new String[]{McdDB.COLUMN_PARENT_ID, 
				McdDB.COLUMN_VIEW_TYPE, McdDB.COLUMN_RELATIVE_ID}, 
				McdDB.COLUMN_ID + " = ?", new String[]{String.valueOf(parentID)}, 
				null, null, null);
			if(!(cursor.moveToFirst())){
				mcdRaiseException("Android Bug-checker found an invalid key in ViewNode table", database);
			}
			viewType = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE));
			parentID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_PARENT_ID));
			relativePos = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_RELATIVE_ID));
			
			cursor.close();
		}
		if(parentID == -1)
			relativePositions.clear();
		
		return relativePositions;
	}
	
	public void removeUiEventInHierarchy(List<McdTriple> relativePosLst, int uiEvent, int uiEnvID,
			int nodeID, SQLiteDatabase db) throws McdException{
		int lstSize = relativePosLst.size();
		Cursor lstCur = db.query(McdDB.TABLE_VIEW_NODE, 
				new String[]{McdDB.COLUMN_VIEW_TYPE,
				McdDB.COLUMN_ID}, McdDB.COLUMN_PARENT_ID + " = ?",
				new String[]{String.valueOf(relativePosLst.get(lstSize - 1).third)},
				null, null, null);
		if(!lstCur.moveToFirst()){
			mcdRaiseException("Android Bug-checker found inconsistency in ViewNode table", db);
		}
		
		for(int i=0; i<lstCur.getCount(); i++){
			if(lstCur.getInt(lstCur.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE)) == 
					relativePosLst.get(lstSize - 1).second){
				Cursor tmp = null;
				int tmpParent = lstCur.getInt(lstCur.getColumnIndexOrThrow(McdDB.COLUMN_ID));
				boolean flagBreak = false;
				for(int j=2; j<=lstSize; j++){
					tmp = db.query(McdDB.TABLE_VIEW_NODE, new String[]{McdDB.COLUMN_ID,
							McdDB.COLUMN_VIEW_TYPE}, McdDB.COLUMN_PARENT_ID + " = ? AND "
							+ McdDB.COLUMN_RELATIVE_ID + " = ?", new String[]{String.valueOf(
									tmpParent), 
									String.valueOf(relativePosLst.get(lstSize - j).first)}, 
									null, null, null);
					if(tmp.moveToFirst()){
						tmpParent = tmp.getInt(tmp.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						if(tmp.getInt(tmp.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE)) == 
								relativePosLst.get(lstSize - j).second){
							flagBreak = false;
						}else{
							Log.e(TAG, "Android Bug-checker expected same view type " +
									"inside a list item which wa not the case");
							flagBreak = true;
							break;
						}
					}else{
						Log.e(TAG,"Android Bug-checker expected a view inside a list " +
								"item which was missing");
					}
					tmp.close();
					tmp = null;
				}
				if(!flagBreak){
					Cursor cur = db.query(McdDB.TABLE_UI_EVENT, new String[]{McdDB.COLUMN_ID}, 
							McdDB.COLUMN_VIEW_ID + " = ? AND " + McdDB.COLUMN_UI_EVENT_TYPE + " = ?"
							, new String[]{String.valueOf(tmpParent), String.valueOf(uiEvent)}, 
							null, null, null);
					if(cur.moveToFirst()){
						ContentValues cv = new ContentValues();
						cv.put(McdDB.COLUMN_EVENT_ID, cur.getInt(
								cur.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
						cv.put(McdDB.COLUMN_EVENT_TYPE, 0);
						cv.put(McdDB.COLUMN_UI_ENV_ID, uiEnvID);
						cv.put(McdDB.COLUMN_NODE_ID, nodeID);
						db.insert(McdDB.TABLE_IGNORE_EVENT, null,
								cv);
						
						Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
					}
					cur.close();
					cur = null;
				}
			}else{
				Log.e(TAG, "Android Bug-checker expected same view type " +
						"inside a list item which wa not the case");
			}
			lstCur.moveToNext();
		}
		lstCur.close();
	}
	
	//recursive function to fetch corresponding view on current screen
	public View reachAndGetViewFromID(int viewID, int screenID, int viewRootLimit, 
			SQLiteDatabase database) 
			throws McdException{
		View targetView = null;
		Cursor cursor = database.query(McdDB.TABLE_VIEW_NODE, new String[]{
				McdDB.COLUMN_RELATIVE_ID, McdDB.COLUMN_PARENT_ID, McdDB.COLUMN_GUI_ID, 
				McdDB.COLUMN_VIEW_TYPE}, 
				McdDB.COLUMN_ID + " = ?",
				new String[]{String.valueOf(viewID)},
				null, null, null);
		if(!(cursor.moveToFirst())){
			mcdRaiseException("Android Bug-checker found an invalid primary key" +
						" for ViewNode table", database);
		}
		
		int relativeID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_RELATIVE_ID));
		int parentID = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_PARENT_ID));
		int guid = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_GUI_ID));
		int viewType = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_TYPE));
		
		cursor.close();
		cursor = null;
		
		if(parentID != -1){
			View parentView = reachAndGetViewFromID(parentID, screenID, viewRootLimit, database);
			if(!((ViewGroup.class.isInstance(parentView) && 
					((ViewGroup)parentView).getChildCount() > relativeID))){
				parentView = null; //to avoid any window leak
				mcdRaiseException("Android Bug-checker found a mismatch bug when reaching a View" +
							" using viewId", database);
			}
			
			targetView = ((ViewGroup)parentView).getChildAt(relativeID);
			
			if(!(((getSimplifiedClassOfView(targetView.getClass()) == viewType) && 
					targetView.getId() == guid))){
				parentView = null;
				targetView = null; //to avoid any window leak
				mcdRaiseException("Android Bug-checker found a mismatch bug when reaching a View" +
							" using viewId", database);
			}
		}else{
			Cursor tmp = database.query(McdDB.TABLE_VIEW_NODE, new String[]{
					McdDB.COLUMN_POPUP_ID}, 
					McdDB.COLUMN_ID + " = ?",
					new String[]{String.valueOf(viewID)},
					null, null, null);
			tmp.moveToFirst();
			int popupID = tmp.getInt(tmp.getColumnIndexOrThrow(McdDB.COLUMN_POPUP_ID));
			tmp.close();
			tmp = null;
			
			int n = WindowManagerImpl.getDefault().getViewRoots().length;
			
			if(popupID == -1){
				if(viewRootLimit < 1){
					mcdRaiseException("view root limit cannot be less than 1", database);
				}
				targetView = WindowManagerImpl.getDefault().getViewRoots()[n - viewRootLimit].getView();
			}else{
				Cursor viewLevel = database.query(McdDB.TABLE_POPUPS_SCREEN, new String[]{
						McdDB.COLUMN_VIEWROOT_LEVEL}, 
						McdDB.COLUMN_SCREEN_ID + " = ? AND " + McdDB.COLUMN_POPUP_ID + " = ?",
						new String[]{String.valueOf(screenID), String.valueOf(popupID)},
						null, null, null);
				
				if(!(viewLevel.moveToFirst())){
						mcdRaiseException("Android Bug-checker found no corresponding viewRootLevel" +
								" for corresponding screen and popup window in PopupScreens table", database);
				}
				int level = viewLevel.getInt(
						viewLevel.getColumnIndexOrThrow(McdDB.COLUMN_VIEWROOT_LEVEL));
				viewLevel.close();
				viewLevel = null;
				
				targetView = WindowManagerImpl.getDefault().getViewRoots()[n - level].getView();
			}
		}
		return targetView;
	}
	
	public void backtrack(SQLiteDatabase database, McdDB mcdDb, final int errorFlag){
		if(!abcSilentReturn){
		
		this.abcSilentReturn = true;
		//todo: write logic for final exit when you finish all the exploring
		database.execSQL("UPDATE " + McdDB.TABLE_PATH + " SET " + McdDB.COLUMN_EXECUTION_STATUS + 
				" = 1 WHERE " + McdDB.COLUMN_ID +" = (SELECT MIN(" + McdDB.COLUMN_ID + ") FROM " +
				McdDB.TABLE_PATH + ");");
		
		//node to be backtracked to...
		database.execSQL("UPDATE " + McdDB.TABLE_PATH_NODE + " SET " + McdDB.COLUMN_EVENT_TYPE + 
				" = -1 WHERE " + McdDB.TABLE_PATH_NODE + "." + McdDB.COLUMN_ID + " IN (SELECT " + 
				McdDB.COLUMN_NODE_ID + " FROM " + McdDB.TABLE_PATH + " WHERE " + McdDB.TABLE_PATH 
				+ "." + McdDB.COLUMN_ID + " = (SELECT MAX(" + McdDB.TABLE_PATH + "." + 
				McdDB.COLUMN_ID + ") FROM " + McdDB.TABLE_PATH + "));");
		
		//remove ignore events added at this node
		database.execSQL("DELETE FROM " + McdDB.TABLE_IGNORE_EVENT + " WHERE " + 
		McdDB.TABLE_IGNORE_EVENT + "." + McdDB.COLUMN_NODE_ID + 
		" IN (SELECT " + McdDB.TABLE_PATH + "." +
				McdDB.COLUMN_NODE_ID + " FROM " + McdDB.TABLE_PATH + " WHERE " + McdDB.TABLE_PATH 
				+ "." + McdDB.COLUMN_ID + " = (SELECT MAX(" + McdDB.TABLE_PATH + "." + 
				McdDB.COLUMN_ID + ") FROM " + McdDB.TABLE_PATH + "));");
		
		database.execSQL("DELETE FROM " + McdDB.TABLE_QUICK_NODE_DATA_MAP + " WHERE " + 
				McdDB.TABLE_QUICK_NODE_DATA_MAP + "." + McdDB.COLUMN_NODE_ID + 
				" IN (SELECT " + McdDB.TABLE_PATH + "." +
						McdDB.COLUMN_NODE_ID + " FROM " + McdDB.TABLE_PATH + " WHERE " + 
				McdDB.TABLE_PATH + "." + McdDB.COLUMN_ID + " = (SELECT MAX(" + McdDB.TABLE_PATH + 
				"." + McdDB.COLUMN_ID + ") FROM " + McdDB.TABLE_PATH + "));");
		
		try{
	        if(database != null && database.isOpen()){
	        	database.close();
		        mcdDb.close();
	        	database = null;
	        	mcdDb = null;
	        }
	    }catch(SQLiteException e){
	      	Log.e(TAG, "sqliteException when closing database hit");
	    }catch(NullPointerException e){
	       	Log.e(TAG, "nullPointerException when closing database hit");
	    }
		
		
		//code to send instructions to server to backup model-checking DB, kill this app, 
		//uninstall, install again and then continue
//		Thread worker = new Thread(new ModelCheckingClient(errorFlag));
//		//loop till model checking is restarted
//		worker.start();
			//contacting local server willnot work if the app does not have INTERNET permission
		    //this is done by contacting our own app which has necessary perms and it informs the server
			
//		    Runnable computeRaceRunnable = new Runnable() {
//				
//				@Override
//				public void run() {
//		            collect stats
		            AbcGlobal.abcSetTraceEndTime(SystemClock.uptimeMillis());
		            AbcGlobal.abcSetRaceDetectionStartTime(SystemClock.uptimeMillis());
		            
		            long traceGenerationTime = 0;
					long raceDetectionTime = 0;
					int traceLength = Thread.currentThread().abcGetTraceLength();
					int threadCount = 0;
					int mqCount = 0;
					int asyncCount = 0;
					int fieldCount = 0;
					int multiRaceCount = 0;
					int asyncRaceCount = 0;
					int delayPostRaceCount = 0;
					int crossPostRaceCount = 0;
					int uiRacecount = 0;
					int nonUiRaceCount = 0;
					
					int success = Thread.currentThread().abcPerformRaceDetection();
					
					//collect stats
					AbcGlobal.abcSetRaceDetectionEndTime(SystemClock.uptimeMillis());
					
					//print race stats to file
					Thread.currentThread().abcPrintRacesDetectedToFile();
					Thread.currentThread().abcComputeMemoryUsedByRaceDetector();
					
					if(success == 1){
					    traceGenerationTime = AbcGlobal.abcGetTraceEndTime() -
							AbcGlobal.abcGetTraceStartTime();
					    raceDetectionTime = AbcGlobal.abcGetRaceDetectionEndTime() -
							AbcGlobal.abcGetRaceDetectionStartTime();
					    
					    //computed during HB graph creation and race dtection stage
					    threadCount = Thread.currentThread().abcGetThreadCount();
					    mqCount = Thread.currentThread().abcGetMessageQueueCount();
					    asyncCount = Thread.currentThread().abcGetAsyncBlockCount();
					    fieldCount = Thread.currentThread().abcGetFieldCount();
					    multiRaceCount = Thread.currentThread().abcGetMultiThreadedRaceCount();
					    asyncRaceCount = Thread.currentThread().abcGetAsyncRaceCount();
					    delayPostRaceCount = Thread.currentThread().abcGetDelayPostRaceCount();
					    crossPostRaceCount = Thread.currentThread().abcGetCrossPostRaceCount();
					    uiRacecount = Thread.currentThread().abcGetCoEnabledEventUiRaces();
					    nonUiRaceCount = Thread.currentThread().abcGetCoEnabledEventNonUiRaces();
					}
					
					Log.e("ABC", "traceGenTime:" + traceGenerationTime + "  raceDetectTime:" + raceDetectionTime);
					
					Intent intent = new Intent("android.intent.action.MAIN");
			        intent.setComponent(ComponentName.unflattenFromString("abc.abcclientapp/abc.abcclientapp.AbcClientActivity"));
			        intent.addCategory("android.intent.category.LAUNCHER");
			        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			        
			        intent.putExtra("package", appUT);
			        intent.putExtra("errCode", errorFlag);
			        intent.putExtra("traceGenerationTime", TimeUnit.MILLISECONDS.toSeconds(traceGenerationTime));
			        intent.putExtra("raceDetectionTime", TimeUnit.MILLISECONDS.toSeconds(raceDetectionTime));
			        intent.putExtra("traceLength", traceLength);
			        intent.putExtra("threadCount", threadCount);
			        intent.putExtra("mqCount", mqCount);
			        intent.putExtra("asyncCount", asyncCount);
			        intent.putExtra("eventDepth", DEPTH_LIMIT);
			        intent.putExtra("fieldCount", fieldCount);
			        intent.putExtra("multiRaceCount", multiRaceCount);
			        intent.putExtra("asyncRaceCount", asyncRaceCount);
			        intent.putExtra("delayPostRaceCount", delayPostRaceCount);
			        intent.putExtra("crossPostRaceCount", crossPostRaceCount);
			        intent.putExtra("uiRacecount", uiRacecount);
			        intent.putExtra("nonUiRaceCount", nonUiRaceCount);
			        intent.putExtra("abcPort", abcPort);
			        
			        getContext().startActivity(intent);
//				}
//			};
//		    
//			Thread.currentThread().abcStopTraceGeneration(); 
//		    Thread raceDetectionThread = new Thread(computeRaceRunnable);
//		    raceDetectionThread.start();
		    
		/*	if(errorFlag != FLAG_ERROR){
				long count = Long.MAX_VALUE;
				while(count > Long.MIN_VALUE){
					count = count-2;
					count++;
			    }
		    }*/
		}else{
			try{
		        if(database != null && database.isOpen()){
		        	database.close();
			        mcdDb.close();
		        	database = null;
		        	mcdDb = null;
		        }
		        }catch(SQLiteException e){
		        	Log.e(TAG, "sqliteException when closing database hit");
		        }
		        catch(NullPointerException e){
		        	Log.e(TAG, "nullPointerException when closing database hit");
		        }
		}
	}
	
	
	public void backtrackOnError() throws McdException{
		if(!abcSilentReturn){
        McdDB mcdDB = new McdDB(Looper.mcd.getContext());
		SQLiteDatabase database = mcdDB.getWritableDatabase();
		
		Log.e(TAG, "storing error path...");
		Cursor cursor = database.query(McdDB.TABLE_PATH, new String[]{McdDB.COLUMN_NODE_ID}, 
				null, null, null, null, McdDB.COLUMN_ID + " DESC");
		int nextEvent = -1;
		
		//storing the error trace into TABLE_ERROR_PATH, 
		//the last node in path has nextEvent = -1 and first node has isFirstNode = 1
		if(cursor.moveToFirst()){
		do{
			Cursor nodeRes = database.query(McdDB.TABLE_PATH_NODE, new String[]{
					McdDB.COLUMN_EVENT_ID, McdDB.COLUMN_EVENT_TYPE}, McdDB.COLUMN_ID + " = ?", 
					new String[]{String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(
							McdDB.COLUMN_NODE_ID)))}, null, null, null);
			
			if(!nodeRes.moveToFirst()){
				mcdRaiseException("Android bug-checker found an invalid key in PathNode table", database);
			}
			
			String data = null;
			if(nodeRes.getInt(nodeRes.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE)) == 0){
				Cursor dataRes = database.query(McdDB.TABLE_QUICK_NODE_DATA_MAP, new String[]{
						McdDB.COLUMN_DATA}, McdDB.COLUMN_NODE_ID + " = ?", 
						new String[]{String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(
								McdDB.COLUMN_NODE_ID)))}, null, null, null);
				if(dataRes.moveToFirst()){
					data = dataRes.getString(dataRes.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
				}
				dataRes.close();
			}
			
			ContentValues cv = new ContentValues();
			cv.put(McdDB.COLUMN_EVENT_ID, nodeRes.getInt(nodeRes.getColumnIndexOrThrow(
					McdDB.COLUMN_EVENT_ID)));
			cv.put(McdDB.COLUMN_DATA, data);
			cv.put(McdDB.COLUMN_EVENT_TYPE, nodeRes.getInt(nodeRes.getColumnIndexOrThrow(
					McdDB.COLUMN_EVENT_TYPE)));
			cv.put(McdDB.COLUMN_NEXT_EVENT, nextEvent);
			cv.put(McdDB.COLUMN_IS_FIRST_NODE, 0);
			nextEvent = (int) database.insert(McdDB.TABLE_ERROR_PATH, null, cv);
			
			nodeRes.close();
		}while(cursor.moveToPrevious());
		
		if(nextEvent != -1){
			ContentValues value = new ContentValues();
			value.put(McdDB.COLUMN_IS_FIRST_NODE, 1);
			database.update(McdDB.TABLE_ERROR_PATH, value, McdDB.COLUMN_ID + " = ?", new String[]{
					String.valueOf(nextEvent)});
		}
		}
		cursor.close();
		Log.e(TAG, "error detected");
		backtrack(database, mcdDB, FLAG_ERROR);
		return;
		}
	}
	
	//pathNodeID - to get the unexplored events available
	//ui_envID - to get the events to ignore for the given screen
	//note: this method arguments have to be modified when you want to express apps with no UI
	//returns int array of size 2. [0]:eventID, [1]:eventType
	public int[] selectNextEventToTrigger(int pathNodeID, int ui_envID, int activityId,
			SQLiteDatabase database) throws McdException{
		int[] eventInfo = {-1, -1};
		
		//if event selected is of the type text input, then do not remove it here from
		//unexplored list, this is handled in retrieveNextTextForView
		
		//in select event always get the highest <viewType,event> available for the
		//pathNodeID in unexploredList.
		
		 Cursor cursor = database.rawQuery("SELECT " + McdDB.COLUMN_EVENT_ID + ", " + 
					McdDB.COLUMN_EVENT_TYPE + ", " + McdDB.COLUMN_ID + " FROM " + 
					McdDB.TABLE_UNEXPLORED_EVENTS + 
					" WHERE " + McdDB.COLUMN_NODE_ID + " = ? AND " + McdDB.COLUMN_EVENT_ID + 
					" NOT IN (SELECT " + McdDB.COLUMN_EVENT_ID + " FROM " + McdDB.TABLE_IGNORE_EVENT
					+ " WHERE " + McdDB.COLUMN_UI_ENV_ID + " = ?) AND " + McdDB.COLUMN_EVENT_ID +
					" NOT IN (SELECT " + McdDB.COLUMN_EVENT_ID + " FROM " + McdDB.TABLE_ACTIVITY_WIDE_IGNORE_EVENT
					+ " WHERE " + McdDB.COLUMN_ACTIVITY_ID + " = ?)" +
					" ORDER BY " + McdDB.COLUMN_EVENT_PRIORITY + " DESC, " + McdDB.COLUMN_ID +
					" ASC LIMIT 1", new String[]{String.valueOf(pathNodeID), 
				 String.valueOf(ui_envID), String.valueOf(activityId)});
		 if(cursor.moveToFirst()){
			 eventInfo[0] = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_ID));
			 eventInfo[1] = cursor.getInt(cursor.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE));
			 
			 if(eventInfo[1] == 0){ //gui event
				 Cursor tempRes = database.query(McdDB.TABLE_UI_EVENT, new String[]{
						 	McdDB.COLUMN_UI_EVENT_TYPE}, 
							McdDB.COLUMN_ID + " = ?",
							new String[]{String.valueOf(eventInfo[0])},
							null, null, null);
				 if(!(tempRes.moveToFirst())){
					mcdRaiseException("Android Bug-checker found an invalid " +
						 		"primary key for UIEvent table", database);
				 }
				 
				 if(!textDataViewSet.contains(tempRes.getInt(tempRes.getColumnIndexOrThrow(
 	        			McdDB.COLUMN_UI_EVENT_TYPE)))){
					 database.delete(McdDB.TABLE_UNEXPLORED_EVENTS, McdDB.COLUMN_ID + " = ?", 
							 new String[]{String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(
									 McdDB.COLUMN_ID)))});
				 }
				 tempRes.close();
				 tempRes = null;
			 }else{
				 database.delete(McdDB.TABLE_UNEXPLORED_EVENTS, McdDB.COLUMN_ID + " = ?", 
						 new String[]{String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(
								 McdDB.COLUMN_ID)))});
			 }
		 }
		 cursor.close();
		 
		 return eventInfo;
	}
	
	public void addToPath(int nodeID, int execStatus, SQLiteDatabase database){
		ContentValues values = new ContentValues();
		values.put(McdDB.COLUMN_NODE_ID, nodeID);
		values.put(McdDB.COLUMN_EXECUTION_STATUS, execStatus);
		database.insert(McdDB.TABLE_PATH, null, values);
	}
	
	public boolean isViewRootFocused(int n){
		boolean focused = false;
		focused = WindowManagerImpl.getDefault().getViewRoots()[n].getView().hasWindowFocus();
		return focused;
	}
	
	/*
	public void setMenuOnExistingActivityIntance(int activityHash, String activityName){
		McdDB mcdDB = new McdDB(Looper.mcd.getContext());
        SQLiteDatabase database = mcdDB.getReadableDatabase();
		Cursor cursor = database.query(McdDB.TABLE_ACTIVITY, new String[]{
				McdDB.COLUMN_HAS_MENU}, 
				McdDB.COLUMN_ACTIVITY_NAME + " = ? AND " +
				McdDB.COLUMN_TMP_ACTIVITY_HASH + " = ?",
				new String[]{activityName, 
				String.valueOf(activityHash)},
				null, null, null);
		if(cursor.moveToFirst()){
			hasMenu = (cursor.getInt(cursor.getColumnIndexOrThrow(
					McdDB.COLUMN_HAS_MENU)) == 1)? true : false;
		}
		cursor.close();
		database.close();
		cursor = null;
		database = null;
	}
	*/
	
	
	public int getViewRootInFocus(){
		int viewRootInFocus = -1;
		for(int i=1; i <= WindowManagerImpl.getDefault().getViewRoots().length; i++){
			if(isViewRootFocused(WindowManagerImpl.getDefault().getViewRoots().length - i)){
				viewRootInFocus = WindowManagerImpl.getDefault().getViewRoots().length - i;
				break;
			}
		}
		
		return viewRootInFocus;
	}
	
	public void mcdRaiseException(String msg, SQLiteDatabase db) throws McdException{
		try{
	        if(db != null && db.isOpen())
	        	db.close();
	        }catch(SQLiteException e){
	        	Log.e(TAG, "sqliteException when closing database hit");
	        }
	        catch(NullPointerException e){
	        	Log.e(TAG, "nullPointerException when closing database hit");
	        }
		
		throw new McdException(msg);
	}
    
	
	//implementation of Android Bug-checker algorithm
	public void androidBugChecker() throws McdException{
		if(initDelay > 0){
			initDelay--;
			return;
		}
		if(abcSilentReturn){
			return;
		}else{ 
		//add extra check for BACK button press later checking if removeView was 
		//performed irrespective of activities being same or different
		if(getVisibleActivity().hashCode() != previousEventActivity){
			if(isCorrectViewOnTop == false){
				return;
			}else if(popupWindows.size() == 0){
				//check if topmost view has focus if not any view has focus
				if(isViewRootFocused(WindowManagerImpl.getDefault().getViewRoots().length - 1) == false){
					if(abcHangCounter < HANG_LIMIT){
					    abcHangCounter++;
					    return;
					}else if(getViewRootInFocus() == -1){
						Log.e(TAG, "no view has focus. cannot continue further till a view gets focus");
						return;
					}					
				}
			}
		}else if(popupWindows.size() == 0){
			//check if topmost view has focus if not any view has focus
			if(isViewRootFocused(WindowManagerImpl.getDefault().getViewRoots().length - 1) == false){
				if(abcHangCounter < HANG_LIMIT){
				    abcHangCounter++;
				    return;
				}else if(getViewRootInFocus() == -1){
					Log.e(TAG, "no view has focus. cannot continue further till a view gets focus");
					return;
				}					
			}
		}
		
		McdDB mcdDB = new McdDB(Looper.mcd.getContext());
        SQLiteDatabase database = mcdDB.getWritableDatabase();
        
        Cursor pathRes = null;
        
        if(MODE == EXPLORE){
        pathRes = database.query(McdDB.TABLE_PATH, new String[]{McdDB.COLUMN_ID, 
        		McdDB.COLUMN_NODE_ID }, 
				McdDB.COLUMN_EXECUTION_STATUS + " = ?",
				new String[]{String.valueOf(1)},
				null, null, null);
        
        if(!(pathRes.getCount() == 1 || pathRes.getCount() == 0)){
			mcdRaiseException("Android Bug-checker expected PATH table to return" +
						" only one or no row, which was not the case", database);
        }
        }else if(MODE == PLAY){
        	if(playNextEvent == -1){
        	pathRes = database.rawQuery("SELECT " + McdDB.COLUMN_ID + ", " + McdDB.COLUMN_EVENT_ID + ", " + 
					McdDB.COLUMN_EVENT_TYPE + ", " + McdDB.COLUMN_DATA + ", " + 
					McdDB.COLUMN_NEXT_EVENT + " FROM " + 
					McdDB.TABLE_ERROR_PATH + 
					" WHERE " + McdDB.COLUMN_IS_FIRST_NODE + " = ? " +
					" ORDER BY " + McdDB.COLUMN_ID + " ASC" +
					" LIMIT 1", new String[]{String.valueOf(1)});
        	}else{
        		pathRes = database.query(McdDB.TABLE_ERROR_PATH, new String[]{
        				McdDB.COLUMN_ID, McdDB.COLUMN_EVENT_ID, McdDB.COLUMN_EVENT_TYPE, McdDB.COLUMN_DATA,
        				McdDB.COLUMN_NEXT_EVENT}, 
        				McdDB.COLUMN_ID + " = ?", 
        				new String[]{String.valueOf(playNextEvent)}, null, null, null);
        	}
        }
        
        int pathID = -1;
        int nodeToExecute = -1;
        
        int playEventID = -1;
        int playEventType = -1;
        String playData = null;
        
        
        if(pathRes.moveToFirst() && MODE == EXPLORE){
        	pathID = pathRes.getInt(
					pathRes.getColumnIndexOrThrow(McdDB.COLUMN_ID));
        	nodeToExecute = pathRes.getInt(
					pathRes.getColumnIndexOrThrow(McdDB.COLUMN_NODE_ID));
        }else if(pathRes.moveToFirst() && MODE == PLAY){
        	playEventID = pathRes.getInt(pathRes.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_ID));
        	playEventType = pathRes.getInt(pathRes.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE));
        	playData = pathRes.getString(pathRes.getColumnIndexOrThrow(McdDB.COLUMN_DATA));
        	playNextEvent = pathRes.getInt(pathRes.getColumnIndexOrThrow(McdDB.COLUMN_NEXT_EVENT));
        	pathID = pathRes.getInt(
					pathRes.getColumnIndexOrThrow(McdDB.COLUMN_ID));
        	
        	//delete this row entry in error table. Always maintain a copy of database before replay!
        	//it is deleted just for the coding logic, the same table stores many error paths..
        	//each complete run of a play deletes a eror path stored, so that in nextplay run
        	//another error path is re-played! Its better if this logic is modified to store error
        	//path and replay, in the future
        	
        	database.delete(McdDB.TABLE_ERROR_PATH, McdDB.COLUMN_ID + " = ?", 
    				new String[]{String.valueOf(pathID)});
        }
        
        pathRes.close();
        
        if(MODE == PLAY && playEventID != -1){
        	if(playEventType == UI_EVENT){
        	Cursor res = database.query(McdDB.TABLE_UI_EVENT, new String[]{
    				McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_UI_EVENT_TYPE}, 
    				McdDB.COLUMN_ID + " = ?",
    				new String[]{String.valueOf(playEventID)},
    				null, null, null);
        	if(!(res.moveToFirst())){
				mcdRaiseException("Android Bug-checker found an invalid key for" +
							" UiEvent table", database);
        	}
        	int viewID = res.getInt(
        			res.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_ID));
    		Cursor screen = database.query(McdDB.TABLE_VIEW_NODE, new String[]{
    				McdDB.COLUMN_SCREEN_ID}, McdDB.COLUMN_ID + " = ?", 
    				new String[]{String.valueOf(viewID)}, 
    						null, null, null);
    		
        	
        	if(!(screen.moveToFirst())){
				mcdRaiseException("Android Bug-checker found an invalid key for" +
							" UiEnv table", database);
        	}
        	
        	int uiEventType = res.getInt(res.getColumnIndexOrThrow(
        			McdDB.COLUMN_UI_EVENT_TYPE));
        	int screenID = screen.getInt(
					screen.getColumnIndexOrThrow(McdDB.COLUMN_SCREEN_ID));
        	
        	res.close();
        	res = null;
        	screen.close();
        	screen = null;
        	
        	if(abcHangCounter < HANG_LIMIT){
        	if(uiEventType == EVENT_CLICK){
				if(viewClickDone == false){
					try{
				        if(database != null && database.isOpen()){
				        	database.close();
				        	mcdDB.close();
				        }
				        }catch(SQLiteException e){
				        	Log.e(TAG, "sqliteException when closing database hit");
				        }
				        catch(NullPointerException e){
				        	Log.e(TAG, "nullPointerException when closing database hit");
				        }
					abcHangCounter++;
					return;
				}
			}else if(uiEventType == EVENT_LONG_CLICK){
				if(viewLongClickDone == false){
					try{
				        if(database != null && database.isOpen()){
				        	database.close();
				        	mcdDB.close();
				        }
				        }catch(SQLiteException e){
				        	Log.e(TAG, "sqliteException when closing database hit");
				        }
				        catch(NullPointerException e){
				        	Log.e(TAG, "nullPointerException when closing database hit");
				        }
					abcHangCounter++;
					return;
				}
			}
            }
        
        	int viewRootLimit = -1;
        	ViewRootImpl[] tmpViewRoots = WindowManagerImpl.getDefault().getViewRoots();

			//modified logic to handle popup windows which ignores popup windows whose base is not a viewGroup
			boolean viewGrpPopupsExist = false;
			
			if(popupsCreatedOnLastEvent > 0){
				for(int l=1; l<=popupsCreatedOnLastEvent; l++){
					if(tmpViewRoots[tmpViewRoots.length - l].getView() instanceof ViewGroup){
						viewGrpPopupsExist = true;
						break;
					}
				}
			}
			
			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
				for(int l=1; l<=popupWindows.size(); l++){
					if(tmpViewRoots[tmpViewRoots.length - l].getView() instanceof ViewGroup){
						viewGrpPopupsExist = true;
						break;
					}
				}
			}
			
			//if no viewGroup view in popup windows then trigger event on the activity screen below all poup windows
			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
				int viewInFocus = getViewRootInFocus();
				if( viewInFocus == -1){
					tmpViewRoots = null; //to avoid any window leak
					mcdRaiseException("re-execution not reaching same UI sate. We have " +
							"hit a screen with no focused window which was not hit during " +
							"initial exploration", database);
				}
				viewRootLimit = tmpViewRoots.length - viewInFocus ;
			}else{
				viewRootLimit = tmpViewRoots.length - getViewRootInFocus() ;
			}
			
			tmpViewRoots = null; //to avoid any window leak
					
        	View tmpView = null;
        	
        	if(uiEventType != EVENT_BACK && uiEventType != EVENT_MENU_CLICK && uiEventType != EVENT_ROTATE_SCREEN){
	        	tmpView = reachAndGetViewFromID(viewID, screenID, viewRootLimit,
	        					database);
        	}
        	
        	//adding to path is not needed as the node is already in path. only updating
        	//the node with event info was needed which has already been done
//        	addToPath(nodeToExecute, -1, database);
        	popupsCreatedOnLastEvent = 0;
			isCorrectViewOnTop = false;
        	previousEventActivity = getVisibleActivity().hashCode();
        	isPreviousBackClickEvent = false;
        	isPreviousMenuClickEvent = false;
        	abcHangCounter = -1;
        	triggerUIEvent(tmpView, uiEventType, playData);
        	
        }else if(playEventType == INTENT_EVENT){
        	Log.v(TAG, "intent event to be triggered");
        }
        }else if(MODE == EXPLORE && pathID != -1){
        	if(abcHangCounter < HANG_LIMIT){
        	Cursor tmpNode = database.rawQuery("SELECT " + McdDB.COLUMN_EVENT_ID + ", " + 
        			McdDB.COLUMN_EVENT_TYPE + " FROM " + 
        			McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.COLUMN_ID + " IN (SELECT "
    				+ McdDB.TABLE_PATH_NODE + "." + McdDB.COLUMN_ID + " FROM " + 
    				McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.TABLE_PATH_NODE +"."+ McdDB.COLUMN_ID 
    				+ " < " + nodeToExecute + 
    				" ORDER BY " + McdDB.TABLE_PATH_NODE +"."+ McdDB.COLUMN_ID + " DESC LIMIT 1);", 
    				null);
        	if(tmpNode.moveToFirst()){
        		//eventType = 0 for Ui event and eventType = 1 for intent
        		if(tmpNode.getInt(tmpNode.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE)) == UI_EVENT){
        			Cursor tmpUiEvent = database.query(McdDB.TABLE_UI_EVENT, new String[]{
        					McdDB.COLUMN_UI_EVENT_TYPE}, McdDB.COLUMN_ID + " = ?", new String[]{
        					String.valueOf(tmpNode.getInt(tmpNode.getColumnIndexOrThrow(
        							McdDB.COLUMN_EVENT_ID)))}, null, null, null);
        			
        			tmpUiEvent.moveToFirst();
        			if(tmpUiEvent.getInt(tmpUiEvent.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE)) 
        					== EVENT_CLICK){
        				if(viewClickDone == false){
        				    tmpUiEvent.close();
        				    tmpNode.close();
        					try{
        				        if(database != null && database.isOpen()){
        				        	database.close();
        				        	mcdDB.close();
        				        }
        				        }catch(SQLiteException e){
        				        	Log.e(TAG, "sqliteException when closing database hit");
        				        }
        				        catch(NullPointerException e){
        				        	Log.e(TAG, "nullPointerException when closing database hit");
        				        }
        					abcHangCounter++;
        					return;
        				}
        			}else if(tmpUiEvent.getInt(tmpUiEvent.getColumnIndexOrThrow(
        					McdDB.COLUMN_EVENT_TYPE)) == EVENT_LONG_CLICK){
        				if(viewLongClickDone == false){
        				    tmpUiEvent.close();
        				    tmpNode.close();
        					try{
        				        if(database != null && database.isOpen()){
        				        	database.close();
        				        	mcdDB.close();
        				        }
        				        }catch(SQLiteException e){
        				        	Log.e(TAG, "sqliteException when closing database hit");
        				        }
        				        catch(NullPointerException e){
        				        	Log.e(TAG, "nullPointerException when closing database hit");
        				        }
        					abcHangCounter++;
        					return;
        				}
        			}
        			
        			tmpUiEvent.close();
        			tmpUiEvent = null;
        		}
        
        	}
        	tmpNode.close();
        	tmpNode = null;
        	}
        	
    		Cursor nodeResult = database.query(McdDB.TABLE_PATH_NODE, new String[]{
    				McdDB.COLUMN_EVENT_ID, McdDB.COLUMN_EVENT_TYPE, McdDB.COLUMN_UI_ENV_ID}, 
    				McdDB.COLUMN_ID + " = ?",
    				new String[]{String.valueOf(nodeToExecute)},
    				null, null, null);
    		
    		int uiEnvID = -1;
    		int eventID = -1;
    		int eventType = -1;
    		
    		if(!(nodeResult.getCount() == 1)){
				mcdRaiseException("Android Bug-checker expected PathNode table to" +
							" return only one row as result which was not the case", database);
    		}else{
	    		nodeResult.moveToFirst();
	    		uiEnvID = nodeResult.getInt(nodeResult.getColumnIndexOrThrow(
	    				McdDB.COLUMN_UI_ENV_ID));
	    		eventID = nodeResult.getInt(nodeResult.getColumnIndexOrThrow(
	    				McdDB.COLUMN_EVENT_ID));
	    		eventType = nodeResult.getInt(nodeResult.getColumnIndexOrThrow(
	    				McdDB.COLUMN_EVENT_TYPE));
    		}
    		nodeResult.close();
    		nodeResult = null;
  
    		
    		//code to set execution status of next node in path table to 1, if no next 
    		//node then do nothing
    		ContentValues cv = new ContentValues();
    		cv.put(McdDB.COLUMN_EXECUTION_STATUS, -1);
    		database.update(McdDB.TABLE_PATH, cv, McdDB.COLUMN_ID + " = ?", new String[]{ 
    				String.valueOf(pathID)});
    		
    		database.execSQL("UPDATE " + McdDB.TABLE_PATH + " SET " + McdDB.COLUMN_EXECUTION_STATUS
    				+ " = 1 WHERE " + McdDB.TABLE_PATH + "." + McdDB.COLUMN_NODE_ID + " IN (SELECT "
    				+ McdDB.TABLE_PATH_NODE + "." + McdDB.COLUMN_ID + " FROM " + 
    				McdDB.TABLE_PATH_NODE + " WHERE " + McdDB.TABLE_PATH_NODE +"."+ McdDB.COLUMN_ID 
    				+ " > " + nodeToExecute + 
    				" ORDER BY " + McdDB.TABLE_PATH_NODE +"."+ McdDB.COLUMN_ID + " ASC LIMIT 1);");
    		
    		//this query returns activity name corresponding to activityID obtained from ui_envID,
    		//provided activityID != -1. Update temporary activity hash only if a result is returned
    		Cursor activityInfo = database.rawQuery("SELECT "+ McdDB.COLUMN_ACTIVITY_NAME +", " + 
    		McdDB.TABLE_ACTIVITY + "." + McdDB.COLUMN_ID +" FROM "+
    		McdDB.TABLE_ACTIVITY + " JOIN " + McdDB.TABLE_UI_ENV + " ON " + McdDB.TABLE_ACTIVITY + 
    		"." + McdDB.COLUMN_ID + " = " + McdDB.TABLE_UI_ENV + "." + McdDB.COLUMN_ACTIVITY_ID + 
    		" WHERE " + McdDB.TABLE_UI_ENV + "." + McdDB.COLUMN_ID + " = ?", new String[]{
    				String.valueOf(uiEnvID)});
    		
    		
			int viewRootLimit = -1;
			int activityID = -1;
					
    		if(activityInfo.moveToFirst()){
    			if(!(activityInfo.getCount() == 1 && 
    				getVisibleActivity().getLocalClassName().equals(activityInfo.getString(
    						activityInfo.getColumnIndexOrThrow(McdDB.COLUMN_ACTIVITY_NAME))))){
    				mcdRaiseException("Android Bug-checker found some mismatch in Activity table", database);
    			}
    			
    			activityID = activityInfo.getInt(
						activityInfo.getColumnIndexOrThrow(McdDB.COLUMN_ID));
						
    			ContentValues tmpVal = new ContentValues();
    			tmpVal.put(McdDB.COLUMN_TMP_ACTIVITY_HASH, getVisibleActivity().hashCode());
    			database.update(McdDB.TABLE_ACTIVITY, tmpVal, McdDB.COLUMN_ID + " = ?", new String[]
    					{String.valueOf(activityID)});
    			
    			//update activity-screen-hash
    			long tmpScreenHash = 0;
    			ViewRootImpl[] tmpViewRoots = WindowManagerImpl.getDefault().getViewRoots();
    			
    			//modified logic to handle popup windows which ignores popup windows whose base is not a viewFroup
    			boolean viewGrpPopupsExist = false;
    			
    			if(popupsCreatedOnLastEvent > 0){
    				for(int l=1; l<=popupsCreatedOnLastEvent; l++){
    					if(tmpViewRoots[tmpViewRoots.length - l].getView() instanceof ViewGroup){
    						viewGrpPopupsExist = true;
	    					ViewGroup vg = (ViewGroup)tmpViewRoots[tmpViewRoots.length - l].getView();
	    					tmpScreenHash += computeScreenHash(vg, tmpScreenHash);
    					}
    				}
    			}
    			
    			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
    				for(int l=1; l<=popupWindows.size(); l++){
    					if(tmpViewRoots[tmpViewRoots.length - l].getView() instanceof ViewGroup){
    						viewGrpPopupsExist = true;
	    					ViewGroup vg = (ViewGroup)tmpViewRoots[tmpViewRoots.length - l].getView();
	    					tmpScreenHash += computeScreenHash(vg, tmpScreenHash);
    					}
    				}
    			}
    			
    			//if no viewGroup view in popup windows then trigger event on the activity screen below all poup windows
    			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
//    				if(!(isViewRootFocused(tmpViewRoots.length - (popupWindows.size() + 1))))
    				int viewInFocus = getViewRootInFocus();
    				if( viewInFocus == -1){
    					tmpViewRoots = null;
    					mcdRaiseException("re-execution not reaching same UI sate. We have " +
    							"hit a screen with no focused window which was not hit during " +
    							"initial exploration", database);
    				}
    				viewRootLimit = tmpViewRoots.length - viewInFocus ;
    			}else{
//    				if(!isPreviousMenuClickEvent)
//    					viewRootLimit = 1;
//    				else{
//    					for(int i=1; i <= tmpViewRoots.length; i++){
//    						if(isViewRootFocused(tmpViewRoots.length - i)){
//    							viewRootLimit = i;
//    							break;
//    						}
//    					}
//    				}
    				viewRootLimit = tmpViewRoots.length - getViewRootInFocus() ;
    			}
    			
    			if(viewGrpPopupsExist == false){
    				tmpScreenHash = computeScreenHash(
    						(ViewGroup)tmpViewRoots[tmpViewRoots.length - viewRootLimit].getView(),
    						0);
    			}
    			
    			tmpVal = null;
    			tmpVal = new ContentValues();
    			tmpVal.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, tmpScreenHash);
    			database.update(McdDB.TABLE_UI_ENV, tmpVal, McdDB.COLUMN_ID + " = ?", 
    					new String[]{String.valueOf(uiEnvID)});
    			
    			//to avoid any activity leak
    			tmpViewRoots = null;
    		}
    		activityInfo.close();
    		activityInfo = null;
    		
    		if(eventType == -1){
    			//control has reached the backtrack node, select a new event and explore
    			
    			int[] nextEvent = selectNextEventToTrigger(nodeToExecute, uiEnvID, activityID, database);
            	updatePathNodeWithEvent(nodeToExecute, nextEvent[0], nextEvent[1], database);
            	if(nextEvent[1] == -1){
            		//no event to be triggered at this point. so backtrack
            		//delete any event remaining in unexplored loist but not triggered due to their 
            		//being in ignored list too
            		//delete the dummy node created
            		database.delete(McdDB.TABLE_UNEXPLORED_EVENTS, McdDB.COLUMN_NODE_ID + " = ?", 
            				new String[]{String.valueOf(nodeToExecute)});
            		database.delete(McdDB.TABLE_PATH_NODE_DATA, McdDB.COLUMN_NODE_ID + " = ?", 
            				new String[]{String.valueOf(nodeToExecute)});
            		database.delete(McdDB.TABLE_PATH, McdDB.COLUMN_NODE_ID + " = ?", 
            				new String[]{String.valueOf(nodeToExecute)});
            		database.delete(McdDB.TABLE_FIELD_SPECIFIC_DATA, McdDB.COLUMN_NODE_ID + " = ?", 
            				new String[]{String.valueOf(nodeToExecute)}); // to remove any default text added at this node
            		database.delete(McdDB.TABLE_PATH_NODE, McdDB.COLUMN_ID + " = ?", 
            				new String[]{String.valueOf(nodeToExecute)});
            		
            		Cursor btCursor = database.rawQuery("SELECT COUNT(*) FROM " + McdDB.TABLE_PATH, null);
            		btCursor.moveToFirst();
            		if(btCursor.getInt(0) == 0){
            			btCursor.close();
                		backtrack(database, mcdDB, FLAG_STOP);
                		return;
            		}else{
            			btCursor.close();
	            		backtrack(database, mcdDB, FLAG_NO_ERROR);
	            		return;
            		}
            	}else if(nextEvent[1] == UI_EVENT){ //eventType is UIevent
            		
            		Cursor res = database.query(McdDB.TABLE_UI_EVENT, new String[]{
            				McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_UI_EVENT_TYPE}, 
            				McdDB.COLUMN_ID + " = ?",
            				new String[]{String.valueOf(nextEvent[0])},
            				null, null, null);
            		Cursor screen = database.query(McdDB.TABLE_UI_ENV, new String[]{
            				McdDB.COLUMN_SCREEN_ID}, McdDB.COLUMN_ID + " = ?", 
            				new String[]{String.valueOf(uiEnvID)}, 
            						null, null, null);
            		
    	        	if(!(res.moveToFirst())){
						mcdRaiseException("Android Bug-checker found an invalid key for" +
									" UiEvent table", database);
    	        	}
    	        	if(!(screen.moveToFirst())){
						mcdRaiseException("Android Bug-checker found an invalid key for" +
									" UiEnv table", database);
    	        	}
    	        	int viewID = res.getInt(
    	        			res.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_ID));
    	        	int uiEventType = res.getInt(res.getColumnIndexOrThrow(
    	        			McdDB.COLUMN_UI_EVENT_TYPE));
    	        	int screenID = screen.getInt(
        					screen.getColumnIndexOrThrow(McdDB.COLUMN_SCREEN_ID));
    	        	
    	        	res.close();
    	        	res = null;
    	        	screen.close();
    	        	screen = null;
    	        	
    	        	String textData = null;
    	        	View tmpView = null;
    	        	
    	        	if(uiEventType != EVENT_BACK && uiEventType != EVENT_MENU_CLICK && uiEventType != EVENT_ROTATE_SCREEN){
	    	        	tmpView = reachAndGetViewFromID(viewID, screenID, viewRootLimit,
	    	        					database);
	    	        	
	    	        	if(textDataViewSet.contains(uiEventType)){
	    	        		textData = retrieveNextDataForView(tmpView, viewID,
	    	        				getVisibleActivity().getLocalClassName(), 
	    							nodeToExecute, database);
	    	        	}
    	        	}
    	        	
    	        	//adding to path is not needed as the node is already in path. only updating
    	        	//the node with event info was needed which has already been done
//    	        	addToPath(nodeToExecute, -1, database);
    	        	popupsCreatedOnLastEvent = 0;
					isCorrectViewOnTop = false;
    	        	previousEventActivity = getVisibleActivity().hashCode();
    	        	isPreviousBackClickEvent = false;
    	        	isPreviousMenuClickEvent = false;
    	        	abcHangCounter = -1;
    	        	triggerUIEvent(tmpView, uiEventType, textData);
    	        	tmpView = null; //to avoid any window leak
            	}else if(nextEvent[1] == INTENT_EVENT){
            		//eventType is intent
            	} 
    		}else if(eventType == UI_EVENT){
    			Cursor viewRes = database.query(McdDB.TABLE_UI_EVENT, new String[]{
        				McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_UI_EVENT_TYPE}, 
        				McdDB.COLUMN_ID + " = ?",
        				new String[]{String.valueOf(eventID)},
        				null, null, null);
        		Cursor screen = database.query(McdDB.TABLE_UI_ENV, new String[]{
        				McdDB.COLUMN_SCREEN_ID}, McdDB.COLUMN_ID + " = ?", 
        				new String[]{String.valueOf(uiEnvID)}, 
        						null, null, null);
    			
        		if(!(screen.moveToFirst())){
					mcdRaiseException("Android Bug-checker found an invalid key for" +
								" UiEnv table", database);
        		}
        		
    			if(!(viewRes.getCount() == 1)){
					mcdRaiseException("Android Bug-checker expected UiEvent table to" +
								" return a single row result which was not the case", database);
    			}
    			viewRes.moveToFirst();
    			View targetView = null;
    			String data = null;
    			int tmpUiEventType = viewRes.getInt(
    					viewRes.getColumnIndexOrThrow(McdDB.COLUMN_UI_EVENT_TYPE));
    			
    			if(tmpUiEventType != EVENT_BACK && tmpUiEventType != EVENT_MENU_CLICK && 
    					tmpUiEventType != EVENT_ROTATE_SCREEN){
	    			targetView = reachAndGetViewFromID(viewRes.getInt(
	    					viewRes.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_ID)), screen.getInt(
	    							screen.getColumnIndexOrThrow(McdDB.COLUMN_SCREEN_ID)), 
	    							viewRootLimit, database);
	    			//get any text data corresponding to the path node
	    			data = getTextDataStoredForPathNode(nodeToExecute, database);
    			}
    			
    			viewRes.close();
    			viewRes = null;
    			screen.close();
    			screen = null;
    			
    			popupsCreatedOnLastEvent = 0;
				isCorrectViewOnTop = false;
	        	previousEventActivity = getVisibleActivity().hashCode();
	        	isPreviousBackClickEvent = false;
	        	isPreviousMenuClickEvent = false;
	        	abcHangCounter = -1;
    			triggerUIEvent(targetView, tmpUiEventType, data);
    			
    			targetView = null; //to avoid any window leak
    		}else if(eventType == INTENT_EVENT){
    			Log.v(TAG,"intent event");
    		}
    		
        }else if(MODE == EXPLORE){ //code to scan current app-state and trigger the next event

        	//check if previous event was on data widget and apply corresponding logic
            Cursor tmpNodeRes = database.rawQuery("SELECT " + McdDB.COLUMN_NODE_ID + " FROM " +
    		McdDB.TABLE_PATH + " WHERE " + McdDB.COLUMN_ID + " = (SELECT MAX(" + McdDB.COLUMN_ID + 
    		") FROM " + McdDB.TABLE_PATH + ")", null);
            
            int prevNodeID = -1;
            if(tmpNodeRes.moveToFirst()){
            	prevNodeID = tmpNodeRes.getInt(tmpNodeRes.getColumnIndexOrThrow(
            			McdDB.COLUMN_NODE_ID));
            }
            tmpNodeRes.close();
            tmpNodeRes = null;
            
            Cursor viewRes = null;
            int tmpPopID = -1;
            int prevViewID = -1;
            int prevUIEnvID = -1;
            int prevEventID = -1;
            int prevEventType = -1;
            int prevUiEventType = -1;
            long screenHash = 0;
            
			ViewRootImpl[] viewRoots = WindowManagerImpl.getDefault().getViewRoots();
			
			boolean viewGrpPopupsExist = false;
			int viewRootLimit = -1;
			
			if(popupsCreatedOnLastEvent > 0){
				for(int l=1; l<=popupsCreatedOnLastEvent; l++){
					if(viewRoots[viewRoots.length - l].getView() instanceof ViewGroup){
						viewGrpPopupsExist = true;
    					ViewGroup vg = (ViewGroup)viewRoots[viewRoots.length - l].getView();
    					screenHash += computeScreenHash(vg, screenHash);
					}
				}
			}
			
			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
				for(int l=1; l<=popupWindows.size(); l++){
					if(viewRoots[viewRoots.length - l].getView() instanceof ViewGroup){
						viewGrpPopupsExist = true;
    					ViewGroup vg = (ViewGroup)viewRoots[viewRoots.length - l].getView();
    					screenHash += computeScreenHash(vg, screenHash);
					}
				}
			}
			
			//if no viewGroup view in popup windows then trigger event on the activity screen below all
			//popup windows which is in focus
			if(viewGrpPopupsExist == false && popupWindows.size() > 0){
				int viewInFocus = getViewRootInFocus();
				if(viewInFocus != -1)
					viewRootLimit = viewRoots.length - viewInFocus;
				else
					viewRootLimit = -2; //perform backtrack because no focused viewRoot
			}else{
//				if(!isPreviousMenuClickEvent)
//					viewRootLimit = 1;
//				else{
//					for(int i=1; i <= viewRoots.length; i++){
//						if(isViewRootFocused(viewRoots.length - i)){
//							viewRootLimit = i;
//							break;
//						}
//					}
//				}
				viewRootLimit = viewRoots.length - getViewRootInFocus();
			}
			
			if(viewGrpPopupsExist == false && viewRootLimit != -2){
				screenHash = computeScreenHash(
						(ViewGroup)viewRoots[viewRoots.length - viewRootLimit].getView(),
						0);
			}
			
			viewRoots = null; //to avoid any window leak
            
            if(prevNodeID != -1){
            	Cursor nodeRes = database.query(McdDB.TABLE_PATH_NODE, new String[]{
        				McdDB.COLUMN_EVENT_ID, McdDB.COLUMN_EVENT_TYPE, McdDB.COLUMN_UI_ENV_ID}, 
        				McdDB.COLUMN_ID + " = ?",
        				new String[]{String.valueOf(prevNodeID)},
        				null, null, null);
            	
            	if(!(nodeRes.moveToFirst())){
					mcdRaiseException("Android Bug-checker found an invalid key in the " +
								"PathNode table", database);
            	}
            	
            	//events like BACK button click, screen rotation etc. will be classified as UI 
            	//events with viewID = -1 and some uiEventType
            	prevEventID = nodeRes.getInt(nodeRes.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_ID));
            	prevEventType = nodeRes.getInt(nodeRes.getColumnIndexOrThrow(
            			McdDB.COLUMN_EVENT_TYPE));
            	prevUIEnvID = nodeRes.getInt(nodeRes.getColumnIndexOrThrow(
						McdDB.COLUMN_UI_ENV_ID));
            	nodeRes.close();
            	nodeRes = null;
            	
            	if(prevEventType == UI_EVENT){ //ui-event
            		
        			viewRes = database.query(McdDB.TABLE_UI_EVENT, new String[]{
            				McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_UI_EVENT_TYPE},
            				McdDB.COLUMN_ID + " = ?",
            				new String[]{String.valueOf(prevEventID)},
            				null, null, null);
        			
        			//if and not assert because orientation change is also a UI event with view=-1
        			if(viewRes.moveToFirst()){
        				prevViewID = viewRes.getInt(viewRes.getColumnIndexOrThrow(
    							McdDB.COLUMN_VIEW_ID));
        				prevUiEventType = viewRes.getInt(viewRes.getColumnIndexOrThrow(
        						McdDB.COLUMN_UI_EVENT_TYPE));
        			}
        			viewRes.close();
        			viewRes = null;
        			
        			if(abcHangCounter < HANG_LIMIT){
        			if(prevUiEventType == EVENT_CLICK){
        				if(viewClickDone == false){
        					try{
        				        if(database != null && database.isOpen()){
        				        	database.close();
        				        	mcdDB.close();
        				        }
        				        }catch(SQLiteException e){
        				        	Log.e(TAG, "sqliteException when closing database hit");
        				        }
        				        catch(NullPointerException e){
        				        	Log.e(TAG, "nullPointerException when closing database hit");
        				        }
        					abcHangCounter++;
        					return;
        				}
        			}else if(prevUiEventType == EVENT_LONG_CLICK){
        				if(viewLongClickDone == false){
        					try{
        				        if(database != null && database.isOpen()){
        				        	database.close();
        				        	mcdDB.close();
        				        }
        				        }catch(SQLiteException e){
        				        	Log.e(TAG, "sqliteException when closing database hit");
        				        }
        				        catch(NullPointerException e){
        				        	Log.e(TAG, "nullPointerException when closing database hit");
        				        }
        					abcHangCounter++;
        					return;
        				}
        			}
        			}
        			
        			Cursor pathLength = database.rawQuery("SELECT COUNT(*) FROM " + McdDB.TABLE_PATH, null);
                    pathLength.moveToFirst();
                    int len = pathLength.getInt(0);
                    Log.v(TAG, "path length seen: " + len);
        			if(len >= DEPTH_LIMIT || viewRootLimit == -2){
        	          	pathLength.close();
        	           	backtrack(database, mcdDB, FLAG_NO_ERROR);
        	           	return;
        			}
        			pathLength.close();
        			pathLength = null;
        			
        			Cursor tmpHashRes = database.query(McdDB.TABLE_UI_ENV, new String[]{
            				McdDB.COLUMN_SCREEN_ACTIVITY_HASH}, 
            				McdDB.COLUMN_ID + " = ?", new String[]{
            				String.valueOf(prevUIEnvID)}, null, null, null);
            		if(!tmpHashRes.moveToFirst()){
						mcdRaiseException("Android Bug-checker found an inconsistent primary key" +
									" in UIEnv table", database);
            		}
            		int tmpHashValue = tmpHashRes.getInt(tmpHashRes.getColumnIndexOrThrow(
							McdDB.COLUMN_SCREEN_ACTIVITY_HASH));
            		tmpHashRes.close();
            		tmpHashRes = null;
            		
            		Cursor activityInfo = database.rawQuery("SELECT "+ McdDB.COLUMN_ACTIVITY_NAME +", " + 
    			    		McdDB.TABLE_ACTIVITY + "." + McdDB.COLUMN_ID +" FROM "+
    			    		McdDB.TABLE_ACTIVITY + " JOIN " + McdDB.TABLE_UI_ENV + " ON " + McdDB.TABLE_ACTIVITY + 
    			    		"." + McdDB.COLUMN_ID + " = " + McdDB.TABLE_UI_ENV + "." + McdDB.COLUMN_ACTIVITY_ID + 
    			    		" WHERE " + McdDB.TABLE_UI_ENV + "." + McdDB.COLUMN_ID + " = ?", new String[]{
    			    				String.valueOf(prevUIEnvID)});
    				
    				activityInfo.moveToFirst();
        				
        				//add UI events to ignore events (events those need not be triggered again and agin in a path. 
        				//E.g., setting an EditText repeatedly) only if previous activity and current 
        				//activity are the same
        				if(activityInfo.getString(activityInfo.getColumnIndexOrThrow(
        						McdDB.COLUMN_ACTIVITY_NAME)).equals(
        								getVisibleActivity().getLocalClassName())){
        				if(prevUiEventType == EVENT_MENU_CLICK || prevUiEventType == EVENT_ROTATE_SCREEN){
        					//just a heuristic to avoid multiple menu clicks or rotate screens on the same activity
        					ContentValues cv = new ContentValues();
							cv.put(McdDB.COLUMN_EVENT_ID, prevEventID);
							cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
							cv.put(McdDB.COLUMN_ACTIVITY_ID, activityInfo.getInt(
									activityInfo.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
							cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
							database.insert(McdDB.TABLE_ACTIVITY_WIDE_IGNORE_EVENT, null,
									cv);
        				}else{ 
        					if(prevViewID > 0){
        					Cursor popRes = database.query(McdDB.TABLE_VIEW_NODE, new String[]
            						{McdDB.COLUMN_POPUP_ID, McdDB.COLUMN_VIEW_TYPE}, 
            						McdDB.COLUMN_ID + " = ?", 
            						new String[]{String.valueOf(prevViewID)}, null, null, null);
            				popRes.moveToFirst();
            				tmpPopID = popRes.getInt(popRes.getColumnIndexOrThrow(
            						McdDB.COLUMN_POPUP_ID));
            				int tmpType = popRes.getInt(popRes.getColumnIndexOrThrow(
    								McdDB.COLUMN_VIEW_TYPE));
            				popRes.close();
            				popRes = null;
            				
            				
        					if((tmpPopID == -1 && popupWindows.size() == 0) || 
        						(tmpPopID != -1 && popupWindows.size() > 0)){ 
    						//logic to ignore events
        					if(screenHash == tmpHashValue){
        						
        						List<McdTriple> relativePosLst = 
        								isViewInsideListOrSpinnerItem(prevViewID, database);
        						Cursor lstCur = null;
        						if(relativePosLst.size() > 0){
        							switch(tmpType){
        							case VIEW_RADIO_BUTTON: 
        								//reasoning about immediate parent of given view
        								if(relativePosLst.size() >= 2 && 
        								relativePosLst.get(1).second == VIEW_RADIO_GROUP){
        									lstCur = database.query(McdDB.TABLE_VIEW_NODE, 
        											new String[]{McdDB.COLUMN_ID}, 
        											McdDB.COLUMN_PARENT_ID + " = ?", 
        											new String[]{String.valueOf(
        													relativePosLst.get(0).third)}, 
        													null, null, null);
        									if(lstCur.moveToFirst()){
        										for(int i=0; i<lstCur.getCount(); i++){
        											Cursor tmpLst = database.query(McdDB.TABLE_UI_EVENT,
        													new String[]{McdDB.COLUMN_ID}, 
        													McdDB.COLUMN_VIEW_ID + " = ? AND " +
        													McdDB.COLUMN_UI_EVENT_TYPE + " = ?", 
        													new String[]{String.valueOf(
        															lstCur.getInt(lstCur.getColumnIndexOrThrow(
        																	McdDB.COLUMN_ID))), 
        																	String.valueOf(prevUiEventType)}, 
        																	null, null, null);
        											if(tmpLst.moveToFirst()){
        											ContentValues cv = new ContentValues();
        											cv.put(McdDB.COLUMN_EVENT_ID, tmpLst.getInt(
        													tmpLst.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
        											cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
        											cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
        											cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
        											database.insert(McdDB.TABLE_IGNORE_EVENT, null,
        													cv);
        											
        											Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
        											}
        											lstCur.moveToNext();
        											tmpLst.close();
        											tmpLst = null;
        										}
        									}
    										lstCur.close();
    										lstCur = null;
        								}
        								break;
        							case VIEW_CHECKED_TEXT_VIEW:
        							case VIEW_CHECK_BOX:
        								if(relativePosLst.size() >= 2){
        									lstCur = database.query(McdDB.TABLE_VIEW_NODE, 
        											new String[]{McdDB.COLUMN_ID}, 
        											McdDB.COLUMN_PARENT_ID + " = ?", 
        											new String[]{String.valueOf(
        													relativePosLst.get(0).third)}, 
        													null, null, null);
        									if(lstCur.moveToFirst()){
        										for(int i=0; i<lstCur.getCount(); i++){
        											Cursor tmpLst = database.query(McdDB.TABLE_UI_EVENT,
        													new String[]{McdDB.COLUMN_ID}, 
        													McdDB.COLUMN_VIEW_ID + " = ? AND " +
        													McdDB.COLUMN_UI_EVENT_TYPE + " = ?", 
        													new String[]{String.valueOf(
        															lstCur.getInt(lstCur.getColumnIndexOrThrow(
        																	McdDB.COLUMN_ID))), 
        																	String.valueOf(prevUiEventType)}, 
        																	null, null, null);
        											if(tmpLst.moveToFirst()){
        											ContentValues cv = new ContentValues();
        											cv.put(McdDB.COLUMN_EVENT_ID, tmpLst.getInt(
        													tmpLst.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
        											cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
        											cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
        											cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
        											database.insert(McdDB.TABLE_IGNORE_EVENT, null,
        													cv);
        											
        											Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
        											}
        											lstCur.moveToNext();
        											tmpLst.close();
        											tmpLst = null;
        										}
        									}
        									lstCur.close();
        									lstCur = null;
        								}
        								break;
        							default:
        								removeUiEventInHierarchy(relativePosLst, prevUiEventType, 
        										prevUIEnvID, prevNodeID, database);
        								break;
        							}
        						}else if(tmpType == VIEW_RADIO_BUTTON){
        							lstCur = database.rawQuery("SELECT " + McdDB.COLUMN_ID + ", "
        									+ McdDB.COLUMN_VIEW_TYPE + " FROM " +
        									McdDB.TABLE_VIEW_NODE + " WHERE " + McdDB.COLUMN_ID +
        									" IN (SELECT " + McdDB.COLUMN_PARENT_ID + " FROM " + 
        									McdDB.TABLE_VIEW_NODE + " WHERE " + McdDB.COLUMN_ID +
        									" = ?)", new String[]{String.valueOf(prevViewID)});
        							if(!lstCur.moveToFirst()){
										mcdRaiseException("Android Bug-checker found invalid key " +
													"in ViewNode table", database);
        							}
        							if(lstCur.getInt(lstCur.getColumnIndexOrThrow(
        									McdDB.COLUMN_VIEW_TYPE)) == VIEW_RADIO_GROUP){
        							Cursor tmpCur = database.query(McdDB.TABLE_VIEW_NODE, 
        									new String[]{McdDB.COLUMN_ID, McdDB.COLUMN_VIEW_TYPE}, 
        									McdDB.COLUMN_PARENT_ID 
        									+ " = ?", new String[]{String.valueOf(lstCur.getInt(
        											lstCur.getColumnIndexOrThrow(McdDB.COLUMN_ID)))}, 
        											null, null, null);
        							if(tmpCur.moveToFirst()){
	        								for(int i=0; i<tmpCur.getCount(); i++){
	        									if(tmpCur.getInt(tmpCur.getColumnIndexOrThrow(
	        											McdDB.COLUMN_VIEW_TYPE)) == 
	        											VIEW_RADIO_BUTTON){
	        									Cursor tmpCur1 = database.query(McdDB.TABLE_UI_EVENT,
    													new String[]{McdDB.COLUMN_ID}, 
    													McdDB.COLUMN_VIEW_ID + " = ? AND " +
    													McdDB.COLUMN_UI_EVENT_TYPE + " = ?", 
    													new String[]{String.valueOf(
    															tmpCur.getInt(tmpCur.getColumnIndexOrThrow(
    																	McdDB.COLUMN_ID))), 
    																	String.valueOf(prevUiEventType)}, 
    																	null, null, null);
	        									if(tmpCur1.moveToFirst()){
		        								ContentValues cv = new ContentValues();
												cv.put(McdDB.COLUMN_EVENT_ID, tmpCur1.getInt(
														tmpCur1.getColumnIndexOrThrow(McdDB.COLUMN_ID)));
												cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
												cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
												cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
												database.insert(McdDB.TABLE_IGNORE_EVENT, null,
														cv);
												
												Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
	        									}
	        									tmpCur1.close();
	        									tmpCur1 = null;
	        								}
											tmpCur.moveToNext();
        								}
        							}
        							tmpCur.close();
        							tmpCur = null;
        							}
        							lstCur.close();
        							lstCur = null;
        							
        						}else if(prevUiEventType == EVENT_LONG_CLICK){
                					//usually long clicks are used to get context menu etc.
                					//so no point exploring it multiple times if new screens are'nt reached
                					ContentValues cv = new ContentValues();
        							cv.put(McdDB.COLUMN_EVENT_ID, prevEventID);
        							cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
        							cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
        							cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
        							database.insert(McdDB.TABLE_IGNORE_EVENT, null,
        									cv);
        							Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
                				}else /*if(isViewTypeDataWidget(tmpType))*/{
        							ContentValues cv = new ContentValues();
									cv.put(McdDB.COLUMN_EVENT_ID, prevEventID);
									cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
									cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
									cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
									database.insert(McdDB.TABLE_IGNORE_EVENT, null,
											cv);
									Log.e("abc", "PREVIOUS EVENT ADDED TO IGNORE LIST");
        						}
        					}
        				}else if(tmpPopID == -1 && popupWindows.size() > 0){
        					//do not repeatedly explore events leading to popup windows
        					//todo: later on if you find this missing interesting paths, remove this
        					//it will still work fine but will repeatedly explore popup windows
        					ContentValues cv = new ContentValues();
							cv.put(McdDB.COLUMN_EVENT_ID, prevEventID);
							cv.put(McdDB.COLUMN_EVENT_TYPE, UI_EVENT);
							cv.put(McdDB.COLUMN_UI_ENV_ID, prevUIEnvID);
							cv.put(McdDB.COLUMN_NODE_ID, prevNodeID);
							database.insert(McdDB.TABLE_IGNORE_EVENT, null,
									cv);
        				}
            			}
        				}
        			
        			}	
        			activityInfo.close();
        			
            	}else if(prevEventType == INTENT_EVENT){
            		//bactrack logic in case path depth limit is reached. Similar code 
            		//is found in the UI_EVENT part too. This control is used only for
            		//backtracking or performing any heuristics corresponding to previous 
            		//event. Next event selection is done later and not here.
            		
            		Cursor pathLength = database.rawQuery("SELECT COUNT(*) FROM " + McdDB.TABLE_PATH, null);
                    pathLength.moveToFirst();
                    int len = pathLength.getInt(0);
        			if(len >= DEPTH_LIMIT){
        	          	pathLength.close();
        	           	backtrack(database, mcdDB, FLAG_NO_ERROR);
        	           	return;
        			}
        			pathLength.close();
        			pathLength = null;
            	}
            }
        	
        	int activityID = -1;
        	int screenID = -1;
        	int ui_envID = -1;
    		Cursor activityResult = database.query(McdDB.TABLE_ACTIVITY, new String[]{
    				McdDB.COLUMN_ID}, 
    				McdDB.COLUMN_ACTIVITY_NAME + " = ? AND " +
    				McdDB.COLUMN_TMP_ACTIVITY_HASH + " = ?",
    				new String[]{getVisibleActivity().getLocalClassName(), 
    				String.valueOf(getVisibleActivity().hashCode())},
    				null, null, null);
    		if(!activityResult.moveToFirst()){
    			ContentValues values = new ContentValues();
    			values.put(McdDB.COLUMN_ACTIVITY_NAME, getVisibleActivity().getLocalClassName());
    			values.put(McdDB.COLUMN_TMP_ACTIVITY_HASH, getVisibleActivity().hashCode());
    			activityID = (int) database.insert(McdDB.TABLE_ACTIVITY, null, values);
    		}else{
    			activityID = activityResult.getInt(
    					activityResult.getColumnIndexOrThrow(McdDB.COLUMN_ID));
    		}
    		
    		activityResult.close();
    		activityResult = null;
    		
    		int windowSize = popupWindows.size();
    		
    		//store and trigger popupWindow events only if there are any ViewGroup based popupWindows
        	if(popupWindows.size() > 0 && viewRootLimit == -1){
        		int viewLevelsSize = WindowManagerImpl.getDefault().getViewRoots().length;
        		Pair<Integer, HashSet<Integer>> matchingPopups = null;
        		HashSet<Integer> matchingScreens = new HashSet<Integer>();
        		int[] matchingPopupIDs = new int[windowSize];
        		boolean matchingScreenExists = true;
        		int tmpPopupID = -1;
        		int tmpViewID;
        		int tmpEventType;
        		
        		if(prevEventID == -1){
        			tmpViewID = -1;
        			tmpEventType = -1;
        		}else if(prevUiEventType == -1){
    				//event that created popup windows is an intent, here event type gives the intent id
    				tmpViewID = -1;
    				tmpEventType = prevEventID;
    				
    			}else if(tmpPopID != -1 && popupsCreatedOnLastEvent == 0){
    				//previous event was on a popup window and no new windows were created and,
        			//the popup windows are'nt yet destroyed
    				Cursor popupRes = database.query(McdDB.TABLE_POPUP, new String[]{
    						McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_EVENT_TYPE}, McdDB.COLUMN_ID 
    						+ " = ?", new String[]{String.valueOf(tmpPopID)},
    								null, null, null);
    				
    				if(!(popupRes.moveToFirst())){
						mcdRaiseException("Android Bug-checker found an invalid key in" +
								" Popup table", database);
    				}
    				tmpViewID = popupRes.getInt(popupRes.getColumnIndexOrThrow(
    						McdDB.COLUMN_VIEW_ID));
    				tmpEventType = popupRes.getInt(
							popupRes.getColumnIndexOrThrow(McdDB.COLUMN_EVENT_TYPE));
    				popupRes.close();
    				popupRes = null;
    			}else{
    				tmpViewID = prevViewID;
    				tmpEventType = prevUiEventType;
    				windowSize = popupsCreatedOnLastEvent; 
    			}
    			
				for(int k=1; k<=windowSize; k++){
					if(WindowManagerImpl.getDefault().getViewRoots()[viewLevelsSize - k].getView() 
							instanceof ViewGroup){
	    				matchingPopups = doIdenticalPopupsExist(tmpViewID, tmpEventType, k, 
	    						WindowManagerImpl.getDefault().getViewRoots()[viewLevelsSize - k].
	    						getView(), database);
	    				
	    				if(matchingPopups == null){ //no matching popups found
	    					matchingScreenExists = false;
	    					
	    					ContentValues popValues = new ContentValues();
	    					popValues.put(McdDB.COLUMN_VIEW_ID, tmpViewID);
	    					popValues.put(McdDB.COLUMN_EVENT_TYPE, tmpEventType);
	    					popValues.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
	    					tmpPopupID = (int) database.insert(McdDB.TABLE_POPUP, null, popValues);
	    					if(!(tmpPopupID > -1)){
	    						mcdRaiseException("Android Bug-checker had a failure when " +
											"inserting a row into Popup table", database);
	    					}
	        				matchingPopupIDs[k-1] = tmpPopupID;
	        				
	        				storeScreenElements(WindowManagerImpl.getDefault().getViewRoots()
	        						[viewLevelsSize - k].getView(), activityID, 
	        						getVisibleActivity().getLocalClassName(), tmpPopupID, 0, 
	        						database);
	        				
	    				}else if(matchingPopups.second.size() != 0 && matchingScreenExists){
	        				if(k == 1){
	        					matchingScreens = matchingPopups.second;
	        				}else{
	        					matchingScreens.retainAll(matchingPopups.second);
	        					if(matchingScreens.size() == 0)
	        						matchingScreenExists = false;
	        				}
	        				matchingPopupIDs[k-1] = matchingPopups.first.intValue();
	    				}else{
	    					matchingScreenExists = false;
	    					matchingPopupIDs[k-1] = matchingPopups.first.intValue();
	    				}
					}
				}
    				
				if(matchingScreenExists){
					boolean matchFound = false;
					for(Integer tmpScreenID : matchingScreens){
						screenID = tmpScreenID.intValue();
						Cursor tmp = database.query(McdDB.TABLE_POPUPS_SCREEN, new String[]{
		        				McdDB.COLUMN_POPUP_ID}, 
		        				McdDB.COLUMN_SCREEN_ID + " ?", new String[]{String.valueOf(screenID)}, 
		        				null, null, null);
						
						if(tmp.getCount() == windowSize){ 
							matchFound = true;
							break;
						}
						tmp.close();
						tmp = null;
					}
					if(!matchFound){
						ContentValues values = new ContentValues();
				        values.put(McdDB.COLUMN_ACTIVITY_NAME, 
				        		getVisibleActivity().getLocalClassName());
				        values.put(McdDB.COLUMN_SCREEN_HASH, 0);
				        screenID = (int) database.insert(McdDB.TABLE_SCREEN, null,
				                values);
				        
				        if(!(screenID > -1)){
							mcdRaiseException("Android Bug-checker had a failure in" +
										" inserting a row into Screen table", database);
				        }
				        
				        values = new ContentValues();
				        values.put(McdDB.COLUMN_SCREEN_ID, screenID);
				        values.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
				        values.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, screenHash);
				        ui_envID = (int) database.insert(McdDB.TABLE_UI_ENV, null, values);
				        
				        for(int j=0; j<windowSize; j++){
				        	if(WindowManagerImpl.getDefault().getViewRoots()
				        			[viewLevelsSize - (j+1)].getView() instanceof ViewGroup){
					        	values = new ContentValues();
					        	values.put(McdDB.COLUMN_POPUP_ID, matchingPopupIDs[j]);
					        	values.put(McdDB.COLUMN_VIEWROOT_LEVEL, j+1);
					        	values.put(McdDB.COLUMN_SCREEN_ID, screenID);
					        	database.insert(McdDB.TABLE_POPUPS_SCREEN, null, values);
				        	}
				        }
					}else{
    					Cursor ui_envRes = database.query(McdDB.TABLE_UI_ENV, new String[]{
            					McdDB.COLUMN_ID}, 
                				McdDB.COLUMN_ACTIVITY_ID + " = ? AND " +
                				McdDB.COLUMN_SCREEN_ID + " = ? AND " +
                				McdDB.COLUMN_SCREEN_ACTIVITY_HASH + " = ?",
                				new String[]{String.valueOf(activityID), String.valueOf(screenID),
    							String.valueOf(screenHash)},
                				null, null, null);
    					if(ui_envRes.moveToFirst()){
    						ui_envID = ui_envRes.getInt(
    								ui_envRes.getColumnIndexOrThrow(McdDB.COLUMN_ID));
    					}else{
    						ContentValues uiEnvTmp = new ContentValues();
    						uiEnvTmp.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
    						uiEnvTmp.put(McdDB.COLUMN_SCREEN_ID, screenID);
    						uiEnvTmp.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, screenHash);
    						ui_envID = (int) database.insert(McdDB.TABLE_UI_ENV, null, uiEnvTmp);
    					}
					}
					
				}else{
					    ContentValues values = new ContentValues();
				        values.put(McdDB.COLUMN_ACTIVITY_NAME, 
				        		getVisibleActivity().getLocalClassName());
				        values.put(McdDB.COLUMN_SCREEN_HASH, 0);
				        screenID = (int) database.insert(McdDB.TABLE_SCREEN, null,
				                values);
				        
				        if(!(screenID > -1)){
							mcdRaiseException("Android Bug-checker had a failure" +
										" inserting a row into Screen table", database);
				        }
				        
				        values = new ContentValues();
				        values.put(McdDB.COLUMN_SCREEN_ID, screenID);
				        values.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
				        values.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, screenHash);
				        ui_envID = (int) database.insert(McdDB.TABLE_UI_ENV, null, values);
				        
				        for(int j=0; j<windowSize; j++){
				        	if(WindowManagerImpl.getDefault().getViewRoots()
				        			[viewLevelsSize - (j+1)].getView() instanceof ViewGroup){
					        	values = new ContentValues();
					        	values.put(McdDB.COLUMN_POPUP_ID, matchingPopupIDs[j]);
					        	values.put(McdDB.COLUMN_VIEWROOT_LEVEL, j+1);
					        	values.put(McdDB.COLUMN_SCREEN_ID, screenID);
					        	database.insert(McdDB.TABLE_POPUPS_SCREEN, null, values);
				        	}
				        }
				}
        		
        	}else{  //perform action on some screen view
        		ui_envID = -1;
        	
        		screenID = findIdenticalActivityScreen(WindowManagerImpl.getDefault().getViewRoots()
        				[WindowManagerImpl.getDefault().getViewRoots().length - viewRootLimit].getView(), 
        				getVisibleActivity().getLocalClassName(), database);
        		if(screenID == -1){
        			int[] res = storeScreenElements(WindowManagerImpl.getDefault().getViewRoots()
        					[WindowManagerImpl.getDefault().getViewRoots().length - 
        					 viewRootLimit].getView(), activityID, 
        					getVisibleActivity().getLocalClassName(), -1, screenHash, 
        					database);
        			ui_envID = res[0];
        			screenID = res[1];
        		}else{
        			//check if hash corresponding to this screen exists or else
        			//create a new (act,sceen,hash) entry in ui_env table
        			Cursor hashRes = database.query(McdDB.TABLE_UI_ENV, new String[]{
        					McdDB.COLUMN_ID}, 
            				McdDB.COLUMN_ACTIVITY_ID + " = ? AND " +
            				McdDB.COLUMN_SCREEN_ID + " = ? AND " +
            				McdDB.COLUMN_SCREEN_ACTIVITY_HASH + " = ?",
            				new String[]{String.valueOf(activityID), String.valueOf(screenID),
        					String.valueOf(screenHash)},
            				null, null, null);
        			if(hashRes.moveToFirst()){
        				ui_envID = hashRes.getInt(hashRes.getColumnIndexOrThrow(McdDB.COLUMN_ID));
        			}else{
        				ContentValues values = new ContentValues();
        				values.put(McdDB.COLUMN_ACTIVITY_ID, activityID);
        				values.put(McdDB.COLUMN_SCREEN_ID, screenID);
        				values.put(McdDB.COLUMN_SCREEN_ACTIVITY_HASH, screenHash);
        				ui_envID = (int) database.insert(McdDB.TABLE_UI_ENV, null, values);
        			}
        			hashRes.close();
        			hashRes = null;
        		}
        	}
        	//code to store events of all viewRoots for current screen after creating a 
        	//dummy path node.
        	//then choose an event and trigger it
        	
        	List<Pair<Integer, Integer>> viewIDLst = new ArrayList<Pair<Integer, Integer>>();
        	if(windowSize > 0 && viewRootLimit == -1){
        		Cursor tmp = database.query(McdDB.TABLE_POPUPS_SCREEN, new String[]{
        				McdDB.COLUMN_POPUP_ID, McdDB.COLUMN_VIEWROOT_LEVEL}, 
        				McdDB.COLUMN_SCREEN_ID + " ?", new String[]{String.valueOf(screenID)}, 
        				null, null, McdDB.COLUMN_VIEWROOT_LEVEL + " ASC");
        		if(!(tmp.moveToFirst())){
					mcdRaiseException("Android Bug-checker encountered a screen" +
							" with no associated popup windows",database);
        		}
        		Cursor rootRes;
        		for(int h=0; h<tmp.getCount(); h++){
        			rootRes = database.query(McdDB.TABLE_VIEW_NODE, new String[]{
            				McdDB.COLUMN_ID}, 
            				McdDB.COLUMN_POPUP_ID + " = ? AND " +
            				McdDB.COLUMN_PARENT_ID + " = ?",
            				new String[]{String.valueOf(tmp.getInt(tmp.getColumnIndexOrThrow(
            						McdDB.COLUMN_POPUP_ID))), String.valueOf(-1)},
            				null, null, null);
            		if(!(rootRes.moveToFirst())){
						mcdRaiseException("Android Bug-checker hit a popup window" +
									" with no rootView", database);
            		}
        			viewIDLst.add(new Pair<Integer, Integer>(rootRes.getInt(
        					rootRes.getColumnIndexOrThrow(McdDB.COLUMN_ID)),
        					tmp.getInt(tmp.getColumnIndexOrThrow(McdDB.COLUMN_VIEWROOT_LEVEL))));
        			
        			rootRes.close();
        			rootRes = null;
        			tmp.moveToNext();
        		}
        		
        		tmp.close();
        		tmp = null;
        	}else{
//        		n = 1;
        		Cursor rootRes = database.query(McdDB.TABLE_VIEW_NODE, new String[]{
        				McdDB.COLUMN_ID}, 
        				McdDB.COLUMN_SCREEN_ID + " = ? AND " +
        				McdDB.COLUMN_PARENT_ID + " = ?",
        				new String[]{String.valueOf(screenID), String.valueOf(-1)},
        				null, null, null);
        		if(!(rootRes.moveToFirst())){
					mcdRaiseException("Android Bug-checker hit a screen with no root view", database);
        		}
    			viewIDLst.add(new Pair<Integer, Integer>(rootRes.getInt(rootRes.getColumnIndexOrThrow(McdDB.COLUMN_ID)),
    					viewRootLimit));
    			rootRes.close();
    			rootRes = null;
        	}
        	
        	int pathNodeID = createDummyPathNode(ui_envID, database);
        	
        	
        	for(int i=0; i<viewIDLst.size(); i++){
        		storeCurrentUIEvents(WindowManagerImpl.getDefault().getViewRoots()
        				[WindowManagerImpl.getDefault().getViewRoots().length - viewIDLst.get(i).second].getView(), 
        				viewIDLst.get(i).first,	pathNodeID, database);
        	}
        	
        	if(getVisibleActivity() != null){
        		addEventToUnexploredList(BACK_CLICK_EVENT_ID, UI_EVENT, pathNodeID, KEY_PRESS_EVENT_PRIORITY, database);
        		addEventToUnexploredList(MENU_CLICK_EVENT_ID, UI_EVENT, pathNodeID, 15, database);
        		addEventToUnexploredList(ROTATE_SCREEN_EVENT_ID, UI_EVENT, pathNodeID, KEY_PRESS_EVENT_PRIORITY, database);
        	}
        	
        	int[] nextEvent = selectNextEventToTrigger(pathNodeID, ui_envID, activityID, database);
        	updatePathNodeWithEvent(pathNodeID, nextEvent[0], nextEvent[1], database);
        	if(nextEvent[1] == -1){
        		//no event to be triggered at this point. so backtrack
        		//this also means no event is stored in unexplored_events table for this pathnodeId
        		
        		//delete the dummy node created
        		database.delete(McdDB.TABLE_UNEXPLORED_EVENTS, McdDB.COLUMN_NODE_ID + " = ?", 
        				new String[]{String.valueOf(pathNodeID)});
        		database.delete(McdDB.TABLE_PATH_NODE, McdDB.COLUMN_ID + " = ?", 
        				new String[]{String.valueOf(pathNodeID)});
        		Cursor btCursor = database.rawQuery("SELECT COUNT(*) FROM " + McdDB.TABLE_PATH, null);
        		btCursor.moveToFirst();
        		if(btCursor.getInt(0) == 0){
        			btCursor.close();
            		backtrack(database, mcdDB, FLAG_STOP);
            		return;
        		}else{
        			btCursor.close();
            		backtrack(database, mcdDB, FLAG_NO_ERROR);
            		return;
        		}
        	}else if(nextEvent[1] == UI_EVENT){ //eventType is UIevent
        		
        		Cursor res = database.query(McdDB.TABLE_UI_EVENT, new String[]{
        				McdDB.COLUMN_VIEW_ID, McdDB.COLUMN_UI_EVENT_TYPE}, 
        				McdDB.COLUMN_ID + " = ?",
        				new String[]{String.valueOf(nextEvent[0])},
        				null, null, null);
        		
	        	if(!(res.moveToFirst())){
					mcdRaiseException("Android Bug-checker found an invalid key in" +
								" UiEvent table", database);
	        	}
	        	int viewID = res.getInt(
	        			res.getColumnIndexOrThrow(McdDB.COLUMN_VIEW_ID));
	        	int uiEventType = res.getInt(res.getColumnIndexOrThrow(
	        			McdDB.COLUMN_UI_EVENT_TYPE));
	        	res.close();
	        	res = null;
	        	
	        	String textData = null;
	        	View tmpView = null;
	        	if(uiEventType != EVENT_BACK && uiEventType != EVENT_MENU_CLICK && uiEventType != EVENT_ROTATE_SCREEN){
		        	tmpView = reachAndGetViewFromID(viewID, screenID, viewRootLimit, database);
		        	if(textDataViewSet.contains(uiEventType)){
		        		textData = retrieveNextDataForView(tmpView, viewID, getVisibleActivity().getLocalClassName(), 
								pathNodeID, database);
		        	}
	        	}
	        	
	        	addToPath(pathNodeID, -1, database);
	        	Log.v(TAG, "triggered event on pathnode:" + String.valueOf(pathNodeID));
//	        	printMessageDetails("\ntriggered event:" + String.valueOf(nextEvent[0]) + 
//	        			"    at node:" + String.valueOf(pathNodeID));
	        	popupsCreatedOnLastEvent = 0;
				isCorrectViewOnTop = false;
	        	previousEventActivity = getVisibleActivity().hashCode();
	        	isPreviousBackClickEvent = false;
	        	isPreviousMenuClickEvent = false;
	        	abcHangCounter = -1;
	        	triggerUIEvent(tmpView, uiEventType, textData);
	        	tmpView = null; //to avoid any window leak
        	}else if(nextEvent[1] == INTENT_EVENT){
        		//eventType is intent
        		Log.v(TAG, "trigger intent event");
        	}
//        }
        }
        
        try{
        if(database != null && database.isOpen()){
        	database.close();
	        mcdDB.close();
        }
        }catch(SQLiteException e){
        	Log.e(TAG, "sqliteException when closing database hit");
        }
        catch(NullPointerException e){
        	Log.e(TAG, "nullPointerException when closing database hit");
        }
	}
	}
	
	public void traverseGUI(ViewGroup vg){
		if(vg != null){
    		for(int i=0; i < vg.getChildCount(); i++){
    			View v = vg.getChildAt(i);
    			int hash = v.hashCode();
    			int guid = v.getId();
    			
    			if(v.isActivated()){
    				Log.v("model_checking", "view is activated");
    			}
    			
    			if(v.isEnabled() && (v.getVisibility() == View.VISIBLE)){
    				
    				if(v.isClickable()){
    					Log.v("model_checking", "is clickable");
    				}
    				
    				if(v.isLongClickable()){
    					Log.v("model_checking", "is long clickable");
    				}
    				
    				if(v.isInEditMode()){
    					Log.v("model_checking", "is editable");
    				}
    				
    			if(ViewGroup.class.isInstance(v)){
//    				Log.v("model_checking", "view is an instance of viewGroup");
    				traverseGUI((ViewGroup)v);
    				
//					}
    			}
    		}
    		}
    	}else
    		return;
		
		return;
	}
	
	public void printMessageDetails(String msg){
		if(Looper.getMainLooper().getThread() == Thread.currentThread()){

    		//log msg into a file
    		File dir = Looper.mcd.getContext().getDir("mcd_dir", Context.MODE_PRIVATE); 
    		File file = new File(dir, "mcd_msgLogs");
    		
    		msg+="\n";
			try {
				byte[] contentInBytes = msg.getBytes();
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
			}
    	 }
    	
	}
	
	public void printMessageDetails(Message msg){
		if(msg != null && Looper.getMainLooper().getThread() == Thread.currentThread()){

    		//log msg into a file
    		File dir = Looper.mcd.getContext().getDir("mcd_dir", Context.MODE_PRIVATE); 
    		File file = new File(dir, "mcd_msgLogs");
    		String msgTxt = "msg:" + msg.toString() + "  what:";
    		msgTxt+=String.valueOf(msg.what);
    		msgTxt+="  target:";
    		if(msg.target != null)
    			msgTxt+=msg.target.toString();
    		msgTxt+="  callback:";
    		if(msg.callback != null)
    			msgTxt+=msg.callback.toString();
    		msgTxt+="  nextMsg:";
    		if(msg.next != null)
    			msgTxt+=msg.next.toString();
    		msgTxt+="  threadID:"+Thread.currentThread().getId();
    		msgTxt+="\n";
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
			}
    	 }
    	
	}
}
