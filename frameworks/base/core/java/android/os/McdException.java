/* Custom exception used by ModelCheckingDriver
 * 
 * Author: Pallavi Maiya
 */

package android.os;

import android.util.Log;

public class McdException extends Exception{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public McdException(String message){
		super(message);
		Log.e("abc",message);
	}
}
