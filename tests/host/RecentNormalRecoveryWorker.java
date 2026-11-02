import java.io.*;
import org.tangowiz.app.RecentNormalRecovery;

/** Host protocol around the exact production recovery policy, not a transcription. */
public final class RecentNormalRecoveryWorker {
 public static void main(String[] args) throws Exception {
  RecentNormalRecovery p=new RecentNormalRecovery();
  BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
  for(String line;(line=input.readLine())!=null;) {
   String[] x=line.split("\t",-1);
   if(x[0].equals("C")){p.clear();System.out.println("OK");}
   else if(x[0].equals("S"))System.out.println(p.shouldCheck(Double.parseDouble(x[1]),Double.parseDouble(x[2]),Integer.parseInt(x[3]),Boolean.parseBoolean(x[4]))?"1":"0");
   else if(x[0].equals("O"))System.out.println(p.observe(Double.parseDouble(x[1]),x[2],Double.parseDouble(x[3]),Double.parseDouble(x[4]),Double.parseDouble(x[5]),Integer.parseInt(x[6]),Double.parseDouble(x[7]))?"1":"0");
   else throw new IllegalArgumentException("Unknown recovery command");
  }
 }
}
