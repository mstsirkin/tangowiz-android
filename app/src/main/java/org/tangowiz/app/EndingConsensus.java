package org.tangowiz.app;
import java.util.*;
/** Streaming port of the frozen native v8 warning experiment; independent of matcher resets. */
public final class EndingConsensus {
 public static final class Candidate {
  public final String family,source;public final double score,reference,end,speed;
  public Candidate(String f,String s,double c,double r,double e,double v){family=f;source=s;score=c;reference=r;end=e;speed=v;}
 }
 public static final class Warning {
  public final String family;public final double end,score,jitter;public final int observations;
  Warning(String f,Estimate e){family=f;end=e.end;score=e.score;jitter=e.jitter;observations=e.n;}
 }
 private static final class Observation {final double t,end,score;Observation(double t,double e,double s){this.t=t;end=e;score=s;}}
 private static final class Track {String family;double sampled=Double.NEGATIVE_INFINITY;final ArrayDeque<Observation> history=new ArrayDeque<>();}
 private static final class Estimate {double end,last,score,jitter;int n;}
 private final Map<String,Track> tracks=new TreeMap<>();private final Map<String,Double> fired=new HashMap<>();
 private double previous=Double.NEGATIVE_INFINITY;
 public void clear(){tracks.clear();fired.clear();previous=Double.NEGATIVE_INFINITY;}
 public void clearEvidence(){tracks.clear();}
 private static double median(List<Double> x){Collections.sort(x);return (x.get((x.size()-1)/2)+x.get(x.size()/2))/2;}
 private void add(Candidate c,double query,double now,int frames){
  if(c==null||c.family==null||c.family.isEmpty()||c.source==null||c.source.isEmpty()||!Double.isFinite(c.score)||c.score<.45||frames<7||!Double.isFinite(c.speed)||c.speed<.85||c.speed>1.15||!Double.isFinite(c.reference)||c.reference<0||!Double.isFinite(c.end)||c.end<=c.reference)return;
  double end=query-(frames-1)*.5*c.speed+(c.end-c.reference)*c.speed;
  if(!Double.isFinite(end)||end<now-3||end-now>180)return;
  Track tr=tracks.computeIfAbsent(c.source,k->new Track());
  if(tr.family!=null&&!tr.family.equals(c.family)){tr.history.clear();tr.sampled=Double.NEGATIVE_INFINITY;}
  tr.family=c.family;if(now-tr.sampled<.99)return;tr.sampled=now;tr.history.addLast(new Observation(now,end,c.score));
 }
 private Estimate estimate(Track tr,double now){
  while(!tr.history.isEmpty()&&now-tr.history.peekFirst().t>20)tr.history.removeFirst();if(tr.history.size()<8)return null;
  List<Double> ends=new ArrayList<>();for(Observation o:tr.history)ends.add(o.end);Collections.sort(ends);
  int a=0,ba=0,bb=0;for(int b=0;b<ends.size();b++){while(ends.get(b)-ends.get(a)>3)a++;if(b-a>bb-ba){ba=a;bb=b;}}
  if(bb-ba+1<8||bb-ba+1<.70*ends.size())return null;
  Estimate e=new Estimate();e.end=median(new ArrayList<>(ends.subList(ba,bb+1)));double first=Double.POSITIVE_INFINITY;e.last=Double.NEGATIVE_INFINITY;
  List<Double> scores=new ArrayList<>(),deviations=new ArrayList<>();
  for(Observation o:tr.history)if(o.end>=ends.get(ba)&&o.end<=ends.get(bb)){first=Math.min(first,o.t);e.last=Math.max(e.last,o.t);scores.add(o.score);deviations.add(Math.abs(o.end-e.end));e.n++;}
  e.score=median(scores);e.jitter=median(deviations);return e.last-first>=12&&now-e.last<=3&&e.score>=.48&&e.jitter<=1?e:null;
 }
 private static boolean supports(Candidate top,Candidate rival,String f){return top!=null&&top.family.equals(f)&&top.score>=.45||rival!=null&&rival.family.equals(f)&&rival.score>=.45&&top!=null&&top.score-rival.score<.035;}
 public interface RecentAudio {Candidate match();}
 /** Recent query runs only at an eligible deadline. Inputs use audio time, not wall time. */
 public Warning observe(double query,double now,int frames,boolean timely,Candidate top,Candidate rival,RecentAudio recent){
  if(!Double.isFinite(query)||!Double.isFinite(now))return null;if(now<previous)clear();previous=now;
  add(top,query,now,frames);if(rival!=null&&(top==null||!rival.source.equals(top.source)))add(rival,query,now,frames);
  Map<String,Estimate> families=new TreeMap<>();Map<String,double[]> bounds=new HashMap<>();
  Iterator<Map.Entry<String,Track>> it=tracks.entrySet().iterator();
  while(it.hasNext()){
   Track tr=it.next().getValue();Estimate e=estimate(tr,now);if(tr.history.isEmpty()){it.remove();continue;}if(e==null)continue;
   boolean rejected=rival!=null&&top!=null&&rival.family.equals(tr.family)&&!top.family.equals(tr.family)&&top.score-rival.score>=.035;
   if(!supports(top,rival,tr.family)&&rejected)continue;
   double[] range=bounds.computeIfAbsent(tr.family,k->new double[]{e.end,e.end});range[0]=Math.min(range[0],e.end);range[1]=Math.max(range[1],e.end);
   if(e.end-now<6)continue;Estimate old=families.get(tr.family);if(old==null||e.end<old.end)families.put(tr.family,e);
  }
  List<Map.Entry<String,Estimate>> eligible=new ArrayList<>();for(Map.Entry<String,Estimate> item:families.entrySet())if(bounds.get(item.getKey())[1]-bounds.get(item.getKey())[0]<=20)eligible.add(item);
  eligible.sort((a,b)->Double.compare(b.getValue().score,a.getValue().score));if(eligible.isEmpty())return null;
  String key=eligible.get(0).getKey();Estimate e=eligible.get(0).getValue();
  if(eligible.size()>1&&eligible.get(1).getValue().score>=e.score-.035)return null;
  if(!supports(top,rival,key)||!timely||now-e.last>2.5)return null;if(top!=null&&!top.family.equals(key)&&top.score>=e.score+.035)return null;
  if(fired.containsKey(key)&&Math.abs(fired.get(key)-e.end)<60)return null;double remaining=e.end-now;if(remaining>8||remaining<6)return null;
  Candidate confirmation=recent.match();if(confirmation==null||!key.equals(confirmation.family)||!Double.isFinite(confirmation.score)||confirmation.score<.50)return null;
  fired.put(key,e.end);return new Warning(key,e);
 }
}
