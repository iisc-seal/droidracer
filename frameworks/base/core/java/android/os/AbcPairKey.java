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

package android.os;

public class AbcPairKey {
	int hash1;
	int hash2;
	
	public AbcPairKey(int hash1, int hash2){
		this.hash1 = hash1;
		this.hash2 = hash2;
	}

@Override
public int hashCode(){
    return hash1 + hash2;
}

@Override
public boolean equals(Object o)
{
    if(o == null || !(o instanceof AbcPairKey)) 
    	return false;
    AbcPairKey apk = (AbcPairKey)o;
    return (apk.hash1 == hash1 && apk.hash2 == hash2);
}

}
