import java.io.*;
import org.tangowiz.app.SongEndPredictor;
/** Line protocol around the unmodified production Java song-end predictor. */
public final class PhoneSongEndWorker {
 public static void main(String[] args)throws Exception {
  SongEndPredictor p=new SongEndPredictor();BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
  for(String line;(line=in.readLine())!=null;){String[] x=line.split("\t");
   if(x[0].equals("C")){p.clear();System.out.println("OK");}
   else if(x[0].equals("R")){var r=p.takeReset(Double.parseDouble(x[1]));System.out.println(r==null?"NONE":"RESET\t"+r.endSeconds+"\t"+r.identity);}
   else if(x[0].equals("O")){p.observe(Double.parseDouble(x[1]),x[2],Double.parseDouble(x[3]),Double.parseDouble(x[4]),Double.parseDouble(x[5]),Integer.parseInt(x[6]),Double.parseDouble(x[7]));System.out.println("OK");}
   else throw new IllegalArgumentException("Unknown command");
  }
 }
}
