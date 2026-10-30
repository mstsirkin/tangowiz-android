package org.tangowiz.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/** Compact catalog-bound optional DJ priors: unsigned 64-bit ID plus three bytes. */
public final class DjPriorTable {
 public static final String FILE_NAME="dj-priors.bin";
 public final String version; public final byte[] catalogHash; public final double[] weights;
 private final long[] ids; private final int[] values;
 private DjPriorTable(String v,byte[] h,double[] w,long[] i,int[] b){version=v;catalogHash=h;weights=w;ids=i;values=b;}
 public static long hash(String id){long h=0xcbf29ce484222325L;for(byte b:id.getBytes(StandardCharsets.UTF_8)){h^=b&255;h*=0x100000001b3L;}return h;}
 public static DjPriorTable decode(byte[] data)throws IOException{
  if(data.length<62||data.length>4*1024*1024)throw new IOException("Invalid DJ data size");
  DataInputStream in=new DataInputStream(new ByteArrayInputStream(data));byte[] magic=new byte[8];in.readFully(magic);if(!Arrays.equals(magic,"TWDJPR01".getBytes(StandardCharsets.US_ASCII)))throw new IOException("Invalid DJ format");
  if(in.readInt()!=1)throw new IOException("Unsupported DJ version");int count=in.readInt();if(count<0||count>200000)throw new IOException("Invalid DJ record count");double[] weights=new double[3];double total=0;for(int i=0;i<3;i++){weights[i]=in.readFloat();if(!Double.isFinite(weights[i])||weights[i]<0||weights[i]>.030001)throw new IOException("Invalid DJ weight");total+=weights[i];}if(total>.070001)throw new IOException("DJ percentage too large");
  byte[] catalog=new byte[32];in.readFully(catalog);int len=in.readUnsignedShort();if(len<1||len>100)throw new IOException("Invalid DJ data version");byte[] text=new byte[len];in.readFully(text);String version=new String(text,StandardCharsets.UTF_8);if(!version.matches("[a-zA-Z0-9._-]+"))throw new IOException("Invalid DJ data label");if(in.available()!=count*11)throw new IOException("Incomplete DJ data");
  long[] ids=new long[count];int[] values=new int[count];for(int i=0;i<count;i++){ids[i]=in.readLong();if(i>0&&Long.compareUnsigned(ids[i-1],ids[i])>=0)throw new IOException("Duplicate or unordered DJ IDs");values[i]=(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();}
  return new DjPriorTable(version,catalog,weights,ids,values);
 }
 public double[] bonuses(String id){long key=hash(id);int lo=0,hi=ids.length-1;while(lo<=hi){int mid=(lo+hi)>>>1;int c=Long.compareUnsigned(ids[mid],key);if(c==0){int v=values[mid];return new double[]{((v>>>16)&255)/255.0*weights[0],((v>>>8)&255)/255.0*weights[1],(v&255)/255.0*weights[2]};}if(c<0)lo=mid+1;else hi=mid-1;}return new double[3];}
 public static double adjusted(double acoustic,double relative){if(!Double.isFinite(acoustic)||!Double.isFinite(relative)||relative<0||relative>.070001)throw new IllegalArgumentException("Invalid DJ score");return acoustic<=0?acoustic:Math.max(acoustic,Math.min(1,acoustic*(1+relative)));}
}
