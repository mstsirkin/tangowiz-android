package org.tangowiz.app;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;
public class DjPriorTableTest {
 private byte[] fixture()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(b);d.writeBytes("TWDJPR01");d.writeInt(1);d.writeInt(1);d.writeFloat(.025f);d.writeFloat(.025f);d.writeFloat(.02f);d.write(new byte[32]);d.writeShort(4);d.writeBytes("test");d.writeLong(DjPriorTable.hash("known"));d.writeByte(255);d.writeByte(255);d.writeByte(255);return b.toByteArray();}
 @Test public void smallRelativeBonusCannotCreateSignal(){assertEquals(0,DjPriorTable.adjusted(0,.07),0);assertEquals(.535,DjPriorTable.adjusted(.5,.07),1e-12);assertEquals(.0107,DjPriorTable.adjusted(.01,.07),1e-12);assertEquals(1,DjPriorTable.adjusted(.99,.07),0);assertEquals(1.01,DjPriorTable.adjusted(1.01,.07),0);assertEquals(-.1,DjPriorTable.adjusted(-.1,.07),0);}
 @Test public void lookupHasThreeIndependentComponents()throws Exception{DjPriorTable t=DjPriorTable.decode(fixture());assertArrayEquals(new double[]{.025,.025,.02},t.bonuses("known"),1e-8);assertArrayEquals(new double[]{0,0,0},t.bonuses("merely-known-name-without-data"),0);}
 @Test public void exactLengthAndSchemaAreRequired()throws Exception{byte[] f=fixture();assertThrows(IOException.class,()->DjPriorTable.decode(java.util.Arrays.copyOf(f,f.length-1)));f[0]=0;assertThrows(IOException.class,()->DjPriorTable.decode(f));}
 @Test public void excessiveWeightRejected()throws Exception{byte[] f=fixture();java.nio.ByteBuffer.wrap(f).putFloat(16,.5f);assertThrows(IOException.class,()->DjPriorTable.decode(f));}
}
