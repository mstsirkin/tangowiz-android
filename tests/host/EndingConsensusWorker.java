import java.io.*;
import org.tangowiz.app.EndingConsensus;
/** Frozen prototype line protocol for Android/host decision parity. */
public final class EndingConsensusWorker {
 public static void main(String[] args)throws Exception{
  EndingConsensus p=new EndingConsensus();BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
  for(String line;(line=in.readLine())!=null;){
   if(line.equals("RESET")){p.clear();continue;}
   String[] v=line.split("\t",-1);
   EndingConsensus.Candidate top=candidate(v,5,20),rival=candidate(v,11,21);
   EndingConsensus.Warning w=p.observe(Double.parseDouble(v[1]),Double.parseDouble(v[2]),Integer.parseInt(v[3]),v[4].equals("1"),top,rival,()->new EndingConsensus.Candidate(v[17],"recent",Double.parseDouble(v[18]),0,1,1));
   if(w!=null)System.out.println(v[0]+"\t"+w.family+"\t"+v[2]+"\t"+w.end+"\t"+w.score+"\t"+w.jitter+"\t"+w.observations);
  }
 }
 static EndingConsensus.Candidate candidate(String[] v,int k,int model){return new EndingConsensus.Candidate(v[k],v[model],Double.parseDouble(v[k+2]),Double.parseDouble(v[k+3]),Double.parseDouble(v[k+4]),Double.parseDouble(v[k+5]));}
}
