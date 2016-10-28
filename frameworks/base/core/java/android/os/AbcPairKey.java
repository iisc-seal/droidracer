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